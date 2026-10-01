package com.example.core

import com.example.core.LedgerEntryType.PAYMENT
import com.example.core.LedgerSourceType.PAYMENT as LEDGER_SOURCE_PAYMENT
import com.example.domain.model.Installment
import com.example.domain.model.Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * T-456 (128th session) — the year-history engine mirror suite.
 *
 * Every fixture + expected value below is the DESKTOP'S OWN
 * `src/tests/domain/ledger/year-history.test.ts` (T-436) +
 * `src/tests/domain/ledger/t-442-year-breakdown.test.ts` (T-442/UI-323)
 * corpus, mirrored VERBATIM in the platform's centime convention
 * (desktop DZD × 100). The scenarios model the owner's exact issue shapes:
 *
 *   1. THE OWNER'S 100k/80k/20k scenario across 2025-2026 → 2026-2027
 *      (three payments, one cross-year settlement, new-year prices);
 *   2. THE T-442 three-year breakdown scenario (every billable service,
 *      a bounced cheque whose retained allocations are NOT coverage, a
 *      legacy payment with NO allocations — the honest "unavailable");
 *   3. The attribution-precedence + freeze invariants (INV-18a/18b);
 *   4. Determinism (INV-20b).
 *
 * A failure here means the two platforms would display DIFFERENT
 * per-year histories for the same data — the desktop ≡ android pin.
 */
class YearHistoryTest {

    private val now = Instant.parse("2027-01-15T12:00:00Z")

    private val years = listOf(
        AcademicYearWindow(id = "ay-2024", code = "2024-2025", startDate = "2024-09-01", endDate = "2025-06-30"),
        AcademicYearWindow(id = "ay-2025", code = "2025-2026", startDate = "2025-09-01", endDate = "2026-06-30"),
        AcademicYearWindow(id = "ay-2026", code = "2026-2027", startDate = "2026-09-01", endDate = "2027-06-30"),
    )

    // ── Fixture builders (mirror the desktop make* builders) ──────────────

    private fun ins(
        id: String,
        parentId: String = "p-1",
        studentId: String? = "s-1",
        category: PaymentCategory = PaymentCategory.TUITION,
        label: String = "Tranche 1",
        trancheNumber: Int = 1,
        amountDue: Long = 0,
        amountPaid: Long = 0,
        amountPending: Long = 0,
        dueDate: String = "2025-09-15",
        paidDate: String? = null,
        status: PaymentStatus = PaymentStatus.UNPAID,
        academicCycle: String? = null,
    ) = Installment(
        id = id, tenantId = "tenant-1", parentId = parentId, studentId = studentId,
        category = category, label = label, trancheNumber = trancheNumber,
        amountDue = amountDue, amountPaid = amountPaid, amountPending = amountPending,
        dueDate = dueDate, paidDate = paidDate, status = status,
        academicCycle = academicCycle,
    )

    private fun pay(
        id: String,
        parentId: String = "p-1",
        amount: Long = 0,
        status: PaymentStatus = PaymentStatus.PAID,
        method: PaymentMethod = PaymentMethod.CASH,
        collectedAt: String = "2025-11-01T10:00:00Z",
        receiptNumber: String = "REC-$id",
    ) = Payment(
        id = id, tenantId = "tenant-1", receiptNumber = receiptNumber, parentId = parentId,
        studentId = null, amount = amount, method = method, status = status,
        category = PaymentCategory.TUITION, installmentId = null, proofUrl = null, notes = null,
        collectedBy = "usr-1", collectedAt = collectedAt, createdAt = collectedAt, updatedAt = collectedAt,
    )

    private fun alloc(
        id: String,
        paymentId: String,
        installmentId: String? = null,
        category: PaymentCategory? = PaymentCategory.TUITION,
        allocatedAmount: Long = 0,
        label: String? = null,
        createdAt: String = "2025-11-01T10:00:00Z",
    ) = PaymentAllocation(
        id = id, paymentId = paymentId, installmentId = installmentId, category = category,
        allocatedAmount = allocatedAmount, label = label, createdAt = createdAt,
    )

    private fun led(
        id: String,
        sourceId: String,
        amount: Long,
        at: String,
        receiptNumber: String? = null,
        paymentStatus: PaymentStatus = PaymentStatus.PAID,
    ) = LedgerEntry(
        id = id, tenantId = "tenant-1", accountId = "parent:p-1:category:all", parentId = "p-1",
        studentId = null, category = PaymentCategory.TUITION, amount = amount,
        type = PAYMENT, sourceType = LEDGER_SOURCE_PAYMENT, sourceId = sourceId,
        method = PaymentMethod.CASH, receiptNumber = receiptNumber, paymentStatus = paymentStatus,
        reversesId = null, description = "Encaissement", actorId = "usr-1", actorName = "Staff",
        at = at, metadata = emptyMap(),
    )

