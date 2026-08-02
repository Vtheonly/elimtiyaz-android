package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * A geographic coordinate (WGS84).
 *
 * Used by the routing module for stop locations, vehicle positions,
 * and optimized-route polylines.
 */
@Serializable
data class GeoPoint(
    val lat: Double,
    val lng: Double,
)

/**
 * Shift during which a stop is served.
 *
 * Wire-protocol: lowercase snake_case (`morning`, `afternoon`, `both`)
 * matching the desktop `RoutingShift` enum.
 */
@Serializable
enum class RoutingShift(val wireCode: String, val displayFr: String) {
    Morning("morning", "Matin"),
    Afternoon("afternoon", "Après-midi"),
    Both("both", "Les deux");

    companion object {
        fun fromCode(code: String?): RoutingShift =
            entries.firstOrNull { it.wireCode.equals(code, ignoreCase = true) } ?: Morning
    }
}

/**
 * A single pickup/drop-off stop on a routing plan.
 *
 * @param orderInRoute 1-indexed position within the optimized route (1 = first stop).
 * @param estimatedMinutesFromPrevious Travel time from the previous stop, estimated by the TSP solver / OSRM.
 */
@Serializable
data class RoutingStop(
    val id: String,
    val studentId: String,
    val studentName: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val shift: RoutingShift = RoutingShift.Morning,
    val orderInRoute: Int = 0,
    val estimatedMinutesFromPrevious: Double = 0.0,
)

/**
 * A vehicle used for student transport.
 *
 * @param hasWheelchairAccess Whether the vehicle is equipped with a wheelchair lift (per plan §16 transport accessibility).
 */
@Serializable
data class Vehicle(
    val id: String,
    val plate: String,
    val driverId: String?,
    val driverName: String?,
    val capacity: Int,
    val hasWheelchairAccess: Boolean = false,
)

/**
 * An optimized route produced by the TSP solver + OSRM.
 *
 * @param polyline The full driving geometry as a list of [GeoPoint]s (straight-line fallback if OSRM unavailable).
 * @param totalDistanceKm Total driving distance in kilometers.
 * @param totalDurationMin Total driving time in minutes.
 */
@Serializable
data class OptimizedRoute(
    val vehicle: Vehicle,
    val stops: List<RoutingStop>,
    val totalDistanceKm: Double,
    val totalDurationMin: Double,
    val polyline: List<GeoPoint>,
)

/**
 * A logged trip — created when a driver starts a route and finalized when they end it.
 *
 * Lifecycle: `started → ended` (no intermediate states in v1).
 */
@Serializable
data class TripLog(
    val id: String,
    val vehicleId: String,
    val driverId: String,
    val startedAt: String,
    val endedAt: String?,
    val stopsPlanned: Int,
    val stopsCompleted: Int,
    val totalDistanceKm: Double,
    val notes: String? = null,
)
