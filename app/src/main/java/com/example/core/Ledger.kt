package com.example.core

/**
 * Ledger entry — the immutable atomic unit of the accounting engine.
 * Mirrors the desktop `LedgerEntry`. All fields `val` (immutable).
 *
 * Signed-amount convention:
 *   - CHARGE     → amount > 0 (parent owes more)
 *   - PAYMENT    → amount < 0 (parent owes less; payment received)
 *   - ADJUSTMENT → amount != 0 (signed; +debit / -credit)
 *   - REFUND     → amount < 0 (money returned to parent)
 *   - REVERSAL   → amount = -original.amount
 *   - TRANSFER   → signed; net zero on balance
 *
 * Amount is Long (centimes) — NEVER Double, to preserve determinism.
 */
data class LedgerEntry(
    val id: String,
    val tenantId: String,
    val accountId: String,
    val parentId: String,
    val studentId: String?,
    val category: PaymentCategory,
    val amount: Long,
    val type: LedgerEntryType,
    val sourceType: LedgerSourceType,
    val sourceId: String,
    val method: PaymentMethod?,
    val receiptNumber: String?,
    val paymentStatus: PaymentStatus?,
    val reversesId: String?,
    val description: String,
    val actorId: String,
    val actorName: String,
    val at: String,
    val metadata: Map<String, Any?>,
)

enum class LedgerEntryType(val code: String) {
    CHARGE("charge"), PAYMENT("payment"), ADJUSTMENT("adjustment"),
    REFUND("refund"), REVERSAL("reversal"), TRANSFER("transfer");
    companion object { fun fromCode(code: String) = values().firstOrNull { it.code == code } ?: throw IllegalArgumentException("Unknown LedgerEntryType: $code") }
}

enum class LedgerSourceType(val code: String) {
    INSTALLMENT("installment"), PAYMENT("payment"), EXPENSE("expense"),
    ADJUSTMENT("adjustment"), REFUND("refund"), BULK_IMPORT("bulk_import"), MANUAL_ENTRY("manual_entry");
    companion object { fun fromCode(code: String) = values().firstOrNull { it.code == code } ?: throw IllegalArgumentException("Unknown LedgerSourceType: $code") }
}

enum class PaymentCategory(val code: String) {
    TUITION("tuition"), TRANSPORT("transport"), CANTEEN("canteen"),
    UNIFORM("uniform"), BOOKS("books"), EXTRACURRICULAR("extracurricular"), OTHER("other");
    companion object { fun fromCode(code: String) = values().firstOrNull { it.code == code } ?: throw IllegalArgumentException("Unknown PaymentCategory: $code") }
}

enum class PaymentMethod(val code: String) {
    CASH("cash"), CHECK("check"), TRANSFER("transfer");
    companion object { fun fromCode(code: String) = values().firstOrNull { it.code == code } ?: throw IllegalArgumentException("Unknown PaymentMethod: $code") }
    val requiresProof: Boolean get() = this != CASH
}

enum class PaymentStatus(val code: String) {
    PAID("paid"), PENDING("pending"), PARTIAL("partial"),
    OVERDUE("overdue"), REFUNDED("refunded"), CANCELLED("cancelled");
    companion object { fun fromCode(code: String) = values().firstOrNull { it.code == code } ?: throw IllegalArgumentException("Unknown PaymentStatus: $code") }
}
