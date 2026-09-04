package com.example.core

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for the canonical Billing Breakdown (T-167) — Kotlin mirror of
 * the desktop suite `src/tests/domain/payment/billing-breakdown.test.ts`
 * (T-164) and the website port `src/lib/canonical/billing-breakdown.test.ts`
 * (T-166). All three are verified against the SAME vectors so the numbers
 * are byte-identical across platforms (amounts here are CENTIMES).
 *
 * Headline scenario (owner-reported): 285 000 DZD imported annual charge,
 * 125 000 DZD cleared payments, no physical tranches → T1 (114 000) fully
 * covered, T2 (85 500) 11 000 covered / 74 500 remaining, T3 (85 500)
 * untouched, Σ remaining = 160 000 DZD.
 */
class BillingBreakdownTest {

    private val child = BillingChildInfo(
        id = "stu-1",
        displayName = "Sara BENALI",
        gradeLevelLabel = "3AP",
    )

    private fun charge(
        id: String = "led-charge-1",
        studentId: String? = "stu-1",
        amount: Long = 28_500_000L, // 285 000 DZD in centimes
        category: PaymentCategory = PaymentCategory.TUITION,
        description: String = "Devis annuel (import Excel run run-1)",
        metadata: Map<String, Any?> = emptyMap(),
    ) = LedgerEntry(
        id = id,
        tenantId = "tenant-1",
        accountId = "parent:p-1:category:tuition",
        parentId = "p-1",
        studentId = studentId,
        category = category,
        amount = amount,
        type = LedgerEntryType.CHARGE,
        sourceType = LedgerSourceType.BULK_IMPORT,
        sourceId = "run-1",
        method = null,
        receiptNumber = null,
        paymentStatus = null,
        reversesId = null,
        description = description,
        actorId = "system",
        actorName = "System",
        at = "2025-08-11T22:22:37Z",
        metadata = metadata,
    )

    private fun installment(
        id: String = "ins-1",
        studentId: String? = "stu-1",
        amountDue: Long = 11_400_000L, // 114 000 DZD
        amountPaid: Long = 0L,
        amountPending: Long = 0L,
        dueDate: String = "2025-09-15",
        status: String = "unpaid",
        label: String = "Tranche 1 — Scolarité",
    ) = BillingInstallmentRow(
        id = id,
        studentId = studentId,
        category = PaymentCategory.TUITION,
        label = label,
        amountDue = amountDue,
        amountPaid = amountPaid,
        amountPending = amountPending,
        dueDate = dueDate,
        status = status,
    )

    // ─── Headline scenario — synthetic schedule (import gap) ──────────────

    @Test fun `splits 285000 DZD into official 114000 85500 85500 tranches with conservation`() {
        val breakdown = parentBillingBreakdown(
            ledgerEntries = listOf(charge()),
            installments = emptyList(),
            clearedPaidTotal = 0L,
            children = listOf(child),
        )
        assertEquals(28_500_000L, breakdown.totalBilled)
        assertTrue(breakdown.hasSyntheticTranches)
        val tranches = breakdown.byChild[0].tranches
        assertEquals(3, tranches.size)
        assertEquals(11_400_000L, tranches[0].amountDue)
        assertEquals(8_550_000L, tranches[1].amountDue)
        assertEquals(8_550_000L, tranches[2].amountDue)
        // Exact conservation.
        assertEquals(28_500_000L, tranches.sumOf { it.amountDue })
    }

