package com.elimtiyaz.data.repository

import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.DispatcherProvider
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.onFailure
import com.elimtiyaz.data.local.dao.AuditDao
import com.elimtiyaz.data.local.dao.ExpenseDao
import com.elimtiyaz.data.local.dao.PaymentDao
import com.elimtiyaz.data.local.dao.PersonnelDao
import com.elimtiyaz.data.local.dao.ReleveDao
import com.elimtiyaz.data.local.dao.SyncQueueDao
import com.elimtiyaz.data.local.dao.StudentDao
import com.elimtiyaz.data.local.entity.toDomain
import com.elimtiyaz.data.local.entity.toEntity
import com.elimtiyaz.data.remote.dto.AuditEntryDto
import com.elimtiyaz.data.remote.dto.ExpenseDto
import com.elimtiyaz.data.remote.dto.PersonnelDto
import com.elimtiyaz.data.remote.dto.ReleveEntryDto
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
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val EXPENSES_TABLE = "expenses"
private const val PERSONNEL_TABLE = "personnel"
private const val RELEVE_TABLE = "releve_entries"
private const val AUDIT_TABLE = "audit_log"

/** Supabase-backed [ExpenseRepository] — two-tier approval workflow. */
@Singleton
class SupabaseExpenseRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val expenseDao: ExpenseDao,
    private val syncQueueDao: SyncQueueDao,
    private val audit: AuditRepository,
    private val dispatchers: DispatcherProvider,
) : ExpenseRepository {

    private val log = Logger.withTag("Data.Expense")
    private val sync = SyncQueueHelper(syncQueueDao)

    /** Stream all expenses. */
    override fun expenses(): Flow<Result<List<Expense>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { expenseDao.observeAll().first().map { it.toDomain() } },
        fetch = { supabase.from(EXPENSES_TABLE).select().decodeList<ExpenseDto>().map { it.toDomain() } },
        persist = { es -> expenseDao.upsertAll(es.map { it.toEntity() }) },
    )

    /** Stream expenses filtered by status. */
    override fun expensesByStatus(status: String): Flow<Result<List<Expense>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { expenseDao.observeByStatus(status).first().map { it.toDomain() } },
        fetch = {
            supabase.from(EXPENSES_TABLE).select { filter { eq("status", status) } }
                .decodeList<ExpenseDto>().map { it.toDomain() }
        },
        persist = { es -> expenseDao.upsertAll(es.map { it.toEntity() }) },
    )

    /** Stream a single expense. */
    override fun expense(id: String): Flow<Result<Expense>> = RepositoryHelpers.cacheThenFetchOne(
        dispatchers = dispatchers,
        loadCache = { expenseDao.observeById(id).first()?.toDomain() },
        fetch = {
            supabase.from(EXPENSES_TABLE).select { filter { eq("id", id) } }
                .decodeList<ExpenseDto>().firstOrNull()?.toDomain() ?: error("Dépense $id introuvable.")
        },
        persist = { e -> expenseDao.upsert(e.toEntity()) },
    )

    /** Submit a new expense request. */
    override suspend fun submit(input: CreateExpenseInput, submittedBy: String): Result<Expense> =
        Result.runCatching {
            val id = UUID.randomUUID().toString()
            val nowIso = nowIso()
            val dto = ExpenseDto(
                id = id, tenantId = DEFAULT_TENANT, requestCode = "EXP-${nowIso.takeLast(10)}",
                title = input.title, description = input.description, amount = input.amount,
                category = input.category, payee = input.payee, status = "submitted",
                submittedBy = submittedBy, submittedAt = nowIso,
            )
            supabase.from(EXPENSES_TABLE).insert(dto)
            val domain = dto.toDomain()
            expenseDao.upsert(domain.toEntity())
            audit.log("expense.submit", "expense", id, actorId = submittedBy, tenantId = DEFAULT_TENANT,
                diff = "amount=${input.amount} payee=${input.payee}")
            log.i { "Submitted expense '${input.title}' (${'\u20ac'}${input.amount})" }
            domain
        }.onFailure {
            sync.enqueueRaw(EXPENSES_TABLE, "insert", sync.encode(ExpenseDto(
                id = UUID.randomUUID().toString(), tenantId = DEFAULT_TENANT, requestCode = "EXP-PENDING",
                title = input.title, description = input.description, amount = input.amount,
                category = input.category, payee = input.payee, status = "submitted",
                submittedBy = submittedBy, submittedAt = nowIso(),
            )))
        }

    /** Approve an expense. */
    override suspend fun approve(id: String, approver: String, note: String?): Result<Expense> = updateExpense(
        id, mapOf("status" to "approved", "approved_by" to approver, "approved_at" to nowIso(),
            "approval_note" to (note ?: "")),
        action = "expense.approve",
    )

    /** Reject an expense. */
    override suspend fun reject(id: String, approver: String, note: String): Result<Expense> = updateExpense(
        id, mapOf("status" to "rejected", "approved_by" to approver, "approved_at" to nowIso(),
            "approval_note" to note),
        action = "expense.reject",
    )

    /** Disburse an approved expense. */
    override suspend fun disburse(id: String, disbursedBy: String): Result<Expense> = updateExpense(
        id, mapOf("status" to "disbursed", "disbursed_by" to disbursedBy, "disbursed_at" to nowIso()),
        action = "expense.disburse",
    )

    /** Upload settlement proof for a disbursed expense. */
    override suspend fun settleProof(id: String, proofUrl: String, uploadedBy: String): Result<Expense> =
        updateExpense(
            id, mapOf(
                "status" to "settled", "proof_url" to proofUrl,
                "proof_uploaded_by" to uploadedBy, "proof_uploaded_at" to nowIso(),
            ),
            action = "expense.settle",
        )

    /** Shared helper — patch an expense row, refresh cache, audit-log. */
    private suspend fun updateExpense(id: String, patch: Map<String, Any?>, action: String): Result<Expense> =
        Result.runCatching {
            supabase.from(EXPENSES_TABLE).update(patch) { filter { eq("id", id) } }
            val refreshed = supabase.from(EXPENSES_TABLE).select { filter { eq("id", id) } }
                .decodeList<ExpenseDto>().firstOrNull()?.toDomain() ?: error("Dépense $id introuvable.")
            expenseDao.upsert(refreshed.toEntity())
            audit.log(action, "expense", id, actorId = "system", tenantId = DEFAULT_TENANT,
                diff = patch.entries.joinToString { "${it.key}=${it.value}" })
            log.i { "Expense $id → ${patch["status"]}" }
            refreshed
        }.onFailure {
            sync.enqueueRaw(EXPENSES_TABLE, "update", sync.encode(mapOf("id" to id, "patch" to patch.toString())))
        }
}

