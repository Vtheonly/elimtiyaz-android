package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.core.Result
import com.example.domain.model.Payment
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.CollectPaymentInput
import com.example.domain.repository.PaymentRepository
import com.example.infrastructure.room.toCacheEntity
import com.example.infrastructure.room.toDomain
import com.example.infrastructure.sync.SyncSupport
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of PaymentRepository.
 *
 * Collect and refund are ATOMIC operations that happen server-side via
 * Edge Functions (`collect-payment` and `refund-payment`). The Edge
 * Functions internally call the `collect_payment` and `refund_payment`
 * PostgreSQL SECURITY DEFINER functions which:
 *   - Insert the payment row (status auto-set by `enforce_payment_proof` trigger)
 *   - Update the linked installment's `amount_paid` (status recomputed by trigger)
 *   - Append a `ledger_entries` row (negative amount = credit)
 *   - Generate a receipt row
 *   - Write an audit log entry
 *
 * The client NEVER performs these steps individually — that would violate
 * the atomicity guarantee.
 */
@Singleton
class SupabasePaymentRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
    private val syncSupport: SyncSupport,
) : PaymentRepository {

    /**
     * Cache-then-network: emit cached payments instantly, then fetch from
     * Supabase, refresh cache, emit again. Offline → cache only.
     *
     * Payments are immutable server-side (RLS blocks UPDATE/DELETE), so the
     * cache is updated only via [upsertPayments] (REPLACE on conflict) — there
     * is no risk of stale edits overwriting newer server rows.
     */
    override fun observe() = syncSupport.cacheThenNetwork(
        cacheRead = {
            syncSupport.listCachedPayments().map { it.toDomain() }
        },
        cacheWrite = { payments: List<Payment> ->
            syncSupport.upsertPayments(payments.map { it.toCacheEntity() })
        },
        fetch = { fetchAll() },
    )

    override fun observeByParent(parentId: String) = kotlinx.coroutines.flow.flow {
        val rows = try {
            provider.postgrest.from("payments")
                .select {
                    filter { eq("parent_id", parentId) }
                    order("collected_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<PaymentDto>()
        } catch (e: Exception) { emptyList() }
        emit(rows.map { it.toDomain() })
    }

    override fun observeByStudent(studentId: String) = kotlinx.coroutines.flow.flow {
        val rows = try {
            provider.postgrest.from("payments")
                .select {
                    filter { eq("student_id", studentId) }
                    order("collected_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<PaymentDto>()
        } catch (e: Exception) { emptyList() }
        emit(rows.map { it.toDomain() })
    }

    override fun observeById(id: String) = kotlinx.coroutines.flow.flow {
        val row = try {
            provider.postgrest.from("payments")
                .select { filter { eq("id", id) } }
                .decodeList<PaymentDto>()
                .firstOrNull()
        } catch (e: Exception) { null }
        emit(row?.toDomain())
    }

    override suspend fun collect(input: CollectPaymentInput, actorId: String, actorName: String): Result<Payment> {
        // Client-side validation (server re-validates via the Edge Function).
        // Validation errors return immediately — they must NOT be enqueued.
        if (input.amount <= 0) return Result.Err(Errors.validation("Amount must be > 0"))
        if (input.method.requiresProof && input.proofPath.isNullOrBlank()) {
            return Result.Err(Errors.validation("Proof is required for ${input.method.code} payments"))
        }
        if (input.method == PaymentMethod.CHECK) {
            if (input.checkNumber.isNullOrBlank() || input.checkBankName.isNullOrBlank()) {
                return Result.Err(Errors.validation("Check number and bank name are required for check payments"))
            }
        }
        if (input.method == PaymentMethod.TRANSFER && input.transferReference.isNullOrBlank()) {
            return Result.Err(Errors.validation("Transfer reference is required for transfer payments"))
        }

        val params = buildJsonObject {
            put("parent_id", input.parentId)
            input.studentId?.let { put("student_id", it) }
            put("amount", input.amount)
            put("method", input.method.code)
            put("category", input.category.code)
            input.installmentId?.let { put("installment_id", it) }
            input.notes?.let { put("notes", it) }
            input.checkNumber?.let { put("check_number", it) }
            input.checkBankName?.let { put("check_bank_name", it) }
            input.checkIssueDate?.let { put("check_issue_date", it) }
            input.checkClearanceDate?.let { put("check_clearance_date", it) }
            input.transferReference?.let { put("transfer_reference", it) }
            input.transferSourceBank?.let { put("transfer_source_bank", it) }
            input.proofPath?.let { put("proof_path", it) }
        }

        // Try direct Edge Function call; on offline/network failure, enqueue the
        // payment insert for [SyncWorker] to drain later (insert semantics —
        // payments are immutable server-side). Returns Result.Err(CODE_OFFLINE)
        // so the UI can surface a "queued for sync" message.
        return syncSupport.tryThenEnqueue(
            entity = "payment",
            operation = "collect",
            payload = {
                syncSupport.json().encodeToString(JsonObject.serializer(), params)
            },
            sourceScreen = "CounterPayment",
        ) {
            val response = provider.functions.invoke("collect-payment") {
                setBody(params)
            }
            val data = response.body<CollectPaymentResponse>()
            val payment = fetchById(data.paymentId)
                ?: error("Payment ${data.paymentId} not found after collect")

            // Audit log (the Edge Function also writes one, but we add a mobile-specific note)
            auditRepository.log(AuditLogInput(
                action = AuditActions.PAYMENT_COLLECT,
                entityType = "payment",
                entityId = payment.id,
                afterJson = """{"amount":${payment.amount},"method":"${payment.method.code}","receipt":"${payment.receiptNumber}"}""",
                note = "Collected from Android app",
            ))

            // Persist to cache so the next observe() emits it instantly.
            syncSupport.upsertPayments(listOf(payment.toCacheEntity()))
            payment
        }
    }

    override suspend fun refund(paymentId: String, reason: String, actorId: String, actorName: String): Result<Payment> {
        // Validation — must NOT be enqueued.
        if (reason.length < 3) return Result.Err(Errors.validation("Refund reason must be at least 3 characters"))

        val params = buildJsonObject {
            put("payment_id", paymentId)
            put("reason", reason)
        }

        // Try direct Edge Function call; on offline, enqueue the refund as a
        // payment insert (the reversal row) for later drain.
        return syncSupport.tryThenEnqueue(
            entity = "payment",
            operation = "refund",
            payload = {
                syncSupport.json().encodeToString(JsonObject.serializer(), params)
            },
            sourceScreen = "PaymentDetail",
        ) {
            val response = provider.functions.invoke("refund-payment") {
                setBody(params)
            }
            val data = response.body<RefundPaymentResponse>()

            // Fetch the reversal payment
            val reversal = fetchById(data.reversalPaymentId)
                ?: error("Reversal payment ${data.reversalPaymentId} not found")

            auditRepository.log(AuditLogInput(
                action = AuditActions.PAYMENT_REFUND,
                entityType = "payment",
                entityId = paymentId,
                afterJson = """{"reversal_payment_id":"${data.reversalPaymentId}","reason":"$reason"}""",
                note = "Refunded from Android app",
            ))

            // Persist reversal to cache.
            syncSupport.upsertPayments(listOf(reversal.toCacheEntity()))
            reversal
        }
    }

    override suspend fun adjust(input: com.example.domain.repository.AdjustAccountInput, actorId: String, actorName: String): Result<Unit> {
        // Account adjustments are done via a ledger entry directly (no Edge Function).
        // This is acceptable because the ledger_entries table accepts inserts from
        // authenticated users with the adjust_account permission (RLS-enforced).
        val params = buildJsonObject {
            put("p_parent_id", input.parentId)
            input.studentId?.let { put("p_student_id", it) }
            put("p_category", input.category.code)
            put("p_amount", input.amount)
            put("p_reason", input.reason)
            input.receiptRef?.let { put("p_receipt_ref", it) }
        }
        // Try direct RPC; on offline, enqueue as a payment/adjust insert for
        // later drain. The drain-side [SupabaseSyncDao.pushPayment] does a
        // direct table insert — for adjust this is best-effort; the audit
        // failure log will surface drain failures after 5 attempts.
        return syncSupport.tryThenEnqueue(
            entity = "payment",
            operation = "adjust",
            payload = {
                syncSupport.json().encodeToString(JsonObject.serializer(), params)
            },
            sourceScreen = "ParentLedger",
        ) {
            provider.postgrest.rpc("create_account_adjustment", params)
            auditRepository.log(AuditLogInput(
                action = AuditActions.PAYMENT_ADJUST,
                entityType = "parent",
                entityId = input.parentId,
                afterJson = """{"amount":${input.amount},"category":"${input.category.code}","reason":"${input.reason}"}""",
                note = "Account adjusted from Android app",
            ))
            Unit
        }
    }

    private suspend fun fetchAll(): List<Payment> = try {
        provider.postgrest.from("payments")
            .select {
                order("collected_at", Order.DESCENDING)
                limit(200)
            }
            .decodeList<PaymentDto>()
            .map { it.toDomain() }
    } catch (e: Exception) { emptyList() }

    private suspend fun fetchById(id: String): Payment? = try {
        provider.postgrest.from("payments")
            .select { filter { eq("id", id) } }
            .decodeList<PaymentDto>()
            .firstOrNull()
            ?.toDomain()
    } catch (e: Exception) { null }

    @Serializable
    data class PaymentDto(
        val id: String,
        val tenantId: String,
        val receiptNumber: String,
        val parentId: String,
        val studentId: String? = null,
        val amount: Long,
        val method: String,
        val status: String,
        val category: String,
        val installmentId: String? = null,
        val proofUrl: String? = null,
        val notes: String? = null,
        val collectedBy: String,
        val collectedAt: String,
        val createdAt: String,
        val updatedAt: String,
    ) {
        fun toDomain() = Payment(
            id = id, tenantId = tenantId, receiptNumber = receiptNumber,
            parentId = parentId, studentId = studentId, amount = amount,
            method = PaymentMethod.fromCode(method) ?: PaymentMethod.CASH,
            status = PaymentStatus.fromCode(status) ?: PaymentStatus.PAID,
            category = PaymentCategory.fromCode(category) ?: PaymentCategory.OTHER,
            installmentId = installmentId, proofUrl = proofUrl, notes = notes,
            collectedBy = collectedBy, collectedAt = collectedAt,
            createdAt = createdAt, updatedAt = updatedAt,
        )
    }

    @Serializable
    data class CollectPaymentResponse(
        val paymentId: String,
        val receiptId: String,
        val newInstallmentStatus: String? = null,
        val message: String? = null,
    )

    @Serializable
    data class RefundPaymentResponse(
        val reversalPaymentId: String,
        val message: String? = null,
    )
}
