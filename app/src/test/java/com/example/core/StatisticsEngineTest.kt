package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * T-285 — the StatisticsEngine parity suite (PARITY-002).
 *
 * Every fixture + expected value below is the desktop's OWN
 * `src/tests/ui/analytics-visuals.test.tsx` corpus (commit b6fbbcd),
 * converted to centimes (×100). The desktop computes in integer DZD;
 * the Android engine computes in centimes — the ×100 factor preserves
 * centime-exact comparability (the canonical corpus convention).
 *
 * A failure here means the Android engine diverges from the desktop
 * derivation layer — i.e. the two platforms would display DIFFERENT
 * numbers for the same data. This is the desktop≡android engine pin.
 */
class StatisticsEngineTest {

    // ── Fixture builder (mirrors the desktop `pay()` builder) ──────────────

    private fun pay(
        id: String,
        amountDzd: Long,
        method: String = "cash",
        status: String = "paid",
        category: String = "tuition",
        collectedAt: String = "2025-09-15T10:00:00Z",
    ) = StatsPayment(id, amountDzd * 100, method, status, category, collectedAt)

    // ── derivePaymentStats ────────────────────────────────────────────────

    @Test
    fun `count total mean median stddev over the slice`() {
        // Desktop: [10,20,30,40]k → count 4, total 100k, mean 25k,
        // median 25k ((20k+30k)/2), σ(sample) ≈ 12 910.
        val slice = listOf(
            pay("a", 10_000), pay("b", 20_000), pay("c", 30_000), pay("d", 40_000),
        )
        val s = derivePaymentStats(slice)
        assertEquals(4, s.count)
        assertEquals(10_000_000L, s.total)
        assertEquals(2_500_000L, s.mean)
        assertEquals(2_500_000L, s.median)
        assertEquals(1_000_000L, s.min)
        assertEquals(4_000_000L, s.max)
        // σ(sample) of [10,20,30,40]k DZD = sqrt(166.67e6) ≈ 12 910 DZD
        // (±10 DZD tolerance as on the desktop: toBeCloseTo(12_910, -1)).
        assertEquals(1_291_000.0, s.stdDev.toDouble(), 10_000.0)
    }

    @Test
    fun `median of an odd-length slice is the middle value`() {
        val s = derivePaymentStats(listOf(pay("a", 5_000), pay("b", 15_000), pay("c", 25_000)))
        assertEquals(1_500_000L, s.median)
    }

    @Test
    fun `mean and even-median round half-up at DZD granularity — the PARITY-001 pin`() {
        // Whole-DZD amounts (the real-world representation): [1, 2] DZD.
        // mean = round(1.5) = 2 DZD (integer division → 1);
        // even median = round((1+2)/2) = 2 DZD (integer division → 1).
        val s = derivePaymentStats(listOf(pay("a", 1), pay("b", 2)))
        assertEquals(200L, s.mean)
        assertEquals(200L, s.median)
        // [1, 2, 4] DZD → mean = round(7/3) = 2 DZD (truncation → 2, .67 pins the round).
        val s2 = derivePaymentStats(listOf(pay("a", 1), pay("b", 2), pay("c", 4)))
        assertEquals(200L, s2.mean)
        assertEquals(200L, s2.median) // median = 2 DZD exactly
    }

    @Test
    fun `best month is the REAL calendar month with the highest encaisse`() {
        val s = derivePaymentStats(
            listOf(
                pay("a", 10_000, collectedAt = "2025-09-10T10:00:00Z"),
                pay("b", 50_000, collectedAt = "2025-11-10T10:00:00Z"),
                pay("c", 20_000, collectedAt = "2025-09-20T10:00:00Z"),
            ),
        )
        assertEquals("Nov", s.bestMonth?.label)
        assertEquals(5_000_000L, s.bestMonth?.amount)
    }

    @Test
    fun `best month never merges the same month across years`() {
        // Jan 2025 = 30k, Jan 2026 = 40k → best = Jan 2026 (40k), NOT Jan 70k.
        val s = derivePaymentStats(
            listOf(
                pay("a", 30_000, collectedAt = "2025-01-10T10:00:00Z"),
                pay("b", 40_000, collectedAt = "2026-01-10T10:00:00Z"),
            ),
        )
        assertEquals(4_000_000L, s.bestMonth?.amount)
    }

