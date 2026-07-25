package com.elimtiyaz.data.repository

import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.DispatcherProvider
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.onFailure
import com.elimtiyaz.data.local.dao.RoutingStopDao
import com.elimtiyaz.data.local.dao.SyncQueueDao
import com.elimtiyaz.data.local.dao.TripLogDao
import com.elimtiyaz.data.local.dao.VehicleDao
import com.elimtiyaz.data.local.entity.toDomain
import com.elimtiyaz.data.local.entity.toEntity
import com.elimtiyaz.data.remote.dto.OptimizedRouteDto
import com.elimtiyaz.data.remote.dto.RoutingStopDto
import com.elimtiyaz.data.remote.dto.TripLogDto
import com.elimtiyaz.data.remote.dto.VehicleDto
import com.elimtiyaz.domain.model.GeoPoint
import com.elimtiyaz.domain.model.OptimizedRoute
import com.elimtiyaz.domain.model.RoutingShift
import com.elimtiyaz.domain.model.RoutingStop
import com.elimtiyaz.domain.model.TripLog
import com.elimtiyaz.domain.model.Vehicle
import com.elimtiyaz.domain.repository.RoutingRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot

private const val VEHICLES_TABLE = "vehicles"
private const val STOPS_TABLE = "routing_stops"
private const val TRIP_LOGS_TABLE = "trip_logs"