    // ========================================================================
    // 1. THE OWNER'S 100k/80k/20k SCENARIO (desktop year-history.test.ts)
    // ========================================================================

    /**
     * 2025-2026: T1 40k (Sept 15) · T2 30k (Dec 15) · T3 30k (Mar 15) =
     * 100k charged. pay-1 40k (Nov 2025, settles T1); pay-2 40k (Feb 2026:
     * 30k on T2 + 10k on T3) — 80k paid, 20k outstanding at the year end.
     * 2026-2027: re-enrolled; NEW prices: 3 × 40k (120k total). pay-3 20k
     * (Oct 2026) settles the OLD T3 — the cross-year settlement.
     */
    private fun ownerScenario(): YearHistoryInput {
        // centimes = DZD × 100
        val dzd = { v: Long -> v * 100 }
        return YearHistoryInput(
            parentId = "p-1",
            installments = listOf(
                ins("ins-T1-25", label = "Tranche 1", trancheNumber = 1, amountDue = dzd(40_000), amountPaid = dzd(40_000), dueDate = "2025-09-15", paidDate = "2025-11-01", status = PaymentStatus.PAID, academicCycle = "2025-2026"),
                ins("ins-T2-25", label = "Tranche 2", trancheNumber = 2, amountDue = dzd(30_000), amountPaid = dzd(30_000), dueDate = "2025-12-15", paidDate = "2026-02-01", status = PaymentStatus.PAID, academicCycle = "2025-2026"),
                ins("ins-T3-25", label = "Tranche 3", trancheNumber = 3, amountDue = dzd(30_000), amountPaid = dzd(30_000), dueDate = "2026-03-15", paidDate = "2026-10-20", status = PaymentStatus.PAID, academicCycle = "2025-2026"),
                ins("ins-T1-26", label = "Tranche 1", trancheNumber = 1, amountDue = dzd(40_000), dueDate = "2026-09-15", academicCycle = "2026-2027"),
                ins("ins-T2-26", label = "Tranche 2", trancheNumber = 2, amountDue = dzd(40_000), dueDate = "2026-12-15", academicCycle = "2026-2027"),
                ins("ins-T3-26", label = "Tranche 3", trancheNumber = 3, amountDue = dzd(40_000), dueDate = "2027-03-15", academicCycle = "2026-2027"),
            ),
            payments = listOf(
                pay("pay-1", amount = dzd(40_000), collectedAt = "2025-11-01T10:00:00Z", receiptNumber = "REC-2025-000001"),
                pay("pay-2", amount = dzd(40_000), collectedAt = "2026-02-01T10:00:00Z", receiptNumber = "REC-2026-000001"),
                pay("pay-3", amount = dzd(20_000), collectedAt = "2026-10-20T10:00:00Z", receiptNumber = "REC-2026-000002"),
            ),
            allocations = listOf(
                alloc("alloc-1", "pay-1", "ins-T1-25", allocatedAmount = dzd(40_000), label = "Tranche 1", createdAt = "2025-11-01T10:00:00Z"),
                alloc("alloc-2", "pay-2", "ins-T2-25", allocatedAmount = dzd(30_000), label = "Tranche 2", createdAt = "2026-02-01T10:00:00Z"),
                alloc("alloc-3", "pay-2", "ins-T3-25", allocatedAmount = dzd(10_000), label = "Tranche 3", createdAt = "2026-02-01T10:00:00Z"),
                alloc("alloc-4", "pay-3", "ins-T3-25", allocatedAmount = dzd(20_000), label = "Tranche 3", createdAt = "2026-10-20T10:00:00Z"),
            ),
            ledgerEntries = listOf(
                led("led-1", "pay-1", -dzd(40_000), "2025-11-01T10:00:00Z", "REC-2025-000001"),
                led("led-2", "pay-2", -dzd(40_000), "2026-02-01T10:00:00Z", "REC-2026-000001"),
                led("led-3", "pay-3", -dzd(20_000), "2026-10-20T10:00:00Z", "REC-2026-000002"),
            ),
            academicYears = years,
            now = now,
        )
    }

    private fun ownerHistory() = computeParentYearHistory(ownerScenario())