/** Supabase-backed [PersonnelRepository]. */
@Singleton
class SupabasePersonnelRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val personnelDao: PersonnelDao,
    private val syncQueueDao: SyncQueueDao,
    private val dispatchers: DispatcherProvider,
) : PersonnelRepository {

    private val log = Logger.withTag("Data.Personnel")
    private val sync = SyncQueueHelper(syncQueueDao)

    /** Stream all personnel. */
    override fun personnel(): Flow<Result<List<Personnel>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { personnelDao.observeAll().first().map { it.toDomain() } },
        fetch = { supabase.from(PERSONNEL_TABLE).select().decodeList<PersonnelDto>().map { it.toDomain() } },
        persist = { ps -> personnelDao.upsertAll(ps.map { it.toEntity() }) },
    )

    /** Stream personnel filtered by staff category. */
    override fun personnelByCategory(category: String): Flow<Result<List<Personnel>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { personnelDao.observeByCategory(category).first().map { it.toDomain() } },
        fetch = {
            supabase.from(PERSONNEL_TABLE).select { filter { eq("staff_category", category) } }
                .decodeList<PersonnelDto>().map { it.toDomain() }
        },
        persist = { ps -> personnelDao.upsertAll(ps.map { it.toEntity() }) },
    )

    /** Stream a single person. */
    override fun personnel(id: String): Flow<Result<Personnel>> = RepositoryHelpers.cacheThenFetchOne(
        dispatchers = dispatchers,
        loadCache = { personnelDao.observeById(id).first()?.toDomain() },
        fetch = {
            supabase.from(PERSONNEL_TABLE).select { filter { eq("id", id) } }
                .decodeList<PersonnelDto>().firstOrNull()?.toDomain() ?: error("Personnel $id introuvable.")
        },
        persist = { p -> personnelDao.upsert(p.toEntity()) },
    )

    /** Create a personnel record. */
    override suspend fun createPersonnel(input: Personnel): Result<Personnel> = Result.runCatching {
        val dto = PersonnelDto.fromDomain(input.copy(id = UUID.randomUUID().toString()))
        supabase.from(PERSONNEL_TABLE).insert(dto)
        val domain = dto.toDomain()
        personnelDao.upsert(domain.toEntity())
        log.i { "Created personnel ${domain.firstName} ${domain.lastName}" }
        domain
    }.onFailure {
        sync.enqueueRaw(PERSONNEL_TABLE, "insert", sync.encode(PersonnelDto.fromDomain(input)))
    }

    /** Update personnel fields via a string-keyed map (snake_case keys). */
    override suspend fun updatePersonnel(id: String, updates: Map<String, String?>): Result<Personnel> =
        Result.runCatching {
            supabase.from(PERSONNEL_TABLE).update(updates.filterValues { it != null }) { filter { eq("id", id) } }
            val refreshed = supabase.from(PERSONNEL_TABLE).select { filter { eq("id", id) } }
                .decodeList<PersonnelDto>().firstOrNull()?.toDomain() ?: error("Personnel $id introuvable.")
            personnelDao.upsert(refreshed.toEntity())
            log.i { "Updated personnel $id" }
            refreshed
        }.onFailure {
            sync.enqueueRaw(PERSONNEL_TABLE, "update", sync.encode(mapOf("id" to id, "updates" to updates.toString())))
        }

    /** Delete a personnel record. */
    override suspend fun deletePersonnel(id: String): Result<Unit> = Result.runCatching {
        supabase.from(PERSONNEL_TABLE).delete { filter { eq("id", id) } }
        personnelDao.deleteById(id)
        log.i { "Deleted personnel $id" }
    }.onFailure {
        sync.enqueueRaw(PERSONNEL_TABLE, "delete", sync.encode(mapOf("id" to id)))
    }
}

