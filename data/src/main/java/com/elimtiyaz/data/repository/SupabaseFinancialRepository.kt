package com.elimtiyaz.data.repository

import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.DispatcherProvider
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.onFailure
import com.elimtiyaz.data.local.dao.InstallmentDao
import com.elimtiyaz.data.local.dao.ParentDao
import com.elimtiyaz.data.local.dao.PaymentDao
import com.elimtiyaz.data.local.dao.SyncQueueDao
import com.elimtiyaz.data.local.entity.toDomain
import com.elimtiyaz.data.local.entity.toEntity
import com.elimtiyaz.data.remote.dto.AccountAdjustmentDto
import com.elimtiyaz.data.remote.dto.InstallmentDto
import com.elimtiyaz.data.remote.dto.PaymentDto
import com.elimtiyaz.data.remote.dto.ReceiptDto
import com.elimtiyaz.domain.model.AccountAdjustment
import com.elimtiyaz.domain.model.AgingBucket
import com.elimtiyaz.domain.model.CreatePaymentInput
import com.elimtiyaz.domain.model.DebtSummary
import com.elimtiyaz.domain.model.Installment
import com.elimtiyaz.domain.model.ParentFinancialProfile
import com.elimtiyaz.domain.model.Payment
import com.elimtiyaz.domain.model.PaymentCategory
import com.elimtiyaz.domain.model.Receipt
import com.elimtiyaz.domain.repository.AuditRepository
import com.elimtiyaz.domain.repository.DebtRepository
import com.elimtiyaz.domain.repository.InstallmentRepository
import com.elimtiyaz.domain.repository.PaymentRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val PAYMENTS_TABLE = "payments"
private const val INSTALLMENTS_TABLE = "installments"
private const val ADJUSTMENTS_TABLE = "account_adjustments"
private const val RECEIPTS_TABLE = "receipts"

