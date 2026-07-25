package com.elimtiyaz.domain.repository

import com.elimtiyaz.core.common.Result
import com.elimtiyaz.domain.model.OptimizedRoute
import com.elimtiyaz.domain.model.RoutingStop
import com.elimtiyaz.domain.model.TripLog
import com.elimtiyaz.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow

interface RoutingRepository {
    fun vehicles(): Flow<Result<List<Vehicle>>>
    fun stops(): Flow<Result<List<RoutingStop>>>
    suspend fun optimizeRoute(vehicleId: String, shift: String): Result<OptimizedRoute>
    fun tripHistory(): Flow<Result<List<TripLog>>>
    suspend fun startTrip(vehicleId: String, driverId: String): Result<TripLog>
    suspend fun endTrip(tripId: String, stopsCompleted: Int, totalDistanceKm: Double): Result<TripLog>
}