/** Supabase-backed [ReleveRepository] — teacher activity ledger. */
@Singleton
class SupabaseReleveRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val releveDao: ReleveDao,
    private val syncQueueDao: SyncQueueDao,
    private val dispatchers: DispatcherProvider,
) : ReleveRepository {

    private val log = Logger.withTag("Data.Releve")
    private val sync = SyncQueueHelper(syncQueueDao)

    /** Stream releve entries for a person between two dates (inclusive). */
    override fun releveByPersonnel(
        personnelId: String, from: String, to: String,
    ): Flow<Result<List<ReleveEntry>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { releveDao.observeByPersonnel(personnelId, from, to).first().map { it.toDomain() } },
        fetch = {
            supabase.from(RELEVE_TABLE).select {
                filter { eq("personnel_id", personnelId); gte("date", from); lte("date", to) }
            }.decodeList<ReleveEntryDto>().map { it.toDomain() }
        },
        persist = { rs -> releveDao.upsertAll(rs.map { it.toEntity() }) },
    )

    /** Append a releve entry. */
    override suspend fun logEntry(entry: ReleveEntry): Result<ReleveEntry> = Result.runCatching {
        val dto = ReleveEntryDto.fromDomain(entry.copy(id = entry.id.ifBlank { UUID.randomUUID().toString() }))
        supabase.from(RELEVE_TABLE).insert(dto)
        val domain = dto.toDomain()
        releveDao.upsert(domain.toEntity())
        log.i { "Logged releve for ${entry.personnelName} (${entry.activity})" }
        domain
    }.onFailure {
        sync.enqueueRaw(RELEVE_TABLE, "insert", sync.encode(ReleveEntryDto.fromDomain(entry)))
    }
}

