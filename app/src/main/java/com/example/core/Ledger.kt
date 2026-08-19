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
    companion object {
        // CANONICAL-FINANCIAL-LOGIC.md §2.4 — `fromCode` MUST be total (never throw).
        // Future migrations adding a new type must not crash older clients.
        fun fromCode(code: String): LedgerEntryType =
            values().firstOrNull { it.code == code } ?: CHARGE
        fun fromCodeOrNull(code: String?): LedgerEntryType? =
            code?.let { c -> values().firstOrNull { it.code == c } }
    }
}

enum class LedgerSourceType(val code: String) {
    INSTALLMENT("installment"), PAYMENT("payment"), EXPENSE("expense"),
    ADJUSTMENT("adjustment"), REFUND("refund"), BULK_IMPORT("bulk_import"), MANUAL_ENTRY("manual_entry");
    companion object {
        // CANONICAL-FINANCIAL-LOGIC.md §2.5 — `fromCode` MUST be total.
        fun fromCode(code: String): LedgerSourceType =
            values().firstOrNull { it.code == code } ?: ADJUSTMENT
        fun fromCodeOrNull(code: String?): LedgerSourceType? =
            code?.let { c -> values().firstOrNull { it.code == c } }
    }
}

enum class PaymentCategory(val code: String) {
    TUITION("tuition"),
    TRANSPORT("transport"),
    CANTEEN("canteen"),
    UNIFORM("uniform"),
    BOOKS("books"),
    EXTRACURRICULAR("extracurricular"),
    // CANONICAL-FINANCIAL-LOGIC.md §2.1 — unified-architecture additions.
    PARENT_CREDIT("parent_credit"),
    THERAPY_PSYCHOLOGY("therapy_psychology"),
    THERAPY_SPEECH("therapy_speech"),
    SECOND_APRON("second_apron"),
    OTHER("other");
    companion object {
        // CANONICAL-FINANCIAL-LOGIC.md §2.1 — `fromCode` MUST be total.
        // Returns OTHER for unknown codes so that pulling a row whose category
        // was added in a future migration does not crash the client.
        fun fromCode(code: String): PaymentCategory =
            values().firstOrNull { it.code == code } ?: OTHER
        fun fromCodeOrNull(code: String?): PaymentCategory? =
            code?.let { c -> values().firstOrNull { it.code == c } }
    }
}

enum class PaymentMethod(val code: String) {
    CASH("cash"), CHECK("check"), TRANSFER("transfer");
    companion object {
        // CANONICAL-FINANCIAL-LOGIC.md §2.2 — only 3 legal methods.
        // `fromCode` is total: unknown → null (callers fall back to CASH).
        fun fromCode(code: String): PaymentMethod =
            values().firstOrNull { it.code == code } ?: CASH
        fun fromCodeOrNull(code: String?): PaymentMethod? =
            code?.let { c -> values().firstOrNull { it.code == c } }
    }
    val requiresProof: Boolean get() = this != CASH
}

enum class PaymentStatus(val code: String) {
    PAID("paid"),
    PENDING("pending"),
    PARTIAL("partial"),
    OVERDUE("overdue"),
    REFUNDED("refunded"),
    CANCELLED("cancelled"),
    // CANONICAL-FINANCIAL-LOGIC.md §2.3 — unified-architecture additions.
    PENDING_CLEARANCE("pending_clearance"),
    UNPAID("unpaid");
    companion object {
        // CANONICAL-FINANCIAL-LOGIC.md §2.3 — `fromCode` MUST be total.
        // Returns null for unknown codes so callers that need to distinguish
        // "known but unusual" from "future-migration code" can do so.
        // For domain fields that require a non-null PaymentStatus (e.g.
        // Payment.status, Installment.status), use `fromCodeOrDefault`.
        fun fromCode(code: String): PaymentStatus? =
            values().firstOrNull { it.code == code }

        // Non-null variant: returns `default` (defaults to PENDING — the
        // most conservative state) when the code is unknown. Use this for
        // domain fields that are typed non-null PaymentStatus.
        fun fromCodeOrDefault(code: String, default: PaymentStatus = PENDING): PaymentStatus =
            fromCode(code) ?: default
    }
}

/**
 * Payment plan — whether a student is billed as 1 charge (full_annual)
 * or 3 tranches (Sept 15 / Dec 15 / Mar 15).
 *
 * CANONICAL-FINANCIAL-LOGIC.md §2.6 + §6.1.
 */
enum class PaymentPlan(val code: String) {
    FULL_ANNUAL("full_annual"),
    TRANCHES("tranches");
    companion object {
        fun fromCode(code: String?): PaymentPlan =
            values().firstOrNull { it.code == code } ?: TRANCHES
    }
}

fun Long.formatDzd(): String = java.text.NumberFormat.getNumberInstance(java.util.Locale.FRANCE).format(this)
fun Int.formatDzd(): String = java.text.NumberFormat.getNumberInstance(java.util.Locale.FRANCE).format(this)
fun Double.formatDzd(): String = java.text.NumberFormat.getNumberInstance(java.util.Locale.FRANCE).format(this)
