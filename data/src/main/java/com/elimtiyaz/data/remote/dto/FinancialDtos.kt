package com.elimtiyaz.data.remote.dto

import com.elimtiyaz.domain.model.AccountAdjustment
import com.elimtiyaz.domain.model.Installment
import com.elimtiyaz.domain.model.Payment
import com.elimtiyaz.domain.model.PaymentCategory
import com.elimtiyaz.domain.model.Receipt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire DTO for the `payments` table. */
@Serializable
data class PaymentDto(
    val id: String,
    @SerialName("tenant_id") val tenantId: String,
    @SerialName("receipt_number") val receiptNumber: String,
    @SerialName("parent_id") val parentId: String,
    @SerialName("student_id") val studentId: String? = null,
    val amount: Double,
    val method: String,
    val status: String,
    val category: PaymentCategory,
    @SerialName("installment_id") val installmentId: String? = null,
    @SerialName("proof_url") val proofUrl: String? = null,
    val notes: String? = null,
    @SerialName("collected_by") val collectedBy: String,
    @SerialName("collected_at") val collectedAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
) {
    /** Convert to a domain [Payment]. */
    fun toDomain(): Payment = Payment(
        id = id, tenantId = tenantId, receiptNumber = receiptNumber, parentId = parentId,
        studentId = studentId, amount = amount, method = method, status = status, category = category,
        installmentId = installmentId, proofUrl = proofUrl, notes = notes, collectedBy = collectedBy,
        collectedAt = collectedAt, createdAt = createdAt, updatedAt = updatedAt,
    )

    companion object {
        /** Build a DTO from a domain [Payment]. */
        fun fromDomain(p: Payment): PaymentDto = PaymentDto(
            id = p.id, tenantId = p.tenantId, receiptNumber = p.receiptNumber, parentId = p.parentId,
            studentId = p.studentId, amount = p.amount, method = p.method, status = p.status,
            category = p.category, installmentId = p.installmentId, proofUrl = p.proofUrl, notes = p.notes,
            collectedBy = p.collectedBy, collectedAt = p.collectedAt, createdAt = p.createdAt, updatedAt = p.updatedAt,
        )
    }
}

/** Wire DTO for the `installments` table. */
@Serializable
data class InstallmentDto(
    val id: String,
    @SerialName("parent_id") val parentId: String,
    @SerialName("student_id") val studentId: String,
    val category: PaymentCategory,
    val label: String,
    @SerialName("amount_due") val amountDue: Double,
    @SerialName("amount_paid") val amountPaid: Double = 0.0,
    @SerialName("due_date") val dueDate: String,
    @SerialName("paid_date") val paidDate: String? = null,
    val status: String,
) {
    /** Convert to a domain [Installment]. */
    fun toDomain(): Installment = Installment(
        id = id, parentId = parentId, studentId = studentId, category = category, label = label,
        amountDue = amountDue, amountPaid = amountPaid, dueDate = dueDate, paidDate = paidDate, status = status,
    )

    companion object {
        /** Build a DTO from a domain [Installment]. */
        fun fromDomain(i: Installment): InstallmentDto = InstallmentDto(
            id = i.id, parentId = i.parentId, studentId = i.studentId, category = i.category, label = i.label,
            amountDue = i.amountDue, amountPaid = i.amountPaid, dueDate = i.dueDate, paidDate = i.paidDate, status = i.status,
        )
    }
}

/** Wire DTO for the `account_adjustments` table. */
@Serializable
data class AccountAdjustmentDto(
    val id: String,
    @SerialName("parent_id") val parentId: String,
    val amount: Double,
    val reason: String,
    @SerialName("approved_by") val approvedBy: String,
    @SerialName("approved_at") val approvedAt: String,
    @SerialName("receipt_ref") val receiptRef: String? = null,
) {
    /** Convert to a domain [AccountAdjustment]. */
    fun toDomain(): AccountAdjustment = AccountAdjustment(
        id = id, parentId = parentId, amount = amount, reason = reason, approvedBy = approvedBy,
        approvedAt = approvedAt, receiptRef = receiptRef,
    )
}

/** Wire DTO for the `receipts` table — generated PDF pointers. */
@Serializable
data class ReceiptDto(
    val id: String,
    @SerialName("payment_id") val paymentId: String,
    @SerialName("receipt_number") val receiptNumber: String,
    @SerialName("pdf_url") val pdfUrl: String,
    @SerialName("generated_at") val generatedAt: String,
    @SerialName("generated_by") val generatedBy: String,
) {
    /** Convert to a domain [Receipt]. */
    fun toDomain(): Receipt = Receipt(
        id = id, paymentId = paymentId, receiptNumber = receiptNumber, pdfUrl = pdfUrl,
        generatedAt = generatedAt, generatedBy = generatedBy,
    )
}
