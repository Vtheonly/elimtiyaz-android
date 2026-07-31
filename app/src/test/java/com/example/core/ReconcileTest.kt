package com.example.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Unit tests for the reconciliation engine — mirrors the desktop
 * `src/test/unit/ledger.test.ts` reconciliation section.
 *
 * Verifies that all 10 checks (7 structural + 3 cross-checks) produce the
 * correct wire-protocol violation codes. These codes must match the desktop
 * exactly for cross-platform audit log compatibility.
 */
class ReconcileTest {

    private val fixedAt = java.time.Instant.parse("2026-07-31T10:00:00Z")

    private fun validCharge(
        id: String = "led-001",
        parentId: String = "p1",
        tenantId: String = "t1",
        amount: Long = 50000L,
    ) = LedgerEngine.createChargeEntry(
        tenantId = tenantId, parentId = parentId, studentId = null,
        category = PaymentCategory.TUITION, amount = amount,
        sourceType = LedgerSourceType.INSTALLMENT, sourceId = "inst-1",
        actorId = "u1", actorName = "Alice", description = "T1", at = fixedAt,
    ).copy(id = id)

    @Test fun `empty ledger passes reconciliation`() {
        val report = Reconcile.reconcileLedger(emptyList())
        assertTrue(report.passed)
        assertEquals(0, report.entryCount)
        assertEquals(0, report.violations.size)
    }

    @Test fun `single valid charge passes reconciliation`() {
        val report = Reconcile.reconcileLedger(listOf(validCharge()))
        assertTrue(report.passed)
        assertEquals(0, report.errorCount)
    }

    @Test fun `duplicate entry IDs produce DUPLICATE_ENTRY_ID error`() {
        val entry = validCharge(id = "led-001")
        val report = Reconcile.reconcileLedger(listOf(entry, entry.copy()))
        assertFalse(report.passed)
        assertTrue(report.violations.any {
            it.code == Reconcile.CODE_DUPLICATE_ENTRY_ID && it.severity == Reconcile.Severity.ERROR
        })
    }

    @Test fun `charge with non-positive amount produces CHARGE_NOT_POSITIVE error`() {
        // Manually construct an invalid entry (bypass the factory)
        val invalid = validCharge().copy(amount = 0L)
        val report = Reconcile.reconcileLedger(listOf(invalid))
        assertTrue(report.violations.any { it.code == Reconcile.CODE_CHARGE_NOT_POSITIVE })
    }

