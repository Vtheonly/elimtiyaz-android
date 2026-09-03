package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-platform financial consistency runner — Kotlin side.
 *
 * CANONICAL-FINANCIAL-LOGIC.md §9 — both apps MUST produce the same domain
 * state for the same operation. This runner hardcodes its scenario set and
 * runs it through the canonical Kotlin calc engine (LedgerEngine +
 * DiscountEngine + WaterfallAllocation).
 * The original 8 YAML scenario files (`financial-tests/scenarios/`) were
 * RETIRED 2026-09-03 (T-043 pass 2, ADR-006 — hub repo): every scenario's
 * semantics live on in BOTH surviving places — the JSON corpus
 * (`financial-tests/equivalence/scenarios/`) and this runner's hardcoded
 * set. The .yml tree was documentation-only drift-bait (DEAD-004): neither
 * runner ever READ the files.
 *
 * The TypeScript runner in
 * `src/test/cross-platform/ScenarioRunner.test.ts` runs the same scenarios
 * through the TypeScript calc engine. Both runners produce the same
 * pass/fail results when the implementations are semantically equivalent.
 */
class CrossPlatformScenarioRunner {

    @Test
    fun `scenario single_payment_partial - balance + installment update`() {
        val now = parseIsoInstantSafe("2026-09-20T00:00:00Z")
        val accountId = "parent:par-001:category:tuition:student:stu-001"
        val charge = createChargeEntry(
            tenantId = "00000000-0000-0000-0000-000000000001",
            parentId = "par-001", studentId = "stu-001",
            category = PaymentCategory.TUITION, amount = 10_000_000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "ins-001",
            actorId = "system", actorName = "System",
            description = "Scolarité Tranche 1",
            at = parseIsoInstantSafe("2026-09-15T00:00:00Z"),
            metadata = mapOf("tranche" to 1, "paymentPlan" to "tranches", "gradeLevel" to "2ap"),
        )
        val entries = mutableListOf(charge)

        // Run collect_payment operation.
        val paymentAmount = 2_500_000L
        val paymentEntry = createPaymentEntry(
            tenantId = charge.tenantId, parentId = "par-001", studentId = "stu-001",
            category = PaymentCategory.TUITION, amount = paymentAmount,
            method = PaymentMethod.CASH, receiptNumber = "REC-2026-000001",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-001", actorId = "usr-001", actorName = "Agent comptoir",
            description = "Encaissement REC-2026-000001",
            at = now,
        )
        entries.add(paymentEntry)

        // Waterfall allocate across installments.
        val waterfallInstallment = WaterfallInstallment(
            id = "ins-001", category = PaymentCategory.TUITION,
            amountDue = 10_000_000L, amountPaid = 0L, amountPending = 0L,
            dueDate = "2026-09-15T00:00:00Z", status = "unpaid",
        )
        val allocation = allocatePaymentToInstallments(
            installments = listOf(waterfallInstallment),
            paymentAmount = paymentAmount,
            categoryFilter = PaymentCategory.TUITION,
            paymentStatus = PaymentStatus.PAID,
        )
        assertEquals(0L, allocation.unallocatedAmount)
        assertEquals(1, allocation.allocations.size)
        assertEquals(2_500_000L, allocation.allocations[0].allocatedAmount)
        assertEquals(2_500_000L, allocation.allocations[0].newAmountPaid)
        assertEquals("partial", allocation.allocations[0].newStatus)

        // Now compute the account balance — should match expected.
        val balance = LedgerEngine.computeAccountBalance(entries, accountId, now)
        assertEquals(7_500_000L, balance.balance)
        assertEquals(10_000_000L, balance.totalCharged)
        assertEquals(2_500_000L, balance.totalPaid)
        assertEquals(2_500_000L, balance.totalCleared)
        assertEquals(0L, balance.totalPending)
        assertEquals(0L, balance.totalAdjusted)
        assertEquals(0L, balance.totalRefunded)
        assertEquals(0L, balance.unallocatedCredit)

        val summary = LedgerEngine.computeParentSummary(entries, "par-001", "Parent Test", emptyMap(), now)
        assertEquals(7_500_000L, summary.totalOutstanding)
        assertEquals(0L, summary.totalOverdue)
        assertEquals(10_000_000L, summary.totalCharged)
        assertEquals(2_500_000L, summary.totalPaid)
        assertEquals(2_500_000L, summary.totalCleared)
        assertEquals(0L, summary.totalPending)
        assertEquals(0L, summary.totalAdjusted)
        assertEquals(0L, summary.totalRefunded)
        assertEquals(0L, summary.totalUnallocatedCredit)
    }

