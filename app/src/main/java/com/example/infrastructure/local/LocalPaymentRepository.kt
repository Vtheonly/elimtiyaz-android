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

// ─── Payment Repository ─────────────────────────────────────────────────────

@Singleton
class LocalPaymentRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val db: ElImtiyazDatabase,
    private val paymentDao: PaymentDao,
    private val installmentDao: InstallmentDao,
    private val ledgerDao: LedgerEntryDao,
    private val auditDao: AuditLogDao,
    // TIER 4 FIX — parent lookup so sync payloads carry the canonical
    // parent_code (the server resolves parent refs by UUID or code, never
    // by mobile-local ids).
    private val parentDao: ParentDao,
    // CANONICAL-FINANCIAL-LOGIC.md §8.1 — wire the SyncSupport helper so
    // Android payment writes propagate to Supabase. Previously Android was
    // read-only relative to Supabase for the payments table.
    private val syncSupport: com.example.infrastructure.sync.SyncSupport? = null,
) : PaymentRepository {

    /** Serialize an entity for the sync queue payload. */
    private fun syncJson(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        kotlinx.serialization.json.buildJsonObject(builder).toString()

    override fun observe(): Flow<List<Payment>> =
        paymentDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByParent(parentId: String): Flow<List<Payment>> =
        paymentDao.observeByParent(parentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByStudent(studentId: String): Flow<List<Payment>> =
        paymentDao.observeByStudent(studentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Payment?> =
        paymentDao.observeById(id).map { it?.let { e -> LocalMappers.run { e.toDomain() } } }

    /** Resolve the canonical parent code for sync payloads (server-side ref). */
    private suspend fun parentCodeFor(parentId: String): String? =
        parentDao.getById(parentId)?.code

    override suspend fun collect(input: CollectPaymentInput, actorId: String, actorName: String): Result<Payment> {
        if (input.amount <= 0L) return Result.Err(Errors.validation("Amount must be > 0"))
        if (input.method.requiresProof && input.proofPath.isNullOrBlank())
            return Result.Err(Errors.validation("Proof is required for ${input.method.code}"))

        val now = Instant.now().toString()
        val year = java.time.LocalDate.now().year
        val seq = (paymentDao.listAll().size + 1).toString().padStart(6, '0')
        val receipt = "REC-$year-$seq"
        val paymentId = "pay-${UUID.randomUUID()}"
        val status = if (input.method == PaymentMethod.CASH) PaymentStatus.PAID else PaymentStatus.PENDING

        val entity = PaymentEntity(
            id = paymentId, tenantId = auditContext.tenantId(), receiptNumber = receipt,
            parentId = input.parentId, studentId = input.studentId, amount = input.amount,
            method = input.method.code, status = status.code, category = input.category.code,
            installmentId = input.installmentId, proofUrl = input.proofPath,
            checkNumber = input.checkNumber, checkBankName = input.checkBankName,
            checkIssueDate = input.checkIssueDate, checkClearanceDate = input.checkClearanceDate,
            transferReference = input.transferReference, transferSourceBank = input.transferSourceBank,
            notes = input.notes, collectedBy = actorId, collectedBy_name = actorName,
            collectedAt = now, createdAt = now, updatedAt = now,
        )
        paymentDao.upsert(entity)
        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the payment row for sync push.
        val parentCode = parentCodeFor(entity.parentId) ?: ""
        syncSupport?.enqueueOnly(
            entity = "payment",
            operation = "create",
            payload = syncJson {
                put("id", entity.id); put("tenantId", entity.tenantId)
                put("receiptNumber", entity.receiptNumber)
                put("parentId", entity.parentId); put("parentCode", parentCode); put("studentId", entity.studentId ?: "")
                put("amount", entity.amount); put("method", entity.method)
                put("status", entity.status); put("category", entity.category)
                put("installmentId", entity.installmentId ?: "")
                put("proofUrl", entity.proofUrl ?: "")
                put("checkNumber", entity.checkNumber ?: "")
                put("checkBankName", entity.checkBankName ?: "")
                put("checkIssueDate", entity.checkIssueDate ?: "")
                put("checkClearanceDate", entity.checkClearanceDate ?: "")
                put("transferReference", entity.transferReference ?: "")
                put("transferSourceBank", entity.transferSourceBank ?: "")
                put("notes", entity.notes ?: "")
                put("collectedBy", entity.collectedBy)
                put("collectedAt", entity.collectedAt)
            },
            isMock = false, sourceScreen = "CounterPaymentScreen",
        )

        val ledgerEntry = createPaymentEntry(
            tenantId = entity.tenantId, parentId = input.parentId, studentId = input.studentId,
            category = input.category, amount = input.amount,
            method = input.method, receiptNumber = receipt, paymentStatus = status,
            sourceId = paymentId, actorId = actorId, actorName = actorName,
            description = "Encaissement $receipt",
        )
        ledgerDao.upsert(ledgerEntry.toEntity())
        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the ledger entry for sync push.
        // FIX (duplicate declaration): `parentCode` was declared twice in the
        // same scope — a compile error that broke the whole build.
        syncSupport?.enqueueOnly(
            entity = "ledger_entry",
            operation = "create",
            payload = syncJson {
                put("id", ledgerEntry.id); put("tenantId", ledgerEntry.tenantId)
                put("accountId", ledgerEntry.accountId)
                put("parentId", ledgerEntry.parentId); put("parentCode", parentCode); put("studentId", ledgerEntry.studentId ?: "")
                put("category", ledgerEntry.category.code); put("amount", ledgerEntry.amount)
                put("type", ledgerEntry.type.code); put("sourceType", ledgerEntry.sourceType.code)
                put("sourceId", ledgerEntry.sourceId); put("method", ledgerEntry.method?.code ?: "")
                put("receiptNumber", ledgerEntry.receiptNumber ?: "")
                put("paymentStatus", ledgerEntry.paymentStatus?.code ?: "")
                put("reversesId", ledgerEntry.reversesId ?: "")
                put("description", ledgerEntry.description)
                put("actorId", ledgerEntry.actorId); put("actorName", ledgerEntry.actorName)
                put("at", ledgerEntry.at)
                put("metadataJson", com.example.infrastructure.room.LocalMappers.serializeMetadataJson(ledgerEntry.metadata))
            },
            isMock = false, sourceScreen = "CounterPaymentScreen",
        )

        val familyInstallments = installmentDao.listByParent(input.parentId)
            .map { WaterfallInstallment(it.id, PaymentCategory.fromCode(it.category), it.amountDue, it.amountPaid, it.amountPending, it.dueDate, it.status) }

        val allocation = allocatePaymentToInstallments(
            installments = familyInstallments,
            paymentAmount = input.amount,
            categoryFilter = input.category,
            paymentStatus = status,
        )

        allocation.allocations.forEach { a ->
            installmentDao.getById(a.installmentId)?.let { ins ->
                installmentDao.update(ins.copy(
                    amountPaid = a.newAmountPaid,
                    amountPending = a.newAmountPending,
                    status = a.newStatus,
                    paidDate = if (a.newStatus == "paid") now else ins.paidDate,
                    updatedAt = now,
                ))
            }
        }

        if (allocation.unallocatedAmount > 0L) {
            // CANONICAL-FINANCIAL-LOGIC.md §4 INV-7 — overpayment credit
            // MUST land on a parent-scoped `parent_credit` account, NOT on
            // the input category's student-scoped account. Otherwise the
            // desktop reconciler raises `UNBACKED_PARENT_CREDIT` and the
            // auto-absorb-on-future-charges logic cannot find the credit.
            val creditEntry = com.example.core.createAdjustmentEntry(
                tenantId = entity.tenantId,
                parentId = input.parentId,
                studentId = null, // parent-scoped — NOT input.studentId
                category = PaymentCategory.PARENT_CREDIT,
                amount = -allocation.unallocatedAmount,
                sourceId = paymentId, actorId = actorId, actorName = actorName,
                reason = "Crédit parent (trop-perçu) $receipt",
            )
            ledgerDao.upsert(creditEntry.toEntity())
            // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the parent_credit
            // adjustment for sync push so the desktop sees it.
            // T-082: `parentCode` re-indented to its block level — lint's
            // SuspiciousIndentation flagged the misleading 8-space indent.
            val parentCode = parentCodeFor(creditEntry.parentId) ?: ""
            syncSupport?.enqueueOnly(
                entity = "ledger_entry",
                operation = "create",
                payload = syncJson {
                    put("id", creditEntry.id); put("tenantId", creditEntry.tenantId)
                    put("accountId", creditEntry.accountId)
                    put("parentId", creditEntry.parentId); put("parentCode", parentCode); put("studentId", creditEntry.studentId ?: "")
                    put("category", creditEntry.category.code); put("amount", creditEntry.amount)
                    put("type", creditEntry.type.code); put("sourceType", creditEntry.sourceType.code)
                    put("sourceId", creditEntry.sourceId); put("method", creditEntry.method?.code ?: "")
                    put("receiptNumber", creditEntry.receiptNumber ?: "")
                    put("paymentStatus", creditEntry.paymentStatus?.code ?: "")
                    put("reversesId", creditEntry.reversesId ?: "")
                    put("description", creditEntry.description)
                    put("actorId", creditEntry.actorId); put("actorName", creditEntry.actorName)
                    put("at", creditEntry.at)
                    put("metadataJson", com.example.infrastructure.room.LocalMappers.serializeMetadataJson(creditEntry.metadata))
                },
                isMock = false, sourceScreen = "CounterPaymentScreen",
            )
        }

        auditDao.upsert(auditContext.audit("payment.collect", "payment", paymentId, actorId, actorName,
            after = """{"receipt":"$receipt","amount":${input.amount},"method":"${input.method.code}"}"""))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    override suspend fun refund(paymentId: String, reason: String, actorId: String, actorName: String): Result<Payment> {
        val existing = paymentDao.getById(paymentId) ?: return Result.Err(Errors.notFound("Payment $paymentId not found"))
        // T-017 / BUSINESS-102 — ALREADY-REFUNDED GUARD (idempotency): the
        // refund flow is NOT idempotent by itself — a second call on the same
        // payment would enqueue a SECOND refund push and create a SECOND
        // reversal ledger entry (double-counting the refund on both Room and
        // the server after drain). A refunded payment is in a TERMINAL
        // state (canonical `revert_payment_allocation` guards terminal
        // states the same way): return the already-refunded row unchanged
        // and touch nothing — no queue entry, no reversal, no audit row.
        if (existing.status == PaymentStatus.REFUNDED.code) {
            return Result.Ok(LocalMappers.run { existing.toDomain() })
        }
        val now = Instant.now().toString()
        val updated = existing.copy(status = PaymentStatus.REFUNDED.code, updatedAt = now)
        paymentDao.update(updated)
        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the payment status update
        // (refunded) for sync push so the desktop sees the refund.
        syncSupport?.enqueueOnly(
            entity = "payment",
            operation = "refund",
            payload = syncJson {
                put("id", existing.id); put("status", PaymentStatus.REFUNDED.code)
                put("receiptNumber", existing.receiptNumber); put("updatedAt", now)
                // T-017 — the refund reason travels with the payload so the
                // server-side audit trail records WHY (was only in the local
                // audit row, never synced).
                put("reason", reason)
            },
            isMock = false, sourceScreen = "PaymentDetailScreen",
        )

        val originalLedger = ledgerDao.listByParent(existing.parentId)
            .firstOrNull { it.sourceId == paymentId && it.type == "payment" }
        if (originalLedger != null) {
            val reversal = createReversalEntry(LocalMappers.run { originalLedger.toDomain() }, reason, actorId, actorName)
            ledgerDao.upsert(reversal.toEntity())
            // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the reversal ledger
            // entry for sync push.
            // T-082: `parentCode` re-indented to its block level — lint's
            // SuspiciousIndentation flagged the misleading 8-space indent.
            val parentCode = parentCodeFor(reversal.parentId) ?: ""
            syncSupport?.enqueueOnly(
                entity = "ledger_entry",
                operation = "reverse",
                payload = syncJson {
                    put("id", reversal.id); put("tenantId", reversal.tenantId)
                    put("accountId", reversal.accountId)
                    put("parentId", reversal.parentId); put("parentCode", parentCode); put("studentId", reversal.studentId ?: "")
                    put("category", reversal.category.code); put("amount", reversal.amount)
                    put("type", reversal.type.code); put("sourceType", reversal.sourceType.code)
                    put("sourceId", reversal.sourceId); put("method", reversal.method?.code ?: "")
                    put("receiptNumber", reversal.receiptNumber ?: "")
                    put("paymentStatus", reversal.paymentStatus?.code ?: "")
                    put("reversesId", reversal.reversesId ?: "")
                    put("description", reversal.description)
                    put("actorId", reversal.actorId); put("actorName", reversal.actorName)
                    put("at", reversal.at)
                    put("metadataJson", com.example.infrastructure.room.LocalMappers.serializeMetadataJson(reversal.metadata))
                },
                isMock = false, sourceScreen = "PaymentDetailScreen",
            )

            // CANONICAL-FINANCIAL-LOGIC.md §4 INV-8 — refund LIFO must
            // branch on `originalWasPending`. Without this, refunding an
            // uncleared (pending) check/transfer tries to subtract from
            // `amountPaid` (which is 0 for a pending payment), the revert
            // is a silent no-op, and `amountPending` stays inflated.
            val originalWasPending = originalLedger.paymentStatus == PaymentStatus.PENDING.code

            val familyInstallments = installmentDao.listByParent(existing.parentId)
                .map { WaterfallInstallment(it.id, PaymentCategory.fromCode(it.category), it.amountDue, it.amountPaid, it.amountPending, it.dueDate, it.status) }
            val revert = com.example.core.revertPaymentAllocation(
                installments = familyInstallments,
                reversalAmount = existing.amount,
                categoryFilter = PaymentCategory.fromCode(existing.category),
                originalWasPending = originalWasPending,
            )
            revert.reverts.forEach { r ->
                installmentDao.getById(r.installmentId)?.let { ins ->
                    val reverted = ins.copy(
                        amountPaid = r.newAmountPaid,
                        amountPending = r.newAmountPending,
                        status = r.newStatus,
                        updatedAt = now,
                    )
                    installmentDao.update(reverted)
                    val revertedParentCode = parentCodeFor(reverted.parentId) ?: ""
                    // CROSS-103 (T-128, 2026-09-02): the locally-reverted
                    // installment state MUST be enqueued for sync push —
                    // nothing else propagates it. The payment-status upsert
                    // and the reversal ledger entry above do NOT trigger any
                    // server-side waterfall revert (verified against the
                    // migration chain: the canonical server refund is the
                    // explicit `revert_payment_allocation` RPC, which the
                    // Android write path does not call — ARCH-003/ADR-005).
                    // The dispatcher pushes these via the idempotent
                    // `upsert_installment_from_import` RPC (TIER 4, migration
                    // 0037) — the SAME path the batch-registration flow uses,
                    // so the payload shape is identical by construction.
                    syncSupport?.enqueueOnly(
                        entity = "installment",
                        operation = "update",
                        payload = syncJson {
                            put("id", reverted.id); put("tenantId", reverted.tenantId)
                            put("parentId", reverted.parentId)
                            put("parentCode", revertedParentCode)
                            put("studentId", reverted.studentId ?: "")
                            put("category", reverted.category); put("label", reverted.label)
                            put("amountDue", reverted.amountDue); put("amountPaid", reverted.amountPaid)
                            put("amountPending", reverted.amountPending); put("dueDate", reverted.dueDate)
                            put("status", reverted.status)
                        },
                        isMock = false, sourceScreen = "PaymentDetailScreen",
                    )
                }
            }
        }

        auditDao.upsert(auditContext.audit("payment.refund", "payment", paymentId, actorId, actorName,
            after = """{"reason":"$reason"}"""))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun adjust(input: com.example.domain.repository.AdjustAccountInput, actorId: String, actorName: String): Result<Unit> {
        // CANONICAL-FINANCIAL-LOGIC.md §4 INV-7 — overpayment credits (amount < 0)
        // MUST land on the parent-scoped parent_credit account, NOT the input
        // category's student-scoped account. Positive adjustments (penalty / late
        // fee) keep their input category + studentId.
        val isCredit = input.amount < 0L
        val resolvedCategory = if (isCredit) PaymentCategory.PARENT_CREDIT else input.category
        val resolvedStudentId = if (isCredit) null else input.studentId
        val entry = com.example.core.createAdjustmentEntry(
            tenantId = auditContext.tenantId(),
            parentId = input.parentId, studentId = resolvedStudentId,
            category = resolvedCategory, amount = input.amount,
            sourceId = "adj-${UUID.randomUUID()}", actorId = actorId, actorName = actorName,
            reason = input.reason, receiptRef = input.receiptRef,
        )
        ledgerDao.upsert(entry.toEntity())
        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the adjustment entry for
        // sync push so the desktop sees it.
        val parentCode = parentCodeFor(entry.parentId) ?: ""
        syncSupport?.enqueueOnly(
            entity = "ledger_entry",
            operation = "adjust",
            payload = syncJson {
                put("id", entry.id); put("tenantId", entry.tenantId)
                put("accountId", entry.accountId)
                put("parentId", entry.parentId); put("parentCode", parentCode); put("studentId", entry.studentId ?: "")
                put("category", entry.category.code); put("amount", entry.amount)
                put("type", entry.type.code); put("sourceType", entry.sourceType.code)
                put("sourceId", entry.sourceId); put("method", entry.method?.code ?: "")
                put("receiptNumber", entry.receiptNumber ?: "")
                put("paymentStatus", entry.paymentStatus?.code ?: "")
                put("reversesId", entry.reversesId ?: "")
                put("description", entry.description)
                put("actorId", entry.actorId); put("actorName", entry.actorName)
                put("at", entry.at)
                put("metadataJson", com.example.infrastructure.room.LocalMappers.serializeMetadataJson(entry.metadata))
            },
            isMock = false, sourceScreen = "AdjustAccount",
        )
        auditDao.upsert(auditContext.audit("payment.adjust", "ledger", entry.id, actorId, actorName,
            after = """{"reason":"${input.reason}","amount":${input.amount}}"""))
        return Result.Ok(Unit)
    }
}