    @Test fun `payment with non-negative amount produces PAYMENT_NOT_NEGATIVE error`() {
        val invalidPayment = LedgerEngine.createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 5000L,
            method = PaymentMethod.CASH, receiptNumber = "RCP-1",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "Test", at = fixedAt,
        ).copy(amount = +5000L)  // INVALID: payment must be negative
        val report = Reconcile.reconcileLedger(listOf(invalidPayment))
        assertTrue(report.violations.any { it.code == Reconcile.CODE_PAYMENT_NOT_NEGATIVE })
    }

    @Test fun `account ID mismatch produces ACCOUNT_ID_MISMATCH error`() {
        val entry = validCharge().copy(accountId = "parent:p1:category:transport")  // wrong category
        val report = Reconcile.reconcileLedger(listOf(entry))
        assertTrue(report.violations.any { it.code == Reconcile.CODE_ACCOUNT_ID_MISMATCH })
    }

    @Test fun `orphan reversal produces ORPHAN_REVERSAL error`() {
        val original = validCharge(id = "led-001")
        val reversal = LedgerEngine.createReversalEntry(
            original, reason = "Test", actorId = "u2", actorName = "Bob", at = fixedAt,
        ).copy(reversesId = "led-nonexistent")  // orphan reference
        val report = Reconcile.reconcileLedger(listOf(original, reversal))
        assertTrue(report.violations.any { it.code == Reconcile.CODE_ORPHAN_REVERSAL })
    }

    @Test fun `double reversal produces DOUBLE_REVERSAL error`() {
        val original = validCharge(id = "led-001")
        val reversal1 = LedgerEngine.createReversalEntry(
            original, reason = "First", actorId = "u2", actorName = "Bob", at = fixedAt,
        )
        val reversal2 = LedgerEngine.createReversalEntry(
            original, reason = "Second", actorId = "u2", actorName = "Bob", at = fixedAt,
        )
        val report = Reconcile.reconcileLedger(listOf(original, reversal1, reversal2))
        assertTrue(report.violations.any { it.code == Reconcile.CODE_DOUBLE_REVERSAL })
    }

    @Test fun `reversal amount mismatch produces REVERSAL_AMOUNT_MISMATCH error`() {
        val original = validCharge(id = "led-001", amount = 50000L)
        val reversal = LedgerEngine.createReversalEntry(
            original, reason = "Test", actorId = "u2", actorName = "Bob", at = fixedAt,
        ).copy(amount = -30000L)  // WRONG: should be -50000
        val report = Reconcile.reconcileLedger(listOf(original, reversal))
        assertTrue(report.violations.any { it.code == Reconcile.CODE_REVERSAL_AMOUNT_MISMATCH })
    }

    @Test fun `duplicate receipt number produces DUPLICATE_RECEIPT_NUMBER error`() {
        val e1 = LedgerEngine.createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 5000L,
            method = PaymentMethod.CASH, receiptNumber = "RCP-2026-00001",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "P1", at = fixedAt,
        ).copy(id = "led-001")
        val e2 = e1.copy(id = "led-002")  // same receipt number, different ID
        val report = Reconcile.reconcileLedger(listOf(e1, e2))
        assertTrue(report.violations.any { it.code == Reconcile.CODE_DUPLICATE_RECEIPT_NUMBER })
    }

    @Test fun `tenant mismatch produces TENANT_MISMATCH error`() {
        val e1 = validCharge(tenantId = "t1")
        val e2 = validCharge(tenantId = "t2", id = "led-002")
        val report = Reconcile.reconcileLedger(listOf(e1, e2))
        assertTrue(report.violations.any { it.code == Reconcile.CODE_TENANT_MISMATCH })
    }

    @Test fun `missing actorId produces MISSING_ACTOR_ID warning`() {
        val entry = validCharge().copy(actorId = "")
        val report = Reconcile.reconcileLedger(listOf(entry))
        assertTrue(report.violations.any {
            it.code == Reconcile.CODE_MISSING_ACTOR_ID && it.severity == Reconcile.Severity.WARNING
        })
    }

    @Test fun `balance sum invariant holds for valid ledger`() {
        val entries = listOf(
            validCharge(id = "led-001", parentId = "p1", amount = 50000L),
            validCharge(id = "led-002", parentId = "p2", amount = 30000L),
            LedgerEngine.createPaymentEntry(
                tenantId = "t1", parentId = "p1", studentId = null,
                category = PaymentCategory.TUITION, amount = 20000L,
                method = PaymentMethod.CASH, receiptNumber = "RCP-1",
                paymentStatus = PaymentStatus.PAID,
                sourceId = "pay-1", actorId = "u1", actorName = "Alice",
                description = "P1", at = fixedAt,
            ).copy(id = "led-003"),
        )
        val report = Reconcile.reconcileLedger(entries)
        // Sum of entries = 50000 + 30000 + (-20000) = 60000
        // Sum of balances:
        //   p1 account: 50000 - 20000 = 30000
        //   p2 account: 30000
        //   total = 60000
        // → invariant holds (drift = 0)
        assertTrue(report.violations.none { it.code == Reconcile.CODE_BALANCE_SUM_MISMATCH })
    }

    @Test fun `payment without ledger entry produces cross-check warning`() {
        val orphanPayment = Reconcile.PaymentCrossCheck(
            id = "pay-orphan", amount = 5000L, status = PaymentStatus.PAID,
        )
        val report = Reconcile.reconcileLedger(
            emptyList(),
            crossCheckInputs = Reconcile.CrossCheckInputs(payments = listOf(orphanPayment)),
        )
        assertTrue(report.violations.any { it.code == Reconcile.CODE_PAYMENT_WITHOUT_LEDGER_ENTRY })
    }

    @Test fun `payment amount mismatch produces cross-check error`() {
        val payment = LedgerEngine.createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = null,
            category = PaymentCategory.TUITION, amount = 5000L,
            method = PaymentMethod.CASH, receiptNumber = "RCP-1",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "P1", at = fixedAt,
        ).copy(id = "led-001")
        // Cross-check says payment amount is 3000, but ledger entry has |amount| = 5000
        val crossCheck = Reconcile.PaymentCrossCheck(
            id = "pay-1", amount = 3000L, status = PaymentStatus.PAID,
        )
        val report = Reconcile.reconcileLedger(
            listOf(payment),
            crossCheckInputs = Reconcile.CrossCheckInputs(payments = listOf(crossCheck)),
        )
        assertTrue(report.violations.any { it.code == Reconcile.CODE_PAYMENT_AMOUNT_MISMATCH })
    }
}
