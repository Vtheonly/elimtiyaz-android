package com.example.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import java.time.Instant

/**
 * Unit tests for the top-level entry factory functions in
 * `LedgerEntryFactory.kt` — mirrors the desktop `src/test/unit/ledger.test.ts`
 * factory section.
 *
 * Verifies:
 *   - Account ID derivation (deterministic, varies by category/student)
 *   - Factory function invariants (throw on invalid input)
 *   - Reversal construction (negates original, linked via reversesId)
 */
class LedgerEntryFactoryTest {

    private val fixedAt: Instant = Instant.parse("2026-07-31T10:00:00Z")

    // ── Account ID derivation ─────────────────────────────────────────────

    @Test fun `deriveAccountId is deterministic for same inputs`() {
        val id1 = deriveAccountId("par-001", PaymentCategory.TUITION, null)
        val id2 = deriveAccountId("par-001", PaymentCategory.TUITION, null)
        assertEquals(id1, id2)
        assertEquals("parent:par-001:category:tuition", id1)
    }

    @Test fun `deriveAccountId changes with category`() {
        val tuition = deriveAccountId("par-001", PaymentCategory.TUITION, null)
        val transport = deriveAccountId("par-001", PaymentCategory.TRANSPORT, null)
        assertTrue(tuition != transport)
    }

    @Test fun `deriveAccountId changes with student`() {
        val parentLevel = deriveAccountId("par-001", PaymentCategory.TUITION, null)
        val studentScoped = deriveAccountId("par-001", PaymentCategory.TUITION, "stu-001")
        assertTrue(parentLevel != studentScoped)
        assertEquals("parent:par-001:category:tuition:student:stu-001", studentScoped)
    }

    // ── Factory invariants ────────────────────────────────────────────────

    @Test fun `createChargeEntry throws on non-positive amount`() {
        assertFailsWith<IllegalArgumentException> {
            createChargeEntry(
                tenantId = "t1", parentId = "p1", studentId = null,
                category = PaymentCategory.TUITION, amount = 0L,
                sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
                actorId = "u1", actorName = "Alice",
                description = "Tuition tranche 1",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            createChargeEntry(
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
            createChargeEntry(
                tenantId = "t1", parentId = "p1", studentId = null,
                category = PaymentCategory.TUITION, amount = 50000L,
                sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
                actorId = "u1", actorName = "Alice",
                description = "",
            )
        }
    }

    @Test fun `createPaymentEntry stores negation as amount`() {
        val entry = createPaymentEntry(
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
            createPaymentEntry(
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
            createAdjustmentEntry(
                tenantId = "t1", parentId = "p1", studentId = null,
                category = PaymentCategory.TUITION, amount = 0L,
                sourceId = "adj-1", actorId = "u1", actorName = "Alice",
                reason = "Test",
            )
        }
    }

    @Test fun `createAdjustmentEntry accepts both positive and negative amounts`() {
        val debit = createAdjustmentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = +500_00L,
            sourceId = "adj-1", actorId = "u1", actorName = "Alice",
            reason = "Penalty", at = fixedAt,
        )
        val credit = createAdjustmentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = -500_00L,
            sourceId = "adj-2", actorId = "u1", actorName = "Alice",
            reason = "Discount", at = fixedAt,
        )
        assertEquals(+500_00L, debit.amount)
        assertEquals(-500_00L, credit.amount)
    }

    @Test fun `createReversalEntry negates original amount and links via reversesId`() {
        val original = createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice",
            description = "Tuition tranche 1", at = fixedAt,
        )
        val reversal = createReversalEntry(
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
        val original = createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 50000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
            actorId = "u1", actorName = "Alice",
            description = "Tuition tranche 1", at = fixedAt,
        )
        assertFailsWith<IllegalArgumentException> {
            createReversalEntry(original, reason = "", actorId = "u2", actorName = "Bob")
        }
    }
}
