package com.elimtiyaz.data.mock

import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.domain.model.AccountAdjustment
import com.elimtiyaz.domain.model.CreatePaymentInput
import com.elimtiyaz.domain.model.DebtSummary
import com.elimtiyaz.domain.model.Installment
import com.elimtiyaz.domain.model.ParentFinancialProfile
import com.elimtiyaz.domain.model.Payment
import com.elimtiyaz.domain.model.PaymentCategory
import com.elimtiyaz.domain.model.Receipt
import com.elimtiyaz.domain.repository.DebtRepository
import com.elimtiyaz.domain.repository.InstallmentRepository
import com.elimtiyaz.domain.repository.PaymentRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private fun mockDelay() = delay((200L..500L).random())

/** Mock [PaymentRepository]. */
@Singleton
class MockPaymentRepository @Inject constructor() : PaymentRepository {

    private val log = Logger.withTag("Mock.Payment")
    private val state = MutableStateFlow(MockData.payments)

    /** Stream all payments. */
    override fun payments(): Flow<Result<List<Payment>>> = state.map { Result.success(it) }

    /** Stream payments for a parent. */
    override fun paymentsByParent(parentId: String): Flow<Result<List<Payment>>> =
        state.map { Result.success(it.filter { p -> p.parentId == parentId }) }

    /** Stream payments for a student. */
    override fun paymentsByStudent(studentId: String): Flow<Result<List<Payment>>> =
        state.map { Result.success(it.filter { p -> p.studentId == studentId }) }

    /** Stream a single payment. */
    override fun payment(id: String): Flow<Result<Payment>> = state.map { payments ->
        val p = payments.firstOrNull { it.id == id }
            ?: return@map Result.failure("Paiement $id introuvable.")
        Result.success(p)
    }

    /** Collect a payment — generates a receipt number. */
    override suspend fun collect(input: CreatePaymentInput, collectedBy: String): Result<Payment> {
        mockDelay()
        val nowIso = Clock.System.now().toString()
        val year = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        val seq = (state.value.size + 100)
        val payment = Payment(
            id = "pay-new-${UUID.randomUUID().toString().take(6)}",
            tenantId = MockData.TENANT_ID,
            receiptNumber = "REC-$year-${seq.toString().padStart(6, '0')}",
            parentId = input.parentId, studentId = input.studentId, amount = input.amount,
            method = input.method, status = "paid", category = input.category,
            installmentId = input.installmentId, proofUrl = input.proofUrl, notes = input.notes,
            collectedBy = collectedBy, collectedAt = nowIso, createdAt = nowIso, updatedAt = nowIso,
        )
        state.value = state.value + payment
        log.i { "Collected payment ${payment.receiptNumber} (${input.amount} DZD)" }
        return Result.success(payment)
    }

    /** Refund a payment. */
    override suspend fun refund(id: String): Result<Payment> {
        mockDelay()
        val updated = state.value.map { p ->
            if (p.id != id) p else p.copy(status = "refunded", updatedAt = Clock.System.now().toString())
        }
        state.value = updated
        val result = updated.firstOrNull { it.id == id }
            ?: return Result.failure("Paiement $id introuvable.")
        log.i { "Refunded payment $id" }
        return Result.success(result)
    }

    /** Discretionary account adjustment. */
    override suspend fun adjust(
        parentId: String, amount: Double, reason: String, approvedBy: String,
    ): Result<AccountAdjustment> {
        mockDelay()
        val adjustment = AccountAdjustment(
            id = "ad-new-${UUID.randomUUID().toString().take(6)}", parentId = parentId,
            amount = amount, reason = reason, approvedBy = approvedBy,
            approvedAt = Clock.System.now().toString(), receiptRef = null,
        )
        log.i { "Adjustment for parent=$parentId amount=$amount" }
        return Result.success(adjustment)
    }

    /** Generate a receipt PDF (mocked — returns a fake URL). */
    override suspend fun generateReceipt(paymentId: String, generatedBy: String): Result<Receipt> {
        mockDelay()
        val payment = state.value.firstOrNull { it.id == paymentId }
            ?: return Result.failure("Paiement $paymentId introuvable.")
        val id = "rc-new-${UUID.randomUUID().toString().take(6)}"
        val receipt = Receipt(
            id = id, paymentId = paymentId, receiptNumber = payment.receiptNumber,
            pdfUrl = "https://elimtiyaz.supabase.co/storage/v1/object/public/receipts/$id.pdf",
            generatedAt = Clock.System.now().toString(), generatedBy = generatedBy,
        )
        log.i { "Generated receipt ${receipt.receiptNumber}" }
        return Result.success(receipt)
    }
}

/** Mock [InstallmentRepository]. */
@Singleton
class MockInstallmentRepository @Inject constructor() : InstallmentRepository {

    private val log = Logger.withTag("Mock.Installment")
    private val state = MutableStateFlow(MockData.installments)

    /** Stream installments for a parent. */
    override fun installmentsByParent(parentId: String): Flow<Result<List<Installment>>> =
        state.map { Result.success(it.filter { i -> i.parentId == parentId }) }

    /** Stream installments for a student. */
    override fun installmentsByStudent(studentId: String): Flow<Result<List<Installment>>> =
        state.map { Result.success(it.filter { i -> i.studentId == studentId }) }

    /** Create a new installment. */
    override suspend fun createInstallment(
        parentId: String, studentId: String, category: PaymentCategory,
        label: String, amountDue: Double, dueDate: String,
    ): Result<Installment> {
        mockDelay()
        val installment = Installment(
            id = "i-new-${UUID.randomUUID().toString().take(6)}", parentId = parentId,
            studentId = studentId, category = category, label = label, amountDue = amountDue,
            amountPaid = 0.0, dueDate = dueDate, paidDate = null, status = "pending",
        )
        state.value = state.value + installment
        log.i { "Created installment $label for parent=$parentId" }
        return Result.success(installment)
    }

    /** Mark an installment as paid. */
    override suspend fun markPaid(id: String, paymentId: String): Result<Installment> {
        mockDelay()
        val updated = state.value.map { i ->
            if (i.id != id) i else i.copy(amountPaid = i.amountDue, paidDate = Clock.System.now().toString(), status = "paid")
        }
        state.value = updated
        val result = updated.firstOrNull { it.id == id }
            ?: return Result.failure("Tranche $id introuvable.")
        log.i { "Marked installment $id paid (payment=$paymentId)" }
        return Result.success(result)
    }
}

/** Mock [DebtRepository]. */
@Singleton
class MockDebtRepository @Inject constructor() : DebtRepository {

    private val log = Logger.withTag("Mock.Debt")

    /** Stream the debt summary across all parents. */
    override fun debtSummary(): Flow<Result<List<DebtSummary>>> =
        MutableStateFlow(MockData.debtSummaries()).map { Result.success(it) }

    /** Stream the parent's full financial profile. */
    override fun parentFinancialProfile(parentId: String): Flow<Result<ParentFinancialProfile>> =
        MutableStateFlow(MockData.parentFinancialProfile(parentId)).map { Result.success(it) }

    /** Trigger a reminder (mocked). */
    override suspend fun sendReminder(parentId: String): Result<Unit> {
        mockDelay()
        log.i { "Reminder sent to parent $parentId" }
        return Result.success(Unit)
    }
}
