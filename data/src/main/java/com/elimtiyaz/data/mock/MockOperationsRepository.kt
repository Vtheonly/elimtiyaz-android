package com.elimtiyaz.data.mock

import co.touchlab.kermit.Logger
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
import com.elimtiyaz.domain.repository.AuditRepository
import com.elimtiyaz.domain.repository.DashboardRepository
import com.elimtiyaz.domain.repository.ExpenseRepository
import com.elimtiyaz.domain.repository.PersonnelRepository
import com.elimtiyaz.domain.repository.ReleveRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private fun mockDelay() = delay((200L..500L).random())

/** Mock [ExpenseRepository]. */
@Singleton
class MockExpenseRepository @Inject constructor() : ExpenseRepository {

    private val log = Logger.withTag("Mock.Expense")
    private val state = MutableStateFlow(MockData.expenses)

    /** Stream all expenses. */
    override fun expenses(): Flow<Result<List<Expense>>> = state.map { Result.success(it) }

    /** Stream expenses filtered by status. */
    override fun expensesByStatus(status: String): Flow<Result<List<Expense>>> =
        state.map { Result.success(it.filter { e -> e.status == status }) }

    /** Stream a single expense. */
    override fun expense(id: String): Flow<Result<Expense>> = state.map { expenses ->
        val e = expenses.firstOrNull { it.id == id }
            ?: return@map Result.failure("Dépense $id introuvable.")
        Result.success(e)
    }

    /** Submit a new expense request. */
    override suspend fun submit(input: CreateExpenseInput, submittedBy: String): Result<Expense> {
        mockDelay()
        val id = "e-new-${UUID.randomUUID().toString().take(6)}"
        val nowIso = Clock.System.now().toString()
        val expense = Expense(
            id = id, tenantId = MockData.TENANT_ID, requestCode = "EXP-NEW-${id.takeLast(4).uppercase()}",
            title = input.title, description = input.description, amount = input.amount,
            category = input.category, payee = input.payee, status = "submitted",
            submittedBy = submittedBy, submittedAt = nowIso,
        )
        state.value = state.value + expense
        log.i { "Submitted expense '${input.title}'" }
        return Result.success(expense)
    }

    /** Approve an expense. */
    override suspend fun approve(id: String, approver: String, note: String?): Result<Expense> =
        patchExpense(id, "approved", mapOf("approvedBy" to approver, "approvalNote" to note))

    /** Reject an expense. */
    override suspend fun reject(id: String, approver: String, note: String): Result<Expense> =
        patchExpense(id, "rejected", mapOf("approvedBy" to approver, "approvalNote" to note))

    /** Disburse an approved expense. */
    override suspend fun disburse(id: String, disbursedBy: String): Result<Expense> =
        patchExpense(id, "disbursed", mapOf("disbursedBy" to disbursedBy))

    /** Upload settlement proof for a disbursed expense. */
    override suspend fun settleProof(id: String, proofUrl: String, uploadedBy: String): Result<Expense> =
        patchExpense(id, "settled", mapOf("proofUrl" to proofUrl, "proofUploadedBy" to uploadedBy))

    /** Shared helper — patch + audit-log. */
    private suspend fun patchExpense(id: String, status: String, fields: Map<String, Any?>): Result<Expense> {
        mockDelay()
        val nowIso = Clock.System.now().toString()
        val updated = state.value.map { e ->
            if (e.id != id) e else {
                val approvedBy = fields["approvedBy"] as? String
                val disbursedBy = fields["disbursedBy"] as? String
                val proofUrl = fields["proofUrl"] as? String
                e.copy(
                    status = status,
                    approvedBy = approvedBy ?: e.approvedBy,
                    approvedAt = if (approvedBy != null) nowIso else e.approvedAt,
                    approvalNote = fields["approvalNote"] as? String ?: e.approvalNote,
                    disbursedBy = disbursedBy ?: e.disbursedBy,
                    disbursedAt = if (disbursedBy != null) nowIso else e.disbursedAt,
                    proofUrl = proofUrl ?: e.proofUrl,
                    proofUploadedBy = if (proofUrl != null) fields["proofUploadedBy"] as? String else e.proofUploadedBy,
                    proofUploadedAt = if (proofUrl != null) nowIso else e.proofUploadedAt,
                )
            }
        }
        state.value = updated
        val result = updated.firstOrNull { it.id == id }
            ?: return Result.failure("Dépense $id introuvable.")
        log.i { "Expense $id → $status" }
        return Result.success(result)
    }
}

/** Mock [PersonnelRepository]. */
@Singleton
class MockPersonnelRepository @Inject constructor() : PersonnelRepository {

    private val log = Logger.withTag("Mock.Personnel")
    private val state = MutableStateFlow(MockData.personnel)

    /** Stream all personnel. */
    override fun personnel(): Flow<Result<List<Personnel>>> = state.map { Result.success(it) }

    /** Stream personnel filtered by staff category. */
    override fun personnelByCategory(category: String): Flow<Result<List<Personnel>>> =
        state.map { Result.success(it.filter { p -> p.staffCategory.name.equals(category, ignoreCase = true) }) }

