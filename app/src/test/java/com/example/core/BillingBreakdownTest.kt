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

/* ============================================================ */
/*  T-520 / BUSINESS-111 — the as-of pair (T-517 Kotlin mirror)  */
/*                                                                */
/*  Pins the SAME contract as the desktop suite                   */
/*  src/tests/domain/payment/t-517-billing-asof-recon.test.ts:    */
/*  the billing sums apply the canonical ledger replay's OWN      */
/*  date cutoff, and payments recorded against FUTURE periods     */
/*  (the T-502/DATA-062 import bucket anchors: 2V → Dec 15,       */
/*  v3 → Mar 15) are split into `futureRecordedPaid` — surfaced,  */
/*  never counted as collected-to-date. The scenarios mirror the  */
/*  owner's issue report (A–I); the reference fixture is the LIVE */
/*  NACEF family shape (docs/recovery/t-517-live-verification.md):*/
/*  four children, per-child Devis 365 000 / 345 000 / 290 000    */
/*  / 310 000, per-child payments V2 (Sept 15, past) + T1 (Sept   */
/*  15, past, transport) + V2_ALT (Dec 15, future) + V3 (Mar 15,  */
/*  future), evaluation date 2026-10-13.                          */
/* ============================================================ */

private val t520Now: java.time.Instant = java.time.Instant.parse("2026-10-13T12:00:00Z")

/** DZD → centimes (the engine's unit). */
private fun dzd(d: Long) = d * 100L

private data class T520Child(
    val id: String,
    val first: String,
    val devis: Long, // DZD
    val v2: Long,    // DZD — Sept 15 bucket (past at t520Now)
    val v3: Long,    // DZD — Mar 15 bucket (future at t520Now)
)

private val t520Children = listOf(
    T520Child("stu-sai", "SAIFISSLEM", 365_000, 100_000, 145_000),
    T520Child("stu-hab", "HABIBELLAH", 345_000, 92_000, 133_000),
    T520Child("stu-elm", "ELMOATSEM", 310_000, 99_000, 91_000),
    T520Child("stu-hib", "HIBERHMAN", 290_000, 70_000, 100_000),
)

/** T1 transport versement per child (20 000 DZD, Sept 15 — past). */
private val T520_T1_TRANSPORT = 20_000L

/** V2_ALT per child (100 000 DZD, Dec 15 2026 — FUTURE at t520Now). */
private val T520_V2_ALT = 100_000L

private val t520NetDue = t520Children.sumOf { it.devis }                     // 1 310 000
private val t520PastPaid = t520Children.sumOf { it.v2 } + 4 * T520_T1_TRANSPORT // 441 000
private val t520FuturePaid = 4 * T520_V2_ALT + t520Children.sumOf { it.v3 }     // 869 000

private val t520Kids = t520Children.map { BillingChildInfo(it.id, it.first, "2026-2027") }

/** The 16 NACEF-shaped payment rows (4 buckets × 4 children). */
private fun t520PaymentRows(): List<PaymentSumRow> = buildList {
    for (c in t520Children) {
        add(PaymentSumRow(PaymentStatus.PAID, "2026-09-15T00:00:00Z", dzd(c.v2)))
        add(PaymentSumRow(PaymentStatus.PAID, "2026-09-15T00:00:00Z", dzd(T520_T1_TRANSPORT)))
        add(PaymentSumRow(PaymentStatus.PAID, "2026-12-15T00:00:00Z", dzd(T520_V2_ALT)))
        add(PaymentSumRow(PaymentStatus.PAID, "2027-03-15T00:00:00Z", dzd(c.v3)))
    }
}

private fun t520Charge(studentId: String, amountDzd: Long, category: PaymentCategory = PaymentCategory.TUITION): LedgerEntry =
    LedgerEntry(
        id = "t520-devis-$studentId",
        tenantId = "tenant-1",
        accountId = "parent:p-1:category:${category.name.lowercase()}:student:$studentId",
        parentId = "p-1",
        studentId = studentId,
        category = category,
        amount = dzd(amountDzd),
        type = LedgerEntryType.CHARGE,
        sourceType = LedgerSourceType.BULK_IMPORT,
        sourceId = "run-t520",
        method = null,
        receiptNumber = null,
        paymentStatus = null,
        reversesId = null,
        description = "Devis annuel (import Excel run run-t520)",
        actorId = "system",
        actorName = "System",
        at = "2026-09-15T00:00:00Z",
        metadata = emptyMap(),
    )