    @Test
    fun `scenario overpayment_creates_parent_credit - INV-7`() {
        val now = parseIsoInstantSafe("2026-09-20T00:00:00Z")
        val tuitionAccount = "parent:par-001:category:tuition:student:stu-001"
        val creditAccount = "parent:par-001:category:parent_credit"

        val charge = createChargeEntry(
            tenantId = "00000000-0000-0000-0000-000000000001",
            parentId = "par-001", studentId = "stu-001",
            category = PaymentCategory.TUITION, amount = 10_000_000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "ins-001",
            actorId = "system", actorName = "System",
            description = "Scolarité Tranche 1",
            at = parseIsoInstantSafe("2026-09-15T00:00:00Z"),
            metadata = mapOf("tranche" to 1, "paymentPlan" to "tranches"),
        )
        val entries = mutableListOf(charge)

        val paymentAmount = 15_000_000L  // overpays by 5,000,000 centimes (50,000 DZD)
        val paymentEntry = createPaymentEntry(
            tenantId = charge.tenantId, parentId = "par-001", studentId = "stu-001",
            category = PaymentCategory.TUITION, amount = paymentAmount,
            method = PaymentMethod.CASH, receiptNumber = "REC-2026-000002",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-002", actorId = "usr-001", actorName = "Agent comptoir",
            description = "Encaissement REC-2026-000002",
            at = now,
        )
        entries.add(paymentEntry)

        // Waterfall: absorbs 10,000,000 into tranche 1, 5,000,000 unallocated.
        val waterfallInstallment = WaterfallInstallment(
            id = "ins-001", category = PaymentCategory.TUITION,
            amountDue = 10_000_000L, amountPaid = 0L, amountPending = 0L,
            dueDate = "2026-09-15T00:00:00Z", status = "unpaid",
        )
        val allocation = allocatePaymentToInstallments(
            installments = listOf(waterfallInstallment),
            paymentAmount = paymentAmount,
            categoryFilter = PaymentCategory.TUITION,
            paymentStatus = PaymentStatus.PAID,
        )
        assertEquals(5_000_000L, allocation.unallocatedAmount)

        // CANONICAL-FINANCIAL-LOGIC.md §4 INV-7 — overpayment credit MUST
        // land on the parent_credit account, NOT the tuition:student account.
        val creditEntry = createAdjustmentEntry(
            tenantId = charge.tenantId,
            parentId = "par-001",
            studentId = null,  // parent-scoped
            category = PaymentCategory.PARENT_CREDIT,
            amount = -allocation.unallocatedAmount,
            sourceId = "pay-002", actorId = "usr-001", actorName = "Agent comptoir",
            reason = "Crédit parent (trop-perçu) REC-2026-000002",
            at = now,
        )
        entries.add(creditEntry)

        // Verify the credit went to the parent_credit account, not the tuition account.
        assertEquals(creditAccount, creditEntry.accountId)

        val tuitionBalance = LedgerEngine.computeAccountBalance(entries, tuitionAccount, now)
        // Tuition account: +10M (charge) - 15M (full payment) = -5M (overpayment
        // stuck on this account). The canonical workflow does NOT move the
        // overpayment off the tuition account — it only writes a SEPARATE
        // -5M adjustment on the parent_credit account. This is a known
        // limitation documented in unification-logic-docs/NEXT-ITERATION.md.
        assertEquals(-5_000_000L, tuitionBalance.balance)
        assertEquals(15_000_000L, tuitionBalance.totalPaid)  // full payment received
        assertEquals(0L, tuitionBalance.unallocatedCredit)  // parent_credit is NOT on this account

        val creditBalance = LedgerEngine.computeAccountBalance(entries, creditAccount, now)
        assertEquals(-5_000_000L, creditBalance.balance)  // negative = banked credit
        assertEquals(-5_000_000L, creditBalance.unallocatedCredit)
        assertEquals(-5_000_000L, creditBalance.totalAdjusted)

        val summary = LedgerEngine.computeParentSummary(entries, "par-001", "Parent Test", emptyMap(), now)
        // Total = -5M (tuition overpayment) + -5M (parent_credit) = -10M.
        // The unallocatedCredit rollup only counts parent_credit accounts: -5M.
        assertEquals(-5_000_000L, summary.totalUnallocatedCredit)
    }

