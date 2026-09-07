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

// ─── Installment Repository ─────────────────────────────────────────────────

@Singleton
class LocalInstallmentRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val installmentDao: InstallmentDao,
    private val auditDao: AuditLogDao,
    // CANONICAL-FINANCIAL-LOGIC.md §8.1 — wire SyncSupport so installment
    // updates (markPaid, updateDueDate) propagate to Supabase.
    private val syncSupport: com.example.infrastructure.sync.SyncSupport? = null,
    // FIX (ledger divergence): needed so an interactive "mark paid" also
    // writes the backing payment + ledger entry — previously ONLY the
    // installment row flipped, so the canonical ledger replay (Progression
    // card, parent balances) never changed.
    private val db: ElImtiyazDatabase,
) : InstallmentRepository {

    private fun syncJson(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        kotlinx.serialization.json.buildJsonObject(builder).toString()

    override fun observeByParent(parentId: String): Flow<List<Installment>> =
        installmentDao.observeByParent(parentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByStudent(studentId: String): Flow<List<Installment>> =
        installmentDao.observeByStudent(studentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Installment?> =
        installmentDao.observeById(id).map { it?.let { e -> LocalMappers.run { e.toDomain() } } }

    override suspend fun markPaid(id: String, actorId: String, actorName: String): Result<Installment> {
        val existing = installmentDao.getById(id) ?: return Result.Err(Errors.notFound("Installment $id not found"))
        val now = Instant.now().toString()
        // CANONICAL-FINANCIAL-LOGIC.md §7.3 — INV "amountPaid >= amountDue"
        // when status='paid'. Set amountPaid = amountDue (matches the desktop
        // SupabaseInstallmentRepository.markPaid fix).
        val updated = existing.copy(amountPaid = existing.amountDue, amountPending = 0L, status = "paid", paidDate = now, updatedAt = now)
        installmentDao.update(updated)

        // FIX (ledger divergence): record the backing cash payment + ledger
        // entry so the canonical ledger replay reflects the settlement.
        // Without this, the tranche showed "paid" while the parent's balance
        // and the Progression card ignored it entirely.
        if (existing.status != "paid" && existing.amountDue > 0L) {
            try {
                val paymentDao = db.paymentDao()
                val ledgerDao = db.ledgerEntryDao()
                val year = java.time.LocalDate.now().year
                val seq = (paymentDao.listAll().size + 1).toString().padStart(6, '0')
                val receipt = "REC-$year-$seq"
                val paymentId = "pay-${UUID.randomUUID()}"
                val paymentEntity = com.example.infrastructure.room.PaymentEntity(
                    id = paymentId,
                    tenantId = auditContext.tenantId(),
                    receiptNumber = receipt,
                    parentId = existing.parentId,
                    studentId = existing.studentId,
                    amount = existing.amountDue,
                    method = PaymentMethod.CASH.code,
                    status = PaymentStatus.PAID.code,
                    category = existing.category,
                    installmentId = existing.id,
                    proofUrl = null, checkNumber = null, checkBankName = null,
                    checkIssueDate = null, checkClearanceDate = null,
                    transferReference = null, transferSourceBank = null,
                    notes = "Marqué payé (tranche ${existing.label})",
                    collectedBy = actorId, collectedBy_name = actorName,
                    collectedAt = now, createdAt = now, updatedAt = now,
                )
                paymentDao.upsert(paymentEntity)
                val category = PaymentCategory.fromCode(existing.category)
                    ?: PaymentCategory.OTHER
                ledgerDao.upsert(
                    createPaymentEntry(
                        tenantId = paymentEntity.tenantId,
                        parentId = existing.parentId,
                        studentId = existing.studentId,
                        category = category,
                        amount = existing.amountDue,
                        method = PaymentMethod.CASH,
                        receiptNumber = receipt,
                        paymentStatus = PaymentStatus.PAID,
                        sourceId = paymentId,
                        actorId = actorId,
                        actorName = actorName,
                        description = "Encaissement $receipt — tranche ${existing.label}",
                    ).toEntity()
                )
                syncSupport?.enqueueOnly(
                    entity = "payment",
                    operation = "create",
                    payload = syncJson {
                        put("id", paymentId)
                        put("tenantId", paymentEntity.tenantId)
                        put("receiptNumber", receipt)
                        put("parentId", existing.parentId)
                        put("studentId", existing.studentId ?: "")
                        put("amount", existing.amountDue)
                        put("method", paymentEntity.method)
                        put("status", paymentEntity.status)
                        put("category", existing.category)
                        put("installmentId", existing.id)
                        put("collectedBy", actorId)
                        put("collectedAt", now)
                    },
                    isMock = false, sourceScreen = "InstallmentSchedule",
                )
            } catch (t: Throwable) {
                // Never fail the markPaid because of the companion entries —
                // but record what happened for diagnosis.
                auditDao.upsert(auditContext.audit("installment.markPaid.companion_error", "installment", id, actorId, actorName,
                    after = "{\"error\":\"" + (t.message ?: "unknown") + "\"}"))
            }
        }

        // Enqueue for sync push.
        syncSupport?.enqueueOnly(
            entity = "installment",
            operation = "markPaid",
            payload = syncJson {
                put("id", id); put("status", "paid"); put("amountPaid", updated.amountPaid)
                put("amountPending", updated.amountPending); put("paidDate", now)
            },
            isMock = false, sourceScreen = "InstallmentSchedule",
        )
        auditDao.upsert(auditContext.audit("installment.markPaid", "installment", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun updateDueDate(id: String, dueDate: String, note: String?, actorId: String, actorName: String): Result<Installment> {
        val existing = installmentDao.getById(id) ?: return Result.Err(Errors.notFound("Installment $id not found"))
        val updated = existing.copy(dueDate = dueDate, customSchedule = true, customScheduleNote = note, updatedAt = Instant.now().toString())
        installmentDao.update(updated)
        // Enqueue for sync push.
        syncSupport?.enqueueOnly(
            entity = "installment",
            operation = "updateDueDate",
            payload = syncJson {
                put("id", id); put("dueDate", dueDate); put("note", note ?: "")
                put("customSchedule", true)
            },
            isMock = false, sourceScreen = "InstallmentSchedule",
        )
        auditDao.upsert(auditContext.audit("installment.updateDueDate", "installment", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    /**
     * T-054 (WEAK-006) — REAL re-derivation, mirroring the desktop's
     * `SupabaseInstallmentRepository.regenerateForCycle`
     * (supabase-shared-repositories.ts:2112): re-derive the due dates of the
     * parent's OUTSTANDING (non-paid) installments from
     * `officialTuitionDueDates(year)` (Sept 15 / Dec 15 / Mar 15 — all cycles
     * share the official schedule per Prices.md), reset the custom-schedule
     * flags, and stamp `academic_cycle`. Paid tranches are preserved
     * (they're settled). The old implementation wrote an audit row and
     * returned the installments UNCHANGED — the audit log lied that
     * "installment.regenerate" happened.
     */
    override suspend fun regenerateForCycle(parentId: String, cycle: String, actorId: String, actorName: String): Result<List<Installment>> {
        val now = Instant.now().toString()
        val year = java.time.Year.now().value
        val (t1, t2, t3) = com.example.core.officialTuitionDueDates(year)

        val family = installmentDao.listByParent(parentId)
        val updated = mutableListOf<Installment>()
        for (inst in family) {
            if (inst.status == "paid") continue // preserve settled tranches
            // Derive the tranche number from the label's first digit
            // (desktop: inst.label?.match(/(\d)/)?.[1] ?? "1").
            val trancheNum = Regex("(\\d)").find(inst.label)?.groupValues?.get(1) ?: "1"
            val newDueDate = when (trancheNum) {
                "1" -> t1
                "2" -> t2
                else -> t3
            }
            val patched = inst.copy(
                dueDate = newDueDate,
                customSchedule = false,
                customScheduleNote = null,
                academicCycle = cycle,
                updatedAt = now,
            )
            installmentDao.update(patched)
            // CANONICAL-FINANCIAL-LOGIC.md §8.1 — propagate to the server via
            // the idempotent upsert_installment_from_import RPC path.
            syncSupport?.enqueueOnly(
                entity = "installment",
                operation = "regenerateForCycle",
                payload = syncJson {
                    put("id", patched.id)
                    put("parentId", patched.parentId)
                    patched.studentId?.let { put("studentId", it) }
                    put("category", patched.category)
                    put("label", patched.label)
                    put("amountDue", patched.amountDue)
                    put("amountPaid", patched.amountPaid)
                    put("amountPending", patched.amountPending)
                    put("dueDate", patched.dueDate)
                    put("status", patched.status)
                    put("academicCycle", cycle)
                },
                isMock = false, sourceScreen = "InstallmentSchedule",
            )
            updated.add(LocalMappers.run { patched.toDomain() })
        }

        auditDao.upsert(auditContext.audit("installment.regenerate", "installment", parentId, actorId, actorName,
            after = """{"cycle":"$cycle","rederived":${updated.size}}"""))

        // Desktop contract: the patched list first, then the untouched rows.
        val updatedIds = updated.map { it.id }.toSet()
        val untouched = family.filter { it.id !in updatedIds }.map { LocalMappers.run { it.toDomain() } }
        return Result.Ok(updated + untouched)
    }

    override suspend fun findOverdue(): Result<List<Installment>> {
        val now = Instant.now().toString()
        return Result.Ok(installmentDao.listOverdue(now).map { LocalMappers.run { it.toDomain() } })
    }
}