    @Test
    fun `empty slice gives zeros and NO best month — honest`() {
        val s = derivePaymentStats(emptyList())
        assertEquals(0, s.count)
        assertEquals(0L, s.total)
        assertEquals(0L, s.mean)
        assertEquals(0L, s.median)
        assertEquals(0L, s.stdDev)
        assertNull(s.bestMonth)
    }

    // ── deriveAmountHistogram ─────────────────────────────────────────────

    @Test
    fun `bins amounts with correct edges — half-open lo hi`() {
        // Desktop: 4 999 → 0–5k; 5 000 → 5k–10k (edge); 19 999 → 10k–20k;
        // 50 000 → 50k+ (edge of the open bin); counts [1,1,1,0,1].
        val slice = listOf(pay("a", 4_999), pay("b", 5_000), pay("c", 19_999), pay("d", 50_000))
        val bins = deriveAmountHistogram(slice)
        assertEquals(listOf(1, 1, 1, 0, 1), bins.map { it.count })
        assertEquals(500_000L, bins[1].amount)
        assertEquals("50k+", bins[4].label)
    }

    // ── deriveMethodMix / deriveCategoryMix ───────────────────────────────

    @Test
    fun `method mix amounts counts percents desc sort`() {
        val slice = listOf(
            pay("a", 20_000, method = "cash"),
            pay("b", 10_000, method = "cash"),
            pay("c", 40_000, method = "check"),
        )
        val mix = deriveMethodMix(slice)
        assertEquals("check", mix[0].key)
        assertEquals(4_000_000L, mix[0].amount)
        assertEquals(1, mix[0].count)
        assertEquals(57, mix[0].percent)
        assertEquals("cash", mix[1].key)
        assertEquals(3_000_000L, mix[1].amount)
        assertEquals(2, mix[1].count)
        assertEquals(43, mix[1].percent)
        assertEquals(listOf("Chèque", "Espèces"), mix.map { it.label })
    }

    @Test
    fun `category mix merges the tail beyond topN into Autres`() {
        val cats = listOf(
            "tuition", "transport", "canteen", "uniform",
            "books", "other", "extracurricular",
        )
        val slice = cats.mapIndexed { i, c -> pay("c$i", (cats.size - i) * 1_000L, category = c) }
        val mix = deriveCategoryMix(slice, 3)
        assertEquals(4, mix.size)
        assertEquals("tuition", mix[0].key)
        assertEquals("__tail__", mix[3].key)
        assertEquals("Autres (4)", mix[3].label)
        assertEquals(4, mix[3].count)
        assertEquals(1_000_000L, mix[3].amount) // 4k+3k+2k+1k DZD
        val sum = mix.sumOf { it.percent }
        assertTrue("percent sum ~100 (was $sum)", sum in 98..102)
    }

    @Test
    fun `category mix covers ALL canonical categories — the PARITY-002 pin`() {
        // The pre-fix Android breakdown silently DROPPED 9 of 11 categories.
        val slice = listOf(
            pay("a", 10_000, category = "canteen"),
            pay("b", 20_000, category = "uniform"),
            pay("c", 30_000, category = "therapy_speech"),
        )
        val mix = deriveCategoryMix(slice)
        assertEquals(setOf("canteen", "uniform", "therapy_speech"), mix.map { it.key }.toSet())
        assertEquals(100, mix.sumOf { it.percent })
    }

    // ── derivePareto ──────────────────────────────────────────────────────

    @Test
    fun `sorts desc caps at topN and accumulates the cumulative share`() {
        val debtors = listOf(
            ParetoDebtor("Famille A", 5_000_000L),
            ParetoDebtor("Famille B", 3_000_000L),
            ParetoDebtor("Famille C", 1_500_000L),
            ParetoDebtor("Famille D", 500_000L),
        )
        val p = derivePareto(debtors, 3)
        assertEquals(listOf("Famille A", "Famille B", "Famille C"), p.map { it.name })
        // Cumulative of the DISPLAYED total (9.5k DZD): 50/95≈53, 80/95≈84, 100.
        assertEquals(53, p[0].cumPercent)
        assertEquals(84, p[1].cumPercent)
        assertEquals(100, p[2].cumPercent)
    }

    @Test
    fun `drops zero and negative outstanding rows`() {
        val p = derivePareto(listOf(ParetoDebtor("X", 0L), ParetoDebtor("Y", -5L)))
        assertTrue(p.isEmpty())
    }

    // ── deriveAgingComposition ────────────────────────────────────────────