private fun t520Charges(): List<LedgerEntry> = t520Children.map { t520Charge(it.id, it.devis) }

/**
 * The ViewModel seam pattern (ParentDetailViewModel.recomputeBilling):
 * split at the sums, feed the breakdown the AS-OF figure + the
 * future-recorded term.
 */
private fun t520Breakdown(
    rows: List<PaymentSumRow>,
    charges: List<LedgerEntry> = t520Charges(),
    kids: List<BillingChildInfo> = t520Kids,
    now: java.time.Instant = t520Now,
    installments: List<BillingInstallmentRow> = emptyList(),
    adjustments: List<BillingAdjustment> = emptyList(),
    serverOutstanding: Long? = null,
): ParentBillingBreakdown {
    val cleared = sumPaidPaymentsAsOf(rows, now)
    val future = sumFutureRecordedPaidPayments(rows, now)
    return parentBillingBreakdown(
        ledgerEntries = charges,
        installments = installments,
        clearedPaidTotal = cleared,
        children = kids,
        academicYearOverride = "2026-2027",
        adjustments = adjustments,
        futureRecordedPaidTotal = future,
        serverOutstanding = serverOutstanding,
    )
}

// ─── The sums pair — the partition invariant (scenario E) ────────────

@Test
fun `t520 - partitions sumPaidPayments exactly asOf plus futureRecorded equals total (scenario E)`() {
    val rows = t520PaymentRows()
    val total = sumPaidPayments(rows)
    val asOf = sumPaidPaymentsAsOf(rows, t520Now)
    val future = sumFutureRecordedPaidPayments(rows, t520Now)
    assertEquals(dzd(441_000), asOf) // 441 000 DZD
    assertEquals(dzd(869_000), future) // 869 000 DZD
    assertEquals(dzd(1_310_000), total)
    assertEquals(total, asOf + future) // no dinar created or lost
}

@Test
fun `t520 - is status-strict pending and refunded never enter either side`() {
    val rows = listOf(
        PaymentSumRow(PaymentStatus.PAID, "2026-09-15T00:00:00Z", dzd(50_000)),
        PaymentSumRow(PaymentStatus.PENDING, "2026-09-15T00:00:00Z", dzd(30_000)),
        PaymentSumRow(PaymentStatus.REFUNDED, "2027-01-01T00:00:00Z", dzd(20_000)),
    )
    assertEquals(dzd(50_000), sumPaidPaymentsAsOf(rows, t520Now))
    assertEquals(0L, sumFutureRecordedPaidPayments(rows, t520Now))
}

@Test
fun `t520 - is conservative on missing dates blank or unparseable collectedAt counts as collected`() {
    val rows = listOf(
        PaymentSumRow(PaymentStatus.PAID, "", dzd(70_000)),
        PaymentSumRow(PaymentStatus.PAID, "not-a-date", dzd(10_000)),
    )
    assertEquals(dzd(80_000), sumPaidPaymentsAsOf(rows, t520Now))
    assertEquals(0L, sumFutureRecordedPaidPayments(rows, t520Now))
}

@Test
fun `t520 - treats a payment collected exactly AT the evaluation instant as collected`() {
    val rows = listOf(
        PaymentSumRow(PaymentStatus.PAID, "2026-10-13T12:00:00Z", dzd(10_000)), // == NOW
        PaymentSumRow(PaymentStatus.PAID, "2026-10-13T12:00:01Z", dzd(5_000)),  // 1s later
    )
    assertEquals(dzd(10_000), sumPaidPaymentsAsOf(rows, t520Now)) // inclusive
    assertEquals(dzd(5_000), sumFutureRecordedPaidPayments(rows, t520Now))
}

// ─── The reconciliation — scenarios A / B / C / C2 / D / F / H ────────

