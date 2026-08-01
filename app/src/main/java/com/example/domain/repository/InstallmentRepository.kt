package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.Installment
import kotlinx.coroutines.flow.Flow

/** Installment repository contract. */
interface InstallmentRepository {
    fun observeByParent(parentId: String): Flow<List<Installment>>
    fun observeByStudent(studentId: String): Flow<List<Installment>>
    fun observeById(id: String): Flow<Installment?>
    suspend fun markPaid(id: String, actorId: String, actorName: String): Result<Installment>
    suspend fun updateDueDate(id: String, dueDate: String, note: String?, actorId: String, actorName: String): Result<Installment>
    suspend fun regenerateForCycle(parentId: String, cycle: String, actorId: String, actorName: String): Result<List<Installment>>
    suspend fun findOverdue(): Result<List<Installment>>
}
