package com.example.infrastructure.local

import com.example.BuildConfig
import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.core.Permission
import com.example.core.Result
import com.example.core.Role
import com.example.core.Session
import com.example.core.allocatePaymentToInstallments
import com.example.core.createChargeEntry
import com.example.core.createPaymentEntry
import com.example.core.createReversalEntry
import com.example.core.deriveAccountId
import com.example.core.generateEntryId
import com.example.core.LedgerEngine
import com.example.core.WaterfallInstallment
import com.example.domain.model.Parent
import com.example.domain.model.Student
import com.example.domain.model.Payment
import com.example.domain.model.Installment
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.BatchRegisterResult
import com.example.domain.repository.CreateParentInput
import com.example.domain.repository.CreateStudentInput
import com.example.domain.repository.CollectPaymentInput
import com.example.domain.repository.InstallmentRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.PaymentRepository
import com.example.domain.repository.StudentRepository
import com.example.domain.repository.UpdateParentInput
import com.example.domain.repository.UpdateStudentInput
import com.example.infrastructure.room.AuditLogDao
import com.example.infrastructure.room.AuditLogEntity
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.room.InstallmentDao
import com.example.infrastructure.room.InstallmentEntity
import com.example.infrastructure.room.LedgerEntryDao
import com.example.infrastructure.room.LedgerEntryEntity
import com.example.infrastructure.room.LocalMappers
import com.example.infrastructure.room.ParentDao
import com.example.infrastructure.room.ParentEntity
import com.example.infrastructure.room.PaymentDao
import com.example.infrastructure.room.PaymentEntity
import com.example.infrastructure.room.StudentDao
import com.example.infrastructure.room.StudentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ─── Ledger Repository ──────────────────────────────────────────────────────