    @Test
    fun `renders one record per academic year, ordered by year start`() {
        val history = ownerHistory()
        assertEquals(listOf("2025-2026", "2026-2027"), history.years.map { it.academicYear })
    }

    @Test
    fun `2025-2026 - what they were supposed to pay - every charge with ITS prices`() {
        val y = ownerHistory().years[0]
        assertEquals(dzdOf(100_000), y.totalCharged)
        assertEquals(listOf(dzdOf(40_000), dzdOf(30_000), dzdOf(30_000)), y.charges.map { it.amountDue })
        assertTrue(y.charges.all { it.attribution.source == AttributionSource.PERSISTED })
    }

    @Test
    fun `2025-2026 - the year-end outstanding is the 20k the issue specifies (as-of the year end - the later settlement does NOT rewrite it)`() {
        val y = ownerHistory().years[0]
        assertEquals(dzdOf(20_000), y.yearEndOutstanding)
        assertEquals(YearEndBasis.ALLOCATIONS, y.yearEndBasis)
        assertFalse(y.isOpen)
    }

    @Test
    fun `2025-2026 - what they paid during the year (the 80k) is itemized`() {
        val y = ownerHistory().years[0]
        assertEquals(dzdOf(80_000), y.paymentsMadeInYearTotal)
        assertEquals(listOf("pay-1", "pay-2"), y.paymentsMadeInYear.map { it.paymentId })
    }

    @Test
    fun `the old debt was fully paid IN 2026-2027 - the exact settlement point is on the old year's charge`() {
        val y = ownerHistory().years[0]
        val t3 = y.charges.first { it.installmentId == "ins-T3-25" }
        assertEquals(YearChargeSettlement.FULLY_PAID, t3.settlement)
        // The completing payment's date (pay-3, 2026-10-20) — NOT the stored
        // paidDate fallback when allocations exist (the exact moment).
        assertEquals("2026-10-20T10:00:00Z", t3.settledAt)
    }

    @Test
    fun `the cross-year settlement is a first-class fact on the RECEIVING year (INV-18c-18d)`() {
        val y = ownerHistory().years[0]
        assertEquals(1, y.settlementsReceivedFromLaterYears.size)
        val s = y.settlementsReceivedFromLaterYears[0]
        assertEquals("pay-3", s.paymentId)
        assertEquals("2026-2027", s.paymentYear)
        assertEquals("2025-2026", s.targetYear)
        assertEquals("ins-T3-25", s.installmentId)
        assertEquals(dzdOf(20_000), s.allocatedAmount)
        assertEquals(dzdOf(20_000), y.settlementsReceivedFromLaterYearsTotal)
    }

    @Test
    fun `2026-2027 - re-enrolled with the carried-forward 20k + NEW prices (120k)`() {
        val history = ownerHistory()
        val y = history.years[1]
        assertEquals(dzdOf(20_000), y.carriedForwardFromPriorYear)
        assertEquals(dzdOf(120_000), y.totalCharged)
        assertEquals(listOf(dzdOf(40_000), dzdOf(40_000), dzdOf(40_000)), y.charges.map { it.amountDue })
        assertTrue(y.isOpen)
        // The re-enrollment flag lives on the DEBT year (2025-2026); the
        // open final year carries neither flag — the honest presentation.
        assertTrue(history.years[0].reEnrolledOwing)
        assertFalse(history.years[0].leftOwing)
        assertFalse(y.reEnrolledOwing)
        assertFalse(y.leftOwing)
    }

    @Test
    fun `2026-2027 - the 20k payment MADE in 2026-2027 is attributed to 2026-2027`() {
        val y = ownerHistory().years[1]
        assertEquals(dzdOf(20_000), y.paymentsMadeInYearTotal)
        assertEquals("pay-3", y.paymentsMadeInYear[0].paymentId)
        assertEquals("2026-2027", y.paymentsMadeInYear[0].attribution.code)
    }

    @Test
    fun `the top-level totals reconcile - the old debt is NOT still owed, the new year is`() {
        val history = ownerHistory()
        assertEquals(dzdOf(120_000), history.totalOutstandingNow)
        assertEquals(0L, history.priorYearOutstandingStillOwed)
        assertEquals(0, history.priorYearsStillOwed.size)
    }