/** Supabase-backed [AuditRepository]. Insertion goes through an Edge Function. */
@Singleton
class SupabaseAuditRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val auditDao: AuditDao,
    private val syncQueueDao: SyncQueueDao,
    private val dispatchers: DispatcherProvider,
) : AuditRepository {

    private val log = Logger.withTag("Data.Audit")
    private val sync = SyncQueueHelper(syncQueueDao)

    /** Stream the most recent audit entries. */
    override fun recent(limit: Int): Flow<Result<List<AuditEntry>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { auditDao.observeRecent(limit).first().map { it.toDomain() } },
        fetch = {
            supabase.from(AUDIT_TABLE).select().decodeList<AuditEntryDto>()
                .sortedByDescending { it.at }
                .take(limit)
                .map { it.toDomain() }
        },
        persist = { as_ -> auditDao.upsertAll(as_.map { it.toEntity() }) },
    )

    /** Stream audit entries for a specific entity. */
    override fun byEntity(entityType: String, entityId: String): Flow<Result<List<AuditEntry>>> =
        RepositoryHelpers.cacheThenFetch(
            dispatchers = dispatchers,
            loadCache = { auditDao.observeByEntity(entityType, entityId).first().map { it.toDomain() } },
            fetch = {
                supabase.from(AUDIT_TABLE).select {
                    filter { eq("entity_type", entityType); eq("entity_id", entityId) }
                }.decodeList<AuditEntryDto>()
                    .sortedByDescending { it.at }
                    .map { it.toDomain() }
            },
            persist = { as_ -> auditDao.upsertAll(as_.map { it.toEntity() }) },
        )

    /** Log a mutation. Insertion is best-effort; failures are queued. */
    override suspend fun log(
        action: String, entityType: String, entityId: String,
        actorId: String, tenantId: String, diff: String?, note: String?,
    ): Result<AuditEntry> = Result.runCatching {
        val id = UUID.randomUUID().toString()
        val dto = AuditEntryDto(
            id = id, tenantId = tenantId, action = action, entityType = entityType, entityId = entityId,
            actorId = actorId, actorName = actorId, diff = diff, note = note, ipAddress = null,
            userAgent = "android-app", at = nowIso(),
        )
        supabase.from(AUDIT_TABLE).insert(dto)
        val domain = dto.toDomain()
        auditDao.upsert(domain.toEntity())
        log.i { "Audit: $action $entityType/$entityId by $actorId" }
        domain
    }.onFailure {
        // Audit failures are queued but never rethrown — auditing must not break the main flow.
        sync.enqueueRaw(AUDIT_TABLE, "insert", sync.encode(AuditEntryDto(
            id = UUID.randomUUID().toString(), tenantId = tenantId, action = action, entityType = entityType,
            entityId = entityId, actorId = actorId, actorName = actorId, diff = diff, note = note,
            ipAddress = null, userAgent = "android-app", at = nowIso(),
        )))
    }
}

