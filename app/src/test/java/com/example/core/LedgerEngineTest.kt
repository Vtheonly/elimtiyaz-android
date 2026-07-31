package com.example.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import java.time.Instant

/**
 * Unit tests for the ledger engine — mirrors the desktop
 * `src/test/unit/ledger.test.ts` (976 lines).
 *
 * These tests verify:
 *   - Factory function invariants (throw on invalid input)
 *   - Account ID derivation (deterministic, varies by category/student)
 *   - Balance computation (single charge, charge+payment, overpayment, reversal exclusion)
 *   - Reversal mechanics (negates original, linked via reversesId)
 *   - Property-based determinism (replay = same result)
 *
 * Run with: `./gradlew :app:testDebugUnitTest --tests "com.elimtiyaz.core.LedgerEngineTest"`
 */
class LedgerEngineTest {

    private val fixedAt: Instant = Instant.parse("2026-07-31T10:00:00Z")

    // ── Account ID derivation ─────────────────────────────────────────────

    @Test fun `deriveAccountId is deterministic for same inputs`() {
        val id1 = LedgerEngine.deriveAccountId("par-001", PaymentCategory.TUITION, null)
        val id2 = LedgerEngine.deriveAccountId("par-001", PaymentCategory.TUITION, null)
        assertEquals(id1, id2)
        assertEquals("parent:par-001:category:tuition", id1)
    }

    @Test fun `deriveAccountId changes with category`() {
        val tuition = LedgerEngine.deriveAccountId("par-001", PaymentCategory.TUITION, null)
        val transport = LedgerEngine.deriveAccountId("par-001", PaymentCategory.TRANSPORT, null)
        assertTrue(tuition != transport)
    }

    @Test fun `deriveAccountId changes with student`() {
        val parentLevel = LedgerEngine.deriveAccountId("par-001", PaymentCategory.TUITION, null)
        val studentScoped = LedgerEngine.deriveAccountId("par-001", PaymentCategory.TUITION, "stu-001")
        assertTrue(parentLevel != studentScoped)
        assertEquals("parent:par-001:category:tuition:student:stu-001", studentScoped)
    }

    // ── Factory invariants ────────────────────────────────────────────────

    @Test fun `createChargeEntry throws on non-positive amount`() {
        assertFailsWith<IllegalArgumentException> {
            LedgerEngine.createChargeEntry(
                tenantId = "t1", parentId = "p1", studentId = null,
                category = PaymentCategory.TUITION, amount = 0L,
                sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
                actorId = "u1", actorName = "Alice",
                description = "Tuition tranche 1",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LedgerEngine.createChargeEntry(
                tenantId = "t1", parentId = "p1", studentId = null,
                category = PaymentCategory.TUITION, amount = -1000L,
                sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
                actorId = "u1", actorName = "Alice",
                description = "Tuition tranche 1",
            )
        }
    }

    @Test fun `createChargeEntry throws on blank description`() {
        assertFailsWith<IllegalArgumentException> {
            LedgerEngine.createChargeEntry(
                tenantId = "t1", parentId = "p1", studentId = null,
                category = PaymentCategory.TUITION, amount = 50000L,
                sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
                actorId = "u1", actorName = "Alice",
                description = "",
            )
        }
    }

    @Test fun `createPaymentEntry stores negation as amount`() {
        val entry = LedgerEngine.createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 5000_00L,  // 5000 DZD in centimes
            method = PaymentMethod.CASH, receiptNumber = "RCP-2026-00001",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "Counter payment",
            at = fixedAt,
        )
        assertEquals(-5000_00L, entry.amount)  // NEGATIVE — credit
        assertEquals(LedgerEntryType.PAYMENT, entry.type)
        assertEquals(PaymentMethod.CASH, entry.method)
    }

    @Test fun `createPaymentEntry throws on non-positive amount param`() {
        assertFailsWith<IllegalArgumentException> {
            LedgerEngine.createPaymentEntry(
                tenantId = "t1", parentId = "p1", studentId = null,
                category = PaymentCategory.TUITION, amount = 0L,
                method = PaymentMethod.CASH, receiptNumber = "RCP-1",
                paymentStatus = PaymentStatus.PAID,
                sourceId = "pay-1", actorId = "u1", actorName = "Alice",
                description = "Test",
            )
        }
    }

    @Test fun `createAdjustmentEntry throws on zero amount`() {
        assertFailsWith<IllegalArgumentException> {
            LedgerEngine.createAdjustmentEntry(
                tenantId = "t1", parentId = "p1", studentId = null,
                category = PaymentCategory.TUITION, amount = 0L,
                sourceId = "adj-1", actorId = "u1", actorName = "Alice",
                reason = "Test",
            )
        }
    }