    @Test
    fun `normalizes shares in the canonical bucket order`() {
        val segs = deriveAgingComposition(
            listOf(
                AgingBucketStat("91_180", "91–180 j", 3_000_000L, 2),
                AgingBucketStat("0_30", "0–30 j", 5_000_000L, 5),
                AgingBucketStat("31_60", "31–60 j", 2_000_000L, 3),
            ),
        )
        assertEquals(listOf("0_30", "31_60", "91_180"), segs.map { it.bucket })
        assertEquals(listOf(50, 20, 30), segs.map { it.share })
        assertEquals(5, segs[0].debtorCount)
    }

    @Test
    fun `zero-amount buckets are dropped and empty input gives empty output`() {
        assertTrue(deriveAgingComposition(listOf(AgingBucketStat("0_30", "0–30 j", 0L, 0))).isEmpty())
        assertTrue(deriveAgingComposition(emptyList()).isEmpty())
    }

    // ── deriveRecoveryFunnel ──────────────────────────────────────────────

    @Test
    fun `funnel stages from the aging census — family counts`() {
        val census = listOf(
            AgingBucketStat("0_30", "0–30 j", 1_000_000L, 5),
            AgingBucketStat("31_60", "31–60 j", 1_000_000L, 3),
            AgingBucketStat("91_180", "91–180 j", 1_000_000L, 2),
        )
        val stages = deriveRecoveryFunnel(census)
        assertEquals(4, stages.size)
        assertEquals("En retard", stages[0].name)
        assertEquals(10, stages[0].count)
        assertEquals(100, stages[0].sharePct)
        assertEquals(8, stages[1].count) // 0_30 + 31_60
        assertEquals(80, stages[1].sharePct)
        assertEquals(0, stages[2].count)
        assertEquals(0, stages[2].sharePct)
        assertEquals(2, stages[3].count) // 91_180 + 180_plus
        assertEquals(20, stages[3].sharePct)
    }

    @Test
    fun `empty census gives NO funnel — never a fabricated 100 percent critical`() {
        assertTrue(deriveRecoveryFunnel(emptyList()).isEmpty())
    }

    // ── collectionRatePct (the desktop insights-rail formula) ─────────────

    @Test
    fun `collection rate — the owner's 49 percent vector`() {
        // 55 227 100 / (55 227 100 + 58 355 700) = 48.62 → 49.
        val encaisse = 55_227_100L * 100
        val creances = 58_355_700L * 100
        assertEquals(49, collectionRatePct(encaisse, creances))
    }

    @Test
    fun `collection rate clamps at 100 and zeroes on empty denominator`() {
        assertEquals(100, collectionRatePct(1_000_000L, 0L))
        assertEquals(80, collectionRatePct(200_000L, 50_000L))
        assertEquals(0, collectionRatePct(0L, 0L))
    }

    // ── deriveRevenueTrend ────────────────────────────────────────────────

    @Test
    fun `running total and 3-month moving average`() {
        val revenue = listOf(
            RevenuePointInput("Sep", 3_000_000L),
            RevenuePointInput("Oct", 4_600_000L),
            RevenuePointInput("Nov", 2_000_000L),
            RevenuePointInput("Déc", 4_000_000L),
        )
        val trend = deriveRevenueTrend(revenue)
        assertEquals(listOf(3_000_000L, 7_600_000L, 9_600_000L, 13_600_000L), trend.map { it.cumulative })
        assertNull(trend[0].movingAvg3)
        assertNull(trend[1].movingAvg3)
        assertEquals(3_200_000L, trend[2].movingAvg3) // round(9.6M/3)
        // The DZD-granularity rounding pin: the desktop computes
        // Math.round(10 600 000 / 3 DZD) = 35 333 DZD → 3 533 300 centimes
        // (centime-granularity rounding would yield 3 533 333 — the silent
        // parity break this suite was built to catch).
        assertEquals(3_533_300L, trend[3].movingAvg3)
    }

    // ── attendanceRatePct (desktop: present + late / total) ───────────────

    @Test
    fun `attendance counts present AND late — the desktop Supabase convention`() {
        assertEquals(67, attendanceRatePct(listOf("present", "late", "absent_unexcused")))
        assertEquals(100, attendanceRatePct(listOf("present", "late")))
        assertEquals(0, attendanceRatePct(listOf("absent_unexcused")))
        assertNull(attendanceRatePct(emptyList()))
    }

