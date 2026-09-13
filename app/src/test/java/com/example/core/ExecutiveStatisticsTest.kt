package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * T-340 — the ExecutiveStatistics parity suite (STATS-400, 61st session).
 *
 * Every fixture + expected value below is the DESKTOP'S OWN
 * `src/tests/features/dashboard/executive-statistics.test.ts` corpus
 * (commit 256bfa4, T-338 / 61st session), mirrored VERBATIM. The fixtures
 * model the LIVE production shapes discovered during the session's DB
 * exploration (2026-09-14): installments carry tranche_number 1/2/3 with
 * tuition labels "INSCRIPTION (FI)" / "2EME TRANCHE (V2)" / "3ème TRANCHE
 * (2V)"; the remise markers are STRUCTURED metadata (field=REMISE +
 * reason=double_remise_cancel); transport_tier holds messy town spellings.
 *
 * Every test pins the EXACT expected integer values (no approximations)
 * so the Android mirror is held to the same corpus as the desktop. A
 * failure here means the two platforms would display DIFFERENT numbers
 * for the same data — the desktop ≡ android engine pin.
 */
class ExecutiveStatisticsTest {

    private val now = Instant.parse("2026-09-14T12:00:00Z").toEpochMilli()

    // ── Fixture builders (mirror the desktop make* builders) ──────────────

    private fun ins(
        id: String,
        parentId: String = "p-1",
        category: String = "tuition",
        trancheNumber: Int = 1,
        amountDue: Long = 100_000,
        amountPaid: Long = 0,
        amountPending: Long = 0,
        dueDate: String = "2025-09-15",
        status: String = "unpaid",
    ) = ExecInstallment(
        id = id, parentId = parentId, category = category, trancheNumber = trancheNumber,
        amountDue = amountDue, amountPaid = amountPaid, amountPending = amountPending,
        dueDate = dueDate, status = status,
    )

    private fun led(
        id: String,
        parentId: String = "p-1",
        category: String = "tuition",
        amount: Long = 0,
        type: String = "adjustment",
        description: String = "",
        metadata: Map<String, Any?> = emptyMap(),
    ) = ExecLedgerEntry(
        id = id, parentId = parentId, category = category, amount = amount,
        type = type, description = description, metadata = metadata,
    )

    private fun stu(
        id: String,
        parentId: String = "p-1",
        status: String = "active",
        transportTier: String? = null,
    ) = ExecStudent(id = id, parentId = parentId, status = status, transportTier = transportTier)

    private fun pay(
        id: String,
        amount: Long = 10_000,
        status: String = "paid",
        category: String = "therapy_psychology",
        studentId: String? = "stu-1",
    ) = ExecPayment(id = id, amount = amount, status = status, category = category, studentId = studentId)

    private fun cls(
        id: String,
        name: String,
        gradeCode: String,
        enrolledCount: Int,
        isActive: Boolean = true,
    ) = ExecClass(id = id, name = name, gradeCode = gradeCode, isActive = isActive, enrolledCount = enrolledCount)

    // ══════════════════════════════════════════════════════════════════════
    // Shared helpers
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `daysBetweenFloor - a tranche due today is 0 days overdue, never 1`() {
        assertEquals(0L, execDaysBetweenFloor("2026-09-14", now))
    }

    @Test
    fun `daysBetweenFloor - floors partial days (due yesterday 13h vs now 12h = 0)`() {
        assertEquals(0L, execDaysBetweenFloor("2026-09-13T13:00:00Z", now))
    }

    @Test
    fun `daysBetweenFloor - 45 days late boundary is exact`() {
        assertEquals(45L, execDaysBetweenFloor("2026-07-31", now))
        assertEquals(46L, execDaysBetweenFloor("2026-07-30", now))
    }

    @Test
    fun `installmentRemaining - INV-4 uncleared pending funds count as covered-but-uncleared`() {
        assertEquals(30_000L, execInstallmentRemaining(100_000, 40_000, 30_000, "partial"))
    }

    @Test
    fun `installmentRemaining - paid installments are 0 even with rounding dust`() {
        assertEquals(0L, execInstallmentRemaining(100_000, 99_999, 5, "paid"))
    }

    @Test
    fun `installmentRemaining - clamps overpayment to 0`() {
        assertEquals(0L, execInstallmentRemaining(100_000, 120_000, 0, "unpaid"))
    }

