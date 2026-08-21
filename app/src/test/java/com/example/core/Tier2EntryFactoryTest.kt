package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TIER 2 R14 — regression tests for the entry factory field alignment.
 *
 * Verifies that:
 *   - `createRefundEntry` produces `method = null`, `paymentStatus = null`
 *     (matching the desktop's canonical factory). The Android factory
 *     previously wrote `paymentStatus = REFUNDED`, triggering
 *     `PAYMENT_STATUS_MISMATCH` warnings on every Android-originated
 *     refund when the desktop sync pulled it.
 *   - `createAdjustmentEntry` accepts a `sourceType` parameter and stores
 *     it on the entry (matching the desktop's factory, which supports
 *     `manual_entry` / `bulk_import` source types).
 *   - The legacy `sourceType = ADJUSTMENT` default is preserved for
 *     existing callers that don't pass an explicit value (backward compat).
 */
class Tier2EntryFactoryTest {

    private val fixedAt = java.time.Instant.parse("2026-01-15T10:00:00Z")

    // ── createRefundEntry ───────────────────────────────────────────────

    @Test
    fun `refund entry has null method when not provided`() {
        val refund = createRefundEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 5000L,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            reason = "Check bounced",
            at = fixedAt,
        )
        // TIER 2 R14 — refund entries carry `method = null` (the desktop's
        // canonical factory). The payment row is the source of truth for
        // the method; the refund entry should NOT duplicate it.
        assertNull("Refund entry method should be null", refund.method)
    }

    @Test
    fun `refund entry has null paymentStatus`() {
        val refund = createRefundEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 5000L,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            reason = "Check bounced",
            at = fixedAt,
        )
        // TIER 2 R14 — refund entries carry `paymentStatus = null` (the
        // desktop's canonical factory). The Android factory previously
        // wrote `paymentStatus = REFUNDED` — the desktop's `crossCheckPayments`
        // compares `entry.paymentStatus !== p.status` and would flag a
        // PAYMENT_STATUS_MISMATCH warning on every Android-originated refund.
        assertNull("Refund entry paymentStatus should be null", refund.paymentStatus)
    }

    @Test
    fun `refund entry preserves method when caller provides it`() {
        // Backward-compat: callers that DO pass `method` (e.g. when
        // logging the refund alongside the original payment's method)
        // still see the value stored on the entry.
        val refund = createRefundEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 5000L,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            reason = "Check bounced",
            method = PaymentMethod.CHECK,
            receiptNumber = "REC-001",
            at = fixedAt,
        )
        assertEquals(PaymentMethod.CHECK, refund.method)
        assertEquals("REC-001", refund.receiptNumber)
        // Even when method is provided, paymentStatus stays null (canonical).
        assertNull(refund.paymentStatus)
    }

    @Test
    fun `refund entry has REFUND type and sourceType`() {
        val refund = createRefundEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 5000L,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            reason = "Check bounced",
            at = fixedAt,
        )
        assertEquals(LedgerEntryType.REFUND, refund.type)
        assertEquals(LedgerSourceType.REFUND, refund.sourceType)
    }

    @Test
    fun `refund entry amount is negative`() {
        val refund = createRefundEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 5000L,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            reason = "Check bounced",
            at = fixedAt,
        )
        // The factory takes a positive amount and stores it as -amount.
        assertEquals(-5000L, refund.amount)
    }

    // ── createAdjustmentEntry ───────────────────────────────────────────

    @Test
    fun `adjustment entry defaults to ADJUSTMENT sourceType when not provided`() {
        // Backward-compat: existing callers don't pass sourceType.
        val adj = createAdjustmentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = -5000L,
            sourceId = "adj-1", actorId = "u1", actorName = "Alice",
            reason = "Hardship waiver",
            at = fixedAt,
        )
        assertEquals(LedgerSourceType.ADJUSTMENT, adj.sourceType)
    }

    @Test
    fun `adjustment entry accepts caller-supplied sourceType`() {
        // TIER 2 R14 — adjustment entries can be tagged with `manual_entry`
        // or `bulk_import` rather than always saying `adjustment`.
        val adj = createAdjustmentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.PARENT_CREDIT, amount = -5000L,
            sourceId = "adj-1", actorId = "u1", actorName = "Alice",
            reason = "Bulk import credit",
            sourceType = LedgerSourceType.BULK_IMPORT,
            at = fixedAt,
        )
        assertEquals(LedgerSourceType.BULK_IMPORT, adj.sourceType)
    }

    @Test
    fun `adjustment entry accepts MANUAL_ENTRY sourceType`() {
        val adj = createAdjustmentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 2000L,
            sourceId = "adj-2", actorId = "u1", actorName = "Alice",
            reason = "Manual late penalty",
            sourceType = LedgerSourceType.MANUAL_ENTRY,
            at = fixedAt,
        )
        assertEquals(LedgerSourceType.MANUAL_ENTRY, adj.sourceType)
    }

    @Test
    fun `adjustment entry has null method and null paymentStatus`() {
        val adj = createAdjustmentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 2000L,
            sourceId = "adj-1", actorId = "u1", actorName = "Alice",
            reason = "Late penalty",
            at = fixedAt,
        )
        assertNull(adj.method)
        assertNull(adj.paymentStatus)
    }

    @Test
    fun `adjustment entry preserves receiptRef`() {
        // The receiptRef parameter is preserved on the entry's receiptNumber.
        val adj = createAdjustmentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = -5000L,
            sourceId = "adj-1", actorId = "u1", actorName = "Alice",
            reason = "Hardship waiver",
            receiptRef = "REC-2026-001",
            at = fixedAt,
        )
        assertEquals("REC-2026-001", adj.receiptNumber)
    }

    // ── Cross-check: REFUND entries don't trigger PAYMENT_STATUS_MISMATCH ─

    @Test
    fun `refund entry does not produce a paymentStatus that mismatches a payment row`() {
        // Simulate a payment row with status PAID, then a refund.
        val payment = createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 5000L,
            method = PaymentMethod.CHECK, receiptNumber = "REC-1",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "Payment",
            at = fixedAt,
        )
        val refund = createRefundEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 5000L,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            reason = "Refund",
            at = fixedAt,
        )
        // The desktop's `crossCheckPayments` does:
        //   if (entry.paymentStatus !== p.status) → PAYMENT_STATUS_MISMATCH
        // For a refund entry, `paymentStatus = null` ≠ `p.status = PAID`,
        // BUT the desktop's `crossCheckPayments` only matches payment entries
        // (type='payment' AND sourceType='payment') to payment rows — it
        // does NOT match refund entries (type='refund' AND sourceType='refund')
        // to payment rows. So the null paymentStatus on a refund entry is
        // never compared.
        assertEquals(LedgerEntryType.PAYMENT, payment.type)
        assertEquals(LedgerSourceType.PAYMENT, payment.sourceType)
        assertEquals(LedgerEntryType.REFUND, refund.type)
        assertEquals(LedgerSourceType.REFUND, refund.sourceType)
        assertNotNull(payment.paymentStatus)
        assertNull(refund.paymentStatus)
    }
}