    // ── deriveDebtAging (the canonical INV-4 census) ──────────────────────

    private val pinnedNowMs: Long = Instant.parse("2026-09-10T00:00:00Z").toEpochMilli()

    private fun inst(
        id: String,
        parentId: String,
        dueDate: String,
        dueDzd: Long = 1_000_000,
        paidDzd: Long = 0,
        pendingDzd: Long = 0,
        status: String = "overdue",
    ) = StatsInstallment(id, parentId, dueDzd * 100, paidDzd * 100, pendingDzd * 100, dueDate, status)

    @Test
    fun `aging buckets from REAL due dates with pinned now`() {
        val census = deriveDebtAging(
            listOf(
                inst("i1", "p1", "2026-09-05"),        // 5 days overdue → 0_30
                inst("i2", "p2", "2026-06-20"),        // 82 days → 61_90
                inst("i3", "p3", "2025-09-01"),        // ~373 days → 180_plus
                inst("i4", "p4", "2026-09-30"),        // NOT yet due → clamped 0 → 0_30
                inst("i5", "p5", "2026-02-01"),        // ~220 days → 180_plus
            ),
            pinnedNowMs,
        )
        val byBucket = census.associateBy { it.bucket }
        assertEquals(2, byBucket.getValue("0_30").debtorCount) // i1 + i4 (not-yet-due)
        assertEquals(1, byBucket.getValue("61_90").debtorCount)
        assertEquals(2, byBucket.getValue("180_plus").debtorCount)
        // i1 + i4 both carry remaining → 2M DZD in the bucket (the desktop
        // Supabase path accumulates the remaining of not-yet-due rows into
        // 0_30 — documented behavior, daysBetweenFloor clamps negative).
        assertEquals(2_000_000L * 100, byBucket.getValue("0_30").amount)
    }

    @Test
    fun `paid installments and zero-remaining rows are skipped`() {
        val census = deriveDebtAging(
            listOf(
                inst("paid1", "p1", "2025-01-01", status = "paid"),
                inst("zero", "p2", "2025-01-01", dueDzd = 100, paidDzd = 100),
                inst("real", "p3", "2025-01-01"),
            ),
            pinnedNowMs,
        )
        assertEquals(1, census.size)
        assertEquals("180_plus", census[0].bucket)
    }

    @Test
    fun `a family with tranches in two buckets counts in BOTH bucket counts`() {
        // The desktop per-installment census (not per-family worst-bucket):
        // p1 has one tranche 20 days overdue and one 200 days overdue →
        // appears in 0_30 AND 180_plus debtorCounts; the funnel totals
        // are the SUM of bucket counts (2), not the distinct families (1).
        val census = deriveDebtAging(
            listOf(
                inst("i1", "p1", "2026-08-25"),  // 16 days → 0_30
                inst("i2", "p1", "2026-02-20"),  // 202 days → 180_plus
            ),
            pinnedNowMs,
        )
        assertEquals(1, census.first { it.bucket == "0_30" }.debtorCount)
        assertEquals(1, census.first { it.bucket == "180_plus" }.debtorCount)
        val funnel = deriveRecoveryFunnel(census)
        assertEquals(2, funnel[0].count) // Σ bucket counts (census semantics)
    }

    @Test
    fun `INV-4 remaining — pending reduces the remaining`() {
        assertEquals(
            700_000L,
            installmentRemaining(inst("i", "p", "2026-01-01", dueDzd = 10_000, paidDzd = 2_000, pendingDzd = 1_000)),
        )
        assertEquals(0L, installmentRemaining(inst("i", "p", "2026-01-01", dueDzd = 5_000, paidDzd = 9_000)))
    }


    // ═════════════════════════════════════════════════════════════════════
    // PARITY-003 (T-292) — the visual-parity derivations (5 new families).
    // Fixtures + expected values mirror the desktop corpus scenarios
    // (analytics_visuals_*.json — generated by the REAL TS derivations).
    // ═════════════════════════════════════════════════════════════════════

    // ── deriveWeeklyRhythm ───────────────────────────────────────────────