    @Test
    fun `scenario pending_check_payment - INV-5 + INV-6`() {
        val now = parseIsoInstantSafe("2026-09-20T00:00:00Z")
        val accountId = "parent:par-001:category:tuition:student:stu-001"
        val charge = createChargeEntry(
            tenantId = "00000000-0000-0000-0000-000000000001",
            parentId = "par-001", studentId = "stu-001",
            category = PaymentCategory.TUITION, amount = 10_000_000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "ins-001",
            actorId = "system", actorName = "System",
            description = "Scolarité Tranche 1",
            at = parseIsoInstantSafe("2026-09-15T00:00:00Z"),
        )
        val entries = mutableListOf(charge)

        // Pending check payment.
        val paymentEntry = createPaymentEntry(
            tenantId = charge.tenantId, parentId = "par-001", studentId = "stu-001",
            category = PaymentCategory.TUITION, amount = 10_000_000L,
            method = PaymentMethod.CHECK, receiptNumber = "REC-2026-000003",
            paymentStatus = PaymentStatus.PENDING,  // uncleared
            sourceId = "pay-003", actorId = "usr-001", actorName = "Agent comptoir",
            description = "Chèque REC-2026-000003",
            at = now,
        )
        entries.add(paymentEntry)

        val balance = LedgerEngine.computeAccountBalance(entries, accountId, now)
        assertEquals(0L, balance.balance)              // INV-5: balance reduced immediately
        assertEquals(10_000_000L, balance.totalPaid)
        assertEquals(0L, balance.totalCleared)         // NOT cleared
        assertEquals(10_000_000L, balance.totalPending) // all in the pending bucket

        // Waterfall with paymentStatus=pending must produce pending_clearance, not paid.
        val waterfallInstallment = WaterfallInstallment(
            id = "ins-001", category = PaymentCategory.TUITION,
            amountDue = 10_000_000L, amountPaid = 0L, amountPending = 0L,
            dueDate = "2026-09-15T00:00:00Z", status = "unpaid",
        )
        val allocation = allocatePaymentToInstallments(
            installments = listOf(waterfallInstallment),
            paymentAmount = 10_000_000L,
            categoryFilter = PaymentCategory.TUITION,
            paymentStatus = PaymentStatus.PENDING,  // uncleared
        )
        assertEquals(1, allocation.allocations.size)
        assertEquals(0L, allocation.allocations[0].newAmountPaid)        // amountPaid unchanged
        assertEquals(10_000_000L, allocation.allocations[0].newAmountPending)
        assertEquals("pending_clearance", allocation.allocations[0].newStatus)
        assertFalse(allocation.allocations[0].fullySatisfied)
    }

