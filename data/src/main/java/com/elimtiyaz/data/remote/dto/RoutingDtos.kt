package com.elimtiyaz.data.remote.dto

import com.elimtiyaz.domain.model.GeoPoint
import com.elimtiyaz.domain.model.OptimizedRoute
import com.elimtiyaz.domain.model.RoutingShift
import com.elimtiyaz.domain.model.RoutingStop
import com.elimtiyaz.domain.model.TripLog
import com.elimtiyaz.domain.model.Vehicle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire DTO for the `routing_stops` table. */
@Serializable
data class RoutingStopDto(
    val id: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("student_name") val studentName: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val shift: RoutingShift = RoutingShift.Morning,
    @SerialName("order_in_route") val orderInRoute: Int = 0,
    @SerialName("est_minutes_from_prev") val estimatedMinutesFromPrevious: Double = 0.0,
) {
    /** Convert to a domain [RoutingStop]. */
    fun toDomain(): RoutingStop = RoutingStop(
        id = id, studentId = studentId, studentName = studentName, address = address, lat = lat, lng = lng,
        shift = shift, orderInRoute = orderInRoute, estimatedMinutesFromPrevious = estimatedMinutesFromPrevious,
    )

    companion object {
        /** Build a DTO from a domain [RoutingStop]. */
        fun fromDomain(s: RoutingStop): RoutingStopDto = RoutingStopDto(
            id = s.id, studentId = s.studentId, studentName = s.studentName, address = s.address, lat = s.lat,
            lng = s.lng, shift = s.shift, orderInRoute = s.orderInRoute, estimatedMinutesFromPrevious = s.estimatedMinutesFromPrevious,
        )
    }
}

/** Wire DTO for the `vehicles` table. */
@Serializable
data class VehicleDto(
    val id: String,
    val plate: String,
    @SerialName("driver_id") val driverId: String,
    @SerialName("driver_name") val driverName: String,
    val capacity: Int,
    @SerialName("has_wheelchair_lift") val hasWheelchairLift: Boolean = false,
) {
    /** Convert to a domain [Vehicle]. */
    fun toDomain(): Vehicle = Vehicle(
        id = id, plate = plate, driverId = driverId, driverName = driverName,
        capacity = capacity, hasWheelchairLift = hasWheelchairLift,
    )

    companion object {
        /** Build a DTO from a domain [Vehicle]. */
        fun fromDomain(v: Vehicle): VehicleDto = VehicleDto(
            id = v.id, plate = v.plate, driverId = v.driverId, driverName = v.driverName,
            capacity = v.capacity, hasWheelchairLift = v.hasWheelchairLift,
        )
    }
}

/** Wire DTO for the `trip_logs` table. */
@Serializable
data class TripLogDto(
    val id: String,
    @SerialName("vehicle_id") val vehicleId: String,
    @SerialName("driver_id") val driverId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("stops_planned") val stopsPlanned: Int,
    @SerialName("stops_completed") val stopsCompleted: Int,
    @SerialName("total_distance_km") val totalDistanceKm: Double,
    val notes: String? = null,
) {
    /** Convert to a domain [TripLog]. */
    fun toDomain(): TripLog = TripLog(
        id = id, vehicleId = vehicleId, driverId = driverId, startedAt = startedAt, endedAt = endedAt,
        stopsPlanned = stopsPlanned, stopsCompleted = stopsCompleted, totalDistanceKm = totalDistanceKm, notes = notes,
    )

    companion object {
        /** Build a DTO from a domain [TripLog]. */
        fun fromDomain(t: TripLog): TripLogDto = TripLogDto(
            id = t.id, vehicleId = t.vehicleId, driverId = t.driverId, startedAt = t.startedAt, endedAt = t.endedAt,
            stopsPlanned = t.stopsPlanned, stopsCompleted = t.stopsCompleted, totalDistanceKm = t.totalDistanceKm, notes = t.notes,
        )
    }
}

/**
 * Response payload from the `optimize-route` Edge Function. Kept separate from
 * the domain [OptimizedRoute] because the wire format serialises the polyline
 * as a flat array of `[lat,lng]` pairs.
 */
@Serializable
data class OptimizedRouteDto(
    val vehicle: VehicleDto,
    val stops: List<RoutingStopDto>,
    @SerialName("total_distance_km") val totalDistanceKm: Double,
    @SerialName("total_duration_min") val totalDurationMin: Double,
    val polyline: List<List<Double>> = emptyList(),
) {
    /** Convert to a domain [OptimizedRoute]. */
    fun toDomain(): OptimizedRoute = OptimizedRoute(
        vehicle = vehicle.toDomain(),
        stops = stops.map { it.toDomain() },
        totalDistanceKm = totalDistanceKm,
        totalDurationMin = totalDurationMin,
        polyline = polyline.mapNotNull { row ->
            if (row.size >= 2) GeoPoint(lat = row[0], lng = row[1]) else null
        },
    )
}