@Test
fun `t520 - scenario A fully paid invoice all payments past-dated 100 pct coverage 0 remaining no bridge`() {
    val rows = t520Children.map {
        PaymentSumRow(PaymentStatus.PAID, "2026-09-20T00:00:00Z", dzd(it.devis))
    }
    val bd = t520Breakdown(rows, serverOutstanding = 0L)
    val r = bd.reconciliation
    assertEquals(dzd(t520NetDue), r.netDue)
    assertEquals(dzd(t520NetDue), r.clearedPaid)
    assertEquals(0L, r.futureRecordedPaid)
    assertEquals(0L, r.derivedRemaining)
    assertEquals(0L, r.bridge)
    assertFalse(r.hasBridge)
}

@Test
fun `t520 - scenario B partially paid 441000 of 1310000 no future rows coverage 34 pct never 100`() {
    val pastOnly = t520PaymentRows().filter {
        java.time.Instant.parse(it.collectedAt) <= t520Now
    }
    val bd = t520Breakdown(pastOnly, serverOutstanding = dzd(t520NetDue - t520PastPaid))
    val r = bd.reconciliation
    assertEquals(dzd(441_000), r.clearedPaid)
    assertEquals(dzd(869_000), r.derivedRemaining)
    assertEquals(0L, r.bridge)
    val pct = kotlin.math.min(100, Math.round(r.clearedPaid * 100.0 / r.netDue).toInt())
    assertEquals(34, pct) // ≈ 33,66 % — the coverage must NOT show 100 %
    org.junit.Assert.assertTrue(pct < 100)
}

@Test
fun `t520 - scenario C the NACEF case future-dated payments excluded from cleared and surfaced bridge collapses to 0`() {
    val bd = t520Breakdown(t520PaymentRows(), serverOutstanding = dzd(869_000))
    val r = bd.reconciliation
    // BEFORE the T-517/T-520 mirror: clearedPaid was 1 310 000 (100 %
    // coverage) beside a 441 000 as-of summary and an unexplained
    // +869 000 bridge.
    assertEquals(dzd(441_000), r.clearedPaid)
    assertEquals(dzd(869_000), r.futureRecordedPaid)
    assertEquals(dzd(1_310_000), r.netDue)
    assertEquals(dzd(869_000), r.derivedRemaining)
    assertEquals(0L, r.bridge) // the mystery bridge is GONE
    assertFalse(r.hasBridge)
    // The coverage percentage agrees with the summary figures.
    val pct = kotlin.math.min(100, Math.round(r.clearedPaid * 100.0 / r.netDue).toInt())
    assertEquals(34, pct)
    // The partition still explains every recorded dinar.
    assertEquals(dzd(1_310_000), r.clearedPaid + r.futureRecordedPaid)
}

@Test
fun `t520 - scenario C2 advancing now past Dec 15 moves V2ALT into cleared deterministically`() {
    val later = java.time.Instant.parse("2026-12-31T12:00:00Z")
    val bd = t520Breakdown(t520PaymentRows(), now = later)
    val expectedCleared = dzd(t520PastPaid + 4 * T520_V2_ALT) // 841 000 DZD
    assertEquals(expectedCleared, bd.reconciliation.clearedPaid)
    // Only the March anchors remain future: Σ v3 = 469 000 DZD.
    assertEquals(dzd(t520Children.sumOf { it.v3 }), bd.reconciliation.futureRecordedPaid)
}

