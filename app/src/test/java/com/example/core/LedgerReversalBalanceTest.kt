package com.example.core

import org.junit.Test
import kotlin.test.assertEquals
import java.time.Instant

/**
 * Unit tests for [LedgerEngine.computeAccountBalance] reversal semantics —
 * mirrors the desktop `src/test/unit/ledger.test.ts` reversal section.
 *
 * Verifies:
 *   - Reversed entries contribute zero net balance AND zero typed totals
 *     (original is excluded from totalCharged/totalPaid/totalCleared)
 *   - Reversal of a payment restores the balance as if the payment never
 *     happened (the reversed payment is excluded from totalPaid)
 *
 * Construction of reversal entries via [createReversalEntry] is covered in
 * [LedgerEntryFactoryTest].
 */
class LedgerReversalBalanceTest {

    private val fixedAt: Instant = Instant.parse("2026-07-31T10:00:00Z")

    // ── Reversal semantics ────────────────────────────────────────────────

    @Test fun `reversed entries contribute zero net balance AND zero typed totals`() {
        val accountId = deriveAccountId("p1", PaymentCategory.TUITION, null)
        val original = createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice", description = "T1", at = fixedAt,
        )
        val reversal = createReversalEntry(
            original, reason = "Mistake",
            actorId = "u2", actorName = "Bob", at = fixedAt.plusSeconds(60),
        )
        val balance = LedgerEngine.computeAccountBalance(listOf(original, reversal), accountId)
        // Net balance = 0 (original +50k, reversal -50k)
        assertEquals(0L, balance.balance)
        // totalCharged should also be 0 — the original is excluded because it's reversed.
        assertEquals(0L, balance.totalCharged)
        // entryCount includes both entries (2)
        assertEquals(2, balance.entryCount)
    }

    @Test fun `reversal of a payment restores balance as if payment never happened`() {
        val accountId = deriveAccountId("p1", PaymentCategory.TUITION, null)
        val charge = createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice", description = "T1", at = fixedAt,
        )
        val payment = createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 30000L,
            method = PaymentMethod.CASH, receiptNumber = "RCP-1",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "Counter", at = fixedAt.plusSeconds(60),
        )
        val reversal = createReversalEntry(
            payment, reason = "Bounced check",
            actorId = "u2", actorName = "Bob", at = fixedAt.plusSeconds(120),
        )
        val balance = LedgerEngine.computeAccountBalance(
            listOf(charge, payment, reversal), accountId
        )
        // balance = 50000 - 30000 - (-(-30000)) = 50000 - 30000 + 30000 = 50000
        // Wait: payment.amount = -30000, reversal.amount = -payment.amount = +30000
        // So balance = 50000 + (-30000) + 30000 = 50000
        assertEquals(50000L, balance.balance)
        // totalPaid should be 0 — the payment is excluded because it's reversed.
        assertEquals(0L, balance.totalPaid)
    }
}