@Singleton
class LocalLedgerRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val ledgerDao: LedgerEntryDao,
    // TIER 4 FIX — reconcile()'s cross-checks (R10) need the payment,
    // installment and parent tables as inputs. The constructor previously
    // injected only ledgerDao, leaving paymentDao / installmentDao / parentDao
    // as unresolved references — a compile error.
    private val paymentDao: PaymentDao,
    private val installmentDao: InstallmentDao,
    private val parentDao: ParentDao,
    // CANONICAL-FINANCIAL-LOGIC.md §8.1 — wire SyncSupport so ledger writes
    // (append, appendMany, reverse) propagate to Supabase.
    private val syncSupport: com.example.infrastructure.sync.SyncSupport? = null,
) : LedgerRepository {

    private fun syncJson(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        kotlinx.serialization.json.buildJsonObject(builder).toString()

    /** Serialize a LedgerEntry for the sync queue payload. */
    private fun com.example.core.LedgerEntry.toSyncPayload(): String = syncJson {
        put("id", id); put("tenantId", tenantId); put("accountId", accountId)
        put("parentId", parentId); put("studentId", studentId ?: "")
        put("category", category.code); put("amount", amount)
        put("type", type.code); put("sourceType", sourceType.code)
        put("sourceId", sourceId); put("method", method?.code ?: "")
        put("receiptNumber", receiptNumber ?: "")
        put("paymentStatus", paymentStatus?.code ?: "")
        put("reversesId", reversesId ?: "")
        put("description", description)
        put("actorId", actorId); put("actorName", actorName); put("at", at)
        put("metadataJson", com.example.infrastructure.room.LocalMappers.serializeMetadataJson(metadata))
    }

    override fun observe(): Flow<List<com.example.core.LedgerEntry>> =
        ledgerDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByParent(parentId: String): Flow<List<com.example.core.LedgerEntry>> =
        ledgerDao.observeByParent(parentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByAccount(accountId: String): Flow<List<com.example.core.LedgerEntry>> =
        ledgerDao.observeByAccount(accountId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override suspend fun append(entry: com.example.core.LedgerEntry): Result<com.example.core.LedgerEntry> {
        ledgerDao.upsert(entry.toEntity())
        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the ledger entry for sync push.
        syncSupport?.enqueueOnly(
            entity = "ledger_entry",
            operation = "create",
            payload = entry.toSyncPayload(),
            isMock = false, sourceScreen = "LedgerAppend",
        )
        return Result.Ok(entry)
    }

    override suspend fun appendMany(entries: List<com.example.core.LedgerEntry>): Result<List<com.example.core.LedgerEntry>> {
        ledgerDao.upsertAll(entries.map { it.toEntity() })
        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue each entry for sync push.
        for (entry in entries) {
            syncSupport?.enqueueOnly(
                entity = "ledger_entry",
                operation = "create",
                payload = entry.toSyncPayload(),
                isMock = false, sourceScreen = "LedgerAppendMany",
            )
        }
        return Result.Ok(entries)
    }

    override suspend fun reverse(originalId: String, reason: String, actorId: String, actorName: String): Result<com.example.core.LedgerEntry> {
        val original = ledgerDao.getById(originalId) ?: return Result.Err(Errors.notFound("Ledger entry $originalId not found"))
        val reversal = createReversalEntry(LocalMappers.run { original.toDomain() }, reason, actorId, actorName)
        ledgerDao.upsert(reversal.toEntity())
        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the reversal entry for sync push.
        syncSupport?.enqueueOnly(
            entity = "ledger_entry",
            operation = "reverse",
            payload = reversal.toSyncPayload(),
            isMock = false, sourceScreen = "LedgerReverse",
        )
        return Result.Ok(reversal)
    }

    override suspend fun summary(parentId: String): Result<com.example.core.ParentLedgerSummary> {
        val entries = ledgerDao.listByParent(parentId).map { LocalMappers.run { it.toDomain() } }
        // TIER 4 FIX — pass the overdue due-date map (canonical rule: an account
        // is overdue when balance > 0 and its latest charge date is in the past).
        // Without the map, computeParentSummary silently reports totalOverdue = 0.
        val dueDates = com.example.core.LedgerEngine.buildOverdueDueDateMap(entries)
        val summary = LedgerEngine.computeParentSummary(entries, parentId, "", dueDates)
        return Result.Ok(summary)
    }

    override suspend fun reconcile(): Result<com.example.core.Reconcile.Report> {
        val entries = ledgerDao.listAll().map { LocalMappers.run { it.toDomain() } }
        // TIER 2 R10 — pass real cross-check inputs so the 3 new unified-architecture
        // cross-checks (UNBACKED_TRANCHE_SATISFACTION, PAYMENT_LEDGER_MISMATCH,
        // UNBACKED_PARENT_CREDIT) have data to verify against. Previously the
        // call passed an empty CrossCheckInputs() — those 3 checks were no-ops.
        val payments = paymentDao.listAll().map { LocalMappers.run { it.toDomain() } }
        val installments = installmentDao.listAll().map { LocalMappers.run { it.toDomain() } }
        val parents = parentDao.listAll()

        val paymentInputs = payments.map { p ->
            com.example.core.Reconcile.PaymentCrossCheck(
                id = p.id, amount = p.amount, status = p.status,
            )
        }
        val installmentInputs = installments.map { i ->
            com.example.core.Reconcile.InstallmentCrossCheck(
                id = i.id,
                parentId = i.parentId,
                studentId = i.studentId,
                category = i.category.code,
                amountDue = i.amountDue,
                amountPaid = i.amountPaid,
                label = i.label,
                status = i.status.code,
            )
        }
        // Build parent summaries — one entry per parent with outstanding + accounts.
        // We group by parentId and compute the canonical summary via LedgerEngine.
        val parentSummaries = parents.map { p ->
            val parentEntries = entries.filter { it.parentId == p.id }
            // T-026 (WEAK-007): build the due-date map — without it the
            // reconciliation cross-check's totalOutstanding silently reports 0
            // (bare-call irregularity surfaced by the T-026 union scan, 2026-09-08).
            val dueDateMap = com.example.core.LedgerEngine.buildOverdueDueDateMap(parentEntries)
            val summary = com.example.core.LedgerEngine.computeParentSummary(
                parentEntries, p.id, p.fullName, dueDateMap,
            )
            com.example.core.Reconcile.ParentSummaryCrossCheck(
                parentId = p.id,
                parentName = p.fullName,
                totalOutstanding = summary.totalOutstanding,
                accounts = summary.accounts.map { acc ->
                    com.example.core.Reconcile.ParentAccountCrossCheck(
                        accountId = acc.accountId,
                        category = acc.category.code,
                        studentId = acc.studentId,
                        balance = acc.balance,
                        unallocatedCredit = acc.unallocatedCredit,
                    )
                },
            )
        }
        // paymentToInstallmentId lookup — payments carry `installmentId` field.
        val payToInst = payments.filter { it.installmentId != null }
            .associate { it.id to it.installmentId!! }

        val inputs = com.example.core.Reconcile.CrossCheckInputs(
            payments = paymentInputs,
            installments = installmentInputs,
            parentSummaries = parentSummaries,
            paymentToInstallmentId = payToInst,
        )
        return Result.Ok(com.example.core.Reconcile.reconcileLedger(entries, inputs))
    }
}