    @Test fun `covers T1 fully and spills 11000 into T2 for 125000 cleared (remaining 160000)`() {
        val breakdown = parentBillingBreakdown(
            ledgerEntries = listOf(charge()),
            installments = emptyList(),
            clearedPaidTotal = 12_500_000L, // 125 000 DZD
            children = listOf(child),
        )
        val tranches = breakdown.byChild[0].tranches
        // T1: fully covered.
        assertEquals(11_400_000L, tranches[0].amountPaid)
        assertEquals(0L, tranches[0].remaining)
        assertEquals(TrancheDisplayStatus.PAID, tranches[0].status)
        // T2: 11 000 covered → 74 500 remaining.
        assertEquals(1_100_000L, tranches[1].amountPaid)
        assertEquals(7_450_000L, tranches[1].remaining)
        assertEquals(TrancheDisplayStatus.PARTIAL, tranches[1].status)
        // T3 untouched.
        assertEquals(0L, tranches[2].amountPaid)
        assertEquals(8_550_000L, tranches[2].remaining)
        assertEquals(TrancheDisplayStatus.UNPAID, tranches[2].status)
        // Σ remaining === 160 000 DZD.
        assertEquals(16_000_000L, tranches.sumOf { it.remaining })
        assertEquals(12_500_000L, breakdown.totalClearedPaid)
    }

    // ─── Real installments are authoritative ──────────────────────────────

    @Test fun `uses stored server-waterfall amounts verbatim (no client re-allocation)`() {
        val breakdown = parentBillingBreakdown(
            ledgerEntries = listOf(charge()),
            installments = listOf(
                installment(id = "ins-1", amountDue = 11_400_000L, amountPaid = 10_000_000L, status = "partial"),
                installment(id = "ins-2", label = "Tranche 2 — Scolarité", amountDue = 8_550_000L, dueDate = "2025-12-15"),
                installment(id = "ins-3", label = "Tranche 3 — Scolarité", amountDue = 8_550_000L, dueDate = "2026-03-15"),
            ),
            clearedPaidTotal = 10_000_000L,
            children = listOf(child),
        )
        assertFalse(breakdown.hasSyntheticTranches)
        val tranches = breakdown.byChild[0].tranches
        assertEquals(3, tranches.size)
        // From the DB row, not recomputed.
        assertEquals(10_000_000L, tranches[0].amountPaid)
        assertEquals(1_400_000L, tranches[0].remaining)
        // Σ remaining === 185 000 DZD (285 000 − 100 000).
        assertEquals(18_500_000L, tranches.sumOf { it.remaining })
    }

    @Test fun `honours INV4 remaining subtracts pending funds`() {
        val breakdown = parentBillingBreakdown(
            ledgerEntries = listOf(charge(amount = 11_400_000L)),
            installments = listOf(
                installment(amountDue = 11_400_000L, amountPaid = 4_000_000L, amountPending = 3_000_000L)
            ),
            clearedPaidTotal = 7_000_000L,
            children = listOf(child),
        )
        // 114 000 − 40 000 − 30 000 = 44 000 DZD.
        assertEquals(4_400_000L, breakdown.byChild[0].tranches[0].remaining)
    }

    // ─── Mixed families — no double counting ──────────────────────────────

    @Test fun `reserves real-installment money so cleared payments are not double counted`() {
        // Child A: real tranche with 100 000 DZD paid server-side.
        // Child B: charges, no tranches. Family cleared: 150 000 DZD.
        // B's synthetic waterfall must receive 50 000, not 150 000.
        val breakdown = parentBillingBreakdown(
            ledgerEntries = listOf(
                charge(id = "c-a", studentId = "stu-a", amount = 20_000_000L),
                charge(id = "c-b", studentId = "stu-b", amount = 10_000_000L),
            ),
            installments = listOf(
                installment(id = "ins-a1", studentId = "stu-a", amountDue = 20_000_000L, amountPaid = 10_000_000L, status = "partial")
            ),
            clearedPaidTotal = 15_000_000L,
            children = listOf(
                BillingChildInfo("stu-a", "A", "1AP"),
                BillingChildInfo("stu-b", "B", "2AP"),
            ),
        )
        val childA = breakdown.byChild.first { it.child.id == "stu-a" }
        val childB = breakdown.byChild.first { it.child.id == "stu-b" }
        assertFalse(childA.isSyntheticSchedule)
        assertEquals(10_000_000L, childA.tranches[0].amountPaid)
        assertTrue(childB.isSyntheticSchedule)
        // B's T1 = 40 % of 100 000 = 40 000 → covered by the 50 000 residual;
        // T2 (30 000) receives the last 10 000.
        assertEquals(4_000_000L, childB.tranches[0].amountPaid)
        assertEquals(TrancheDisplayStatus.PAID, childB.tranches[0].status)
        assertEquals(1_000_000L, childB.tranches[1].amountPaid)
        assertEquals(TrancheDisplayStatus.PARTIAL, childB.tranches[1].status)
    }

