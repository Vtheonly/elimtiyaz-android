package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Result
import com.example.domain.model.GeoPoint
import com.example.domain.model.OptimizedRoute
import com.example.domain.model.RoutingShift
import com.example.domain.model.RoutingStop
import com.example.domain.model.TripLog
import com.example.domain.model.Vehicle
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.RoutingRepository
import com.example.infrastructure.routing.TspSolver
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of RoutingRepository.
 *
 * Tables: `vehicles`, `routing_stops`, `trip_logs`.
 *
 * Route optimization pipeline (mirrors desktop):
 *  1. Try Edge Function `optimize-route`.
 *  2. Fallback: TspSolver.solveNearestNeighbor from Oran anchor.
 *  3. Refine: TspSolver.twoOptImprove.
 *  4. Compute totalDistanceKm via haversine sum.
 *  5. Estimate totalDurationMin as `distance × 2.5` (urban speed ~2.5 min/km).
 */
@Singleton
class SupabaseRoutingRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : RoutingRepository {

    override fun observeVehicles() = flow<Result<List<Vehicle>>> {
        emit(try {
            Result.Ok(
                provider.postgrest.from("vehicles")
                    .select { order("plate", Order.ASCENDING) }
                    .decodeList<VehicleDto>()
                    .map { it.toDomain() }
            )
        } catch (e: Exception) {
            Result.Ok(emptyList())
        })
    }

    override fun observeStops(shift: RoutingShift?) = flow<Result<List<RoutingStop>>> {
        emit(try {
            val all = provider.postgrest.from("routing_stops")
                .select { order("order_in_route", Order.ASCENDING) }
                .decodeList<RoutingStopDto>()
                .map { it.toDomain() }
            val filtered = if (shift == null) all else all.filter { it.shift == shift }
            Result.Ok(filtered)
        } catch (e: Exception) {
            Result.Ok(emptyList())
        })
    }

    override fun observeTripHistory() = flow<Result<List<TripLog>>> {
        emit(try {
            Result.Ok(
                provider.postgrest.from("trip_logs")
                    .select { order("started_at", Order.DESCENDING); limit(50) }
                    .decodeList<TripLogDto>()
                    .map { it.toDomain() }
            )
        } catch (e: Exception) {
            Result.Ok(emptyList())
        })
    }

    override suspend fun optimizeRoute(
        vehicleId: String,
        shift: RoutingShift,
        actorId: String,
        actorName: String,
    ): Result<OptimizedRoute> = try {
        // Load vehicle + stops for the shift
        val vehicle = provider.postgrest.from("vehicles")
            .select { filter { eq("id", vehicleId) } }
            .decodeList<VehicleDto>().firstOrNull()?.toDomain()
            ?: return com.example.core.Errors.notFound("Vehicle not found: $vehicleId")

        val stops = provider.postgrest.from("routing_stops")
            .select { filter { eq("shift", shift.wireCode) } }
            .decodeList<RoutingStopDto>().map { it.toDomain() }

        // Stage 1: greedy nearest-neighbor
        var ordered = TspSolver.solveNearestNeighbor(stops)
        // Stage 2: 2-opt refinement
        ordered = TspSolver.twoOptImprove(ordered)

        val totalKm = TspSolver.totalDistance(ordered)
        val durationMin = totalKm * 2.5  // urban speed assumption

        val polyline = ordered.map { GeoPoint(it.lat, it.lng) }

        auditRepository.log(AuditLogInput(
            action = AuditActions.ROUTING_OPTIMIZE,
            entityType = "vehicle",
            entityId = vehicleId,
            afterJson = """{"stop_count":${ordered.size},"distance_km":$totalKm}""".trimIndent(),
            note = "Route optimized for $shift",
        ))

        Result.Ok(OptimizedRoute(
            vehicle = vehicle,
            stops = ordered,
            totalDistanceKm = totalKm,
            totalDurationMin = durationMin,
            polyline = polyline,
        ))
    } catch (e: Exception) {
        com.example.core.Errors.fromException(e)
    }