    @Test
    fun `the balance evolution shows the state change from one year to the next`() {
        val history = ownerHistory()
        val y25 = history.years[0]
        // 2025-2026: 3 charges (40+30+30) − 2 payments (40+40) → 20,000 out.
        assertEquals(dzdOf(20_000), y25.balanceEvolution.last().runningOutstanding)
        val y26 = history.years[1]
        // 2026-2027 starts FROM the carried 20,000, +120,000 − 20,000.
        assertEquals(dzdOf(20_000 + 40_000), y26.balanceEvolution.first().runningOutstanding)
        assertEquals(dzdOf(20_000 + 120_000 - 20_000), y26.balanceEvolution.last().runningOutstanding)
    }

    // ========================================================================
    // 2. THE T-442 THREE-YEAR BREAKDOWN SCENARIO (INV-20e)
    // ========================================================================

    /**
     * 2024-2025 (CLOSED, left owing): FI 25k (paid) · T1 60k (40k paid) ·
     * T2 40k (unpaid) · transport T1 20k (unpaid) · therapy 15k (unpaid).
     * 2025-2026 (CLOSED, re-enrolled owing): FI 25k (paid) · T1 80k (unpaid).
     * 2026-2027 (OPEN, current): FI 25k (paid) · T1 90k (unpaid) ·
     * transport T1 18k (unpaid) · canteen 12k (9k paid, 3k remaining) —
     * plus pay-26x (settles OLD debt 10k on 2024-2025 T1), pay-26b (a
     * BOUNCED cheque whose retained allocations are NOT coverage), and
     * pay-26l (a legacy payment with NO allocation rows).
     */
    private fun t442Scenario(): YearHistoryInput {
        val dzd = { v: Long -> v * 100 }
        return YearHistoryInput(
            parentId = "p-1",
            installments = listOf(
                // ── 2024-2025 ──
                ins("fi-24", label = "Frais d'inscription (FI)", trancheNumber = 0, amountDue = dzd(25_000), amountPaid = dzd(25_000), dueDate = "2024-09-15", paidDate = "2024-09-20", status = PaymentStatus.PAID, academicCycle = "2024-2025"),
                ins("t1-24", label = "Tranche 1 — Scolarité (V1)", trancheNumber = 1, amountDue = dzd(60_000), amountPaid = dzd(40_000), dueDate = "2024-09-15", status = PaymentStatus.PARTIAL, academicCycle = "2024-2025"),
                ins("t2-24", label = "Tranche 2 — Scolarité (2V)", trancheNumber = 2, amountDue = dzd(40_000), dueDate = "2024-12-15", academicCycle = "2024-2025"),
                ins("tr1-24", label = "Transport T1", category = PaymentCategory.TRANSPORT, trancheNumber = 1, amountDue = dzd(20_000), dueDate = "2024-09-15", academicCycle = "2024-2025"),
                ins("psy-24", label = "Séance de psychologie", category = PaymentCategory.THERAPY_PSYCHOLOGY, trancheNumber = 0, amountDue = dzd(15_000), dueDate = "2024-10-01", academicCycle = "2024-2025"),
                // ── 2025-2026 ──
                ins("fi-25", label = "Frais d'inscription (FI)", trancheNumber = 0, amountDue = dzd(25_000), amountPaid = dzd(25_000), dueDate = "2025-09-15", paidDate = "2025-09-18", status = PaymentStatus.PAID, academicCycle = "2025-2026"),
                ins("t1-25", label = "Tranche 1 — Scolarité (V1)", trancheNumber = 1, amountDue = dzd(80_000), dueDate = "2025-09-15", academicCycle = "2025-2026"),
                // ── 2026-2027 (open) ──
                ins("fi-26", label = "Frais d'inscription (FI)", trancheNumber = 0, amountDue = dzd(25_000), amountPaid = dzd(25_000), dueDate = "2026-09-15", paidDate = "2026-09-20", status = PaymentStatus.PAID, academicCycle = "2026-2027"),
                ins("t1-26", label = "Tranche 1 — Scolarité (V1)", trancheNumber = 1, amountDue = dzd(90_000), dueDate = "2026-09-15", academicCycle = "2026-2027"),
                ins("tr1-26", label = "Transport T1", category = PaymentCategory.TRANSPORT, trancheNumber = 1, amountDue = dzd(18_000), dueDate = "2026-09-15", academicCycle = "2026-2027"),
                ins("can-26", label = "Cantine", category = PaymentCategory.CANTEEN, trancheNumber = 0, amountDue = dzd(12_000), amountPaid = dzd(9_000), dueDate = "2026-10-01", status = PaymentStatus.PARTIAL, academicCycle = "2026-2027"),
            ),
            payments = listOf(
                pay("pay-24a", amount = dzd(25_000), collectedAt = "2024-09-20T10:00:00Z", receiptNumber = "REC-2024-000001"),
                pay("pay-24b", amount = dzd(30_000), collectedAt = "2024-11-05T10:00:00Z", receiptNumber = "REC-2024-000002"),
                pay("pay-25a", amount = dzd(25_000), collectedAt = "2025-09-18T10:00:00Z", receiptNumber = "REC-2025-000001"),
                pay("pay-26a", amount = dzd(34_000), collectedAt = "2026-09-25T10:00:00Z", receiptNumber = "REC-2026-000001"),
                // pay-26x: settles OLD debt (10k on 2024-2025 T1) + current-year items.
                pay("pay-26x", amount = dzd(44_000), collectedAt = "2026-12-10T10:00:00Z", receiptNumber = "REC-2026-000002"),
                // pay-26b: a BOUNCED cheque — retained allocation rows are NOT funds.
                pay("pay-26b", amount = dzd(5_000), status = PaymentStatus.UNPAID, method = PaymentMethod.CHECK, collectedAt = "2026-12-20T10:00:00Z", receiptNumber = "REC-2026-000003"),
                // pay-26l: a legacy payment with NO allocation rows (the import-era corpus).
                pay("pay-26l", amount = dzd(8_000), collectedAt = "2027-01-05T10:00:00Z", receiptNumber = "REC-2027-000001"),
            ),
            allocations = listOf(
                alloc("alloc-24a", "pay-24a", "fi-24", allocatedAmount = dzd(25_000), label = "Frais d'inscription (FI)", createdAt = "2024-09-20T10:00:00Z"),
                alloc("alloc-24b", "pay-24b", "t1-24", allocatedAmount = dzd(30_000), label = "Tranche 1 — Scolarité (V1)", createdAt = "2024-11-05T10:00:00Z"),
                alloc("alloc-25a", "pay-25a", "fi-25", allocatedAmount = dzd(25_000), label = "Frais d'inscription (FI)", createdAt = "2025-09-18T10:00:00Z"),
                alloc("alloc-26a1", "pay-26a", "fi-26", allocatedAmount = dzd(25_000), label = "Frais d'inscription (FI)", createdAt = "2026-09-25T10:00:00Z"),
                alloc("alloc-26a2", "pay-26a", "can-26", category = PaymentCategory.CANTEEN, allocatedAmount = dzd(9_000), label = "Cantine", createdAt = "2026-09-25T10:00:00Z"),
                // pay-26x: 10k settles the OLD 2024-2025 T1; 25k + 9k on current-year items.
                alloc("alloc-26x1", "pay-26x", "t1-24", allocatedAmount = dzd(10_000), label = "Tranche 1 — Scolarité (V1)", createdAt = "2026-12-10T10:00:00Z"),
                alloc("alloc-26x2", "pay-26x", "t1-26", allocatedAmount = dzd(25_000), label = "Tranche 1 — Scolarité (V1)", createdAt = "2026-12-10T10:00:00Z"),
                alloc("alloc-26x3", "pay-26x", "tr1-26", category = PaymentCategory.TRANSPORT, allocatedAmount = dzd(9_000), label = "Transport T1", createdAt = "2026-12-10T10:00:00Z"),
                // pay-26b's retained allocation rows (the 0039 bounce never deletes them).
                alloc("alloc-26b", "pay-26b", "can-26", category = PaymentCategory.CANTEEN, allocatedAmount = dzd(5_000), label = "Cantine", createdAt = "2026-12-20T10:00:00Z"),
                // NO allocations for pay-26l (the legacy corpus shape).
            ),
            ledgerEntries = listOf(
                led("led-24a", "pay-24a", -dzd(25_000), "2024-09-20T10:00:00Z", "REC-2024-000001"),
                led("led-24b", "pay-24b", -dzd(30_000), "2024-11-05T10:00:00Z", "REC-2024-000002"),
                led("led-25a", "pay-25a", -dzd(25_000), "2025-09-18T10:00:00Z", "REC-2025-000001"),
                led("led-26a", "pay-26a", -dzd(34_000), "2026-09-25T10:00:00Z", "REC-2026-000001"),
                led("led-26x", "pay-26x", -dzd(44_000), "2026-12-10T10:00:00Z", "REC-2026-000002"),
                led("led-26b", "pay-26b", -dzd(5_000), "2026-12-20T10:00:00Z", "REC-2026-000003", paymentStatus = PaymentStatus.UNPAID),
                led("led-26l", "pay-26l", -dzd(8_000), "2027-01-05T10:00:00Z", "REC-2027-000001"),
            ),
            academicYears = years,
            now = now,
        )
    }