    @Test fun `attributes charges per child and consolidates per service`() {
        val breakdown = parentBillingBreakdown(
            ledgerEntries = listOf(
                charge(id = "c-a", studentId = "stu-a", amount = 20_000_000L, category = PaymentCategory.TUITION),
                charge(id = "c-b", studentId = "stu-b", amount = 3_000_000L, category = PaymentCategory.TRANSPORT),
            ),
            installments = listOf(
                installment(id = "ins-a", studentId = "stu-a", amountDue = 20_000_000L, amountPaid = 20_000_000L, status = "paid")
            ),
            clearedPaidTotal = 20_000_000L,
            children = listOf(
                BillingChildInfo("stu-a", "A", "1AP"),
                BillingChildInfo("stu-b", "B", "2AP"),
            ),
        )
        assertEquals(listOf(20_000_000L, 3_000_000L), breakdown.byChild.map { it.billedTotal })
        assertEquals(20_000_000L, breakdown.byService.first { it.category == PaymentCategory.TUITION }.amount)
        assertEquals(3_000_000L, breakdown.byService.first { it.category == PaymentCategory.TRANSPORT }.amount)
        // Canonical FR labels (desktop parity).
        assertEquals("Scolarité", breakdown.byService.first { it.category == PaymentCategory.TUITION }.label)
        assertEquals("Transport", breakdown.byService.first { it.category == PaymentCategory.TRANSPORT }.label)
    }

    // ─── Academic year ─────────────────────────────────────────────────────

    @Test fun `resolves academic year from metadata then description then default`() {
        assertEquals(
            "2026-2027",
            resolveBillingAcademicYear(listOf(charge(metadata = mapOf("academicYear" to "2026-2027")))),
        )
        assertEquals(
            "2025-2026",
            resolveBillingAcademicYear(listOf(charge(description = "Scolarité 2025-2026 (import)"))),
        )
        assertEquals("2025-2026", resolveBillingAcademicYear(emptyList()))
    }

    @Test fun `synthesizes against the resolved academic year calendar`() {
        val breakdown = parentBillingBreakdown(
            ledgerEntries = listOf(charge(metadata = mapOf("academicYear" to "2026-2027"))),
            installments = emptyList(),
            clearedPaidTotal = 0L,
            children = listOf(child),
        )
        assertEquals("2026-2027", breakdown.academicYear)
        assertTrue(breakdown.byChild[0].tranches[0].dueDate!!.startsWith("2026-09-15"))
    }

    // ─── Adjustment diagnostics ────────────────────────────────────────────

    @Test fun `labels negative as credit with the stored reason`() {
        val diag = describeAdjustment(-7_100_000L, "Remise fratrie (3 enfants)")
        assertEquals("credit", diag.kind)
        assertEquals("Crédit / Déduction", diag.badgeLabel)
        assertEquals("Remise fratrie (3 enfants)", diag.reasonLabel)
        assertFalse(diag.isDiagnosticFallback)
    }

    @Test fun `labels positive as debit discount reversal`() {
        val diag = describeAdjustment(7_100_000L, "Annulation de remise lors du ré-import")
        assertEquals("debit", diag.kind)
        assertEquals("Débit / Majoration", diag.badgeLabel)
    }

    @Test fun `substitutes the shared diagnostic when the reason is blank`() {
        val credit = describeAdjustment(-5_000_000L, "   ")
        assertTrue(credit.isDiagnosticFallback)
        assertTrue(credit.reasonLabel.contains("Déduction"))

        val debit = describeAdjustment(5_000_000L, null)
        assertTrue(debit.isDiagnosticFallback)
        assertTrue(debit.reasonLabel.contains("Régularisation"))
    }
}
