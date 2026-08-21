package com.example.core

import java.time.Instant
import java.util.UUID

/**
 * Pure ledger entry factory functions.
 *
 * These are top-level (free) functions — they hold no shared state and rely
 * only on their arguments + the deterministic ID helpers below. Moving them
 * out of [LedgerEngine] keeps the engine focused on balance/summary
 * computation while leaving the entry-construction contracts callable from
 * anywhere in the same package without an object qualifier.
 *
 * Wire format mirrors the desktop `src/domain/ledger.ts` factories — every
 * field name, sign convention, and validation message is identical for
 * cross-platform audit-log compatibility.
 */

/** Derive an account ID. Pure, deterministic. No `accounts` table exists. */
fun deriveAccountId(parentId: String, category: PaymentCategory, studentId: String? = null): String {
    val parts = mutableListOf("parent", parentId, "category", category.code)
    if (studentId != null) { parts.add("student"); parts.add(studentId) }
    return parts.joinToString(":")
}

fun generateEntryId(at: Instant = Instant.now()): String {
    val datePart = at.toString().substring(0, 10).replace("-", "")
    val randomPart = UUID.randomUUID().toString().replace("-", "").take(8)
    return "led-$datePart-$randomPart"
}

fun createChargeEntry(
    tenantId: String, parentId: String, studentId: String?,
    category: PaymentCategory, amount: Long,
    sourceType: LedgerSourceType, sourceId: String,
    actorId: String, actorName: String, description: String,
    receiptNumber: String? = null, paymentStatus: PaymentStatus? = null,
    at: Instant = Instant.now(), metadata: Map<String, Any?> = emptyMap(),
): LedgerEntry {
    require(amount > 0) { "Charge amount must be > 0 (got $amount)" }
    require(description.isNotBlank()) { "Charge description must be non-blank" }
    require(actorId.isNotBlank()) { "Charge actorId must be non-blank" }
    require(actorName.isNotBlank()) { "Charge actorName must be non-blank" }
    return LedgerEntry(
        id = generateEntryId(at), tenantId = tenantId,
        accountId = deriveAccountId(parentId, category, studentId),
        parentId = parentId, studentId = studentId, category = category,
        amount = amount, type = LedgerEntryType.CHARGE,
        sourceType = sourceType, sourceId = sourceId,
        method = null, receiptNumber = receiptNumber, paymentStatus = paymentStatus,
        reversesId = null, description = description,
        actorId = actorId, actorName = actorName, at = at.toString(),
        metadata = metadata.toMap(),
    )
}

fun createPaymentEntry(
    tenantId: String, parentId: String, studentId: String?,
    category: PaymentCategory, amount: Long,
    method: PaymentMethod, receiptNumber: String, paymentStatus: PaymentStatus,
    sourceId: String, actorId: String, actorName: String, description: String,
    at: Instant = Instant.now(), metadata: Map<String, Any?> = emptyMap(),
): LedgerEntry {
    require(amount > 0) { "Payment amount must be > 0 (got $amount)" }
    require(description.isNotBlank()) { "Payment description must be non-blank" }
    return LedgerEntry(
        id = generateEntryId(at), tenantId = tenantId,
        accountId = deriveAccountId(parentId, category, studentId),
        parentId = parentId, studentId = studentId, category = category,
        amount = -amount, type = LedgerEntryType.PAYMENT,
        sourceType = LedgerSourceType.PAYMENT, sourceId = sourceId,
        method = method, receiptNumber = receiptNumber, paymentStatus = paymentStatus,
        reversesId = null, description = description,
        actorId = actorId, actorName = actorName, at = at.toString(),
        metadata = metadata.toMap(),
    )
}