    private fun t442History() = computeParentYearHistory(t442Scenario())

    @Test
    fun `T-442 - three year records, ordered, with the flags`() {
        val history = t442History()
        assertEquals(listOf("2024-2025", "2025-2026", "2026-2027"), history.years.map { it.academicYear })
        // 2024-2025: closed with debt and NO following-year charges? NO —
        // 2025-2026 HAS charges → re-enrolled owing.
        assertTrue(history.years[0].reEnrolledOwing)
        assertFalse(history.years[0].leftOwing)
        // 2025-2026: closed with debt, following year (2026-2027) has charges → re-enrolled.
        assertTrue(history.years[1].reEnrolledOwing)
        // 2026-2027: open → neither flag.
        assertFalse(history.years[2].leftOwing)
        assertFalse(history.years[2].reEnrolledOwing)
    }

    @Test
    fun `T-442 - the service breakdown PARTITIONS the year's charges (2024-2025)`() {
        val y = t442History().years[0]
        val groups = y.serviceBreakdown
        // registration → tuition → transport → services (alphabetical).
        assertEquals(
            listOf(YearServiceGroupKey.REGISTRATION, YearServiceGroupKey.TUITION, YearServiceGroupKey.TRANSPORT, YearServiceGroupKey.SERVICE),
            groups.map { it.key },
        )
        assertEquals(1, groups[0].chargeCount)                       // FI
        assertEquals(listOf(0), groups[0].trancheNumbers)            // FI = T0
        assertEquals(dzdOf(25_000), groups[0].amountDue)
        assertEquals(2, groups[1].chargeCount)                       // T1 + T2
        assertEquals(listOf(1, 2), groups[1].trancheNumbers)
        assertEquals(dzdOf(100_000), groups[1].amountDue)            // 60k + 40k
        assertEquals(dzdOf(40_000), groups[1].amountPaid)
        assertEquals(1, groups[2].chargeCount)                       // transport T1
        assertEquals(dzdOf(20_000), groups[2].amountDue)
        assertEquals(1, groups[3].chargeCount)                       // therapy
        assertEquals(PaymentCategory.THERAPY_PSYCHOLOGY, groups[3].category)
        assertEquals(dzdOf(15_000), groups[3].amountDue)
        // The partition: Σ groups === the year's charges.
        assertEquals(y.charges.size, groups.sumOf { it.chargeCount })
        assertEquals(y.totalCharged, groups.sumOf { it.amountDue })
    }