@Test
fun `t520 - scenario D a scheduled installment not collected is never money received`() {
    val kids = listOf(BillingChildInfo("stu-1", "Sara BENALI", "3AP"))
    val charges = listOf(t520Charge("stu-1", 300_000))
    val rows = listOf(PaymentSumRow(PaymentStatus.PAID, "2026-09-16T00:00:00Z", dzd(120_000)))

    // (a) REAL unpaid rows are authoritative (the server-side waterfall
    // state renders verbatim — never re-allocated client-side); the
    // reconciliation still counts ONLY the cleared payment as received.
    val realRows = listOf(
        BillingInstallmentRow("ins-t1", "stu-1", PaymentCategory.TUITION, "Tranche 1 — Scolarité", dzd(120_000), 0L, 0L, "2026-09-15", "unpaid"),
        BillingInstallmentRow("ins-t2", "stu-1", PaymentCategory.TUITION, "Tranche 2 — Scolarité", dzd(90_000), 0L, 0L, "2026-12-15", "unpaid"),
        BillingInstallmentRow("ins-t3", "stu-1", PaymentCategory.TUITION, "Tranche 3 — Scolarité", dzd(90_000), 0L, 0L, "2027-03-15", "unpaid"),
    )
    val withRealRows = t520Breakdown(rows, charges = charges, kids = kids, installments = realRows)
    assertEquals(dzd(120_000), withRealRows.reconciliation.clearedPaid) // money received
    assertEquals(0L, withRealRows.reconciliation.futureRecordedPaid)
    assertEquals(dzd(180_000), withRealRows.reconciliation.derivedRemaining)
    assertEquals(dzd(300_000), withRealRows.byChild[0].tranchesRemaining) // the rows' own state, verbatim
    assertFalse(withRealRows.byChild[0].isSyntheticSchedule)

    // (b) The SYNTHETIC path (no physical rows): the display waterfall
    // fills only the official tranches with the CLEARED payment — the two
    // future-due tranches are obligations, never money received.
    val synthetic = t520Breakdown(rows, charges = charges, kids = kids)
    assertEquals(dzd(120_000), synthetic.reconciliation.clearedPaid)
    assertTrue(synthetic.byChild[0].isSyntheticSchedule)
    assertEquals(dzd(180_000), synthetic.byChild[0].tranchesRemaining) // 300k − 120k cleared
    val t1 = synthetic.byChild[0].tranches[0]
    assertEquals(dzd(120_000), t1.amountPaid) // the cleared money landed on T1
    assertEquals(0L, t1.remaining)
    assertTrue(synthetic.byChild[0].tranches.drop(1).all { it.amountPaid == 0L })
}

@Test
fun `t520 - scenario F four-child family per-child charges reconcile with the family aggregate`() {
    val rows = t520PaymentRows()
    val bd = t520Breakdown(rows)
    // Per-child billed totals match the Devis exactly (no family-level
    // duplication, no per-child multiplication of family rows).
    assertEquals(t520Children.map { dzd(it.devis) }, bd.byChild.map { it.billedTotal })
    assertEquals(dzd(t520NetDue), bd.totalBilled)
    assertEquals(dzd(t520PastPaid), bd.totalClearedPaid)
    assertEquals(dzd(t520FuturePaid), bd.totalFutureRecordedPaid)
    // The per-child payments (each child's four buckets) each appear once.
    assertEquals(16, rows.size)
    assertEquals(dzd(t520NetDue), bd.reconciliation.clearedPaid + bd.reconciliation.futureRecordedPaid)
}

@Test
fun `t520 - scenario H adjustments coexist with the as-of cutoff netDue adjusts clearedPaid stays as-of`() {
    val kids = listOf(BillingChildInfo("stu-1", "Sara BENALI", "3AP"))
    val charges = listOf(t520Charge("stu-1", 300_000))
    val adjustments = listOf(
        BillingAdjustment("adj-1", dzd(-30_000), "Remise fratrie", "2026-09-02T10:00:00Z", "usr-admin"),
    )
    val rows = listOf(
        PaymentSumRow(PaymentStatus.PAID, "2026-09-20T00:00:00Z", dzd(100_000)),
        PaymentSumRow(PaymentStatus.PAID, "2027-01-05T00:00:00Z", dzd(170_000)), // future
    )
    val bd = t520Breakdown(rows, charges = charges, kids = kids, adjustments = adjustments)
    assertEquals(dzd(270_000), bd.reconciliation.netDue)
    assertEquals(dzd(100_000), bd.reconciliation.clearedPaid)
    assertEquals(dzd(170_000), bd.reconciliation.futureRecordedPaid)
    assertEquals(dzd(170_000), bd.reconciliation.derivedRemaining)
}

// ─── Scenarios G + I — the canonical engine agreement ─────────────────

/** The NACEF ledger: 4 tuition charges + payment entries mirroring the
 *  payments (past entries Sept 15; the 8 future entries carry the same
 *  future anchors the T-502 remediation wrote). */