    @Test fun `createAdjustmentEntry accepts both positive and negative amounts`() {
        val debit = LedgerEngine.createAdjustmentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = +500_00L,
            sourceId = "adj-1", actorId = "u1", actorName = "Alice",
            reason = "Penalty", at = fixedAt,
        )
        val credit = LedgerEngine.createAdjustmentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = -500_00L,
            sourceId = "adj-2", actorId = "u1", actorName = "Alice",
            reason = "Discount", at = fixedAt,
        )
        assertEquals(+500_00L, debit.amount)
        assertEquals(-500_00L, credit.amount)
    }

    @Test fun `createReversalEntry negates original amount and links via reversesId`() {
        val original = LedgerEngine.createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice",
            description = "Tuition tranche 1", at = fixedAt,
        )
        val reversal = LedgerEngine.createReversalEntry(
            original = original,
            reason = "Mistaken charge",
            actorId = "u2", actorName = "Bob",
            at = fixedAt.plusSeconds(60),
        )
        assertEquals(-50000L, reversal.amount)
        assertEquals(LedgerEntryType.REVERSAL, reversal.type)
        assertEquals(original.id, reversal.reversesId)
        assertEquals(original.accountId, reversal.accountId)
        assertTrue(reversal.description.startsWith("REVERSAL of ${original.id}"))
        assertEquals(original.id, reversal.metadata["reversedEntryId"])
    }

    @Test fun `createReversalEntry throws on blank reason`() {
        val original = LedgerEngine.createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice",
            description = "Tuition tranche 1", at = fixedAt,
        )
        assertFailsWith<IllegalArgumentException> {
            LedgerEngine.createReversalEntry(original, reason = "", actorId = "u2", actorName = "Bob")
        }
    }

    // ── Balance computation ───────────────────────────────────────────────

    @Test fun `empty ledger has zero balance`() {
        val balance = LedgerEngine.computeAccountBalance(emptyList(), "parent:p1:category:tuition")
        assertEquals(0L, balance.balance)
        assertEquals(0, balance.entryCount)
        assertNull(balance.lastActivityAt)
    }

    @Test fun `single charge produces positive balance and totalCharged`() {
        val entry = LedgerEngine.createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice",
            description = "T1", at = fixedAt,
        )
        val accountId = LedgerEngine.deriveAccountId("p1", PaymentCategory.TUITION, null)
        val balance = LedgerEngine.computeAccountBalance(listOf(entry), accountId)
        assertEquals(50000L, balance.balance)
        assertEquals(50000L, balance.totalCharged)
        assertEquals(0L, balance.totalPaid)
        assertEquals(1, balance.entryCount)
    }

    @Test fun `charge plus payment reduces balance`() {
        val accountId = LedgerEngine.deriveAccountId("p1", PaymentCategory.TUITION, null)
        val charge = LedgerEngine.createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice", description = "T1",
            at = fixedAt,
        )
        val payment = LedgerEngine.createPaymentEntry(
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
        val accountId = LedgerEngine.deriveAccountId("p1", PaymentCategory.TUITION, null)
        val charge = LedgerEngine.createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice", description = "T1", at = fixedAt,
        )
        val payment = LedgerEngine.createPaymentEntry(
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
        val accountId = LedgerEngine.deriveAccountId("p1", PaymentCategory.TUITION, null)
        val charge = LedgerEngine.createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice", description = "T1", at = fixedAt,
        )
        val payment = LedgerEngine.createPaymentEntry(
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
        val accountId = LedgerEngine.deriveAccountId("p1", PaymentCategory.TUITION, null)
        val charge = LedgerEngine.createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice", description = "T1", at = fixedAt,
        )
        val paidPayment = LedgerEngine.createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 20000L,
            method = PaymentMethod.CASH, receiptNumber = "RCP-1",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "Cash", at = fixedAt.plusSeconds(60),
        )
        val pendingPayment = LedgerEngine.createPaymentEntry(
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

    // ── Reversal semantics ────────────────────────────────────────────────

    @Test fun `reversed entries contribute zero net balance AND zero typed totals`() {
        val accountId = LedgerEngine.deriveAccountId("p1", PaymentCategory.TUITION, null)
        val original = LedgerEngine.createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice", description = "T1", at = fixedAt,
        )
        val reversal = LedgerEngine.createReversalEntry(
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
        val accountId = LedgerEngine.deriveAccountId("p1", PaymentCategory.TUITION, null)
        val charge = LedgerEngine.createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice", description = "T1", at = fixedAt,
        )
        val payment = LedgerEngine.createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 30000L,
            method = PaymentMethod.CASH, receiptNumber = "RCP-1",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "Counter", at = fixedAt.plusSeconds(60),
        )
        val reversal = LedgerEngine.createReversalEntry(
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

    // ── As-of queries ─────────────────────────────────────────────────────

    @Test fun `as-of query excludes entries with at greater than now`() {
        val accountId = LedgerEngine.deriveAccountId("p1", PaymentCategory.TUITION, null)
        val pastCharge = LedgerEngine.createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice", description = "T1",
            at = fixedAt.minusSeconds(86400),  // yesterday
        )
        val futureCharge = LedgerEngine.createChargeEntry(
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
        val accountId = LedgerEngine.deriveAccountId("p1", PaymentCategory.TUITION, null)
        val entries = listOf(
            LedgerEngine.createChargeEntry("t1", "p1", null, PaymentCategory.TUITION, 50000L,
                LedgerSourceType.INSTALLMENT, "inst-1", "u1", "Alice", "T1", at = fixedAt),
            LedgerEngine.createPaymentEntry("t1", "p1", null, PaymentCategory.TUITION, 20000L,
                PaymentMethod.CASH, "RCP-1", PaymentStatus.PAID, "pay-1", "u1", "Alice",
                "P1", at = fixedAt.plusSeconds(60)),
            LedgerEngine.createAdjustmentEntry("t1", "p1", null, PaymentCategory.TUITION, -5000L,
                "adj-1", "u1", "Alice", "Discount", at = fixedAt.plusSeconds(120)),
        )
        val balance1 = LedgerEngine.computeAccountBalance(entries, accountId)
        val balance2 = LedgerEngine.computeAccountBalance(entries, accountId)
        assertEquals(balance1, balance2)
        // balance = 50000 - 20000 - 5000 = 25000
        assertEquals(25000L, balance1.balance)
    }
}