    @Test
    fun `T-442 - the coverage lines resolve the TARGET year (cross-year leg)`() {
        val y26 = t442History().years[2]
        val payX = y26.paymentsMadeInYear.first { it.paymentId == "pay-26x" }
        assertEquals(CoverageBasis.ALLOCATIONS, payX.coverageBasis)
        assertEquals(3, payX.coveredCharges.size)
        val crossYear = payX.coveredCharges.first { it.installmentId == "t1-24" }
        assertEquals("2024-2025", crossYear.targetYear)              // INV-18c
        assertEquals(dzdOf(10_000), crossYear.allocatedAmount)
        val sameYear = payX.coveredCharges.first { it.installmentId == "t1-26" }
        assertEquals("2026-2027", sameYear.targetYear)
    }

    @Test
    fun `T-442 - a bounced payment's retained allocation rows are NOT coverage (CALC-003)`() {
        val y26 = t442History().years[2]
        val payB = y26.paymentsMadeInYear.first { it.paymentId == "pay-26b" }
        assertEquals(CoverageBasis.UNAVAILABLE, payB.coverageBasis)
        assertEquals(0, payB.coveredCharges.size)
    }

    @Test
    fun `T-442 - a legacy payment with no allocations reports the honest unavailable basis`() {
        val y26 = t442History().years[2]
        val payL = y26.paymentsMadeInYear.first { it.paymentId == "pay-26l" }
        assertEquals(CoverageBasis.UNAVAILABLE, payL.coverageBasis)
        assertEquals(0, payL.coveredCharges.size)
    }

    @Test
    fun `T-442 - the cross-year settlement lands on the RECEIVING year`() {
        val y24 = t442History().years[0]
        val received = y24.settlementsReceivedFromLaterYears
        assertEquals(1, received.size)
        assertEquals("pay-26x", received[0].paymentId)
        assertEquals("2026-2027", received[0].paymentYear)
        assertEquals("2024-2025", received[0].targetYear)
        assertEquals("t1-24", received[0].installmentId)
        assertEquals(dzdOf(10_000), received[0].allocatedAmount)
        // And the bounced cheque never fabricates a settlement.
        assertTrue(y24.settlementsReceivedFromLaterYears.none { it.paymentId == "pay-26b" })
    }