    @Test
    fun `scenario refund_cleared_payment - INV-8 cleared branch`() {
        val now = parseIsoInstantSafe("2026-09-25T00:00:00Z")
        val accountId = "parent:par-001:category:tuition:student:stu-001"
        val chargeAt = parseIsoInstantSafe("2026-09-15T00:00:00Z")
        val paymentAt = parseIsoInstantSafe("2026-09-20T00:00:00Z")

        val charge = createChargeEntry(
            tenantId = "00000000-0000-0000-0000-000000000001",
            parentId = "par-001", studentId = "stu-001",
            category = PaymentCategory.TUITION, amount = 10_000_000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "ins-001",
            actorId = "system", actorName = "System",
            description = "Scolarité Tranche 1",
            at = chargeAt,
        )
        val payment = createPaymentEntry(
            tenantId = charge.tenantId, parentId = "par-001", studentId = "stu-001",
            category = PaymentCategory.TUITION, amount = 10_000_000L,
            method = PaymentMethod.CASH, receiptNumber = "REC-2026-000001",
            paymentStatus = PaymentStatus.PAID,
            sourceId = "pay-001", actorId = "usr-001", actorName = "Agent comptoir",
            description = "Encaissement REC-2026-000001",
            at = paymentAt,
        )
        val entries = mutableListOf(charge, payment)

        // Run refund — write a reversal entry.
        val reversal = createReversalEntry(
            original = payment, reason = "Annulation — erreur de saisie",
            actorId = "usr-001", actorName = "Agent comptoir",
            at = now,
        )
        entries.add(reversal)

        // LIFO revert with originalWasPending=false (payment was PAID).
        val waterfallInstallment = WaterfallInstallment(
            id = "ins-001", category = PaymentCategory.TUITION,
            amountDue = 10_000_000L, amountPaid = 10_000_000L, amountPending = 0L,
            dueDate = "2026-09-15T00:00:00Z", status = "paid",
        )
        val revert = revertPaymentAllocation(
            installments = listOf(waterfallInstallment),
            reversalAmount = 10_000_000L,
            categoryFilter = PaymentCategory.TUITION,
            originalWasPending = false,  // CRITICAL — was PAID, not PENDING
        )
        assertEquals(1, revert.reverts.size)
        assertEquals(0L, revert.reverts[0].newAmountPaid)
        // CANONICAL-FINANCIAL-LOGIC.md §7.3 — `reevaluateInstallmentStatus`
        // returns "pending" (not "unpaid") for the post-revert state when
        // amountPaid=0 and the due date is in the future. The "unpaid"
        // status is reserved for initial installment creation.
        assertEquals("pending", revert.reverts[0].newStatus)

        val balance = LedgerEngine.computeAccountBalance(entries, accountId, now)
        // Balance = 10M (charge) - 10M (payment) + 10M (reversal) = 10M
        assertEquals(10_000_000L, balance.balance)
        // Typed totals: original payment is excluded from totalPaid (reversed)
        assertEquals(0L, balance.totalPaid)
        assertEquals(0L, balance.totalCleared)
    }

    @Test
    fun `scenario refund_pending_payment - INV-8 pending branch (R5 fix)`() {
        val now = parseIsoInstantSafe("2026-09-25T00:00:00Z")
        val accountId = "parent:par-001:category:tuition:student:stu-001"

        val charge = createChargeEntry(
            tenantId = "00000000-0000-0000-0000-000000000001",
            parentId = "par-001", studentId = "stu-001",
            category = PaymentCategory.TUITION, amount = 10_000_000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "ins-001",
            actorId = "system", actorName = "System",
            description = "Scolarité Tranche 1",
            at = parseIsoInstantSafe("2026-09-15T00:00:00Z"),
        )
        val payment = createPaymentEntry(
            tenantId = charge.tenantId, parentId = "par-001", studentId = "stu-001",
            category = PaymentCategory.TUITION, amount = 10_000_000L,
            method = PaymentMethod.CHECK, receiptNumber = "REC-2026-000001",
            paymentStatus = PaymentStatus.PENDING,  // UNCLEARED
            sourceId = "pay-001", actorId = "usr-001", actorName = "Agent comptoir",
            description = "Chèque REC-2026-000001",
            at = parseIsoInstantSafe("2026-09-20T00:00:00Z"),
        )
        val entries = mutableListOf(charge, payment)

        val reversal = createReversalEntry(
            original = payment, reason = "Chèque sans provision",
            actorId = "usr-001", actorName = "Agent comptoir",
            at = now,
        )
        entries.add(reversal)

        // CRITICAL: originalWasPending = TRUE because the payment was PENDING.
        // The R5 bug was that this was hardcoded false, causing the revert to
        // subtract from amountPaid (=0 for a pending payment), a silent no-op.
        val waterfallInstallment = WaterfallInstallment(
            id = "ins-001", category = PaymentCategory.TUITION,
            amountDue = 10_000_000L, amountPaid = 0L, amountPending = 10_000_000L,
            dueDate = "2026-09-15T00:00:00Z", status = "pending_clearance",
        )
        val revert = revertPaymentAllocation(
            installments = listOf(waterfallInstallment),
            reversalAmount = 10_000_000L,
            categoryFilter = PaymentCategory.TUITION,
            originalWasPending = true,   // R5 FIX — was PENDING, revert from amountPending
        )
        assertEquals(1, revert.reverts.size)
        assertEquals(0L, revert.reverts[0].newAmountPending)  // reverted to 0
        assertEquals(0L, revert.reverts[0].newAmountPaid)

        val balance = LedgerEngine.computeAccountBalance(entries, accountId, now)
        // Balance = 10M (charge) - 10M (payment) + 10M (reversal) = 10M
        assertEquals(10_000_000L, balance.balance)
        assertEquals(0L, balance.totalPending)  // CRITICAL: amountPending reverted
        assertEquals(0L, balance.totalCleared)
    }