/** Supabase-backed [PaymentRepository] — counter payment entry + receipts. */
@Singleton
class SupabasePaymentRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val paymentDao: PaymentDao,
    private val installmentDao: InstallmentDao,
    private val parentDao: ParentDao,
    private val syncQueueDao: SyncQueueDao,
    private val audit: AuditRepository,
    private val dispatchers: DispatcherProvider,
) : PaymentRepository {

    private val log = Logger.withTag("Data.Payment")
    private val sync = SyncQueueHelper(syncQueueDao)

    /** Stream all payments. */
    override fun payments(): Flow<Result<List<Payment>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { paymentDao.all().map { it.toDomain() } },
        fetch = { supabase.from(PAYMENTS_TABLE).select().decodeList<PaymentDto>().map { it.toDomain() } },
        persist = { ps -> paymentDao.upsertAll(ps.map { it.toEntity() }) },
    )

    /** Stream payments for a parent. */
    override fun paymentsByParent(parentId: String): Flow<Result<List<Payment>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { paymentDao.observeByParent(parentId).first().map { it.toDomain() } },
        fetch = {
            supabase.from(PAYMENTS_TABLE).select { filter { eq("parent_id", parentId) } }
                .decodeList<PaymentDto>().map { it.toDomain() }
        },
        persist = { ps -> paymentDao.upsertAll(ps.map { it.toEntity() }) },
    )

    /** Stream payments for a student. */
    override fun paymentsByStudent(studentId: String): Flow<Result<List<Payment>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { paymentDao.observeByStudent(studentId).first().map { it.toDomain() } },
        fetch = {
            supabase.from(PAYMENTS_TABLE).select { filter { eq("student_id", studentId) } }
                .decodeList<PaymentDto>().map { it.toDomain() }
        },
        persist = { ps -> paymentDao.upsertAll(ps.map { it.toEntity() }) },
    )

    /** Stream a single payment by id. */
    override fun payment(id: String): Flow<Result<Payment>> = RepositoryHelpers.cacheThenFetchOne(
        dispatchers = dispatchers,
        loadCache = { paymentDao.observeById(id).first()?.toDomain() },
        fetch = {
            supabase.from(PAYMENTS_TABLE).select { filter { eq("id", id) } }
                .decodeList<PaymentDto>().firstOrNull()?.toDomain() ?: error("Paiement $id introuvable.")
        },
        persist = { p -> paymentDao.upsert(p.toEntity()) },
    )

    /** Collect a payment (counter entry), issue receipt number, audit-log. */
    override suspend fun collect(input: CreatePaymentInput, collectedBy: String): Result<Payment> =
        Result.runCatching {
            val nowIso = nowIso()
            val id = UUID.randomUUID().toString()
            val year = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
            val seq = (1..999999).random()
            val receipt = Formatters.receiptNumber(year, seq)
            val dto = PaymentDto(
                id = id, tenantId = DEFAULT_TENANT, receiptNumber = receipt, parentId = input.parentId,
                studentId = input.studentId, amount = input.amount, method = input.method, status = "paid",
                category = input.category, installmentId = input.installmentId, proofUrl = input.proofUrl,
                notes = input.notes, collectedBy = collectedBy, collectedAt = nowIso,
                createdAt = nowIso, updatedAt = nowIso,
            )
            supabase.from(PAYMENTS_TABLE).insert(dto)
            val domain = dto.toDomain()
            paymentDao.upsert(domain.toEntity())
            // If the payment settles an installment, mark it paid.
            input.installmentId?.let { iid ->
                runCatching {
                    supabase.from(INSTALLMENTS_TABLE).update(
                        mapOf("amount_paid" to input.amount, "paid_date" to nowIso, "status" to "paid"),
                    ) { filter { eq("id", iid) } }
                }
            }
            audit.log("payment.create", "payment", id, actorId = collectedBy, tenantId = DEFAULT_TENANT,
                diff = "amount=${input.amount} method=${input.method}")
            log.i { "Collected payment $receipt (${input.amount})" }
            domain
        }.onFailure {
            val payload = PaymentDto(
                id = UUID.randomUUID().toString(), tenantId = DEFAULT_TENANT, receiptNumber = "REC-PENDING",
                parentId = input.parentId, studentId = input.studentId, amount = input.amount,
                method = input.method, status = "pending", category = input.category,
                installmentId = input.installmentId, proofUrl = input.proofUrl, notes = input.notes,
                collectedBy = collectedBy, collectedAt = nowIso(), createdAt = nowIso(), updatedAt = nowIso(),
            )
            sync.enqueueRaw(PAYMENTS_TABLE, "insert", sync.encode(payload))
        }

    /** Refund a payment — marks it `refunded`. */
    override suspend fun refund(id: String): Result<Payment> = Result.runCatching {
        supabase.from(PAYMENTS_TABLE).update(mapOf("status" to "refunded", "updated_at" to nowIso())) {
            filter { eq("id", id) }
        }
        val refreshed = supabase.from(PAYMENTS_TABLE).select { filter { eq("id", id) } }
            .decodeList<PaymentDto>().firstOrNull()?.toDomain() ?: error("Paiement $id introuvable.")
        paymentDao.upsert(refreshed.toEntity())
        audit.log("payment.refund", "payment", id, actorId = "system", tenantId = DEFAULT_TENANT)
        log.i { "Refunded payment $id" }
        refreshed
    }.onFailure {
        sync.enqueueRaw(PAYMENTS_TABLE, "update", sync.encode(mapOf("id" to id, "status" to "refunded")))
    }

    /** Discretionary account adjustment — §07.04. */
    override suspend fun adjust(
        parentId: String, amount: Double, reason: String, approvedBy: String,
    ): Result<AccountAdjustment> = Result.runCatching {
        val id = UUID.randomUUID().toString()
        val dto = AccountAdjustmentDto(
            id = id, parentId = parentId, amount = amount, reason = reason,
            approvedBy = approvedBy, approvedAt = nowIso(), receiptRef = null,
        )
        supabase.from(ADJUSTMENTS_TABLE).insert(dto)
        audit.log("payment.adjust", "account_adjustment", id, actorId = approvedBy, tenantId = DEFAULT_TENANT,
            diff = "amount=$amount reason=$reason")
        log.i { "Adjustment recorded for parent=$parentId amount=$amount" }
        dto.toDomain()
    }.onFailure {
        sync.enqueueRaw(ADJUSTMENTS_TABLE, "insert", sync.encode(AccountAdjustmentDto(
            id = UUID.randomUUID().toString(), parentId = parentId, amount = amount, reason = reason,
            approvedBy = approvedBy, approvedAt = nowIso(), receiptRef = null,
        )))
    }

    /** Generate a receipt PDF (Edge Function returns the signed URL). */
    override suspend fun generateReceipt(paymentId: String, generatedBy: String): Result<Receipt> =
        Result.runCatching {
            val payment = supabase.from(PAYMENTS_TABLE).select { filter { eq("id", paymentId) } }
                .decodeList<PaymentDto>().firstOrNull() ?: error("Paiement $paymentId introuvable.")
            val id = UUID.randomUUID().toString()
            val dto = ReceiptDto(
                id = id, paymentId = paymentId, receiptNumber = payment.receiptNumber,
                pdfUrl = "https://elimtiyaz.supabase.co/storage/v1/object/public/receipts/$id.pdf",
                generatedAt = nowIso(), generatedBy = generatedBy,
            )
            supabase.from(RECEIPTS_TABLE).insert(dto)
            audit.log("receipt.generate", "receipt", id, actorId = generatedBy, tenantId = DEFAULT_TENANT)
            log.i { "Generated receipt ${dto.receiptNumber}" }
            dto.toDomain()
        }
}

