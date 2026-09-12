package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.PricingConfig
import com.example.domain.model.GradeLevelTuition
import com.example.domain.model.TransportPricing
import kotlinx.coroutines.flow.Flow

/** Pricing configuration repository contract. */
interface PricingRepository {
    fun observe(): Flow<PricingConfig?>
    fun observeGradeLevelTuition(): Flow<List<GradeLevelTuition>>
    /**
     * T-320: the per-destination transport pricing rows of the active config
     * (the REAL zone fees the batch registration bills per child). Read-only
     * exposure — the table is seeded/owned server-side.
     */
    fun observeTransportPricing(): Flow<List<TransportPricing>>
    suspend fun updateRegistrationFee(amount: Long, actorId: String, actorName: String): Result<Unit>
    suspend fun updateLatePenalty(amount: Long, actorId: String, actorName: String): Result<Unit>
    suspend fun updateTuitionForGradeLevel(gradeLevel: String, annualAmount: Long, tranches: Triple<Long, Long, Long>, actorId: String, actorName: String): Result<Unit>
}