    @Test
    fun `weekly rhythm — refunded excluded, pending counted, Fri Sat dropped`() {
        // The desktop corpus scenario (analytics_visuals_rhythm_heatmap_yoy):
        // Mon cash 30k paid; Wed check 25k paid; Fri transfer 40k paid (DROPPED);
        // Sat cash 15k paid (DROPPED); Sun transfer 90k paid; Tue cash 20k refunded
        // (EXCLUDED — counter-activity keeps pending/partial, drops only refunded);
        // Thu cash 45k pending (COUNTED).
        val payments = listOf(
            pay("p1", 30_000, "cash", "paid", "tuition", "2025-09-15T09:00:00Z"),     // Mon
            pay("p2", 25_000, "check", "paid", "tuition", "2025-09-17T11:00:00Z"),    // Wed
            pay("p3", 40_000, "transfer", "paid", "transport", "2025-09-19T10:00:00Z"), // Fri
            pay("p4", 15_000, "cash", "paid", "canteen", "2025-09-20T10:00:00Z"),     // Sat
            pay("p5", 90_000, "transfer", "paid", "tuition", "2025-10-05T08:30:00Z"), // Sun
            pay("p6", 20_000, "cash", "refunded", "tuition", "2025-10-07T12:00:00Z"), // Tue
            pay("p7", 45_000, "cash", "pending", "uniform", "2025-11-06T14:00:00Z"),  // Thu
        )
        val rhythm = deriveWeeklyRhythm(payments, StatsDateRange("2025-09-01", "2026-06-30"))
        assertEquals(5, rhythm.size)
        assertEquals(listOf("Dim", "Lun", "Mar", "Mer", "Jeu"), rhythm.map { it.day })
        // Dim: only the 90k transfer
        assertEquals(0L, rhythm[0].cash); assertEquals(0L, rhythm[0].check); assertEquals(9_000_000L, rhythm[0].transfer)
        // Lun: 30k cash (paid)
        assertEquals(3_000_000L, rhythm[1].cash); assertEquals(0L, rhythm[1].check); assertEquals(0L, rhythm[1].transfer)
        // Mar: the refunded 20k EXCLUDED
        assertEquals(0L, rhythm[2].cash); assertEquals(0L, rhythm[2].check); assertEquals(0L, rhythm[2].transfer)
        // Mer: 25k check
        assertEquals(0L, rhythm[3].cash); assertEquals(2_500_000L, rhythm[3].check); assertEquals(0L, rhythm[3].transfer)
        // Jeu: the PENDING 45k COUNTED (counter-activity)
        assertEquals(4_500_000L, rhythm[4].cash); assertEquals(0L, rhythm[4].check); assertEquals(0L, rhythm[4].transfer)
    }

    @Test
    fun `weekly rhythm — range filtering excludes out-of-window payments`() {
        val payments = listOf(
            pay("in", 10_000, collectedAt = "2025-10-06T10:00:00Z"),   // Mon, in range
            pay("out", 99_000, collectedAt = "2025-08-06T10:00:00Z"),  // before range
        )
        val rhythm = deriveWeeklyRhythm(payments, StatsDateRange("2025-09-01", "2025-10-31"))
        val lun = rhythm.first { it.day == "Lun" }
        assertEquals(1_000_000L, lun.cash)
        assertEquals(1_000_000L, rhythm.sumOf { it.total })
    }

    // ── deriveCollectionHeatmap ───────────────────────────────────────────

    @Test
    fun `heatmap — levels quantized in 5 steps, Fri Sat excluded, month columns walked`() {
        // Range 2025-09-01 → 2026-06-30 → 10 month columns (Sep..Juin).
        val paid = listOf(
            pay("h1", 30_000, collectedAt = "2025-09-15T09:00:00Z"),   // Mon Sep
            pay("h2", 90_000, collectedAt = "2025-10-05T08:30:00Z"),   // Sun Oct — the max cell
            pay("h3", 40_000, collectedAt = "2025-09-19T10:00:00Z"),   // Fri Sep — DROPPED
            pay("h4", 22_500, collectedAt = "2025-10-08T10:00:00Z"),   // Wed Oct = 25% of max
            pay("h5", 45_000, "cash", "pending", "uniform", "2025-11-06T14:00:00Z"), // pending — NOT in the paid slice
        )
        val hm = deriveCollectionHeatmap(paid, StatsDateRange("2025-09-01", "2026-06-30"))
        assertEquals(10, hm.monthLabels.size)
        assertEquals("Sep", hm.monthLabels[0])
        assertEquals(9_000_000L, hm.max)
        // The 90k Sunday cell → level 4 (== max)
        val dimRow = hm.rows.first { it.day == "Dim" }
        assertEquals(4, dimRow.cells[1].level)
        // The 22.5k Wednesday cell → ceil(0.25*4) = 1
        val merRow = hm.rows.first { it.day == "Mer" }
        assertEquals(1, merRow.cells[1].level)
        // Friday row: all zero (dropped)
        assertTrue(hm.rows.none { it.day == "Ven" })
        // Sep column: only the 30k Monday cell (the Friday 40k dropped)
        assertEquals(3_000_000L, hm.monthTotals[0])
        // Empty cells → level 0
        assertEquals(0, dimRow.cells[0].level)
    }