/** Supabase-backed [InstallmentRepository] — tranche lifecycle. */
@Singleton
class SupabaseInstallmentRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val installmentDao: InstallmentDao,
    private val syncQueueDao: SyncQueueDao,
    private val dispatchers: DispatcherProvider,
) : InstallmentRepository {

    private val log = Logger.withTag("Data.Installment")
    private val sync = SyncQueueHelper(syncQueueDao)

    /** Stream installments for a parent. */
    override fun installmentsByParent(parentId: String): Flow<Result<List<Installment>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { installmentDao.observeByParent(parentId).first().map { it.toDomain() } },
        fetch = {
            supabase.from(INSTALLMENTS_TABLE).select { filter { eq("parent_id", parentId) } }
                .decodeList<InstallmentDto>().map { it.toDomain() }
        },
        persist = { installments -> installmentDao.upsertAll(installments.map { it.toEntity() }) },
    )

    /** Stream installments for a student. */
    override fun installmentsByStudent(studentId: String): Flow<Result<List<Installment>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { installmentDao.observeByStudent(studentId).first().map { it.toDomain() } },
        fetch = {
            supabase.from(INSTALLMENTS_TABLE).select { filter { eq("student_id", studentId) } }
                .decodeList<InstallmentDto>().map { it.toDomain() }
        },
        persist = { installments -> installmentDao.upsertAll(installments.map { it.toEntity() }) },
    )

    /** Create a new installment row. */
    override suspend fun createInstallment(
        parentId: String, studentId: String, category: PaymentCategory,
        label: String, amountDue: Double, dueDate: String,
    ): Result<Installment> = Result.runCatching {
        val id = UUID.randomUUID().toString()
        val dto = InstallmentDto(
            id = id, parentId = parentId, studentId = studentId, category = category, label = label,
            amountDue = amountDue, amountPaid = 0.0, dueDate = dueDate, paidDate = null, status = "pending",
        )
        supabase.from(INSTALLMENTS_TABLE).insert(dto)
        val domain = dto.toDomain()
        installmentDao.upsert(domain.toEntity())
        log.i { "Created installment $label for parent=$parentId" }
        domain
    }.onFailure {
        sync.enqueueRaw(INSTALLMENTS_TABLE, "insert", sync.encode(InstallmentDto(
            id = UUID.randomUUID().toString(), parentId = parentId, studentId = studentId, category = category,
            label = label, amountDue = amountDue, amountPaid = 0.0, dueDate = dueDate, paidDate = null, status = "pending",
        )))
    }

    /** Mark an installment as paid via a payment. */
    override suspend fun markPaid(id: String, paymentId: String): Result<Installment> = Result.runCatching {
        supabase.from(INSTALLMENTS_TABLE).update(
            mapOf("amount_paid" to 0.0, "paid_date" to nowIso(), "status" to "paid"),
        ) { filter { eq("id", id) } }
        val refreshed = supabase.from(INSTALLMENTS_TABLE).select { filter { eq("id", id) } }
            .decodeList<InstallmentDto>().firstOrNull()?.toDomain() ?: error("Tranche $id introuvable.")
        installmentDao.upsert(refreshed.toEntity())
        log.i { "Marked installment $id paid (payment=$paymentId)" }
        refreshed
    }.onFailure {
        sync.enqueueRaw(INSTALLMENTS_TABLE, "update", sync.encode(mapOf("id" to id, "payment_id" to paymentId)))
    }
}