    @Test
    fun `scenario discount_engine_all_5_rules - INV §5`() {
        val params = EvaluateAllDiscountsParams(
            grossTuition = 33_000_000L,    // 330,000 DZD
            previousGradeLevel = "5ap",
            currentGradeLevel = "1am",
            childIndex = 3,                // 2 additional siblings
            paymentPlan = PaymentPlan.FULL_ANNUAL,
            paymentDate = "2026-06-15T00:00:00Z",   // before June 30
            academicYearStartYear = 2026,
            academicYearStart = "2026-09-15T00:00:00Z",
            enrollmentDate = "2020-09-01T00:00:00Z",   // 6 years seniority
            previousRank = 1,
        )
        val evaluations = evaluateAllSystemDiscounts(params)
        val total = sumDiscounts(evaluations)
        // Expected:
        //   passage_palier: -10,000 DZD = -1,000,000 centimes
        //   sibling_fixed:  -10,000 DZD (2 × 5,000) = -1,000,000 centimes
        //   full_annual:    -10% of 330,000 = -33,000 DZD = -3,300,000 centimes
        //   highest_average: -10% of 330,000 = -33,000 DZD = -3,300,000 centimes
        //   seniority_5y:   -5% of 330,000 = -16,500 DZD = -1,650,000 centimes
        //   total = -102,500 DZD = -10,250,000 centimes
        //   net = 330,000 - 102,500 = 227,500 DZD = 22,750,000 centimes
        assertEquals(5, evaluations.size)
        assertEquals(-1_000_000L, evaluations.first { it.code == "passage_palier" }.amount)
        assertEquals(-1_000_000L, evaluations.first { it.code == "sibling_fixed" }.amount)
        assertEquals(-3_300_000L, evaluations.first { it.code == "full_annual" }.amount)
        assertEquals(-3_300_000L, evaluations.first { it.code == "highest_average" }.amount)
        assertEquals(-1_650_000L, evaluations.first { it.code == "seniority_5y" }.amount)
        assertEquals(-10_250_000L, total)
        // Net tuition after discounts.
        val net = (33_000_000L + total).coerceAtLeast(0L)
        assertEquals(22_750_000L, net)
    }

    @Test
    fun `scenario discount_engine_sibling_only - single rule case`() {
        val params = EvaluateAllDiscountsParams(
            grossTuition = 20_000_000L,    // 200,000 DZD
            previousGradeLevel = null,
            currentGradeLevel = "2ap",
            childIndex = 2,
            paymentPlan = PaymentPlan.TRANCHES,
            paymentDate = "2026-09-15T00:00:00Z",  // after June 30
            academicYearStartYear = 2026,
            academicYearStart = "2026-09-15T00:00:00Z",
            enrollmentDate = "2026-09-01T00:00:00Z",
            previousRank = null,
        )
        val evaluations = evaluateAllSystemDiscounts(params)
        val total = sumDiscounts(evaluations)
        assertEquals(1, evaluations.size)
        assertEquals("sibling_fixed", evaluations[0].code)
        assertEquals(-500_000L, evaluations[0].amount)
        assertEquals(-500_000L, total)
        val net = (20_000_000L + total).coerceAtLeast(0L)
        assertEquals(19_500_000L, net)
    }

