package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.OptimizedRoute
import com.example.domain.model.RoutingShift
import com.example.domain.model.TripLog
import com.example.domain.model.Vehicle
import com.example.domain.model.RoutingStop
import kotlinx.coroutines.flow.Flow

/**
 * Routing / transport repository.
 *
 * Mirrors the desktop `RoutingRepository` contract. The mobile app uses this
 * for the driver-mode hub (`RoutingScreen`), live navigation (`RoutingMapScreen`),
 * and trip history.
 *
 * All write operations take `actorId` / `actorName` for audit logging.
 */
interface RoutingRepository {

    /** Observe all vehicles (active only). */
    fun observeVehicles(): Flow<Result<List<Vehicle>>>

    /** Observe all routing stops, optionally filtered by shift. */
    fun observeStops(shift: RoutingShift? = null): Flow<Result<List<RoutingStop>>>

    /** Observe trip history (most recent first). */
    fun observeTripHistory(): Flow<Result<List<TripLog>>>

    /**
     * Optimize a route for the given vehicle + shift.
     *
     * Pipeline (mirrors desktop):
     * 1. Try Edge Function `optimize-route`.
     * 2. Fallback: `TspSolver.solveNearestNeighbor` from the Oran anchor.
     * 3. Refine: `TspSolver.twoOptImprove` (max 50 iterations).
     * 4. Optionally fetch real OSRM polyline (polyline6 decoder included).
     * 5. Final fallback: straight-line haversine.
     */
    suspend fun optimizeRoute(
        vehicleId: String,
        shift: RoutingShift,
        actorId: String,
        actorName: String,
    ): Result<OptimizedRoute>

    /**
     * Start a new trip. Creates a `TripLog` with `endedAt = null`.
     * Returns the new trip id.
     */
    suspend fun startTrip(
        vehicleId: String,
        driverId: String,
        driverName: String,
    ): Result<TripLog>

    /**
     * End an in-progress trip. Patches `endedAt`, `stopsCompleted`, `totalDistanceKm`.
     */
    suspend fun endTrip(
        tripId: String,
        stopsCompleted: Int,
        totalDistanceKm: Double,
        actorId: String,
        actorName: String,
    ): Result<TripLog>
}
