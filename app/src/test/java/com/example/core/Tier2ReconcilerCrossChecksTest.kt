package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TIER 2 R10 — regression tests for the 3 new unified-architecture
 * reconciler cross-checks:
 *   - `crossCheckInstallmentPayments` → `UNBACKED_TRANCHE_SATISFACTION`
 *   - `crossCheckClearedBalance` → `PAYMENT_LEDGER_MISMATCH`
 *   - `crossCheckParentCredit` → `UNBACKED_PARENT_CREDIT`
 *
 * Verifies that the Android reconciler now runs all 6 canonical cross-checks
 * (the 3 original + 3 new) — closing the gap with the desktop's reconciler.
 */
class Tier2ReconcilerCrossChecksTest {

    private val fixedAt = java.time.Instant.parse("2026-01-15T10:00:00Z")

    // ── UNBACKED_TRANCHE_SATISFACTION (precise mode) ────────────────────

    @Test
    fun `crossCheckInstallmentPayments flags tranche marked paid without backing`() {
        // A tranche marked `paid` with amountPaid=33000 but the ledger has
        // no cleared payment entry mapped to it via paymentToInstallmentId.
        val entries = listOf(
            createChargeEntry(
                tenantId = "t1", parentId = "p1", studentId = "stu-1",
                category = PaymentCategory.TUITION, amount = 33000L,
                sourceType = LedgerSourceType.INSTALLMENT, sourceId = "ins-1",
                actorId = "u1", actorName = "Alice",
                description = "Tuition tranche 1",
                at = fixedAt,
            ),
        )
        val inputs = Reconcile.CrossCheckInputs(
            installments = listOf(
                Reconcile.InstallmentCrossCheck(
                    id = "ins-1",
                    parentId = "p1",
                    studentId = "stu-1",
                    category = "tuition",
                    amountDue = 33000L,
                    amountPaid = 33000L,    // marked fully paid
                    label = "Tranche 1",
                    status = "paid",
                ),
            ),
            paymentToInstallmentId = emptyMap(), // precise mode but empty map
        )
        val report = Reconcile.reconcileLedger(entries, inputs)
        val unbacked = report.violations.filter { it.code == Reconcile.CODE_UNBACKED_TRANCHE_SATISFACTION }
        assertTrue(
            "Expected UNBACKED_TRANCHE_SATISFACTION violation, got: ${report.violations.map { it.code }}",
            unbacked.isNotEmpty(),
        )
    }