private fun t520Ledger(): List<LedgerEntry> = buildList {
    addAll(t520Charges())
    for (c in t520Children) {
        fun payEntry(suffix: String, category: PaymentCategory, amountDzd: Long, at: String) = LedgerEntry(
            id = "t520-${c.id}-$suffix",
            tenantId = "tenant-1",
            accountId = "parent:p-1:category:${category.name.lowercase()}:student:${c.id}",
            parentId = "p-1",
            studentId = c.id,
            category = category,
            amount = -dzd(amountDzd),
            type = LedgerEntryType.PAYMENT,
            sourceType = LedgerSourceType.BULK_IMPORT,
            sourceId = "run-t520",
            method = PaymentMethod.CASH,
            receiptNumber = "IMP-${c.id}-$suffix",
            paymentStatus = PaymentStatus.PAID,
            reversesId = null,
            description = "Versement (import Excel run run-t520)",
            actorId = "system",
            actorName = "System",
            at = at,
            metadata = emptyMap(),
        )
        add(payEntry("V2", PaymentCategory.TUITION, c.v2, "2026-09-15T00:00:00Z"))
        add(payEntry("T1", PaymentCategory.TRANSPORT, T520_T1_TRANSPORT, "2026-09-15T00:00:00Z"))
        add(payEntry("V2_ALT", PaymentCategory.TUITION, T520_V2_ALT, "2026-12-15T00:00:00Z"))
        add(payEntry("V3", PaymentCategory.TUITION, c.v3, "2027-03-15T00:00:00Z"))
    }
}

@Test
fun `t520 - scenario I recon clearedPaid equals the replay paid and derivedRemaining equals its outstanding`() {
    val entries = t520Ledger()
    val summary = LedgerEngine.computeParentSummary(
        entries, "p-1", "Famille NACEF",
        LedgerEngine.buildOverdueDueDateMap(entries),
        t520Now,
    )
    // The canonical as-of replay: future-dated payment entries excluded.
    assertEquals(dzd(869_000), summary.totalOutstanding)
    assertEquals(dzd(441_000), summary.totalPaid)

    val bd = t520Breakdown(
        t520PaymentRows(),
        charges = entries, // the breakdown filters CHARGE rows internally
        serverOutstanding = summary.totalOutstanding,
    )
    val r = bd.reconciliation
    // THE CONSISTENCY CONTRACT (the surface's two paths agree):
    assertEquals(summary.totalPaid, r.clearedPaid)                 // 441 000 === 441 000
    assertEquals(summary.totalOutstanding, r.derivedRemaining)    // 869 000 === 869 000
    assertEquals(0L, r.bridge)
    assertFalse(r.hasBridge)
}

@Test
fun `t520 - scenario G the overdue-vs-outstanding offset is the stranded transport credit`() {
    val entries = t520Ledger()
    // The stranded-credit shape: the four per-child transport accounts
    // hold the T1 versements with NO transport charges (the Devis is a
    // single tuition-category charge that includes the transport
    // component).
    val summary = LedgerEngine.computeParentSummary(
        entries, "p-1", "Famille NACEF",
        LedgerEngine.buildOverdueDueDateMap(entries),
        t520Now,
    )
    // Four tuition accounts positive (Σ 949 000 overdue) + four transport
    // accounts at −20 000 each (−80 000) → net 869 000 outstanding.
    val positiveAccounts = summary.accounts.filter { it.balance > 0L }
    val creditAccounts = summary.accounts.filter { it.balance < 0L }
    assertEquals(4, positiveAccounts.size)
    assertEquals(4, creditAccounts.size)
    assertEquals(dzd(949_000), summary.totalOverdue)
    assertEquals(dzd(869_000), summary.totalOutstanding)
    assertEquals(dzd(80_000), summary.totalOverdue - summary.totalOutstanding)
    // The 80 000 is exactly the stranded transport credit — the amount
    // the modal's T-517 note explains inline (« avances non affectées »).
    assertEquals(dzd(80_000), creditAccounts.sumOf { kotlin.math.abs(it.balance) })
}
}