    /** Stream a single person. */
    override fun personnel(id: String): Flow<Result<Personnel>> = state.map { personnel ->
        val p = personnel.firstOrNull { it.id == id }
            ?: return@map Result.failure("Personnel $id introuvable.")
        Result.success(p)
    }

    /** Create a personnel record. */
    override suspend fun createPersonnel(input: Personnel): Result<Personnel> {
        mockDelay()
        val person = input.copy(id = "pe-new-${UUID.randomUUID().toString().take(6)}")
        state.value = state.value + person
        log.i { "Created personnel ${person.firstName} ${person.lastName}" }
        return Result.success(person)
    }

    /** Update personnel fields via a string-keyed map. */
    override suspend fun updatePersonnel(id: String, updates: Map<String, String?>): Result<Personnel> {
        mockDelay()
        val updated = state.value.map { p ->
            if (p.id != id) p else p.copy(
                firstName = updates["first_name"] ?: p.firstName,
                lastName = updates["last_name"] ?: p.lastName,
                phone = updates["phone"] ?: p.phone,
                email = updates["email"] ?: p.email,
                salary = updates["salary"]?.toDoubleOrNull() ?: p.salary,
            )
        }
        state.value = updated
        val result = updated.firstOrNull { it.id == id }
            ?: return Result.failure("Personnel $id introuvable.")
        log.i { "Updated personnel $id" }
        return Result.success(result)
    }

    /** Delete a personnel record. */
    override suspend fun deletePersonnel(id: String): Result<Unit> {
        mockDelay()
        state.value = state.value.filterNot { it.id == id }
        log.i { "Deleted personnel $id" }
        return Result.success(Unit)
    }
}

/** Mock [ReleveRepository]. */
@Singleton
class MockReleveRepository @Inject constructor() : ReleveRepository {

    private val log = Logger.withTag("Mock.Releve")
    private val state = MutableStateFlow(MockData.releveEntries)

    /** Stream releve entries for a person between two dates (inclusive). */
    override fun releveByPersonnel(
        personnelId: String, from: String, to: String,
    ): Flow<Result<List<ReleveEntry>>> = state.map { Result.success(it.filter { r ->
        r.personnelId == personnelId && r.date >= from && r.date <= to
    }) }

    /** Append a releve entry. */
    override suspend fun logEntry(entry: ReleveEntry): Result<ReleveEntry> {
        mockDelay()
        val withId = entry.copy(id = entry.id.ifBlank { "r-new-${UUID.randomUUID().toString().take(6)}" })
        state.value = state.value + withId
        log.i { "Logged releve for ${entry.personnelName} (${entry.activity})" }
        return Result.success(withId)
    }
}

/** Mock [AuditRepository]. */
@Singleton
class MockAuditRepository @Inject constructor() : AuditRepository {

    private val log = Logger.withTag("Mock.Audit")
    private val state = MutableStateFlow(MockData.auditEntries)

    /** Stream the most recent audit entries. */
    override fun recent(limit: Int): Flow<Result<List<AuditEntry>>> = state.map {
        Result.success(it.sortedByDescending { a -> a.at }.take(limit))
    }

    /** Stream audit entries for a specific entity. */
    override fun byEntity(entityType: String, entityId: String): Flow<Result<List<AuditEntry>>> = state.map {
        Result.success(it.filter { a -> a.entityType == entityType && a.entityId == entityId }
            .sortedByDescending { a -> a.at })
    }

    /** Log a mutation — append to the in-memory list. */
    override suspend fun log(
        action: String, entityType: String, entityId: String,
        actorId: String, tenantId: String, diff: String?, note: String?,
    ): Result<AuditEntry> {
        val entry = AuditEntry(
            id = "au-new-${UUID.randomUUID().toString().take(6)}",
            tenantId = tenantId, action = action, entityType = entityType, entityId = entityId,
            actorId = actorId, actorName = actorId, diff = diff, note = note,
            ipAddress = null, userAgent = "android-app", at = Clock.System.now().toString(),
        )
        state.value = (state.value + entry).sortedByDescending { a -> a.at }.take(200)
        log.i { "Audit: $action $entityType/$entityId by $actorId" }
        return Result.success(entry)
    }
}

/** Mock [DashboardRepository]. */
@Singleton
class MockDashboardRepository @Inject constructor() : DashboardRepository {

    private val kpisState = MutableStateFlow(MockData.dashboardKpis)
    private val revenueState = MutableStateFlow(MockData.revenueLast12Months)
    private val debtState = MutableStateFlow(MockData.debtByAging)
    private val demoState = MutableStateFlow(MockData.demographics)

    /** Stream the dashboard KPI block. */
    override fun kpis(): Flow<Result<DashboardKpi>> = kpisState.map { Result.success(it) }

    /** Stream the 12-month revenue series. */
    override fun revenueLast12Months(): Flow<Result<List<RevenuePoint>>> = revenueState.map { Result.success(it) }

    /** Stream the debt-by-aging-bucket breakdown. */
    override fun debtByAging(): Flow<Result<List<DebtByAgingBucket>>> = debtState.map { Result.success(it) }

    /** Stream the demographic slices. */
    override fun demographics(): Flow<Result<List<DemographicSlice>>> = demoState.map { Result.success(it) }
}