    // ── deriveYearOverYear ────────────────────────────────────────────────

    @Test
    fun `yoy — null deltas where previous is zero, label alignment, totals`() {
        // The desktop corpus scenario series (DZD → centimes):
        val current = listOf(
            "Sep" to 5_000_000L, "Oct" to 7_000_000L, "Nov" to 0L, "Déc" to 3_000_000L,
            "Jan" to 14_000_000L, "Fév" to 6_000_000L, "Mar" to 5_500_000L, "Avr" to 2_000_000L,
            "Mai" to 0L, "Juin" to 1_000_000L,
        ).map { RevenuePointInput(it.first, it.second * 100) }
        val previous = listOf(
            "Sep" to 4_000_000L, "Oct" to 7_000_000L, "Nov" to 1_000_000L, "Déc" to 0L,
            "Jan" to 9_000_000L, "Fév" to 0L, "Mar" to 5_500_000L, "Avr" to 2_500_000L,
            "Mai" to 500_000L, "Juin" to 0L,
        ).map { RevenuePointInput(it.first, it.second * 100) }
        val yoy = deriveYearOverYear(current, previous)
        assertEquals(10, yoy.points.size)
        // Sep: (5M-4M)/4M = +25%
        assertEquals(25, yoy.points[0].deltaPercent)
        // Oct: (7M-7M)/7M = 0%
        assertEquals(0, yoy.points[1].deltaPercent)
        // Nov: current 0, previous 1M → -100% (prev > 0 — a real delta)
        assertEquals(-100, yoy.points[2].deltaPercent)
        // Déc: previous 0 → null (a divide-by-zero is NOT a −100% trend)
        assertNull(yoy.points[3].deltaPercent)
        // Mar: equal → 0
        assertEquals(0, yoy.points[6].deltaPercent)
        // Totals: current Σ = 43.5M, previous Σ = 29.5M → +47%? round((43.5−29.5)/29.5×100) = round(47.457) = 47
        assertEquals(4_350_000_000L, yoy.totalCurrent)
        assertEquals(2_950_000_000L, yoy.totalPrevious)
        assertEquals(47, yoy.deltaPercent)
    }

    // ── deriveTrancheWaves + trancheNumberOf ──────────────────────────────

    @Test
    fun `tranche waves — regex traps, pct clamp, next-target, paid includes pending`() {
        // The desktop corpus scenario (analytics_visuals_tranche_waves):
        // T1: 60k due / 50k paid → pct 83; T2: 60k due / 15k paid + 5k pending → pct 25;
        // T3: 30k due / 0 → pct 0. "Année complète" + "Tranche 10" NEVER match.
        // "tranche 3" (lowercase) MATCHES (IGNORE_CASE).
        assertEquals(1, trancheNumberOf("Tranche 1"))
        assertEquals(2, trancheNumberOf("Tranche 2 (Jan–Mar)"))
        assertEquals(3, trancheNumberOf("tranche 3"))
        assertNull(trancheNumberOf("Année complète"))
        assertNull(trancheNumberOf("Tranche 10")) // \b after [1-3] blocks the 0
        assertNull(trancheNumberOf("Tranche 4"))

        val rows = listOf(
            StatsTrancheRow("Tranche 1", 4_000_000, 4_000_000, 0),
            StatsTrancheRow("Tranche 1", 2_000_000, 1_000_000, 0),
            StatsTrancheRow("Tranche 2 (Jan–Mar)", 3_000_000, 1_500_000, 500_000),
            StatsTrancheRow("Tranche 2", 3_000_000, 0, 0),
            StatsTrancheRow("tranche 3", 3_000_000, 0, 0),
            StatsTrancheRow("Année complète", 9_000_000, 9_000_000, 0),   // ignored
            StatsTrancheRow("Tranche 10", 1_000_000, 1_000_000, 0),       // ignored
        )
        val waves = deriveTrancheWaves(rows)
        assertEquals(3, waves.size)
        assertEquals("Tranche 1 (Septembre)", waves[0].label)
        // T1: due 60k, paid 50k → pct = round(50/60×100) = 83
        assertEquals(6_000_000L, waves[0].due)
        assertEquals(5_000_000L, waves[0].paid)
        assertEquals(83, waves[0].pct)
        // T1 remaining = 60−50−0 = 10k > 0 → the FIRST wave with remaining = next target
        assertTrue(waves[0].isNextTarget)
        // T2: due 60k, paid 15k (Σ amountPaid — the PENDING 5k is separate) → pct 25
        assertEquals(6_000_000L, waves[1].due)
        assertEquals(1_500_000L, waves[1].paid)
        assertEquals(500_000L, waves[1].pending)
        assertEquals(25, waves[1].pct)
        assertFalse(waves[1].isNextTarget)
        // T3: due 30k, nothing paid
        assertEquals(3_000_000L, waves[2].due)
        assertEquals(0, waves[2].pct)
        assertFalse(waves[2].isNextTarget)
    }

