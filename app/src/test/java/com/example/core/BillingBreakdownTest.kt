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

    /* ============================================================ */
    /*  T-168 — parity corpus (identical vectors to the TS engines)  */
    /* ============================================================ */

private val kids2 = listOf(
    BillingChildInfo(id = "s1", displayName = "Sara BENALI", gradeLevelLabel = "3AP"),
    BillingChildInfo(id = "s2", displayName = "Yanis BENALI", gradeLevelLabel = "4AM"),
)

private val shoppingListCharges = listOf(
    charge(id = "c-t1", studentId = "s1", amount = 28_500_000L, category = PaymentCategory.TUITION),
    charge(id = "c-t2", studentId = "s2", amount = 28_500_000L, category = PaymentCategory.TUITION),
    charge(id = "c-tr1", studentId = "s1", amount = 4_500_000L, category = PaymentCategory.TRANSPORT),
    charge(id = "c-tr2", studentId = "s2", amount = 4_500_000L, category = PaymentCategory.TRANSPORT),
    charge(
        id = "c-ins",
        studentId = null,
        amount = 4_000_000L,
        category = PaymentCategory.OTHER,
        description = "Frais d'inscription (family-level)",
    ),
)

@Test
fun `t168 - accounts for every dinar - sum byChild plus unattributed equals totalBilled 700k`() {
    val bd = parentBillingBreakdown(
        ledgerEntries = shoppingListCharges,
        installments = emptyList(),
        clearedPaidTotal = 0L,
        children = kids2,
    )
    assertEquals(70_000_000L, bd.totalBilled) // 700 000 DZD in centimes
    assertEquals(listOf(33_000_000L, 33_000_000L), bd.byChild.map { it.billedTotal })
    assertEquals(4_000_000L, bd.unattributedTotal)
    assertEquals(
        bd.totalBilled,
        bd.byChild.sumOf { it.billedTotal } + bd.unattributedTotal,
    )
}

@Test
fun `t168 - per-service share pct and child attribution match the TS engines`() {
    val bd = parentBillingBreakdown(
        ledgerEntries = shoppingListCharges,
        installments = emptyList(),
        clearedPaidTotal = 0L,
        children = kids2,
    )
    val tuition = bd.byService.first { it.category == PaymentCategory.TUITION }
    val transport = bd.byService.first { it.category == PaymentCategory.TRANSPORT }
    val other = bd.byService.first { it.category == PaymentCategory.OTHER }
    assertEquals(57_000_000L, tuition.amount)
    assertEquals(81, tuition.sharePct) // 570/700 = 81.4 → 81 (Math.round)
    assertEquals(
        listOf(
            ServiceChildAttribution("s1", "Sara BENALI", 28_500_000L),
            ServiceChildAttribution("s2", "Yanis BENALI", 28_500_000L),
        ),
        tuition.childAttribution,
    )
    assertEquals(13, transport.sharePct) // 90/700 = 12.857 → 13 (rounds like TS)
    assertEquals(
        listOf(ServiceChildAttribution(null, "Famille", 4_000_000L)),
        other.childAttribution,
    )
    assertEquals(100, bd.byService.sumOf { it.sharePct })
}

@Test
fun `t168 - single-child family owns family-level rows`() {
    val bd = parentBillingBreakdown(
        ledgerEntries = listOf(
            charge(id = "c-t", studentId = "s1", amount = 28_500_000L, category = PaymentCategory.TUITION),
            charge(id = "c-ins", studentId = null, amount = 4_000_000L, category = PaymentCategory.OTHER),
        ),
        installments = emptyList(),
        clearedPaidTotal = 0L,
        children = kids2.take(1),
    )
    assertEquals(32_500_000L, bd.byChild[0].billedTotal) // 325 000 DZD
    assertTrue(bd.unattributedItems.isEmpty())
}

@Test
fun `t168 - adjustment-aware reconciliation balances to the server figure`() {
    val adjustments = listOf(
        BillingAdjustment("adj-1", -7_100_000L, "Remise fratrie", "2025-09-02T10:00:00Z", "usr-admin"),
        BillingAdjustment("adj-2", 2_000_000L, "Majoration transport", "2025-09-03T10:00:00Z", "usr-admin"),
    )
    val bd = parentBillingBreakdown(
        ledgerEntries = listOf(charge(id = "c-1", studentId = "s1", amount = 28_500_000L)),
        installments = emptyList(),
        clearedPaidTotal = 9_500_000L,
        pendingPaidTotal = 3_000_000L,
        children = kids2.take(1),
        adjustments = adjustments,
        serverOutstanding = 10_900_000L,
    )
    val r = bd.reconciliation
    assertEquals(28_500_000L, r.grossBilled)
    assertEquals(7_100_000L, r.adjustmentsCredit)
    assertEquals(2_000_000L, r.adjustmentsDebit)
    assertEquals(23_400_000L, r.netDue) // 234 000 DZD
    assertEquals(10_900_000L, r.derivedRemaining)
    assertEquals(10_900_000L, r.serverOutstanding)
    assertEquals(0L, r.bridge)
    assertFalse(r.hasBridge)
}

