package com.example.core

import org.junit.Assert.assertEquals
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
}