    @Test
    fun `T-442 - the per-year still-owed-now composition - Σ entries === the aggregate`() {
        val history = t442History()
        // 2024-2025 remaining: t2-24 40k + tr1-24 20k + psy-24 15k = 75k
        // (t1-24 60k − 40k stored = 20k... wait: the stored amountPaid on
        // t1-24 is 40k, so remaining = 20k) → 40+20+15+20 = 95k? No:
        // t2-24 40k + t1-24 20k + tr1-24 20k + psy-24 15k = 95k.
        val y24 = history.years[0]
        assertEquals(dzdOf(95_000), y24.outstandingStillOwedNow)
        // 2025-2026 remaining: t1-25 80k.
        assertEquals(dzdOf(80_000), history.years[1].outstandingStillOwedNow)
        // The prior-years enumeration: [2024-2025, 2025-2026] (before the
        // last year WITH charges — 2026-2027).
        assertEquals(2, history.priorYearsStillOwed.size)
        assertEquals("2024-2025", history.priorYearsStillOwed[0].academicYear)
        assertEquals(dzdOf(95_000), history.priorYearsStillOwed[0].outstanding)
        assertEquals("2025-2026", history.priorYearsStillOwed[1].academicYear)
        assertEquals(dzdOf(80_000), history.priorYearsStillOwed[1].outstanding)
        // Σ entries === the aggregate.
        assertEquals(history.priorYearOutstandingStillOwed, history.priorYearsStillOwed.sumOf { it.outstanding })
    }

    @Test
    fun `T-442 - the year-end as-of split uses the allocations replay`() {
        val y24 = t442History().years[0]
        // At the 2024-2025 year end (2025-06-30): fi-24 fully paid (25k),
        // t1-24 30k in-window cleared (pay-24b Nov 2024; the 10k
        // cross-year top-up lands 2026-12-10 — AFTER the year end, so NOT
        // counted in the year-end outstanding), t2-24 40k unpaid, tr1-24
        // 20k, psy-24 15k.
        // MIXED: the charges WITH allocations (fi-24, t1-24) replay exactly;
        // the charges WITHOUT allocation rows (t2-24, tr1-24, psy-24) use
        // the paid-date heuristic — the desktop's same basis verdict.
        assertEquals(YearEndBasis.MIXED, y24.yearEndBasis)
        assertEquals(dzdOf(30_000 + 40_000 + 20_000 + 15_000), y24.yearEndOutstanding)
        assertEquals(dzdOf(105_000), y24.yearEndOutstanding)
    }

    @Test
    fun `T-442 - the current-year record carries the current state (INV-20c)`() {
        val history = t442History()
        val y26 = history.years[2]
        // canteen 12k − 9k paid = 3k remaining (the pending bounced 5k is
        // NOT a settlement — INV-4: the stored amounts are the truth).
        val can = y26.charges.first { it.installmentId == "can-26" }
        assertEquals(dzdOf(3_000), can.remaining)
        assertEquals(YearChargeSettlement.PARTIALLY_PAID, can.settlement)
        // The top-level total: 95k + 80k + (90k + 18k + 3k).
        assertEquals(dzdOf(95_000 + 80_000 + 90_000 + 18_000 + 3_000), history.totalOutstandingNow)
    }

    @Test
    fun `T-442 - INV-20a pin - the prior-years aggregate equals 175k`() {
        val history = t442History()
        assertEquals(dzdOf(175_000), history.priorYearOutstandingStillOwed)
    }

    @Test
    fun `T-442 - a fully-settled prior year is ABSENT from the enumeration (only years with outstanding above epsilon appear)`() {
        // Recompute with 2024-2025 fully paid: every charge settled.
        val s = t442Scenario()
        val settled = s.installments.map { ins ->
            if (ins.academicCycle == "2024-2025") {
                ins.copy(amountPaid = ins.amountDue, amountPending = 0, status = PaymentStatus.PAID, paidDate = "2025-06-01")
            } else ins
        }
        val history2 = computeParentYearHistory(s.copy(installments = settled))
        assertEquals(listOf("2025-2026"), history2.priorYearsStillOwed.map { it.academicYear })
        assertEquals(dzdOf(80_000), history2.priorYearsStillOwed[0].outstanding)
        assertTrue(
            history2.years.first { it.academicYear == "2024-2025" }.outstandingStillOwedNow <= YEAR_HISTORY_EPSILON_CENTIMES,
        )
    }