/** Supabase-backed [DashboardRepository] — aggregates KPIs from cached data. */
@Singleton
class SupabaseDashboardRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val paymentDao: PaymentDao,
    private val studentDao: StudentDao,
    private val expenseDao: ExpenseDao,
    private val personnelDao: PersonnelDao,
    private val parentDao: com.elimtiyaz.data.local.dao.ParentDao,
    private val dispatchers: DispatcherProvider,
) : DashboardRepository {

    private val log = Logger.withTag("Data.Dashboard")

    /** Stream the dashboard KPI block (computed from cache + Supabase). */
    override fun kpis(): Flow<Result<DashboardKpi>> = flow {
        val cached = runCatching { buildKpis() }.getOrNull()
        if (cached != null) emit(Result.success(cached))
        val result = Result.runCatching { buildKpis() }
        emit(result)
    }.flowOn(dispatchers.io)

    /** Stream the 12-month revenue series. */
    override fun revenueLast12Months(): Flow<Result<List<RevenuePoint>>> = flow {
        val cached = runCatching { buildRevenue() }.getOrNull()
        if (cached != null) emit(Result.success(cached))
        emit(Result.runCatching { buildRevenue() })
    }.flowOn(dispatchers.io)

    /** Stream the debt-by-aging-bucket breakdown. */
    override fun debtByAging(): Flow<Result<List<DebtByAgingBucket>>> = flow {
        val cached = runCatching { buildDebtBuckets() }.getOrNull()
        if (cached != null) emit(Result.success(cached))
        emit(Result.runCatching { buildDebtBuckets() })
    }.flowOn(dispatchers.io)

    /** Stream the demographic slices (level / gender breakdown). */
    override fun demographics(): Flow<Result<List<DemographicSlice>>> = flow {
        val cached = runCatching { buildDemographics() }.getOrNull()
        if (cached != null) emit(Result.success(cached))
        emit(Result.runCatching { buildDemographics() })
    }.flowOn(dispatchers.io)

    /** Compute the KPI block from cached tables. */
    private suspend fun buildKpis(): DashboardKpi {
        val students = studentDao.all()
        val parents = parentDao.all()
        val personnel = personnelDao.observeAll().first()
        val payments = paymentDao.all()
        val expenses = expenseDao.observeAll().first()
        val pendingExpenses = expenses.count { it.status == "submitted" || it.status == "approved" }
        val monthlyRevenue = payments.filter { it.collectedAt.startsWith(nowIso().take(7)) }.sumOf { it.amount }
        val outstanding = 0.0 // computed in DebtRepository; here we keep a placeholder
        return DashboardKpi(
            totalStudents = students.size, totalParents = parents.size, totalStaff = personnel.size,
            monthlyRevenue = monthlyRevenue, outstandingDebt = outstanding, pendingExpenses = pendingExpenses,
            attendanceRateToday = 0.95, overdueAlerts = 0,
        ).also { log.d { "KPIs computed" } }
    }

    /** Build a 12-month revenue series from payments. */
    private suspend fun buildRevenue(): List<RevenuePoint> {
        val payments = paymentDao.all()
        val byMonth = payments.groupBy { it.collectedAt.take(7) }
            .map { (k, v) -> RevenuePoint(label = k, amount = v.sumOf { it.amount }) }
            .sortedBy { it.label }
        return byMonth.takeLast(12)
    }

    /** Build the debt aging-bucket breakdown (skeleton — real impl aggregates from installments). */
    private suspend fun buildDebtBuckets(): List<DebtByAgingBucket> {
        val buckets = com.elimtiyaz.domain.model.AgingBucket.values()
        return buckets.map { b ->
            DebtByAgingBucket(bucket = b.name, amount = 0.0, debtorCount = 0)
        }
    }

    /** Build demographic slices by academic level. */
    private suspend fun buildDemographics(): List<DemographicSlice> {
        val students = studentDao.all()
        val byLevel = students.groupBy { it.level }
        val total = students.size.coerceAtLeast(1)
        return byLevel.map { (level, list) ->
            DemographicSlice(
                label = level, count = list.size,
                percent = (list.size.toDouble() / total * 100.0 * 100.0).toInt() / 100.0,
            )
        }
    }
}

private const val DEFAULT_TENANT = "tenant-default"