    @Test
    fun `tranche waves — pct clamps at 100 and all-paid waves leave no next target`() {
        val rows = listOf(StatsTrancheRow("Tranche 1", 1_000_000, 1_200_000, 0)) // overpaid → clamp
        val waves = deriveTrancheWaves(rows)
        assertEquals(100, waves[0].pct)
        // T1 fully paid (no remaining) → NOT the next target
        assertFalse(waves[0].isNextTarget)
    }

    // ── deriveDemographics ────────────────────────────────────────────────

    @Test
    fun `demographics — grade fallback chain, gender, age buckets, capacity defaults`() {
        // The desktop corpus scenario (analytics_visuals_demographics),
        // currentYear = 2026:
        val students = listOf(
            StatsStudentRow("male", "2020-05-10", "cls-1"),   // age 6 → 6–8
            StatsStudentRow("female", "2019-11-02", "cls-1"), // age 7 → 6–8
            StatsStudentRow("female", "2017-01-15", "cls-1"), // age 9 → 9–11
            StatsStudentRow("male", "2012-08-30", "cls-1"),   // age 14 → 12–14
            StatsStudentRow("unspecified", null, "cls-2"),    // no dob → skipped in age
            StatsStudentRow("male", "2015-03-20", "cls-2"),   // age 11 → 9–11
            StatsStudentRow("female", "2009-06-01", "cls-3"), // age 17 → 15–17
            StatsStudentRow("female", "2008-02-28", "cls-3"), // age 18 → 18+
            StatsStudentRow("female", "2007-12-31", "cls-3"), // age 19 → 18+
            StatsStudentRow("male", "1900-01-01", "cls-3"),   // age 126 → OUT of range → no bucket
            StatsStudentRow("male", "2024-09-05", null),      // no class → Non assigné; age 2 → < 6
            StatsStudentRow("female", "2022-04-18", "cls-4"), // age 4 → < 6
            StatsStudentRow("male", "2025-01-01", "cls-4"),   // age 1 → < 6
        )
        val classes = listOf(
            StatsClassRow("cls-1", "1AP-A", "1ap", 30),
            StatsClassRow("cls-2", "Groupe Intégré", "unknown_code", 22),
            StatsClassRow("cls-3", "3AM-B", "3am", 25),
            StatsClassRow("cls-4", "Sans-Cap", null, null),  // capacity null → 30
        )
        val d = deriveDemographics(students, classes, currentYear = 2026)

        // Grade: cls-1 (1ap) 4× → "1AP" 4 (31%); cls-2 (unknown code → class
        // NAME fallback) 2×; cls-3 (3am) 4× → "3AM" (st-7..st-10 — the 1900
        // birthdate student is still IN the class); 1 unassigned; cls-4 2×.
        // Values pinned to the DESKTOP-generated scenario then-block.
        val gradeByLabel = d.grade.associate { it.label to it }
        assertEquals(4, gradeByLabel["1AP"]!!.count)
        assertEquals(31, gradeByLabel["1AP"]!!.percent) // round(4/13×100) = 31
        assertEquals(2, gradeByLabel["Groupe Intégré"]!!.count) // the fallback chain
        assertEquals(4, gradeByLabel["3AM"]!!.count)
        assertEquals(1, gradeByLabel["Non assigné"]!!.count)
        assertEquals(2, gradeByLabel["Sans-Cap"]!!.count) // null grade_code → class name

        // Gender: 6 male, 6 female, 1 unspecified (present because > 0)
        assertEquals(6, d.gender[0].count) // Garçons
        assertEquals(6, d.gender[1].count) // Filles
        assertEquals(1, d.gender[2].count) // Non spécifié
        assertEquals("Garçons", d.gender[0].label)

        // Age: <6 = 3, 6–8 = 2, 9–11 = 2, 12–14 = 1, 15–17 = 1, 18+ = 2;
        // the 126-year outlier lands in NO bucket; percents over 13 students.
        val ageByLabel = d.age.associate { it.label to it }
        assertEquals(3, ageByLabel["< 6 ans"]!!.count)
        assertEquals(2, ageByLabel["6–8 ans"]!!.count)
        assertEquals(2, ageByLabel["9–11 ans"]!!.count)
        assertEquals(1, ageByLabel["12–14 ans"]!!.count)
        assertEquals(1, ageByLabel["15–17 ans"]!!.count)
        assertEquals(2, ageByLabel["18+ ans"]!!.count)
        assertEquals(23, ageByLabel["< 6 ans"]!!.percent) // round(3/13×100) = 23

        // Capacity: cls-1 4/30 → 13%; cls-2 2/22 → 9%; cls-3 4/25 → 16%;
        // cls-4 null cap → 30 default, 2/30 → 7%.
        val capByLabel = d.capacity.associate { it.label to it }
        assertEquals(13, capByLabel["1AP-A"]!!.percent)
        assertEquals(9, capByLabel["Groupe Intégré"]!!.percent)
        assertEquals(16, capByLabel["3AM-B"]!!.percent)
        assertEquals(7, capByLabel["Sans-Cap"]!!.percent) // the default-30 path
    }