    @Test
    fun `T-442 - the multi-service payment's coverage spans categories (FI + canteen in ONE payment)`() {
        val y26 = t442History().years[2]
        val pay26a = y26.paymentsMadeInYear.first { it.paymentId == "pay-26a" }
        assertEquals(listOf("canteen", "tuition"), pay26a.coveredCharges.map { it.category!!.code }.sorted())
        assertEquals(dzdOf(34_000), pay26a.coveredCharges.sumOf { it.allocatedAmount })
    }

    // ========================================================================
    // 3. The attribution-precedence + freeze invariants (INV-18a/18b)
    // ========================================================================

    @Test
    fun `INV-18a - the persisted column wins over the INV-14 date rule`() {
        // An installment DUE 2026-03-15 (INV-14 → 2025-2026) but persisted
        // as 2024-2025: the persisted attribution wins.
        val history = computeParentYearHistory(
            YearHistoryInput(
                parentId = "p-1",
                installments = listOf(
                    ins("ins-x", amountDue = dzdOf(10_000), dueDate = "2026-03-15", academicCycle = "2024-2025"),
                ),
                ledgerEntries = emptyList(),
                academicYears = years,
                now = now,
            ),
        )
        assertEquals(1, history.years.size)
        assertEquals("2024-2025", history.years[0].academicYear)
        assertEquals(AttributionSource.PERSISTED, history.years[0].charges[0].attribution.source)
    }

    @Test
    fun `INV-18a fallback - a NULL persisted column uses the INV-14 date rule`() {
        val history = computeParentYearHistory(
            YearHistoryInput(
                parentId = "p-1",
                installments = listOf(
                    ins("ins-y", amountDue = dzdOf(10_000), dueDate = "2026-03-15", academicCycle = null),
                ),
                ledgerEntries = emptyList(),
                academicYears = years,
                now = now,
            ),
        )
        assertEquals("2025-2026", history.years[0].academicYear)
        assertEquals(AttributionSource.DUE_DATE, history.years[0].charges[0].attribution.source)
    }

    @Test
    fun `INV-14 convention - July-December starts YYYY-(YYYY+1), January-June (YYYY-1)-YYYY`() {
        assertEquals("2025-2026", resolveAcademicYearForDate("2025-09-15"))
        assertEquals("2025-2026", resolveAcademicYearForDate("2025-07-01"))
        assertEquals("2024-2025", resolveAcademicYearForDate("2025-01-15"))
        assertEquals("2024-2025", resolveAcademicYearForDate("2025-06-30"))
        // A tenant window containing the date wins over the convention.
        assertEquals(
            "2026-2027",
            resolveAcademicYearForDate("2027-06-15", listOf(AcademicYearWindow(code = "2026-2027", startDate = "2026-09-01", endDate = "2027-06-30"))),
        )
    }

    @Test
    fun `INV-18b the freeze - a due-date edit NEVER re-attributes a persisted row`() {
        // The same installment with an EDITED due date (re-enrollment year):
        // the persisted attribution is unchanged (DATA-051's fix).
        val edited = ins("ins-x", amountDue = dzdOf(10_000), dueDate = "2027-01-15", academicCycle = "2024-2025")
        val attr = attributeInstallmentAcademicYear(edited.dueDate, edited.academicCycle, years)
        assertEquals("2024-2025", attr.code)
        assertEquals(AttributionSource.PERSISTED, attr.source)
    }

    // ========================================================================
    // 4. Determinism (INV-20b) + the honest empty state
    // ========================================================================

    @Test
    fun `INV-20b determinism - same inputs + clock, identical records`() {
        val a = computeParentYearHistory(t442Scenario())
        val b = computeParentYearHistory(t442Scenario())
        assertEquals(a, b)
    }

    @Test
    fun `empty inputs - honest empty output`() {
        val history = computeParentYearHistory(
            YearHistoryInput(parentId = "p-none", installments = emptyList(), ledgerEntries = emptyList(), now = now),
        )
        assertEquals(0, history.years.size)
        assertEquals(0L, history.totalOutstandingNow)
        assertEquals(0L, history.priorYearOutstandingStillOwed)
        assertEquals(now.toString(), history.computedAt)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun dzdOf(v: Long): Long = v * 100
}
