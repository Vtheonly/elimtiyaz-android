package com.example.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.time.Instant

/**
 * Unit tests for [LedgerEngine.computeAccountBalance] — mirrors the desktop
 * `src/test/unit/ledger.test.ts` balance section.
 *
 * Verifies:
 *   - Single charge, charge+payment, overpayment, full payment
 *   - Pending payments counted separately from cleared
 *   - As-of queries exclude future entries
 *   - Replay determinism (same entries → same balance)
 *
 * Reversal-balance semantics live in [LedgerReversalBalanceTest].
 */
class LedgerBalanceTest {

    private val fixedAt: Instant = Instant.parse("2026-07-31T10:00:00Z")

    // ── Balance computation ───────────────────────────────────────────────

    @Test fun `empty ledger has zero balance`() {
        val balance = LedgerEngine.computeAccountBalance(emptyList(), "parent:p1:category:tuition")
        assertEquals(0L, balance.balance)
        assertEquals(0, balance.entryCount)
        assertNull(balance.lastActivityAt)
    }

    @Test fun `single charge produces positive balance and totalCharged`() {
        val entry = createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice",
            description = "T1", at = fixedAt,
        )
        val accountId = deriveAccountId("p1", PaymentCategory.TUITION, null)
        val balance = LedgerEngine.computeAccountBalance(listOf(entry), accountId)
        assertEquals(50000L, balance.balance)
        assertEquals(50000L, balance.totalCharged)
        assertEquals(0L, balance.totalPaid)
        assertEquals(1, balance.entryCount)
    }

    @Test fun `charge plus payment reduces balance`() {
        val accountId = deriveAccountId("p1", PaymentCategory.TUITION, null)
        val charge = createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice", description = "T1",
            at = fixedAt,
        )
        val payment = createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 30000L,
            method = PaymentMethod.CASH, receiptNumber = "RCP-1",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "Counter payment",
            at = fixedAt.plusSeconds(60),
        )
        val balance = LedgerEngine.computeAccountBalance(listOf(charge, payment), accountId)
        assertEquals(20000L, balance.balance)  // 50000 - 30000
        assertEquals(50000L, balance.totalCharged)
        assertEquals(30000L, balance.totalPaid)
        assertEquals(30000L, balance.totalCleared)
    }

    @Test fun `fully paid account has zero balance`() {
        val accountId = deriveAccountId("p1", PaymentCategory.TUITION, null)
        val charge = createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice", description = "T1", at = fixedAt,
        )
        val payment = createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            method = PaymentMethod.CASH, receiptNumber = "RCP-1",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "Full payment", at = fixedAt.plusSeconds(60),
        )
        val balance = LedgerEngine.computeAccountBalance(listOf(charge, payment), accountId)
        assertEquals(0L, balance.balance)
    }

    @Test fun `overpayment produces negative balance (school owes parent)`() {
        val accountId = deriveAccountId("p1", PaymentCategory.TUITION, null)
        val charge = createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice", description = "T1", at = fixedAt,
        )
        val payment = createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 60000L,
            method = PaymentMethod.CASH, receiptNumber = "RCP-1",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "Overpayment", at = fixedAt.plusSeconds(60),
        )
        val balance = LedgerEngine.computeAccountBalance(listOf(charge, payment), accountId)
        assertEquals(-10000L, balance.balance)  // 50000 - 60000
    }

    @Test fun `pending payments counted in totalPending not totalCleared`() {
        val accountId = deriveAccountId("p1", PaymentCategory.TUITION, null)
        val charge = createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice", description = "T1", at = fixedAt,
        )
        val paidPayment = createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 20000L,
            method = PaymentMethod.CASH, receiptNumber = "RCP-1",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "Cash", at = fixedAt.plusSeconds(60),
        )
        val pendingPayment = createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 30000L,
            method = PaymentMethod.CHECK, receiptNumber = "RCP-2",
            paymentStatus = PaymentStatus.PENDING,
            sourceId = "pay-2", actorId = "u1", actorName = "Alice",
            description = "Check pending clearance", at = fixedAt.plusSeconds(120),
        )
        val balance = LedgerEngine.computeAccountBalance(
            listOf(charge, paidPayment, pendingPayment), accountId
        )
        // totalPaid = |paid.amount| + |pending.amount| = 20000 + 30000 = 50000
        assertEquals(50000L, balance.totalPaid)
        // totalCleared = |paid.amount| = 20000 (only "paid" status)
        assertEquals(20000L, balance.totalCleared)
        // totalPending = |pending.amount| = 30000 (only "pending" status)
        assertEquals(30000L, balance.totalPending)
        // balance = 50000 - 20000 - 30000 = 0 (both reduce balance)
        assertEquals(0L, balance.balance)
    }

    // ── As-of queries ─────────────────────────────────────────────────────

    @Test fun `as-of query excludes entries with at greater than now`() {
        val accountId = deriveAccountId("p1", PaymentCategory.TUITION, null)
        val pastCharge = createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice", description = "T1",
            at = fixedAt.minusSeconds(86400),  // yesterday
        )
        val futureCharge = createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 30000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-2",
            actorId = "u1", actorName = "Alice", description = "T2",
            at = fixedAt.plusSeconds(86400),  // tomorrow
        )
        // As-of today (fixedAt), only the past charge counts.
        val balance = LedgerEngine.computeAccountBalance(
            listOf(pastCharge, futureCharge), accountId, now = fixedAt
        )
        assertEquals(50000L, balance.balance)
        assertEquals(1, balance.entryCount)
    }

    // ── Determinism (replay = same result) ────────────────────────────────

    @Test fun `replaying ledger twice produces identical balances`() {
        val accountId = deriveAccountId("p1", PaymentCategory.TUITION, null)
        val entries = listOf(
            createChargeEntry("t1", "p1", null, PaymentCategory.TUITION, 50000L,
                LedgerSourceType.INSTALLMENT, "inst-1", "u1", "Alice", "T1", at = fixedAt),
            createPaymentEntry("t1", "p1", null, PaymentCategory.TUITION, 20000L,
                PaymentMethod.CASH, "RCP-1", PaymentStatus.PAID, "pay-1", "u1", "Alice",
                "P1", at = fixedAt.plusSeconds(60)),
            createAdjustmentEntry("t1", "p1", null, PaymentCategory.TUITION, -5000L,
                "adj-1", "u1", "Alice", "Discount", at = fixedAt.plusSeconds(120)),
        )
        val balance1 = LedgerEngine.computeAccountBalance(entries, accountId)
        val balance2 = LedgerEngine.computeAccountBalance(entries, accountId)
        assertEquals(balance1, balance2)
        // balance = 50000 - 20000 - 5000 = 25000
        assertEquals(25000L, balance1.balance)
    }
}