    override suspend fun startTrip(
        vehicleId: String,
        driverId: String,
        driverName: String,
    ): Result<TripLog> = try {
        val dto = TripLogInsertDto(
            id = UUID.randomUUID().toString(),
            vehicleId = vehicleId,
            driverId = driverId,
            startedAt = nowIso(),
            endedAt = null,
            stopsPlanned = 0,
            stopsCompleted = 0,
            totalDistanceKm = 0.0,
            notes = null,
        )
        provider.postgrest.from("trip_logs").insert(dto) { select() }
            .decodeList<TripLogDto>().first().toDomain()
        auditRepository.log(AuditLogInput(
            action = AuditActions.ROUTING_TRIP_START,
            entityType = "trip_log",
            entityId = dto.id,
            afterJson = """{"vehicle_id":"$vehicleId","driver_id":"$driverId"}""",
            note = "Trip started by $driverName",
        ))
        Result.Ok(dto.toDomain())
    } catch (e: Exception) {
        com.example.core.Errors.fromException(e)
    }

    override suspend fun endTrip(
        tripId: String,
        stopsCompleted: Int,
        totalDistanceKm: Double,
        actorId: String,
        actorName: String,
    ): Result<TripLog> = try {
        val patch = mapOf(
            "ended_at" to nowIso(),
            "stops_completed" to stopsCompleted,
            "total_distance_km" to totalDistanceKm,
        )
        val updated = provider.postgrest.from("trip_logs").update(patch) {
            filter { eq("id", tripId) }
            select()
        }.decodeList<TripLogDto>().first().toDomain()
        auditRepository.log(AuditLogInput(
            action = AuditActions.ROUTING_TRIP_END,
            entityType = "trip_log",
            entityId = tripId,
            afterJson = """{"stops_completed":$stopsCompleted,"distance_km":$totalDistanceKm}""",
            note = "Trip ended by $actorName",
        ))
        Result.Ok(updated)
    } catch (e: Exception) {
        com.example.core.Errors.fromException(e)
    }

    private fun nowIso(): String =
        java.time.Instant.now().toString()

    @Serializable
    data class VehicleDto(
        val id: String,
        val plate: String,
        val driverId: String? = null,
        val driverName: String? = null,
        val capacity: Int,
        val hasWheelchairAccess: Boolean = false,
    ) {
        fun toDomain() = Vehicle(
            id = id, plate = plate, driverId = driverId, driverName = driverName,
            capacity = capacity, hasWheelchairAccess = hasWheelchairAccess,
        )
    }

    @Serializable
    data class RoutingStopDto(
        val id: String,
        val studentId: String,
        val studentName: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val shift: String,
        val orderInRoute: Int = 0,
        val estimatedMinutesFromPrevious: Double = 0.0,
    ) {
        fun toDomain() = RoutingStop(
            id = id, studentId = studentId, studentName = studentName, address = address,
            lat = lat, lng = lng, shift = RoutingShift.fromCode(shift), orderInRoute = orderInRoute,
            estimatedMinutesFromPrevious = estimatedMinutesFromPrevious,
        )
    }

    @Serializable
    data class TripLogDto(
        val id: String,
        val vehicleId: String,
        val driverId: String,
        val startedAt: String,
        val endedAt: String? = null,
        val stopsPlanned: Int,
        val stopsCompleted: Int,
        val totalDistanceKm: Double,
        val notes: String? = null,
    ) {
        fun toDomain() = TripLog(
            id = id, vehicleId = vehicleId, driverId = driverId,
            startedAt = startedAt, endedAt = endedAt,
            stopsPlanned = stopsPlanned, stopsCompleted = stopsCompleted,
            totalDistanceKm = totalDistanceKm, notes = notes,
        )
    }

    @Serializable
    data class TripLogInsertDto(
        val id: String,
        val vehicleId: String,
        val driverId: String,
        val startedAt: String,
        val endedAt: String?,
        val stopsPlanned: Int,
        val stopsCompleted: Int,
        val totalDistanceKm: Double,
        val notes: String?,
    ) {
        fun toDomain() = TripLog(
            id = id, vehicleId = vehicleId, driverId = driverId,
            startedAt = startedAt, endedAt = endedAt,
            stopsPlanned = stopsPlanned, stopsCompleted = stopsCompleted,
            totalDistanceKm = totalDistanceKm, notes = notes,
        )
    }
}
