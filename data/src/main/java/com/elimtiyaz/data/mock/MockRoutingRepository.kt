package com.elimtiyaz.data.mock

import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.domain.model.GeoPoint
import com.elimtiyaz.domain.model.OptimizedRoute
import com.elimtiyaz.domain.model.RoutingShift
import com.elimtiyaz.domain.model.RoutingStop
import com.elimtiyaz.domain.model.TripLog
import com.elimtiyaz.domain.model.Vehicle
import com.elimtiyaz.domain.repository.RoutingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

private fun mockDelay() = delay((200L..500L).random())

/** Mock [RoutingRepository] — driver mode with a nearest-neighbour optimiser. */
@Singleton
class MockRoutingRepository @Inject constructor() : RoutingRepository {

    private val log = Logger.withTag("Mock.Routing")
    private val vehicleState = MutableStateFlow(MockData.vehicles)
    private val stopState = MutableStateFlow(MockData.routingStops)
    private val tripLogState = MutableStateFlow(MockData.tripLogs)

    /** Stream all vehicles. */
    override fun vehicles(): Flow<Result<List<Vehicle>>> = vehicleState.map { Result.success(it) }

    /** Stream all routing stops ordered by shift + position. */
    override fun stops(): Flow<Result<List<RoutingStop>>> = stopState.map {
        Result.success(it.sortedWith(compareBy({ it.shift.name }, { it.orderInRoute })))
    }

    /**
     * Optimise a route — runs a nearest-neighbour algorithm against the mock
     * stops anchored at Oran city centre (35.6911, -0.6417). Returns the
     * ordered stops, total distance (km), and duration (min).
     */
    override suspend fun optimizeRoute(vehicleId: String, shift: String): Result<OptimizedRoute> {
        mockDelay()
        val vehicle = vehicleState.value.firstOrNull { it.id == vehicleId }
            ?: return Result.failure("Véhicule $vehicleId introuvable.")
        val shiftEnum = runCatching { RoutingShift.valueOf(shift) }.getOrDefault(RoutingShift.Morning)
        val stops = stopState.value.filter { it.shift == shiftEnum }
        val anchor = GeoPoint(35.6911, -0.6417)
        val ordered = nearestNeighbour(stops, anchor)
        val distance = polylineDistance(ordered.map { GeoPoint(it.lat, it.lng) })
        val route = OptimizedRoute(
            vehicle = vehicle, stops = ordered, totalDistanceKm = distance,
            totalDurationMin = distance * 2.5, // ~2.5 min/km in urban traffic
            polyline = ordered.map { GeoPoint(it.lat, it.lng) },
        )
        log.i { "Optimised route: ${ordered.size} stops, $distance km" }
        return Result.success(route)
    }

    /** Stream recent trip logs. */
    override fun tripHistory(): Flow<Result<List<TripLog>>> = tripLogState.map {
        Result.success(it.sortedByDescending { t -> t.startedAt })
    }

    /** Start a new trip — inserts a TripLog with `endedAt = null`. */
    override suspend fun startTrip(vehicleId: String, driverId: String): Result<TripLog> {
        mockDelay()
        val trip = TripLog(
            id = "tl-new-${UUID.randomUUID().toString().take(6)}", vehicleId = vehicleId, driverId = driverId,
            startedAt = Clock.System.now().toString(), endedAt = null,
            stopsPlanned = stopState.value.size, stopsCompleted = 0, totalDistanceKm = 0.0, notes = null,
        )
        tripLogState.value = tripLogState.value + trip
        log.i { "Started trip for vehicle=$vehicleId driver=$driverId" }
        return Result.success(trip)
    }

    /** End a trip — patches `endedAt`, `stopsCompleted`, `totalDistanceKm`. */
    override suspend fun endTrip(tripId: String, stopsCompleted: Int, totalDistanceKm: Double): Result<TripLog> {
        mockDelay()
        val updated = tripLogState.value.map { t ->
            if (t.id != tripId) t else t.copy(
                endedAt = Clock.System.now().toString(), stopsCompleted = stopsCompleted,
                totalDistanceKm = totalDistanceKm,
            )
        }
        tripLogState.value = updated
        val result = updated.firstOrNull { it.id == tripId }
            ?: return Result.failure("Trajet $tripId introuvable.")
        log.i { "Ended trip $tripId ($stopsCompleted stops, $totalDistanceKm km)" }
        return Result.success(result)
    }

    /** Naive nearest-neighbour from the school anchor. */
    private fun nearestNeighbour(stops: List<RoutingStop>, anchor: GeoPoint): List<RoutingStop> {
        if (stops.isEmpty()) return emptyList()
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

    /** Sum of haversine distances between consecutive points (in km). */
    private fun polylineDistance(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        return points.zipWithNext { a, b -> haversineKm(a, b) }.sum()
    }

    /** Haversine formula for two geo points, returning km. */
    private fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val s = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) *
            sin(dLng / 2).let { it * it }
        return 2 * r * atan2(sqrt(s), sqrt(1 - s))
    }
}