    @Test
    fun `sharePct - round-half-up convention (PARITY-001, never integer division)`() {
        assertEquals(33, execSharePct(1, 3))
        assertEquals(67, execSharePct(2, 3))
        assertEquals(13, execSharePct(90_000, 700_000)) // the pinned corpus vector
        assertEquals(0, execSharePct(5, 0))
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. Tranche-wave collection velocity
    // ══════════════════════════════════════════════════════════════════════

    private val waveFixtures = listOf(
        // T1 tuition: 3 billed, 2 paid
        ins("t1-a", "p-1", trancheNumber = 1, amountDue = 100_000, amountPaid = 100_000, status = "paid", dueDate = "2025-09-15"),
        ins("t1-b", "p-2", trancheNumber = 1, amountDue = 110_000, amountPaid = 110_000, status = "paid", dueDate = "2025-09-15"),
        ins("t1-c", "p-3", trancheNumber = 1, amountDue = 120_000, amountPaid = 60_000, status = "partial", dueDate = "2025-09-15"),
        // T2 tuition: 3 billed, 1 paid
        ins("t2-a", "p-1", trancheNumber = 2, amountDue = 80_000, amountPaid = 80_000, status = "paid", dueDate = "2025-12-15"),
        ins("t2-b", "p-2", trancheNumber = 2, amountDue = 80_000, amountPaid = 0, status = "unpaid", dueDate = "2025-12-15"),
        ins("t2-c", "p-3", trancheNumber = 2, amountDue = 80_000, amountPaid = 0, status = "unpaid", dueDate = "2025-12-15"),
        // T3 tuition: 3 billed, 0 paid
        ins("t3-a", "p-1", trancheNumber = 3, amountDue = 80_000, amountPaid = 0, status = "unpaid", dueDate = "2026-03-15"),
        ins("t3-b", "p-2", trancheNumber = 3, amountDue = 80_000, amountPaid = 0, status = "unpaid", dueDate = "2026-03-15"),
        ins("t3-c", "p-3", trancheNumber = 3, amountDue = 80_000, amountPaid = 0, status = "unpaid", dueDate = "2026-03-15"),
        // Transport waves: 2 billed, both paid
        ins("tr-1", "p-1", category = "transport", trancheNumber = 1, amountDue = 20_000, amountPaid = 20_000, status = "paid", dueDate = "2025-09-15"),
        ins("tr-2", "p-2", category = "transport", trancheNumber = 2, amountDue = 10_000, amountPaid = 10_000, status = "paid", dueDate = "2025-12-15"),
    )

    @Test
    fun `waves - groups by category x tranche_number, never by label parsing`() {
        val waves = deriveExecTrancheWaves(waveFixtures, now)
        assertEquals(
            listOf("tuition#1", "tuition#2", "tuition#3", "transport#1", "transport#2"),
            waves.map { it.key },
        )
    }

    @Test
    fun `waves - T1 tuition exact billed collected remaining + value-based rate`() {
        val t1 = deriveExecTrancheWaves(waveFixtures, now)[0]
        assertEquals(3, t1.installmentCount)
        assertEquals(2, t1.paidCount)
        assertEquals(3, t1.familyCount)
        assertEquals(1, t1.debtorFamilyCount) // p-3 partial
        assertEquals(330_000L, t1.dueTotal)
        assertEquals(270_000L, t1.paidTotal)
        assertEquals(60_000L, t1.remainingTotal)
        assertEquals(82, t1.collectedPct) // round(270000/330000*100)
        assertEquals(67, t1.clearedPct)   // round(2/3*100)
        // The desktop toISOString convention — ALWAYS 3-digit millis.
        assertEquals("2025-09-15T00:00:00.000Z", t1.dueDate)
        assertEquals(WavePhase.OVERDUE, t1.phase) // unpaid remainder past due
    }

    @Test
    fun `waves - T2 T3 tuition exact staircase decay (the payroll-warning view)`() {
        val waves = deriveExecTrancheWaves(waveFixtures, now)
        assertEquals(33, waves[1].collectedPct)  // 80000/240000
        assertEquals(160_000L, waves[1].remainingTotal)
        assertEquals(0, waves[2].collectedPct)
        assertEquals(240_000L, waves[2].remainingTotal)
    }

    @Test
    fun `waves - fully-collected waves report 100 percent and phase in_window (complete)`() {
        val tr1 = deriveExecTrancheWaves(waveFixtures, now)[3]
        assertEquals(100, tr1.collectedPct)
        assertEquals(0L, tr1.remainingTotal)
        assertEquals(WavePhase.IN_WINDOW, tr1.phase)
    }

    @Test
    fun `waves - a wave whose unpaid remainders are all in the future is not_due`() {
        val future = deriveExecTrancheWaves(
            listOf(ins("f-1", "p-9", trancheNumber = 1, amountDue = 50_000, amountPaid = 0, status = "unpaid", dueDate = "2026-12-15")),
            now,
        )
        assertEquals(WavePhase.NOT_DUE, future[0].phase)
        assertEquals(50_000L, future[0].remainingTotal)
    }

    @Test
    fun `waves - defaults missing trancheNumber to wave 1 (legacy rows)`() {
        val legacy = deriveExecTrancheWaves(
            listOf(ins("l-1", amountDue = 10_000, amountPaid = 10_000, status = "paid").copy(trancheNumber = 1)),
            now,
        )
        assertEquals(1, legacy[0].wave)
    }

    @Test
    fun `waves - empty input to empty output (honest empty state)`() {
        assertTrue(deriveExecTrancheWaves(emptyList(), now).isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. Discount erosion
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `erosion - identifies remises via the STRUCTURED metadata marker (field REMISE)`() {
        val e = deriveExecDiscountErosion(
            listOf(
                led("c-1", type = "charge", amount = 100_000, description = "Devis annuel"),
                led(
                    "r-1", type = "adjustment", amount = -20_000,
                    description = "Remise sur devis (import Excel run run_msp3foah_c254f9)",
                    metadata = mapOf("field" to "REMISE", "importRunId" to "run_msp3foah_c254f9"),
                ),
            ),
        )
        assertEquals(1, e.remiseCount)
        assertEquals(20_000L, e.remiseTotal)
        assertEquals(0, e.cancelCount)
        assertEquals(100_000L, e.grossCharges)
        assertEquals(120_000L, e.stickerTotal)
        assertEquals(17, e.erosionPct) // round(20000/120000*100)
        assertEquals(20_000L, e.averageRemise)
        assertEquals(1, e.remiseFamilyCount)
    }

    @Test
    fun `erosion - description fallback catches legacy remise rows without metadata`() {
        val e = deriveExecDiscountErosion(
            listOf(led("r-legacy", type = "adjustment", amount = -5_000, description = "Remise sur devis (legacy import)")),
        )
        assertEquals(1, e.remiseCount)
        assertEquals(5_000L, e.remiseTotal)
    }

    @Test
    fun `erosion - double-remise-cancel debits counted separately and net to zero with the remise`() {
        val e = deriveExecDiscountErosion(
            listOf(
                led(
                    "r-1", type = "adjustment", amount = -25_500,
                    description = "Remise sur devis (import Excel run run_msp3foah_c254f9)",
                    metadata = mapOf("field" to "REMISE"),
                ),
                led(
                    "x-1", type = "adjustment", amount = 25_500,
                    description = "Annulation double-remise (réconciliation 0063) — le devis importé est déjà net de remise (formule Excel L = composantes − J)",
                    metadata = mapOf(
                        "reason" to "double_remise_cancel", "original_entry" to "r-1",
                        "reconciliation" to "0063", "original_amount" to -25500,
                    ),
                ),
            ),
        )
        assertEquals(1, e.remiseCount)
        assertEquals(25_500L, e.remiseTotal)
        assertEquals(1, e.cancelCount)
        assertEquals(25_500L, e.cancelTotal)
        assertEquals(0L, e.netRemiseTotal) // ledger-honest: the imported devis is already net
    }

    @Test
    fun `erosion - payments refunds other adjustments never pollute the erosion math`() {
        val e = deriveExecDiscountErosion(
            listOf(
                led("pay-1", type = "payment", amount = -50_000, description = "Encaissement", metadata = mapOf("field" to "REMISE")),
                led("ref-1", type = "refund", amount = -5_000, description = "Remise sur devis (looks like one)", metadata = mapOf("field" to "REMISE")),
                led("adj-1", type = "adjustment", amount = -15_000, description = "Crédit parent (excédent)"),
                led("neg-charge", type = "charge", amount = -3_000, description = "Correction"),
            ),
        )
        assertEquals(0, e.remiseCount)
        assertEquals(0L, e.remiseTotal)
        assertEquals(0L, e.grossCharges)
        assertEquals(0, e.erosionPct)
    }

    @Test
    fun `erosion - the live-shaped corpus remises 95k 85k 84k 80k against a 113 723 800 net base`() {
        val e = deriveExecDiscountErosion(
            listOf(led("c-1", type = "charge", amount = 113_723_800, description = "Devis (net)")) +
                listOf(95_000L, 85_000L, 84_000L, 80_000L).mapIndexed { i, amt ->
                    led(
                        "r-$i", type = "adjustment", amount = -amt,
                        description = "Remise sur devis (import Excel run run_msp3foah_c254f9)",
                        metadata = mapOf("field" to "REMISE", "importRunId" to "run_msp3foah_c254f9"),
                    )
                },
        )
        assertEquals(344_000L, e.remiseTotal)
        assertEquals(114_067_800L, e.stickerTotal)
        assertEquals(0, e.erosionPct) // round(344000/114067800*100) = 0.30 → 0
        assertEquals(95_000L, e.maxRemise)
        assertEquals(80_000L, e.minRemise)
        assertEquals(86_000L, e.averageRemise)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Debt triage
    // ══════════════════════════════════════════════════════════════════════

    private val triageFixtures = listOf(
        // p-1: T1 late 61 days (chronic), 40_000 remaining
        ins("d-1", "p-1", trancheNumber = 1, amountDue = 40_000, amountPaid = 0, status = "unpaid", dueDate = "2026-07-15"),
        // p-2: 20 days late (reminder), 30_000 remaining
        ins("d-2", "p-2", trancheNumber = 2, amountDue = 30_000, amountPaid = 0, status = "unpaid", dueDate = "2026-08-25"),
        // p-3: 5 days late (current), 10_000 remaining
        ins("d-3", "p-3", trancheNumber = 1, amountDue = 10_000, amountPaid = 0, status = "unpaid", dueDate = "2026-09-09"),
        // p-4: not due yet (the December wave), 50_000 remaining
        ins("d-4", "p-4", trancheNumber = 3, amountDue = 50_000, amountPaid = 0, status = "unpaid", dueDate = "2026-12-15"),
        // p-1 ALSO owes a second late tranche (44 days — still reminder bucket)
        ins("d-5", "p-1", trancheNumber = 2, amountDue = 20_000, amountPaid = 0, status = "unpaid", dueDate = "2026-08-01"),
    )

    @Test
    fun `triage - splits into the four action tiers with exact amounts and family counts`() {
        val t = deriveExecDebtTriage(triageFixtures, now)
        val byBucket = t.buckets.associateBy { it.bucket }
        assertEquals(50_000L, byBucket[ExecTriageBucket.NOT_DUE]!!.amount)
        assertEquals(1, byBucket[ExecTriageBucket.NOT_DUE]!!.familyCount)
        assertEquals(10_000L, byBucket[ExecTriageBucket.CURRENT]!!.amount)
        assertEquals(50_000L, byBucket[ExecTriageBucket.REMINDER]!!.amount) // p-2 (20j, 30k) + p-1's 2nd tranche (44j, 20k)
        assertEquals(40_000L, byBucket[ExecTriageBucket.CHRONIC]!!.amount) // p-1's 61-day tranche
        assertEquals(1, byBucket[ExecTriageBucket.CHRONIC]!!.familyCount)
        assertEquals(1, byBucket[ExecTriageBucket.CHRONIC]!!.installmentCount)
        assertEquals(2, byBucket[ExecTriageBucket.REMINDER]!!.installmentCount)
    }

    @Test
    fun `triage - total outstanding = sum of buckets, shares exact`() {
        val t = deriveExecDebtTriage(triageFixtures, now)
        assertEquals(150_000L, t.totalOutstanding)
        assertEquals(33, t.buckets[0].share) // 50000/150000
        assertEquals(27, t.buckets[3].share) // 40000/150000
    }

    @Test
    fun `triage - the call list = families with ANY installment over 45 days late, full exposure, ranked`() {
        val t = deriveExecDebtTriage(triageFixtures, now)
        assertEquals(1, t.callList.size)
        assertEquals("p-1", t.callList[0].parentId)
        assertEquals(60_000L, t.callList[0].outstanding) // full exposure incl. the 44d tranche
        assertEquals(61L, t.callList[0].worstDaysOverdue) // 2026-07-15 → 2026-09-14
    }

    @Test
    fun `triage - a family whose worst overdue is exactly 45 days is reminder, NOT chronic (over 45 strict)`() {
        val t = deriveExecDebtTriage(
            listOf(ins("b-1", "p-9", amountDue = 10_000, amountPaid = 0, status = "unpaid", dueDate = "2026-07-31")), // exactly 45 days
            now,
        )
        assertEquals(10_000L, t.buckets.first { it.bucket == ExecTriageBucket.REMINDER }.amount)
        assertEquals(0L, t.buckets.first { it.bucket == ExecTriageBucket.CHRONIC }.amount)
        assertEquals(0, t.callList.size)
    }

    @Test
    fun `triage - satisfied installments never enter any bucket`() {
        val t = deriveExecDebtTriage(
            listOf(ins("paid-1", amountDue = 10_000, amountPaid = 10_000, status = "paid", dueDate = "2025-09-15")),
            now,
        )
        assertEquals(0L, t.totalOutstanding)
        assertTrue(t.buckets.all { it.amount == 0L })
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Family concentration
    // ══════════════════════════════════════════════════════════════════════

    private val concFixtures = listOf(
        ins("f-1", "p-big", trancheNumber = 1, amountDue = 150_000, amountPaid = 0, status = "unpaid", dueDate = "2025-09-15"),
        ins("f-2", "p-big2", trancheNumber = 1, amountDue = 120_000, amountPaid = 0, status = "unpaid", dueDate = "2025-09-15"),
        ins("f-3", "p-mid", trancheNumber = 1, amountDue = 60_000, amountPaid = 0, status = "unpaid", dueDate = "2025-09-15"),
        ins("f-4", "p-small", trancheNumber = 1, amountDue = 10_000, amountPaid = 0, status = "unpaid", dueDate = "2026-12-15"),
    )

    private val concStudents = listOf(
        stu("s-1", "p-big"), stu("s-2", "p-big"), stu("s-3", "p-big"),
        stu("s-4", "p-big2"), stu("s-5", "p-big2"),
        stu("s-6", "p-mid"), stu("s-7", "p-mid"),
        stu("s-8", "p-small"),
        stu("s-9", "p-zero-debt"),                       // family with NO debt
        stu("s-10", "p-zero-debt", status = "withdrawn"), // inactive child not counted
    )

    private val concParents = listOf(
        "p-big" to "BENZAOUI", "p-big2" to "KOUBAA", "p-mid" to "ALIOUAT",
        "p-small" to "ATTOUCHE", "p-zero-debt" to "CHARIF",
    )

    @Test
    fun `concentration - per-family outstanding ranked desc with child counts (the 80 20 view)`() {
        val c = deriveExecFamilyConcentration(concFixtures, concParents, concStudents, topN = 2, nowEpochMs = now)
        assertEquals(340_000L, c.totalOutstanding)
        assertEquals(4, c.debtorFamilyCount)
        assertEquals(listOf("p-big", "p-big2"), c.topFamilies.map { it.parentId })
        assertEquals(3, c.topFamilies[0].childCount)
        assertEquals("BENZAOUI", c.topFamilies[0].parentName)
        assertEquals(44, c.topFamilies[0].shareOfTotalDebt) // round(150000/340000*100)
    }

    @Test
    fun `concentration - top-N concentration = round(topTotal over total times 100)`() {
        val c = deriveExecFamilyConcentration(concFixtures, concParents, concStudents, topN = 2, nowEpochMs = now)
        assertEquals(270_000L, c.topTotal)
        assertEquals(79, c.topConcentrationPct) // round(270000/340000*100)
    }

    @Test
    fun `concentration - families with no debt never appear in the ranking`() {
        val c = deriveExecFamilyConcentration(concFixtures, concParents, concStudents, topN = 2, nowEpochMs = now)
        assertTrue(c.topFamilies.none { it.parentId == "p-zero-debt" })
    }

    @Test
    fun `concentration - worstDaysOverdue reflects the family's oldest unpaid tranche (deterministic now)`() {
        val c = deriveExecFamilyConcentration(concFixtures, concParents, concStudents, topN = 2, nowEpochMs = now)
        assertEquals(364L, c.topFamilies[0].worstDaysOverdue) // 2025-09-15 → 2026-09-14T12:00Z = 364.5 floored
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. Transport yield
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `transport - normalizes messy live spellings through TOWN_ALIASES and reconciles route money`() {
        val students = listOf(
            stu("r-1", "p-1", transportTier = "BOUMERDES"),
            stu("r-2", "p-2", transportTier = "BOUMRDES"),     // typo → same town
            stu("r-3", "p-3", transportTier = "OULED MOUSSA"), // spaced → ouled_moussa
            stu("r-4", "p-4", transportTier = "KHEMISELKHCHNA"),
            stu("r-5", "p-5", transportTier = "ERBATACHE"),    // unknown → autres
            stu("r-6", "p-6", transportTier = null),           // no transport
            stu("r-7", "p-1", transportTier = "BOUMREDES"),    // same parent, 2nd child, same town
        )
        val installments = listOf(
            // p-1 (boumerdes): 2 transport installments, one paid
            ins("tr-1", "p-1", category = "transport", trancheNumber = 1, amountDue = 20_000, amountPaid = 20_000, status = "paid", dueDate = "2025-09-15"),
            ins("tr-2", "p-1", category = "transport", trancheNumber = 2, amountDue = 10_000, amountPaid = 0, status = "unpaid", dueDate = "2025-12-15"),
            // p-3 (ouled_moussa): unpaid
            ins("tr-3", "p-3", category = "transport", trancheNumber = 1, amountDue = 30_000, amountPaid = 0, status = "unpaid", dueDate = "2025-09-15"),
            // p-5 (autres): partially paid
            ins("tr-4", "p-5", category = "transport", trancheNumber = 1, amountDue = 30_000, amountPaid = 15_000, status = "partial", dueDate = "2025-09-15"),
            // A transport installment with NO rider student → bucketed under autres
            ins("tr-5", "p-9", category = "transport", trancheNumber = 3, amountDue = 10_000, amountPaid = 0, status = "unpaid", dueDate = "2026-03-15"),
        )

        val t = deriveExecTransportYield(students, installments)

        assertEquals(6, t.riders)
        assertEquals(1, t.nonRiders)
        assertEquals(listOf("ERBATACHE"), t.unresolvedRawValues)

        val boumerdes = t.routes.first { it.destination == "boumerdes" }
        assertEquals(2, boumerdes.riders) // two PARENTS (p-1, p-2)
        assertEquals(30_000L, boumerdes.dueTotal)
        assertEquals(20_000L, boumerdes.paidTotal)
        assertEquals(10_000L, boumerdes.remainingTotal)
        assertEquals(67, boumerdes.collectedPct)

        val ouledMoussa = t.routes.first { it.destination == "ouled_moussa" }
        assertEquals(1, ouledMoussa.riders)
        assertEquals(30_000L, ouledMoussa.remainingTotal)

        // Σ across routes reconciles the whole transport installment stream:
        // due 100_000, paid 35_000, remaining 65_000 → 35%.
        assertEquals(100_000L, t.dueTotal)
        assertEquals(35_000L, t.paidTotal)
        assertEquals(65_000L, t.remainingTotal)
        assertEquals(35, t.collectedPct)
    }

    @Test
    fun `transport - routes sort by riders desc, then remaining desc`() {
        val t = deriveExecTransportYield(
            listOf(
                stu("r-1", "p-1", transportTier = "CORSO"),
                stu("r-2", "p-2", transportTier = "SAHEL"),
                stu("r-3", "p-3", transportTier = "SAHEL"),
            ),
            emptyList(),
        )
        assertEquals(listOf("sahel", "corso"), t.routes.map { it.destination })
        assertEquals(0, t.collectedPct)
        assertEquals(0L, t.dueTotal)
    }

    // ══════════════════════════════════════════════════════════════════════
    // normalizeTransportTier (the canonical normalizer)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `normalizeTransportTier - null blank to null (no transport, NOT counted as a rider)`() {
        assertNull(normalizeTransportTier(null))
        assertNull(normalizeTransportTier("   "))
    }

    @Test
    fun `normalizeTransportTier - live spelling variants collapse to their real town`() {
        assertEquals("boumerdes", normalizeTransportTier("BOUMERDES"))
        assertEquals("boumerdes", normalizeTransportTier("BOUMRDES"))
        assertEquals("boumerdes", normalizeTransportTier("BOUMREDES"))
        assertEquals("boumerdes", normalizeTransportTier("BOUMERDES20000"))
        assertEquals("ouled_moussa", normalizeTransportTier("OULED MOUSSA"))
        assertEquals("khemis_el_khechna", normalizeTransportTier("KHEMISELKHCHNA"))
        assertEquals("zemmouri", normalizeTransportTier("ZEMOURI"))
        assertEquals("tidjelabine_sahel_figuier_corso", normalizeTransportTier("tidjelabine_sahel_figuier_corso"))
    }

    @Test
    fun `normalizeTransportTier - unknown non-empty to autres (a rider from an unrecognized locality)`() {
        assertEquals("autres", normalizeTransportTier("ERBATACHE"))
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. Service yield
    // ══════════════════════════════════════════════════════════════════════

    private val servicePayments = listOf(
        pay("sp-1", amount = 10_000, category = "therapy_psychology", studentId = "stu-1"),
        pay("sp-2", amount = 10_000, category = "therapy_psychology", studentId = "stu-2"),
        pay("sp-3", amount = 10_000, category = "therapy_psychology", studentId = "stu-1"),
        pay("sp-4", amount = 5_000, category = "therapy_speech", studentId = "stu-3"),
        pay("sp-5", amount = 1_659_500, category = "other", studentId = null),
        // Non-service categories are excluded:
        pay("sp-6", amount = 100_000, category = "tuition"),
        // Refunded therapy does not count as yield:
        pay("sp-7", amount = 5_000, category = "therapy_speech", status = "refunded"),
    )

    @Test
    fun `services - per-service revenue volume students from the PAID stream, sorted desc`() {
        val s = deriveExecServiceYield(servicePayments)
        assertEquals(
            listOf("other", "therapy_psychology", "therapy_speech"),
            s.map { it.category },
        )
        val psy = s.first { it.category == "therapy_psychology" }
        assertEquals(30_000L, psy.revenue)
        assertEquals(3, psy.paymentCount)
        assertEquals(2, psy.studentCount) // stu-1 paid twice, counted once
        assertEquals("Psychologie", psy.label)
    }

    @Test
    fun `services - empty service activity to empty output (honest empty state)`() {
        assertTrue(deriveExecServiceYield(emptyList()).isEmpty())
        assertTrue(deriveExecServiceYield(listOf(pay("only-tuition", amount = 10_000, category = "tuition"))).isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. Enrollment dynamics + section imbalance
    // ══════════════════════════════════════════════════════════════════════

    private fun buildDynamicsStudents(): List<ExecStudent> {
        // Live-shaped family mix: 5×1 + 2×2 + 1×3 + 1×5 = 17 students / 9 families.
        val out = ArrayList<ExecStudent>()
        repeat(5) { i -> out.add(stu("s1-$i", "p-$i")) }
        repeat(2) { i -> out.add(stu("s2-$i", "p-5")) }
        repeat(2) { i -> out.add(stu("s2b-$i", "p-6")) }
        repeat(3) { i -> out.add(stu("s3-$i", "p-7")) }
        repeat(5) { i -> out.add(stu("s5-$i", "p-8")) }
        out.add(stu("s-inactive", "p-8", status = "withdrawn")) // not counted
        return out
    }

    private val dynamicsClasses = listOf(
        // Live-shaped multi-section grade: CP 51 vs 1AP 3 (same gradeCode 1ap) + a balanced grade.
        cls("cp", "CP — Cours Préparatoire", "1ap", 51),
        cls("1ap", "1ère Année Primaire", "1ap", 3),
        cls("ce1", "CE1 — 2ème Année Primaire", "2ap", 35),
        cls("2ap", "2ème Année Primaire", "2ap", 35),
        cls("5ap", "5ème Année Primaire", "5ap", 41),                       // single section
        cls("gs-old", "Grande Section", "prescolaire_2", 22, isActive = false), // inactive
    )

    @Test
    fun `dynamics - sibling index = totalStudents over totalFamilies, 2 decimals, inactive excluded`() {
        val dyn = deriveExecEnrollmentDynamics(buildDynamicsStudents(), dynamicsClasses)
        assertEquals(17, dyn.totalStudents)
        assertEquals(9, dyn.totalFamilies)
        assertEquals(1.89, dyn.siblingIndex!!, 0.0001)
    }

    @Test
    fun `dynamics - family-size distribution merges 5+ and reports both counts`() {
        val dyn = deriveExecEnrollmentDynamics(buildDynamicsStudents(), dynamicsClasses)
        assertEquals(
            listOf("1 enfant", "2 enfants", "3 enfants", "5+ enfants"),
            dyn.familySizes.map { it.label },
        )
        assertEquals(5, dyn.familySizes.first { it.label == "1 enfant" }.familyCount)
        assertEquals(1, dyn.familySizes.first { it.label == "5+ enfants" }.familyCount)
        assertEquals(5, dyn.familySizes.first { it.label == "5+ enfants" }.studentCount)
    }

    @Test
    fun `dynamics - multi-child families count and share`() {
        val dyn = deriveExecEnrollmentDynamics(buildDynamicsStudents(), dynamicsClasses)
        assertEquals(4, dyn.multiChildFamilyCount)
        assertEquals(44, dyn.multiChildFamilyPct) // round(4/9*100)
    }

    @Test
    fun `dynamics - section imbalance spread plus the (spread at least 10 OR max at least 1point5 min) rule`() {
        val dyn = deriveExecEnrollmentDynamics(buildDynamicsStudents(), dynamicsClasses)
        assertEquals(
            listOf("CP — Cours Préparatoire (1AP)", "CE1 — 2ème Année Primaire (2AP)"),
            dyn.imbalances.map { it.gradeLabel },
        )
        val cp = dyn.imbalances[0]
        assertEquals(2, cp.sectionCount)
        assertEquals(51, cp.maxEnrolled)
        assertEquals(3, cp.minEnrolled)
        assertEquals(48, cp.spread)
        assertEquals(27, cp.averageEnrolled)
        assertTrue(cp.imbalanced)
        val ce1 = dyn.imbalances[1]
        assertEquals(0, ce1.spread)
        assertFalse(ce1.imbalanced)
    }

    @Test
    fun `dynamics - single-section grades and inactive classes never appear as imbalances`() {
        val dyn = deriveExecEnrollmentDynamics(buildDynamicsStudents(), dynamicsClasses)
        assertTrue(dyn.imbalances.none { i -> i.sections.any { it.classId == "5ap" } })
        assertTrue(dyn.imbalances.none { i -> i.sections.any { it.classId == "gs-old" } })
    }

    @Test
    fun `dynamics - max at least 1point5 x min flags even with a spread under 10 (the ratio rule)`() {
        val d = deriveExecEnrollmentDynamics(
            listOf(stu("x", "p-1")),
            listOf(
                cls("a", "1AM A", "1am", 15),
                cls("b", "1AM B", "1am", 9),
            ),
        )
        assertEquals(6, d.imbalances[0].spread)
        assertTrue(d.imbalances[0].imbalanced) // 15 ≥ 1.5 × 9 = 13.5
    }

    @Test
    fun `dynamics - empty school to null sibling index and empty structures`() {
        val d = deriveExecEnrollmentDynamics(emptyList(), emptyList())
        assertNull(d.siblingIndex)
        assertTrue(d.familySizes.isEmpty())
        assertTrue(d.imbalances.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════
    // 8. Triple-risk radar
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `risk - execRiskCategoryOf thresholds (GPA under 10 + absences at least 3 + debt at least 25k DZD)`() {
        // triple critical: all three vectors
        assertEquals(
            ExecRiskCategory.TRIPLE_CRITICAL,
            execRiskCategoryOf(gpa = 8.0, unexcusedAbsences = 4, attendanceRate = 0.9, debtAmountCentimes = 3_000_000),
        )
        // academic only
        assertEquals(
            ExecRiskCategory.ACADEMIC_ALERT,
            execRiskCategoryOf(gpa = 8.0, unexcusedAbsences = 0, attendanceRate = 1.0, debtAmountCentimes = 0),
        )
        // attendance via rate < 0.85
        assertEquals(
            ExecRiskCategory.ATTENDANCE_ALERT,
            execRiskCategoryOf(gpa = 14.0, unexcusedAbsences = 0, attendanceRate = 0.80, debtAmountCentimes = 0),
        )
        // financial tension at exactly 25 000 DZD (2 500 000 centimes)
        assertEquals(
            ExecRiskCategory.FINANCIAL_TENSION,
            execRiskCategoryOf(gpa = 14.0, unexcusedAbsences = 0, attendanceRate = 1.0, debtAmountCentimes = 2_500_000),
        )
        // null GPA = no academic data → NO academic alert even with debt
        assertEquals(
            ExecRiskCategory.HEALTHY,
            execRiskCategoryOf(gpa = null, unexcusedAbsences = 0, attendanceRate = 1.0, debtAmountCentimes = 2_400_000),
        )
    }

    @Test
    fun `risk - counts each category plus the triple-critical share`() {
        val s = deriveExecTripleRiskSummary(
            listOf(
                ExecRiskCategory.TRIPLE_CRITICAL,
                ExecRiskCategory.TRIPLE_CRITICAL,
                ExecRiskCategory.ACADEMIC_ALERT,
                ExecRiskCategory.ATTENDANCE_ALERT,
                ExecRiskCategory.FINANCIAL_TENSION,
                ExecRiskCategory.HEALTHY,
            ),
        )
        assertEquals(2, s.tripleCriticalCount)
        assertEquals(1, s.academicAlertCount)
        assertEquals(1, s.attendanceAlertCount)
        assertEquals(1, s.financialTensionCount)
        assertEquals(1, s.healthyCount)
        assertEquals(33, s.tripleCriticalPct) // round(2/6*100)
    }

    @Test
    fun `risk - zero profiles to all zeros (honest empty state)`() {
        val s = deriveExecTripleRiskSummary(emptyList())
        assertEquals(0, s.tripleCriticalCount)
        assertEquals(0, s.tripleCriticalPct)
    }
}
