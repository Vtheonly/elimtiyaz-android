package com.elimtiyaz.domain.repository

import com.elimtiyaz.core.common.Result
import com.elimtiyaz.domain.model.AuditEntry
import com.elimtiyaz.domain.model.CreateExpenseInput
import com.elimtiyaz.domain.model.DashboardKpi
import com.elimtiyaz.domain.model.DebtByAgingBucket
import com.elimtiyaz.domain.model.DemographicSlice
import com.elimtiyaz.domain.model.Expense
import com.elimtiyaz.domain.model.Personnel
import com.elimtiyaz.domain.model.ReleveEntry
import com.elimtiyaz.domain.model.RevenuePoint
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun expenses(): Flow<Result<List<Expense>>>
    fun expensesByStatus(status: String): Flow<Result<List<Expense>>>
    fun expense(id: String): Flow<Result<Expense>>
    suspend fun submit(input: CreateExpenseInput, submittedBy: String): Result<Expense>
    suspend fun approve(id: String, approver: String, note: String?): Result<Expense>
    suspend fun reject(id: String, approver: String, note: String): Result<Expense>
    suspend fun disburse(id: String, disbursedBy: String): Result<Expense>
    suspend fun settleProof(id: String, proofUrl: String, uploadedBy: String): Result<Expense>
}

interface PersonnelRepository {
    fun personnel(): Flow<Result<List<Personnel>>>
    fun personnelByCategory(category: String): Flow<Result<List<Personnel>>>
    fun personnel(id: String): Flow<Result<Personnel>>
    suspend fun createPersonnel(input: Personnel): Result<Personnel>
    suspend fun updatePersonnel(id: String, updates: Map<String, String?>): Result<Personnel>
    suspend fun deletePersonnel(id: String): Result<Unit>
}

interface ReleveRepository {
    fun releveByPersonnel(personnelId: String, from: String, to: String): Flow<Result<List<ReleveEntry>>>
    suspend fun logEntry(entry: ReleveEntry): Result<ReleveEntry>
}

interface AuditRepository {
    fun recent(limit: Int = 50): Flow<Result<List<AuditEntry>>>
    fun byEntity(entityType: String, entityId: String): Flow<Result<List<AuditEntry>>>
    suspend fun log(action: String, entityType: String, entityId: String, actorId: String, tenantId: String, diff: String? = null, note: String? = null): Result<AuditEntry>
}

interface DashboardRepository {
    fun kpis(): Flow<Result<DashboardKpi>>
    fun revenueLast12Months(): Flow<Result<List<RevenuePoint>>>
    fun debtByAging(): Flow<Result<List<DebtByAgingBucket>>>
    fun demographics(): Flow<Result<List<DemographicSlice>>>
}
