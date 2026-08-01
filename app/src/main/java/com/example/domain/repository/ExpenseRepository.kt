package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.Expense
import kotlinx.coroutines.flow.Flow

/** Expense repository contract. */
interface ExpenseRepository {
    fun observe(): Flow<List<Expense>>
    fun observeByStatus(status: String): Flow<List<Expense>>
    fun observeById(id: String): Flow<Expense?>
    suspend fun submit(input: SubmitExpenseInput, actorId: String, actorName: String): Result<Expense>
    suspend fun approve(id: String, note: String, actorId: String, actorName: String): Result<Expense>
    suspend fun reject(id: String, reason: String, actorId: String, actorName: String): Result<Expense>
    suspend fun disburse(id: String, actorId: String, actorName: String): Result<Expense>
    suspend fun settleProof(id: String, proofPath: String, finalAmount: Long, actorId: String, actorName: String): Result<Expense>
}

/** Input payload for [ExpenseRepository.submit]. */
data class SubmitExpenseInput(
    val title: String, val description: String, val amount: Long,
    val category: String, val payee: String, val urgency: String = "normal",
)
