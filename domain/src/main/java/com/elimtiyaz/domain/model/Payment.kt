package com.elimtiyaz.domain.model

import kotlinx.serialization.Serializable

/**
 * Financial engine per master plan §07.
 * Payment methods: Cash / Check / Transfer (§07.01).
 * Lifecycle: Pending → Partial → Paid (or Overdue, Refunded, Cancelled).
 */

@Serializable
data class Payment(
    val id: String,
    val tenantId: String,
    val receiptNumber: String,         // REC-2025-000123
    val parentId: String,
    val studentId: String? = null,
    val amount: Double,
    val method: String,                // cash / check / transfer
    val status: String,                // pending / partial / paid / overdue / refunded / cancelled
    val category: PaymentCategory,
    val installmentId: String? = null,
    val proofUrl: String? = null,      // signed-URL for check/transfer proof
    val notes: String? = null,
    val collectedBy: String,
    val collectedAt: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
enum class PaymentCategory { Tuition, Transport, Canteen, Uniform, Books, Extracurricular, Other }

@Serializable
data class CreatePaymentInput(
    val parentId: String,
    val studentId: String? = null,
    val amount: Double,
    val method: String,
    val category: PaymentCategory,
    val installmentId: String? = null,
    val proofUrl: String? = null,
    val notes: String? = null,
)

/** Installments / tranches — §07.03. Tuition always 3 tranches; transport tier-based. */
@Serializable
data class Installment(
    val id: String,
    val parentId: String,
    val studentId: String,
    val category: PaymentCategory,
    val label: String,                 // Tranche 1 / Tranche 2 / Tranche 3
    val amountDue: Double,
    val amountPaid: Double = 0.0,
    val dueDate: String,
    val paidDate: String? = null,
    val status: String,                // pending / partial / paid / overdue
)

/** Discretionary account adjustment — §07.04 (replaces deprecated scholarships). */
@Serializable
data class AccountAdjustment(
    val id: String,
    val parentId: String,
    val amount: Double,                // + credit, - debit
    val reason: String,
    val approvedBy: String,
    val approvedAt: String,
    val receiptRef: String? = null,
)

@Serializable
data class ParentFinancialProfile(
    val parentId: String,
    val parentName: String,
    val totalDue: Double,
    val totalPaid: Double,
    val totalOutstanding: Double,
    val overdueAmount: Double,
    val installments: List<Installment> = emptyList(),
    val recentPayments: List<Payment> = emptyList(),
    val adjustments: List<AccountAdjustment> = emptyList(),
)

/** Debt dashboard aging buckets — §07.06. */
@Serializable
data class DebtSummary(
    val parentId: String,
    val parentName: String,
    val parentPhone: String,
    val studentCount: Int,
    val outstandingAmount: Double,
    val daysOverdue: Int,
) {
    val agingBucket: AgingBucket get() = when (daysOverdue) {
        in 0..30      -> AgingBucket.Bucket0_30
        in 31..60     -> AgingBucket.Bucket31_60
        in 61..90     -> AgingBucket.Bucket61_90
        in 91..180    -> AgingBucket.Bucket91_180
        else          -> AgingBucket.Bucket180Plus
    }
}

enum class AgingBucket(val displayFr: String, val displayAr: String) {
    Bucket0_30("0–30 j",    "0–30 ي"),
    Bucket31_60("31–60 j",  "31–60 ي"),
    Bucket61_90("61–90 j",  "61–90 ي"),
    Bucket91_180("91–180 j","91–180 ي"),
    Bucket180Plus("180+ j", "180+ ي"),
}

@Serializable
data class Receipt(
    val id: String,
    val paymentId: String,
    val receiptNumber: String,
    val pdfUrl: String,
    val generatedAt: String,
    val generatedBy: String,
)