    @Test
    fun `scenario unknown_category_does_not_crash - R2 fix`() {
        // CANONICAL-FINANCIAL-LOGIC.md §2.1 — fromCode MUST be total.
        // The previous implementation threw IllegalArgumentException on
        // parent_credit, therapy_*, second_apron, pending_clearance, unpaid.
        // This made Android pull-sync crash on any desktop-originated
        // parent_credit / therapy / pending_clearance row.
        val resolved = PaymentCategory.fromCode("parent_credit")
        assertEquals(PaymentCategory.PARENT_CREDIT, resolved)
        val therapyPsy = PaymentCategory.fromCode("therapy_psychology")
        assertEquals(PaymentCategory.THERAPY_PSYCHOLOGY, therapyPsy)
        val therapySpeech = PaymentCategory.fromCode("therapy_speech")
        assertEquals(PaymentCategory.THERAPY_SPEECH, therapySpeech)
        val secondApron = PaymentCategory.fromCode("second_apron")
        assertEquals(PaymentCategory.SECOND_APRON, secondApron)
        // Unknown codes fall back to OTHER — never throw.
        val unknown = PaymentCategory.fromCode("unknown_future_category")
        assertEquals(PaymentCategory.OTHER, unknown)

        // PaymentStatus — also total (nullable on unknown).
        assertEquals(PaymentStatus.PENDING_CLEARANCE, PaymentStatus.fromCode("pending_clearance"))
        assertEquals(PaymentStatus.UNPAID, PaymentStatus.fromCode("unpaid"))
        // Unknown code → null (callers can fall back to a default).
        assertNull(PaymentStatus.fromCode("unknown_future_status"))

        // PaymentPlan.
        assertEquals(PaymentPlan.FULL_ANNUAL, PaymentPlan.fromCode("full_annual"))
        assertEquals(PaymentPlan.TRANCHES, PaymentPlan.fromCode("tranches"))
        assertEquals(PaymentPlan.TRANCHES, PaymentPlan.fromCode(null))
        assertEquals(PaymentPlan.TRANCHES, PaymentPlan.fromCode("unknown"))
    }

    @Test
    fun `scenario unallocatedCredit rolls up - INV-3`() {
        val now = parseIsoInstantSafe("2026-09-20T00:00:00Z")
        val creditAccount = "parent:par-001:category:parent_credit"
        val tuitionAccount = "parent:par-001:category:tuition:student:stu-001"

        // Tuition charge of 100,000 DZD.
        val charge = createChargeEntry(
            tenantId = "00000000-0000-0000-0000-000000000001",
            parentId = "par-001", studentId = "stu-001",
            category = PaymentCategory.TUITION, amount = 10_000_000L,
            sourceType = LedgerSourceType.INSTALLMENT, sourceId = "ins-001",
            actorId = "system", actorName = "System",
            description = "Scolarité Tranche 1",
            at = parseIsoInstantSafe("2026-09-15T00:00:00Z"),
        )
        // Parent credit of 50,000 DZD.
        val credit = createAdjustmentEntry(
            tenantId = "00000000-0000-0000-0000-000000000001",
            parentId = "par-001", studentId = null,  // parent-scoped
            category = PaymentCategory.PARENT_CREDIT,
            amount = -5_000_000L,
            sourceId = "adj-001", actorId = "usr-001", actorName = "Agent comptoir",
            reason = "Crédit parent (trop-perçu)",
            at = now,
        )
        val entries = listOf(charge, credit)

        // Per-account: tuition account shows charged amount + 0 unallocatedCredit.
        val tuitionBal = LedgerEngine.computeAccountBalance(entries, tuitionAccount, now)
        assertEquals(10_000_000L, tuitionBal.balance)
        assertEquals(0L, tuitionBal.unallocatedCredit)

        // Per-account: parent_credit account shows -5M balance + -5M unallocatedCredit.
        val creditBal = LedgerEngine.computeAccountBalance(entries, creditAccount, now)
        assertEquals(-5_000_000L, creditBal.balance)
        assertEquals(-5_000_000L, creditBal.unallocatedCredit)

        // Parent summary rolls them up: totalUnallocatedCredit = -5M.
        val summary = LedgerEngine.computeParentSummary(entries, "par-001", "Test", emptyMap(), now)
        assertEquals(-5_000_000L, summary.totalUnallocatedCredit)
        // Total outstanding = tuition balance (10M) + credit balance (-5M) = 5M
        assertEquals(5_000_000L, summary.totalOutstanding)
    }

    private fun assertNull(actual: Any?) {
        org.junit.Assert.assertNull(actual)
    }
}
