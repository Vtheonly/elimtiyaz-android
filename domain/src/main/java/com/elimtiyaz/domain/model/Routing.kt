package com.elimtiyaz.domain.model

import kotlinx.serialization.Serializable

/** Routing — pickup stops and route optimisation (replaces the legacy Traffic app). */
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

@Serializable
enum class RoutingShift { Morning, Afternoon, Both }

@Serializable
data class Vehicle(
    val id: String,
    val plate: String,
    val driverId: String,
    val driverName: String,
    val capacity: Int,
    val hasWheelchairLift: Boolean = false,
)

@Serializable
data class OptimizedRoute(
    val vehicle: Vehicle,
    val stops: List<RoutingStop>,
    val totalDistanceKm: Double,
    val totalDurationMin: Double,
    val polyline: List<GeoPoint>,
)

@Serializable
data class GeoPoint(val lat: Double, val lng: Double)

@Serializable
data class TripLog(
    val id: String,
    val vehicleId: String,
    val driverId: String,
    val startedAt: String,
    val endedAt: String? = null,
    val stopsPlanned: Int,
    val stopsCompleted: Int,
    val totalDistanceKm: Double,
    val notes: String? = null,
)