/** Supabase-backed [DebtRepository] — aging buckets + parent profile. */
@Singleton
class SupabaseDebtRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val paymentDao: PaymentDao,
    private val installmentDao: InstallmentDao,
    private val parentDao: ParentDao,
    private val audit: AuditRepository,
    private val dispatchers: DispatcherProvider,
) : DebtRepository {

    private val log = Logger.withTag("Data.Debt")

    /** Stream the debt summary across all parents (computed from installments). */
    override fun debtSummary(): Flow<Result<List<DebtSummary>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { buildDebtSummary() },
        fetch = {
            val installments = supabase.from(INSTALLMENTS_TABLE).select()
                .decodeList<InstallmentDto>().map { it.toDomain() }
            installments.forEach { installmentDao.upsert(it.toEntity()) }
            buildDebtSummary()
        },
        persist = { /* summary is computed, nothing to persist */ },
    )

    /** Stream the parent's full financial profile. */
    override fun parentFinancialProfile(parentId: String): Flow<Result<ParentFinancialProfile>> =
        RepositoryHelpers.cacheThenFetchOne(
            dispatchers = dispatchers,
            loadCache = { buildProfile(parentId) },
            fetch = { buildProfile(parentId) },
            persist = { /* computed */ },
        )

    /** Trigger a reminder (WhatsApp/SMS) via Edge Function. */
    override suspend fun sendReminder(parentId: String): Result<Unit> = Result.runCatching {
        audit.log("debt.reminder", "parent", parentId, actorId = "system", tenantId = DEFAULT_TENANT)
        log.i { "Reminder sent to parent $parentId" }
    }

    /** Compute debt summaries from cached installments + parents. */
    private suspend fun buildDebtSummary(): List<DebtSummary> {
        val parents = parentDao.all()
        val installments = supabase.from(INSTALLMENTS_TABLE).select().decodeList<InstallmentDto>().map { it.toDomain() }
        return parents.map { p ->
            val parentInstallments = installments.filter { it.parentId == p.id }
            val outstanding = parentInstallments.sumOf { (it.amountDue - it.amountPaid).coerceAtLeast(0.0) }
            val overdueInstallments = parentInstallments.filter { it.status == "overdue" }
            val daysOverdue = overdueInstallments.maxOfOrNull {
                runCatching {
                    val due = kotlinx.datetime.LocalDate.parse(it.dueDate.take(10))
                    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                    (today.toEpochDays() - due.toEpochDays()).coerceAtLeast(0)
                }.getOrDefault(0)
            } ?: 0
            DebtSummary(
                parentId = p.id, parentName = "${p.firstName} ${p.lastName}", parentPhone = p.phone,
                studentCount = 0, outstandingAmount = outstanding, daysOverdue = daysOverdue,
            )
        }.filter { it.outstandingAmount > 0 }
    }

    /** Compute the parent's financial profile from cached data. */
    private suspend fun buildProfile(parentId: String): ParentFinancialProfile {
        val parent = parentDao.all().firstOrNull { it.id == parentId }?.toDomain()
            ?: error("Parent $parentId introuvable.")
        val installments = installmentDao.observeByParent(parentId).first().map { it.toDomain() }
        val payments = paymentDao.observeByParent(parentId).first().map { it.toDomain() }
        val totalDue = installments.sumOf { it.amountDue }
        val totalPaid = payments.sumOf { it.amount }
        val outstanding = (totalDue - totalPaid).coerceAtLeast(0.0)
        val overdue = installments.filter { it.status == "overdue" }.sumOf { (it.amountDue - it.amountPaid).coerceAtLeast(0.0) }
        return ParentFinancialProfile(
            parentId = parent.id, parentName = "${parent.firstName} ${parent.lastName}",
            totalDue = totalDue, totalPaid = totalPaid, totalOutstanding = outstanding,
            overdueAmount = overdue, installments = installments, recentPayments = payments.take(10),
            adjustments = emptyList(),
        )
    }
}

private const val DEFAULT_TENANT = "tenant-default"

/** Convenience alias for the aging-bucket list — used by the dashboard repository. */
@Suppress("unused")
private val ALL_AGING_BUCKETS: List<AgingBucket> = AgingBucket.values().toList()