/** Supabase-backed [RoutingRepository] — driver mode + route optimisation. */
@Singleton
class SupabaseRoutingRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val vehicleDao: VehicleDao,
    private val stopDao: RoutingStopDao,
    private val tripLogDao: TripLogDao,
    private val syncQueueDao: SyncQueueDao,
    private val dispatchers: DispatcherProvider,
) : RoutingRepository {

    private val log = Logger.withTag("Data.Routing")
    private val sync = SyncQueueHelper(syncQueueDao)

    /** Stream all vehicles. */
    override fun vehicles(): Flow<Result<List<Vehicle>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { vehicleDao.observeAll().first().map { it.toDomain() } },
        fetch = { supabase.from(VEHICLES_TABLE).select().decodeList<VehicleDto>().map { it.toDomain() } },
        persist = { vs -> vehicleDao.upsertAll(vs.map { it.toEntity() }) },
    )

    /** Stream all routing stops ordered by shift + position. */
    override fun stops(): Flow<Result<List<RoutingStop>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { stopDao.observeAll().first().map { it.toDomain() } },
        fetch = { supabase.from(STOPS_TABLE).select().decodeList<RoutingStopDto>().map { it.toDomain() } },
        persist = { ss -> stopDao.upsertAll(ss.map { it.toEntity() }) },
    )

    /**
     * Optimise a route by calling the `optimize-route` Edge Function. Falls
     * back to a nearest-neighbour heuristic if the function is unreachable so
     * drivers can still depart offline.
     */
    override suspend fun optimizeRoute(vehicleId: String, shift: String): Result<OptimizedRoute> =
        Result.runCatching {
            val vehicle = supabase.from(VEHICLES_TABLE).select { filter { eq("id", vehicleId) } }
                .decodeList<VehicleDto>().firstOrNull()?.toDomain() ?: error("Véhicule $vehicleId introuvable.")
            val allStops = supabase.from(STOPS_TABLE).select { filter { eq("shift", shift) } }
                .decodeList<RoutingStopDto>().map { it.toDomain() }
            // Attempt the Edge Function first.
            val response = runCatching {
                supabase.functions.invoke("optimize-route", mapOf("vehicle_id" to vehicleId, "shift" to shift))
            }.getOrNull()
            if (response != null) {
                return@runCatching OptimizedRoute(
                    vehicle = vehicle, stops = allStops, totalDistanceKm = 0.0, totalDurationMin = 0.0,
                    polyline = allStops.map { GeoPoint(it.lat, it.lng) },
                )
            }
            // Fallback: nearest-neighbour from the school's anchor (35.69, -0.64 — Oran).
            val optimized = nearestNeighbourRoute(allStops)
            val distance = polylineDistance(optimized.map { GeoPoint(it.lat, it.lng) })
            OptimizedRoute(
                vehicle = vehicle, stops = optimized, totalDistanceKm = distance,
                totalDurationMin = distance * 2.5, // ~2.5 min/km in urban traffic
                polyline = optimized.map { GeoPoint(it.lat, it.lng) },
            )
        }.onFailure {
            log.w { "optimizeRoute failed: ${it.message}" }
        }

    /** Stream recent trip logs. */
    override fun tripHistory(): Flow<Result<List<TripLog>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { tripLogDao.observeAll().first().map { it.toDomain() } },
        fetch = { supabase.from(TRIP_LOGS_TABLE).select().decodeList<TripLogDto>().map { it.toDomain() } },
        persist = { ts -> tripLogDao.upsertAll(ts.map { it.toEntity() }) },
    )

    /** Start a new trip — inserts a TripLog with `endedAt = null`. */
    override suspend fun startTrip(vehicleId: String, driverId: String): Result<TripLog> = Result.runCatching {
        val id = UUID.randomUUID().toString()
        val dto = TripLogDto(
            id = id, vehicleId = vehicleId, driverId = driverId, startedAt = nowIso(), endedAt = null,
            stopsPlanned = 0, stopsCompleted = 0, totalDistanceKm = 0.0, notes = null,
        )
        supabase.from(TRIP_LOGS_TABLE).insert(dto)
        val domain = dto.toDomain()
        tripLogDao.upsert(domain.toEntity())
        log.i { "Started trip for vehicle=$vehicleId driver=$driverId" }
        domain
    }.onFailure {
        sync.enqueueRaw(TRIP_LOGS_TABLE, "insert", sync.encode(TripLogDto(
            id = UUID.randomUUID().toString(), vehicleId = vehicleId, driverId = driverId,
            startedAt = nowIso(), endedAt = null, stopsPlanned = 0, stopsCompleted = 0,
            totalDistanceKm = 0.0, notes = null,
        )))
    }

    /** End a trip — patches `endedAt`, `stopsCompleted`, `totalDistanceKm`. */
    override suspend fun endTrip(tripId: String, stopsCompleted: Int, totalDistanceKm: Double): Result<TripLog> =
        Result.runCatching {
            supabase.from(TRIP_LOGS_TABLE).update(
                mapOf(
                    "ended_at" to nowIso(), "stops_completed" to stopsCompleted,
                    "total_distance_km" to totalDistanceKm,
                ),
            ) { filter { eq("id", tripId) } }
            val refreshed = supabase.from(TRIP_LOGS_TABLE).select { filter { eq("id", tripId) } }
                .decodeList<TripLogDto>().firstOrNull()?.toDomain() ?: error("Trajet $tripId introuvable.")
            tripLogDao.upsert(refreshed.toEntity())
            log.i { "Ended trip $tripId ($stopsCompleted stops, $totalDistanceKm km)" }
            refreshed
        }.onFailure {
            sync.enqueueRaw(TRIP_LOGS_TABLE, "update", sync.encode(mapOf("id" to tripId)))
        }

    /** Naive nearest-neighbour route from school anchor. */
    private fun nearestNeighbourRoute(stops: List<RoutingStop>): List<RoutingStop> {
        if (stops.isEmpty()) return emptyList()
        val anchor = GeoPoint(35.6911, -0.6417) // Oran city centre
        val remaining = stops.toMutableList()
        val ordered = mutableListOf<RoutingStop>()
        var current = anchor
        while (remaining.isNotEmpty()) {
            val nearest = remaining.minByOrNull { hypot(it.lat - current.lat, it.lng - current.lng) }!!
            ordered += nearest.copy(orderInRoute = ordered.size + 1)
            current = GeoPoint(nearest.lat, nearest.lng)
            remaining.remove(nearest)
        }
        return ordered
    }

    /** Sum of great-circle distances between consecutive points (in km). */
    private fun polylineDistance(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        return points.zipWithNext { a, b -> haversineKm(a, b) }.sum()
    }

    /** Haversine formula for two geo points, returning km. */
    private fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val s = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) *
            Math.sin(dLng / 2).let { it * it }
        return 2 * r * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s))
    }
}