    // ── Range helpers (slicer + shift) ────────────────────────────────────

    @Test
    fun `shiftIsoYearBack is leap-day safe`() {
        assertEquals("2024-09-01", shiftIsoYearBack("2025-09-01"))
        assertEquals("2023-02-28", shiftIsoYearBack("2024-02-29"))
        assertEquals("garbage", shiftIsoYearBack("garbage"))
    }

    @Test
    fun `previousAcademicYear shifts both bounds`() {
        assertEquals("2024-2025", previousAcademicYear("2025-2026"))
        assertNull(previousAcademicYear("2025/2026"))
    }

    @Test
    fun `applyAnalyticsFilters — empty sets include all, paid-only slice`() {
        val payments = listOf(
            pay("a", 10_000, "cash", "paid", "tuition", "2025-10-01T10:00:00Z"),
            pay("b", 20_000, "check", "pending", "tuition", "2025-10-02T10:00:00Z"),
            pay("c", 30_000, "cash", "paid", "transport", "2025-10-03T10:00:00Z"),
            pay("d", 40_000, "transfer", "paid", "canteen", "2025-10-04T10:00:00Z"),
        )
        // No filters → the paid slice only
        assertEquals(listOf("a", "c", "d"), applyAnalyticsFilters(payments, null, emptySet(), emptySet()).map { it.id })
        // Method filter: cash only
        assertEquals(listOf("a", "c"), applyAnalyticsFilters(payments, null, setOf("cash"), emptySet()).map { it.id })
        // Category filter: transport
        assertEquals(listOf("c"), applyAnalyticsFilters(payments, null, emptySet(), setOf("transport")).map { it.id })
        // Both (AND semantics)
        assertEquals(listOf("c"), applyAnalyticsFilters(payments, null, setOf("cash"), setOf("transport")).map { it.id })
        // Range filter
        assertEquals(listOf("a"), applyAnalyticsFilters(payments, StatsDateRange("2025-10-01", "2025-10-01"), emptySet(), emptySet()).map { it.id })
    }

    @Test
    fun `deriveFilteredMonthly aligns by month index`() {
        val slice = listOf(
            pay("a", 10_000, collectedAt = "2025-09-15T10:00:00Z"),
            pay("b", 20_000, collectedAt = "2025-11-20T10:00:00Z"),
            pay("c", 5_000, collectedAt = "2025-11-25T10:00:00Z"),
            pay("d", 7_000, collectedAt = "2026-03-10T10:00:00Z"), // month not in labels → skipped
        )
        val out = deriveFilteredMonthly(slice, listOf("Sep", "Oct", "Nov"))
        assertEquals(listOf(1_000_000L, 0L, 2_500_000L), out)
    }
}
