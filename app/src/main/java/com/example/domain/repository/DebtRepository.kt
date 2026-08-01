package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.DebtSummary
import com.example.domain.model.Installment
import com.example.domain.model.Payment
import kotlinx.coroutines.flow.Flow

/** Debt repository contract — aggregated parent-debt views. */
interface DebtRepository {
    fun observeSummary(): Flow<List<DebtSummary>>
    fun observeParentProfile(parentId: String): Flow<ParentFinancialProfile?>
    suspend fun sendReminder(parentId: String, actorId: String, actorName: String): Result<Unit>
}

/** Aggregated parent financial profile — totals + installments + recent payments. */
data class ParentFinancialProfile(
    val parentId: String, val parentName: String,
    val totalDue: Long, val totalPaid: Long, val totalOutstanding: Long, val overdueAmount: Long,
    val installments: List<Installment>, val recentPayments: List<Payment>,
)