@Test
fun `t168 - bridge surfaces when the server balance has invisible items`() {
    val bd = parentBillingBreakdown(
        ledgerEntries = listOf(charge(id = "c-1", studentId = "s1", amount = 28_500_000L)),
        installments = emptyList(),
        clearedPaidTotal = 12_500_000L,
        children = kids2.take(1),
        serverOutstanding = 7_900_000L, // 10 000 DZD refund server-side only
    )
    assertEquals(16_000_000L, bd.reconciliation.derivedRemaining)
    assertEquals(-8_100_000L, bd.reconciliation.bridge)
    assertTrue(bd.reconciliation.hasBridge)
}

@Test
fun `t168 - detects the plus-minus re-import flip-flop as reversal pairs`() {
    val classified = classifyAdjustmentHistory(
        listOf(
            BillingAdjustment("adj-c1", 5_000_000L, "", "2025-09-05T09:00:00Z", "system"),
            BillingAdjustment("adj-d2", -7_100_000L, "", "2025-09-06T09:00:00Z", "system"),
            BillingAdjustment("adj-d1", 7_100_000L, "", "2025-09-05T10:00:00Z", "system"),
            BillingAdjustment("adj-c2", -5_000_000L, "", "2025-09-06T10:00:00Z", "system"),
        ),
    )
    val byId = classified.associateBy { it.id }
    assertEquals("adj-d2", byId.getValue("adj-d1").pairedWithId)
    assertEquals("adj-d1", byId.getValue("adj-d2").pairedWithId)
    assertEquals("adj-c2", byId.getValue("adj-c1").pairedWithId)
    classified.forEach {
        assertEquals(AdjustmentProvenance.REVERSAL_PAIR, it.provenance)
        assertEquals("Contrepassation", it.provenanceLabel)
        assertTrue(it.meaningLabel.contains("nul"))
    }
}

@Test
fun `t168 - documented vs undocumented provenance labels`() {
    val documented = classifyAdjustmentHistory(
        listOf(BillingAdjustment("adj-1", -7_100_000L, "Remise fratrie (3 enfants)", "2025-09-02T10:00:00Z", "usr-admin")),
    )
    assertEquals(AdjustmentProvenance.DOCUMENTED, documented[0].provenance)
    assertEquals("Documenté", documented[0].provenanceLabel)
    assertTrue(documented[0].meaningLabel.contains("réduit le solde dû"))

    val undocumented = classifyAdjustmentHistory(
        listOf(BillingAdjustment("adj-2", -5_000_000L, "   ", "2025-09-02T10:00:00Z", "system")),
    )
    assertEquals(AdjustmentProvenance.UNDOCUMENTED, undocumented[0].provenance)
    assertEquals("Non documenté", undocumented[0].provenanceLabel)
    assertTrue(undocumented[0].meaningLabel.contains("auditer"))
}

@Test
fun `t168 - never pairs same-sign entries and skips zero amounts`() {
    val classified = classifyAdjustmentHistory(
        listOf(
            BillingAdjustment("adj-a", 5_000_000L, "Note A", "2025-09-01T09:00:00Z", "u1"),
            BillingAdjustment("adj-b", 5_000_000L, "Note B", "2025-09-02T09:00:00Z", "u1"),
            BillingAdjustment("adj-c", -5_000_000L, "Remise", "2025-09-03T09:00:00Z", "u1"),
            BillingAdjustment("adj-z", 0L, "", "2025-09-04T09:00:00Z", "u1"),
        ),
    )
    val byId = classified.associateBy { it.id }
    assertEquals(AdjustmentProvenance.REVERSAL_PAIR, byId.getValue("adj-a").provenance)
    assertEquals("adj-c", byId.getValue("adj-a").pairedWithId)
    assertEquals(AdjustmentProvenance.DOCUMENTED, byId.getValue("adj-b").provenance)
    assertNull(byId.getValue("adj-b").pairedWithId)
    assertNull(byId.getValue("adj-z").pairedWithId)
    assertEquals(listOf("adj-a", "adj-b", "adj-c", "adj-z"), classified.map { it.id })
}
}