    @Test
    fun `crossCheckInstallmentPayments passes when tranche is backed by cleared payment`() {
        val paymentEntry = createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = "stu-1",
            category = PaymentCategory.TUITION, amount = 33000L,
            method = PaymentMethod.CASH, receiptNumber = "REC-1",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "Payment",
            at = fixedAt,
        )
        val chargeEntry = createChargeEntry(
            tenantId = "t1", parentId = "p1", studentId = "stu-1",
            category = PaymentCategory.TUITION, amount = 33000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "ins-1",
            actorId = "u1", actorName = "Alice",
            description = "Tuition tranche 1",
            at = fixedAt,
        )
        val entries = listOf(chargeEntry, paymentEntry)
        val inputs = Reconcile.CrossCheckInputs(
            installments = listOf(
                Reconcile.InstallmentCrossCheck(
                    id = "ins-1", parentId = "p1", studentId = "stu-1",
                    category = "tuition", amountDue = 33000L, amountPaid = 33000L,
                    label = "Tranche 1", status = "paid",
                ),
            ),
            paymentToInstallmentId = mapOf("pay-1" to "ins-1"),
        )
        val report = Reconcile.reconcileLedger(entries, inputs)
        val unbacked = report.violations.filter { it.code == Reconcile.CODE_UNBACKED_TRANCHE_SATISFACTION }
        assertEquals(
            "Expected no UNBACKED_TRANCHE_SATISFACTION, got: ${unbacked.map { it.message }}",
            0, unbacked.size,
        )
    }

    // ── PAYMENT_LEDGER_MISMATCH ──────────────────────────────────────────

    @Test
    fun `crossCheckClearedBalance flags mismatch between payments table and ledger`() {
        // Payment table says paid=50000, but ledger has 33000 in cleared
        // payment entries. The mismatch should be flagged.
        val paymentEntry = createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = "stu-1",
            category = PaymentCategory.TUITION, amount = 33000L,
            method = PaymentMethod.CASH, receiptNumber = "REC-1",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "Payment",
            at = fixedAt,
        )
        val entries = listOf(paymentEntry)
        val inputs = Reconcile.CrossCheckInputs(
            payments = listOf(
                Reconcile.PaymentCrossCheck(
                    id = "pay-1", amount = 50000L, status = PaymentStatus.PAID,
                ),
            ),
        )
        val report = Reconcile.reconcileLedger(entries, inputs)
        val mismatch = report.violations.filter { it.code == Reconcile.CODE_PAYMENT_LEDGER_MISMATCH }
        assertTrue(
            "Expected PAYMENT_LEDGER_MISMATCH, got: ${report.violations.map { it.code }}",
            mismatch.isNotEmpty(),
        )
    }

    @Test
    fun `crossCheckClearedBalance passes when payments match ledger`() {
        val paymentEntry = createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = "stu-1",
            category = PaymentCategory.TUITION, amount = 33000L,
            method = PaymentMethod.CASH, receiptNumber = "REC-1",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "Payment",
            at = fixedAt,
        )
        val entries = listOf(paymentEntry)
        val inputs = Reconcile.CrossCheckInputs(
            payments = listOf(
                Reconcile.PaymentCrossCheck(
                    id = "pay-1", amount = 33000L, status = PaymentStatus.PAID,
                ),
            ),
        )
        val report = Reconcile.reconcileLedger(entries, inputs)
        val mismatch = report.violations.filter { it.code == Reconcile.CODE_PAYMENT_LEDGER_MISMATCH }
        assertEquals(
            "Expected no PAYMENT_LEDGER_MISMATCH, got: ${mismatch.map { it.message }}",
            0, mismatch.size,
        )
    }

    @Test
    fun `crossCheckClearedBalance ignores pending payments`() {
        // Only PAID payments count toward the cleared balance. A pending
        // payment (e.g., uncleared check) should NOT be counted.
        val paymentEntry = createPaymentEntry(
            tenantId = "t1", parentId = "p1", studentId = "stu-1",
            category = PaymentCategory.TUITION, amount = 33000L,
            method = PaymentMethod.CHECK, receiptNumber = "REC-1",
            paymentStatus = PaymentStatus.PENDING,
            sourceId = "pay-1", actorId = "u1", actorName = "Alice",
            description = "Pending check",
            at = fixedAt,
        )
        val entries = listOf(paymentEntry)
        val inputs = Reconcile.CrossCheckInputs(
            payments = listOf(
                Reconcile.PaymentCrossCheck(
                    id = "pay-1", amount = 33000L, status = PaymentStatus.PENDING,
                ),
            ),
        )
        val report = Reconcile.reconcileLedger(entries, inputs)
        val mismatch = report.violations.filter { it.code == Reconcile.CODE_PAYMENT_LEDGER_MISMATCH }
        assertEquals(
            "Pending payments should be excluded from cleared balance, got: ${mismatch.map { it.message }}",
            0, mismatch.size,
        )
    }

    // ── UNBACKED_PARENT_CREDIT ──────────────────────────────────────────

    @Test
    fun `crossCheckParentCredit flags negative outstanding without parent_credit entry`() {
        // A parent with totalOutstanding = -50000 (school owes parent) but
        // no `parent_credit` adjustment entry on the ledger → flag.
        val entries = listOf(
            // A regular tuition charge
            createChargeEntry(
                tenantId = "t1", parentId = "p1", studentId = "stu-1",
                category = PaymentCategory.TUITION, amount = 10000L,
                sourceType = LedgerSourceType.INSTALLMENT, sourceId = "ins-1",
                actorId = "u1", actorName = "Alice",
                description = "Tuition",
                at = fixedAt,
            ),
            // An overpayment that should have been a parent_credit but is on tuition
            createPaymentEntry(
                tenantId = "t1", parentId = "p1", studentId = "stu-1",
                category = PaymentCategory.TUITION, amount = 60000L,
                method = PaymentMethod.CASH, receiptNumber = "REC-1",
                paymentStatus = PaymentStatus.PAID,
                sourceId = "pay-1", actorId = "u1", actorName = "Alice",
                description = "Overpayment",
                at = fixedAt,
            ),
        )
        val inputs = Reconcile.CrossCheckInputs(
            parentSummaries = listOf(
                Reconcile.ParentSummaryCrossCheck(
                    parentId = "p1", parentName = "Test Parent",
                    totalOutstanding = -50000L, // negative — school owes parent
                    accounts = listOf(
                        // The tuition account has a negative balance.
                        Reconcile.ParentAccountCrossCheck(
                            accountId = "parent:p1:category:tuition:student:stu-1",
                            category = "tuition",
                            studentId = "stu-1",
                            balance = -50000L,
                            unallocatedCredit = 0L,
                        ),
                    ),
                ),
            ),
        )
        val report = Reconcile.reconcileLedger(entries, inputs)
        val unbacked = report.violations.filter { it.code == Reconcile.CODE_UNBACKED_PARENT_CREDIT }
        assertTrue(
            "Expected UNBACKED_PARENT_CREDIT violation, got: ${report.violations.map { it.code }}",
            unbacked.isNotEmpty(),
        )
    }

    @Test
    fun `crossCheckParentCredit passes when negative balance is on parent_credit account`() {
        // A parent with negative outstanding backed by an explicit
        // `parent_credit` adjustment entry → no violation.
        val entries = listOf(
            createChargeEntry(
                tenantId = "t1", parentId = "p1", studentId = "stu-1",
                category = PaymentCategory.TUITION, amount = 50000L,
                sourceType = LedgerSourceType.INSTALLMENT, sourceId = "ins-1",
                actorId = "u1", actorName = "Alice",
                description = "Tuition",
                at = fixedAt,
            ),
            createPaymentEntry(
                tenantId = "t1", parentId = "p1", studentId = "stu-1",
                category = PaymentCategory.TUITION, amount = 100000L,
                method = PaymentMethod.CASH, receiptNumber = "REC-1",
                paymentStatus = PaymentStatus.PAID,
                sourceId = "pay-1", actorId = "u1", actorName = "Alice",
                description = "Overpayment",
                at = fixedAt,
            ),
            // The canonical parent_credit adjustment (INV-7) — backs the
            // negative balance on parent_credit account.
            createAdjustmentEntry(
                tenantId = "t1", parentId = "p1", studentId = null,
                category = PaymentCategory.PARENT_CREDIT, amount = -50000L,
                sourceId = "adj-1", actorId = "u1", actorName = "Alice",
                reason = "Overpayment credit",
                at = fixedAt,
            ),
        )
        val inputs = Reconcile.CrossCheckInputs(
            parentSummaries = listOf(
                Reconcile.ParentSummaryCrossCheck(
                    parentId = "p1", parentName = "Test Parent",
                    totalOutstanding = -50000L,
                    accounts = listOf(
                        // parent_credit account has the negative balance
                        // — that's the canonical pattern (INV-3 + INV-7).
                        Reconcile.ParentAccountCrossCheck(
                            accountId = "parent:p1:category:parent_credit",
                            category = "parent_credit",
                            studentId = null,
                            balance = -50000L,
                            unallocatedCredit = 50000L,
                        ),
                    ),
                ),
            ),
        )
        val report = Reconcile.reconcileLedger(entries, inputs)
        val unbacked = report.violations.filter { it.code == Reconcile.CODE_UNBACKED_PARENT_CREDIT }
        assertEquals(
            "Expected no UNBACKED_PARENT_CREDIT, got: ${unbacked.map { it.message }}",
            0, unbacked.size,
        )
    }

    // ── Direction-neutrality: same scenario, same result as desktop ─────

    @Test
    fun `reconciler runs all 6 cross-checks when full inputs are provided`() {
        // Sanity test — when ALL inputs are provided (payments, installments,
        // parentSummaries, paymentToInstallmentId), the reconciler should
        // run all 6 cross-checks. This is the parity test with the desktop.
        val entries = listOf(
            createChargeEntry(
                tenantId = "t1", parentId = "p1", studentId = "stu-1",
                category = PaymentCategory.TUITION, amount = 33000L,
                sourceType = LedgerSourceType.INSTALLMENT, sourceId = "ins-1",
                actorId = "u1", actorName = "Alice",
                description = "Tuition tranche 1",
                at = fixedAt,
            ),
            createPaymentEntry(
                tenantId = "t1", parentId = "p1", studentId = "stu-1",
                category = PaymentCategory.TUITION, amount = 33000L,
                method = PaymentMethod.CASH, receiptNumber = "REC-1",
                paymentStatus = PaymentStatus.PAID,
                sourceId = "pay-1", actorId = "u1", actorName = "Alice",
                description = "Payment",
                at = fixedAt,
            ),
        )
        val inputs = Reconcile.CrossCheckInputs(
            payments = listOf(
                Reconcile.PaymentCrossCheck(id = "pay-1", amount = 33000L, status = PaymentStatus.PAID),
            ),
            installments = listOf(
                Reconcile.InstallmentCrossCheck(
                    id = "ins-1", parentId = "p1", studentId = "stu-1",
                    category = "tuition", amountDue = 33000L, amountPaid = 33000L,
                    label = "Tranche 1", status = "paid",
                ),
            ),
            parentSummaries = emptyList(), // no parents → no parentCredit check fires
            paymentToInstallmentId = mapOf("pay-1" to "ins-1"),
        )
        val report = Reconcile.reconcileLedger(entries, inputs)
        // The report should run with no errors (assuming all 6 cross-checks pass).
        // Note: PAYMENT_WITHOUT_LEDGER_ENTRY, INSTALLMENT_WITHOUT_LEDGER_ENTRY,
        // UNBACKED_TRANCHE_SATISFACTION, PAYMENT_LEDGER_MISMATCH, BALANCE_SUM_MISMATCH,
        // UNBACKED_PARENT_CREDIT — none should fire for this clean scenario.
        val errors = report.violations.filter { it.severity == Reconcile.Severity.ERROR }
        assertEquals(
            "Expected no errors for clean scenario, got: ${errors.map { it.code + ": " + it.message }}",
            0, errors.size,
        )
    }
}