fun createAdjustmentEntry(
    tenantId: String, parentId: String, studentId: String?,
    category: PaymentCategory, amount: Long,
    sourceId: String, actorId: String, actorName: String, reason: String,
    receiptRef: String? = null,
    /**
     * TIER 2 R14 — caller-supplied source type.
     *
     * Mirrors the desktop's `createAdjustmentEntry` signature, which accepts
     * a `sourceType` parameter so adjustment entries can be tagged with
     * `manual_entry` or `bulk_import` rather than always saying `adjustment`.
     *
     * Default `LedgerSourceType.ADJUSTMENT` preserves the legacy behavior
     * for existing callers that don't pass an explicit value.
     */
    sourceType: LedgerSourceType = LedgerSourceType.ADJUSTMENT,
    at: Instant = Instant.now(), metadata: Map<String, Any?> = emptyMap(),
): LedgerEntry {
    require(amount != 0L) { "Adjustment amount must be != 0 (got $amount)" }
    require(reason.isNotBlank()) { "Adjustment reason must be non-blank" }
    return LedgerEntry(
        id = generateEntryId(at), tenantId = tenantId,
        accountId = deriveAccountId(parentId, category, studentId),
        parentId = parentId, studentId = studentId, category = category,
        amount = amount, type = LedgerEntryType.ADJUSTMENT,
        sourceType = sourceType, sourceId = sourceId,
        method = null, receiptNumber = receiptRef, paymentStatus = null,
        reversesId = null, description = reason,
        actorId = actorId, actorName = actorName, at = at.toString(),
        metadata = metadata.toMap(),
    )
}

fun createRefundEntry(
    tenantId: String, parentId: String, studentId: String?,
    category: PaymentCategory, amount: Long,
    sourceId: String, actorId: String, actorName: String, reason: String,
    /**
     * TIER 2 R14 — method + receiptNumber params are now OPTIONAL.
     *
     * The desktop canonical factory sets `method = null` and
     * `paymentStatus = null` on refund entries. The Android factory
     * previously required a `method` parameter and wrote
     * `paymentStatus = REFUNDED`. The desktop's `crossCheckPayments`
     * compares `entry.paymentStatus !== p.status` — for a refund, the
     * desktop stores `paymentStatus = null` but Android stored
     * `REFUNDED`, triggering a `PAYMENT_STATUS_MISMATCH` warning on
     * every Android-originated refund when the desktop sync pulled it.
     *
     * The signature is backward-compatible: existing callers that pass
     * `method = X` and `receiptNumber = Y` still work — but `method` is
     * now stored on the **payment row** (the source of truth for the
     * payment's method), NOT on the refund ledger entry, and the refund
     * entry's `paymentStatus` is null (matching the desktop).
     */
    method: PaymentMethod? = null,
    receiptNumber: String? = null,
    at: Instant = Instant.now(), metadata: Map<String, Any?> = emptyMap(),
): LedgerEntry {
    require(amount > 0) { "Refund amount must be > 0 (got $amount)" }
    require(reason.isNotBlank()) { "Refund reason must be non-blank" }
    return LedgerEntry(
        id = generateEntryId(at), tenantId = tenantId,
        accountId = deriveAccountId(parentId, category, studentId),
        parentId = parentId, studentId = studentId, category = category,
        amount = -amount, type = LedgerEntryType.REFUND,
        sourceType = LedgerSourceType.REFUND, sourceId = sourceId,
        method = method, receiptNumber = receiptNumber,
        // TIER 2 R14 — null paymentStatus matches the desktop's canonical
        // `createRefundEntry` factory. The desktop's `crossCheckPayments`
        // would otherwise flag `PAYMENT_STATUS_MISMATCH` on every refund.
        paymentStatus = null,
        reversesId = null, description = reason,
        actorId = actorId, actorName = actorName, at = at.toString(),
        metadata = metadata.toMap(),
    )
}

/** Create a reversal entry that negates a prior entry. */
fun createReversalEntry(
    original: LedgerEntry, reason: String,
    actorId: String, actorName: String,
    at: Instant = Instant.now(),
): LedgerEntry {
    require(reason.isNotBlank()) { "Reversal reason must be non-blank" }
    require(actorId.isNotBlank()) { "Reversal actorId must be non-blank" }
    return LedgerEntry(
        id = generateEntryId(at), tenantId = original.tenantId,
        accountId = original.accountId, parentId = original.parentId,
        studentId = original.studentId, category = original.category,
        amount = -original.amount, type = LedgerEntryType.REVERSAL,
        sourceType = original.sourceType, sourceId = original.sourceId,
        method = original.method, receiptNumber = original.receiptNumber,
        paymentStatus = original.paymentStatus,
        reversesId = original.id,
        description = "REVERSAL of ${original.id}: $reason",
        actorId = actorId, actorName = actorName, at = at.toString(),
        metadata = mapOf("reversedEntryId" to original.id, "reason" to reason),
    )
}
