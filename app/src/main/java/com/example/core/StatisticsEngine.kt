package com.example.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * StatisticsEngine — the Kotlin mirror of the desktop's canonical analytics
 * derivation layer.
 *
 * MIRRORED FROM (ADR-002 — ported verbatim, divergent hand-copies forbidden):
 *   `elimtiyaz-desktop/src/features/dashboard/components/analytics/analytics-derivations.ts`
 *   (source commit b6fbbcd, T-255..T-257 / 38th session, UI-307)
 *   `elimtiyaz-desktop/src/features/dashboard/components/recovery-funnel-card.tsx`
 *   (deriveRecoveryFunnel — same source commit)
 *   `elimtiyaz-desktop/src/features/dashboard/components/insights-rail.tsx` /
 *   `see-details-modal.tsx` (the collection-rate formula)
 *   `elimtiyaz-desktop/src/infrastructure/supabase/repositories/supabase-dashboard-repository.ts`
 *   (debtByAgingForRange — the canonical per-installment INV-4 aging path)
 *
 * PARITY-002 (44th session, 2026-09-11): before this engine, every one of
 * these derivations lived INLINE in `LocalDashboardRepository` (population σ,
 * integer-division mean/median, a 2-category breakdown, a cross-year
 * best-month with a fabricated "Août" fallback) or worse in UI composables
 * (collection rate, aging donut, funnel percentages — with the DESKTOP's
 * production numbers hard-coded as empty-state fallbacks). This file is the
 * single Android implementation; the dashboard, debt, and PDF surfaces all
 * consume it (§15.16 — nothing synthesized, honest empty states).
 *
 * PARITY-003 (45th session, 2026-09-11): the visual-parity extension —
 * deriveWeeklyRhythm, deriveCollectionHeatmap, deriveYearOverYear (+ range/
 * slicer helpers), deriveTrancheWaves, deriveDemographics (the 5 families
 * the 13-chart inventory required). Same mirror discipline.
 *
 * UNITS: all monetary values are CENTIMES (Long) — the Android engine's
 * native representation. The desktop derivation layer works in integer DZD;
 * the equivalence corpus converts at the boundaries (×100 / ÷100). Every
 * percentage/rounding site replicates the desktop's `Math.round`
 * (round-half-up for the non-negative values used here) — NEVER Kotlin
 * integer division (PARITY-001).
 *
 * The desktop's paid-slice definition (status === "paid") is the canonical
 * input contract for every payments-derived statistic.
 */

// ============================================================================
// Labels (mirrors of the desktop's domain/model/payment.ts FR maps)
// ============================================================================

/** French month labels, Jan→Déc (canonical order; the desktop uses "Sep" for September). */
val MONTH_LABELS_FR: List<String> = listOf(
    "Jan", "Fév", "Mar", "Avr", "Mai", "Juin",
    "Juil", "Août", "Sep", "Oct", "Nov", "Déc",
)

/** Month label → 0-based calendar month index. */
val MONTH_INDEX_BY_LABEL: Map<String, Int> = MONTH_LABELS_FR.withIndex().associate { (i, l) -> l to i }

/** Canonical aging-bucket order + FR labels (desktop AGING_BUCKET_LABELS_FR). */
val AGING_BUCKETS_IN_ORDER: List<String> = listOf("0_30", "31_60", "61_90", "91_180", "180_plus")

val AGING_BUCKET_LABELS_FR: Map<String, String> = mapOf(
    "0_30" to "0–30 j",
    "31_60" to "31–60 j",
    "61_90" to "61–90 j",
    "91_180" to "91–180 j",
    "180_plus" to "180+ j",
)

/** Canonical payment-category FR labels (desktop PAYMENT_CATEGORY_LABELS_FR — ALL 11). */
fun paymentCategoryLabelFr(category: String): String = when (category) {
    "tuition" -> "Scolarité"
    "transport" -> "Transport"
    "canteen" -> "Cantine"
    "uniform" -> "Uniforme"
    "books" -> "Livres"
    "extracurricular" -> "Activité parascolaire"
    "therapy_psychology" -> "Psychologie"
    "therapy_speech" -> "Orthophonie"
    "second_apron" -> "2ème Tablier"
    "parent_credit" -> "Crédit Parent"
    "other" -> "Autre"
    else -> "Autre"
}

// ============================================================================
// Fixed histogram bins (centimes; the desktop's AMOUNT_BINS in DZD ×100)
// ============================================================================

data class AmountBin(val label: String, val lo: Long, val hi: Long)

/** Half-open [lo, hi) bins; the last bin is unbounded (Long.MAX_VALUE). */
val AMOUNT_BINS: List<AmountBin> = listOf(
    AmountBin("0–5k", 0L, 500_000L),
    AmountBin("5k–10k", 500_000L, 1_000_000L),
    AmountBin("10k–20k", 1_000_000L, 2_000_000L),
    AmountBin("20k–50k", 2_000_000L, 5_000_000L),
    AmountBin("50k+", 5_000_000L, Long.MAX_VALUE),
)

// ============================================================================
// Input contract — the paid-slice payment row (centimes)
// ============================================================================

/**
 * The minimal payment projection the statistics consume. Mirrors the desktop
 * `Payment` fields used by `analytics-derivations.ts`: amount, method,
 * status, category, collectedAt.
 */
data class StatsPayment(
    val id: String,
    val amount: Long,          // centimes
    val method: String,        // "cash" | "check" | "transfer" | …
    val status: String,        // "paid" | "pending" | … (only "paid" is encaissé)
    val category: String,      // canonical category code
    val collectedAt: String,   // ISO-8601 timestamp
)

// ============================================================================
// Round-half-up helper — the Math.round parity seam (PARITY-001)
// ============================================================================

/**
 * Replicates JS `Math.round` for the non-negative values used by every
 * percentage derivation: floor(x + 0.5).
 * NEVER replace with `.toInt()` (truncation) — PARITY-001.
 * Used for DIMENSIONLESS values (percentages, rates). Money-valued
 * derivations use [dzRound] instead (the desktop rounds its DZD numbers).
 */
internal fun mathRound(x: Double): Long = Math.floor(x + 0.5).toLong()

/**
 * The money-rounding mirror: the desktop derivation layer computes in
 * integer DZD and `Math.round`s its money-valued statistics (mean, even
 * median, σ, moving averages) at DZD precision; the canonical corpus then
 * converts back via Math.round(dzd * 100). The exact centime mirror of that
 * is: round the DZD value, scale by 100. Rounding at centime granularity
 * instead would produce e.g. 3 533 333 where the desktop produces
 * 3 533 300 (MA3 of 46k+20k+40k DZD) — a silent parity break discovered
 * by the T-285 engine suite.
 */
internal fun dzRound(xCentimes: Double): Long = mathRound(xCentimes / 100.0) * 100L

// ============================================================================
// T-255 — descriptive statistics over the paid slice (SAMPLE σ, Math.round)
// ============================================================================

data class BestMonth(val label: String, val amount: Long)

data class PaymentStats(
    val count: Int,
    val total: Long,            // centimes
    val mean: Long,             // centimes, Math.round'd
    val median: Long,           // centimes, Math.round'd (even lengths average)
    val stdDev: Long,           // centimes, SAMPLE standard deviation (÷ (n−1))
    val min: Long,
    val max: Long,
    val bestMonth: BestMonth?,  // null when the slice is empty (honest)
)

/** Parse an ISO timestamp to epoch-ms; null when invalid (payments with unparseable dates are skipped). */
internal fun parseIsoMs(iso: String): Long? = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()

/** Median of a SORTED list (even lengths average the two middles, rounded at DZD granularity like the desktop). */
internal fun medianOfSorted(sorted: List<Long>): Long {
    if (sorted.isEmpty()) return 0L
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid]
    else dzRound((sorted[mid - 1] + sorted[mid]).toDouble() / 2.0)
}

/**
 * Descriptive statistics over the (already filtered) paid payments —
 * count, total, mean, median, SAMPLE standard deviation, min/max, best
 * month (the calendar month of collectedAt with the highest encaissé —
 * per calendar month, NEVER merged across years; first-encountered wins
 * ties, mirroring the desktop's Map iteration semantics).
 */
fun derivePaymentStats(slice: List<StatsPayment>): PaymentStats {
    val amounts = slice.map { it.amount }
    val total = amounts.sum()
    val count = amounts.size
    val sorted = amounts.sorted()
    val mean = if (count > 0) dzRound(total.toDouble() / count) else 0L

    // SAMPLE variance (÷ (n−1)) — the desktop analytics convention
    // (the AI-copilot statistics tool uses population variance; that is a
    // DIFFERENT desktop surface and is intentionally not mirrored here).
    val variance = if (count > 1) {
        val meanDouble = total.toDouble() / count
        amounts.sumOf { val d = it - meanDouble; d * d } / (count - 1)
    } else 0.0
    val stdDev = dzRound(kotlin.math.sqrt(variance))

    // Best month — REAL calendar-month aggregation of the same slice
    // (UTC year*12+month keys, mirroring the desktop exactly).
    val byMonth = LinkedHashMap<Int, Long>()
    for (p in slice) {
        val t = parseIsoMs(p.collectedAt) ?: continue
        val d = Instant.ofEpochMilli(t).atZone(ZoneOffset.UTC)
        val key = d.year * 12 + (d.monthValue - 1)
        byMonth[key] = (byMonth[key] ?: 0L) + p.amount
    }
    var bestMonth: BestMonth? = null
    for ((key, amount) in byMonth) {
        if (bestMonth == null || amount > bestMonth.amount) {
            val monthIndex = ((key % 12) + 12) % 12
            bestMonth = BestMonth(MONTH_LABELS_FR[monthIndex], amount)
        }
    }

    return PaymentStats(
        count = count,
        total = total,
        mean = mean,
        median = medianOfSorted(sorted),
        stdDev = stdDev,
        min = if (count > 0) sorted.first() else 0L,
        max = if (count > 0) sorted.last() else 0L,
        bestMonth = bestMonth,
    )
}

// ============================================================================
// T-256 — amount histogram (half-open [lo, hi) bins, count + amount)
// ============================================================================

data class HistogramBin(val label: String, val count: Int, val amount: Long)

/** Distribution of payment amounts into the fixed centime bins. */
fun deriveAmountHistogram(slice: List<StatsPayment>): List<HistogramBin> {
    val counts = IntArray(AMOUNT_BINS.size)
    val amounts = LongArray(AMOUNT_BINS.size)
    for (p in slice) {
        val idx = AMOUNT_BINS.indexOfFirst { p.amount >= it.lo && p.amount < it.hi }
        if (idx != -1) {
            counts[idx] += 1
            amounts[idx] += p.amount
        }
    }
    return AMOUNT_BINS.mapIndexed { i, b -> HistogramBin(b.label, counts[i], amounts[i]) }
}

// ============================================================================
// T-256 — method / category mix (percent = Math.round share, desc amount sort)
// ============================================================================

data class MixSlice(
    val key: String,
    val label: String,
    val amount: Long,
    val count: Int,
    val percent: Int,
)

private fun deriveMix(
    slice: List<StatsPayment>,
    keyOf: (StatsPayment) -> String,
    labelOf: (String) -> String,
): List<MixSlice> {
    val agg = LinkedHashMap<String, LongArray>() // key → [amount, count]
    for (p in slice) {
        val k = keyOf(p)
        val cur = agg.getOrPut(k) { longArrayOf(0L, 0) }
        cur[0] += p.amount
        cur[1] += 1
    }
    val total = agg.values.sumOf { it[0] }
    return agg.entries.map { (key, v) ->
        MixSlice(
            key = key,
            label = labelOf(key),
            amount = v[0],
            count = v[1].toInt(),
            percent = if (total > 0) mathRound(v[0].toDouble() / total * 100).toInt() else 0,
        )
    }.sortedByDescending { it.amount }
}

/** Payment-method mix over the slice (Espèces / Chèque / Virement / …). */
fun deriveMethodMix(slice: List<StatsPayment>): List<MixSlice> =
    deriveMix(slice, { it.method }, { m ->
        when (m) {
            "cash" -> "Espèces"
            "check" -> "Chèque"
            "transfer" -> "Virement"
            else -> m
        }
    })

/**
 * Payment-category mix over the slice. `topN` merges the tail into
 * "Autres (N)" so the ranking stays readable (desktop default 6).
 */
fun deriveCategoryMix(slice: List<StatsPayment>, topN: Int = 6): List<MixSlice> {
    val all = deriveMix(slice, { it.category }, { paymentCategoryLabelFr(it) })
    if (all.size <= topN) return all
    val head = all.take(topN)
    val tail = all.drop(topN)
    val tailAmount = tail.sumOf { it.amount }
    val tailCount = tail.sumOf { it.count }
    val total = all.sumOf { it.amount }
    return head + MixSlice(
        key = "__tail__",
        label = "Autres (${tail.size})",
        amount = tailAmount,
        count = tailCount,
        percent = if (total > 0) mathRound(tailAmount.toDouble() / total * 100).toInt() else 0,
    )
}

// ============================================================================
// T-257 — top-debtors Pareto (top 8, cumulative % of the DISPLAYED total)
// ============================================================================

data class ParetoDatum(
    val name: String,
    val amount: Long,
    val cumPercent: Int,
)

fun derivePareto(
    topDebtors: List<ParetoDebtor>,
    topN: Int = 8,
): List<ParetoDatum> {
    val sorted = topDebtors
        .filter { it.outstandingAmount > 0L }
        .sortedByDescending { it.outstandingAmount }
        .take(topN)
    val total = sorted.sumOf { it.outstandingAmount }
    var cum = 0L
    return sorted.map { d ->
        cum += d.outstandingAmount
        ParetoDatum(
            name = d.parentName,
            amount = d.outstandingAmount,
            cumPercent = if (total > 0) mathRound(cum.toDouble() / total * 100).toInt() else 0,
        )
    }
}

data class ParetoDebtor(val parentName: String, val outstandingAmount: Long)

// ============================================================================
// The canonical INV-4 installment aging (desktop supabase-dashboard-repository
// debtByAgingForRange — per-installment, distinct-parent counts per bucket)
// ============================================================================

data class AgingBucketStat(
    val bucket: String,       // "0_30" | "31_60" | "61_90" | "91_180" | "180_plus"
    val label: String,
    val amount: Long,         // Σ remaining of the bucket's installments (centimes)
    val debtorCount: Int,     // DISTINCT parents with ≥1 installment in the bucket
)

/** The minimal installment projection the aging derivation consumes (centimes). */
data class StatsInstallment(
    val id: String,
    val parentId: String,
    val amountDue: Long,
    val amountPaid: Long,
    val amountPending: Long,
    val dueDate: String,      // ISO date (yyyy-mm-dd) or timestamp
    val status: String,       // "paid" | "pending" | "overdue" | …
)

/**
 * Canonical remaining per INV-4: clampNonNegative(due − paid − pending),
 * evaluated ONLY on unpaid installments (status !== "paid").
 */
fun installmentRemaining(inst: StatsInstallment): Long =
    if (inst.status == "paid") 0L
    else (inst.amountDue - inst.amountPaid - inst.amountPending).coerceAtLeast(0L)

/**
 * The canonical aging derivation (desktop Supabase path, migration 0042
 * "canonical_overdue_asof_equivalence"): per-INSTALLMENT remaining, overdue
 * depth from the installment's REAL dueDate via daysBetweenFloor, families
 * counted DISTINCTLY per bucket. A family with tranches in two buckets
 * counts in BOTH bucket counts (the recovery funnel consumes exactly this
 * census). Not-yet-due installments land in 0_30 (daysBetweenFloor clamps
 * negative to 0 — documented desktop behavior).
 */
fun deriveDebtAging(
    installments: List<StatsInstallment>,
    nowEpochMs: Long = System.currentTimeMillis(),
): List<AgingBucketStat> {
    val amountByBucket = HashMap<String, Long>()
    val parentsByBucket = HashMap<String, MutableSet<String>>()
    for (inst in installments) {
        val remaining = installmentRemaining(inst)
        if (remaining <= 0L) continue
        val days = daysBetweenFloor(inst.dueDate, nowEpochMs)
        val bucket = agingBucketFromDays(days)
        amountByBucket[bucket] = (amountByBucket[bucket] ?: 0L) + remaining
        parentsByBucket.getOrPut(bucket) { mutableSetOf() }.add(inst.parentId)
    }
    return AGING_BUCKETS_IN_ORDER.mapNotNull { bucket ->
        val amount = amountByBucket[bucket] ?: return@mapNotNull null
        AgingBucketStat(
            bucket = bucket,
            label = AGING_BUCKET_LABELS_FR.getValue(bucket),
            amount = amount,
            debtorCount = parentsByBucket[bucket]?.size ?: 0,
        )
    }
}

// ============================================================================
// T-257 — aging composition (100% stacked shares) + recovery funnel
// ============================================================================

data class AgingSegment(
    val bucket: String,
    val label: String,
    val amount: Long,
    val debtorCount: Int,
    /** Share of the total outstanding, in percent (Math.round'd). */
    val share: Int,
)

/** The aging composition as normalized segments (the 100% stacked chart). */
fun deriveAgingComposition(debtAging: List<AgingBucketStat>): List<AgingSegment> {
    val present = debtAging.associateBy { it.bucket }
    val total = debtAging.sumOf { it.amount }
    return AGING_BUCKETS_IN_ORDER
        .filter { present.containsKey(it) }
        .map { bucket ->
            val b = present.getValue(bucket)
            AgingSegment(
                bucket = bucket,
                label = b.label,
                amount = b.amount,
                debtorCount = b.debtorCount,
                share = if (total > 0) mathRound(b.amount.toDouble() / total * 100).toInt() else 0,
            )
        }
        .filter { it.amount > 0L || it.debtorCount > 0 }
}

data class FunnelStage(
    val name: String,        // "En retard" | "≤ 60 j" | "61–90 j" | "> 90 j"
    val count: Int,
    val sharePct: Int,       // share of the TOTAL overdue families (stage-1 base)
)

/**
 * The recovery funnel from the aging census (desktop recovery-funnel-card
 * deriveRecoveryFunnel): total = Σ all bucket family counts; ≤60j = 0_30+31_60;
 * 61–90j = 61_90; >90j = 91_180+180_plus. Empty input → empty list (honest —
 * NEVER a fabricated "100% critical").
 */
fun deriveRecoveryFunnel(debtAging: List<AgingBucketStat>): List<FunnelStage> {
    val byBucket = debtAging.associate { it.bucket to it.debtorCount }
    val total =
        (byBucket["0_30"] ?: 0) + (byBucket["31_60"] ?: 0) + (byBucket["61_90"] ?: 0) +
            (byBucket["91_180"] ?: 0) + (byBucket["180_plus"] ?: 0)
    if (total == 0) return emptyList()
    fun pct(n: Int): Int = mathRound(n.toDouble() / total * 100).toInt()
    val under60 = (byBucket["0_30"] ?: 0) + (byBucket["31_60"] ?: 0)
    val over90 = (byBucket["91_180"] ?: 0) + (byBucket["180_plus"] ?: 0)
    return listOf(
        FunnelStage("En retard", total, 100),
        FunnelStage("≤ 60 j", under60, pct(under60)),
        FunnelStage("61–90 j", byBucket["61_90"] ?: 0, pct(byBucket["61_90"] ?: 0)),
        FunnelStage("> 90 j", over90, pct(over90)),
    )
}

// ============================================================================
// The collection rate (desktop insights-rail / see-details-modal formula)
// ============================================================================

/**
 * Taux de Recouvrement Annuel = encaissé / (encaissé + créances ouvertes),
 * Math.round'd, clamped to 100 (the insights-rail convention). 0 when the
 * denominator is 0. Computed from REAL repository aggregates only — the UI
 * never re-derives it (and never falls back to 0.49f — PARITY-002).
 */
fun collectionRatePct(annualRevenueCentimes: Long, outstandingDebtCentimes: Long): Int {
    val totalExpected = annualRevenueCentimes + outstandingDebtCentimes
    if (totalExpected <= 0L) return 0
    val pct = mathRound(annualRevenueCentimes.toDouble() / totalExpected * 100).toInt()
    return pct.coerceAtMost(100)
}

// ============================================================================
// T-255 — revenue trend (cumulative + 3-month moving average)
// ============================================================================

data class RevenueTrendPoint(
    val label: String,
    val amount: Long,
    val cumulative: Long,
    val movingAvg3: Long?,   // null until the 3rd point — never fabricated
)

/** The hero trend derivation: monthly series + running total + 3-month MA. */
fun deriveRevenueTrend(revenue: List<RevenuePointInput>): List<RevenueTrendPoint> {
    var cumulative = 0L
    return revenue.mapIndexed { i, r ->
        cumulative += r.amount
        val movingAvg3 = if (i >= 2) {
            dzRound((revenue[i - 2].amount + revenue[i - 1].amount + r.amount).toDouble() / 3)
        } else null
        RevenueTrendPoint(label = r.label, amount = r.amount, cumulative = cumulative, movingAvg3 = movingAvg3)
    }
}

data class RevenuePointInput(val label: String, val amount: Long)

// ============================================================================
// Class roll-call statistics (desktop supabase-dashboard-repository
// attendanceToday: present+late / total — the canonical attendance rate)
// ============================================================================

/**
 * The canonical daily attendance rate: (present + late) / total records,
 * as a 0..100 percent. Returns null when there are no records today (the
 * caller renders the honest "no roll call" state — the desktop Supabase
 * path defaults the KPI card to 1.0, the DESKTOP MOCK to 0; the honest
 * empty signal is the nullable contract).
 */
fun attendanceRatePct(records: List<String>): Int? {
    if (records.isEmpty()) return null
    val presentish = records.count { it == "present" || it == "late" }
    return mathRound(presentish.toDouble() / records.size * 100).toInt()
}

// ============================================================================
// PARITY-003 (45th session, 2026-09-11) — the visual-parity derivations.
//
// MIRRORED FROM (ADR-002 — ported verbatim, divergent hand-copies forbidden):
//   `elimtiyaz-desktop/src/features/dashboard/components/weekly-operating-rhythm.tsx`
//   (deriveWeeklyRhythm)
//   `elimtiyaz-desktop/src/features/dashboard/components/analytics/analytics-derivations.ts`
//   (inRange, applyAnalyticsFilters, presentCategories, deriveFilteredMonthly,
//    deriveCollectionHeatmap, deriveYearOverYear, shiftIsoYearBack,
//    previousAcademicYear)
//   `elimtiyaz-desktop/src/features/financials/installment-schedule-tab.tsx`
//   (trancheNumberOf, deriveTrancheWaves) + `domain/calc/payment/sums.ts` +
//   `domain/calc/payment/queries.ts` (totalOutstanding)
//   `elimtiyaz-desktop/src/infrastructure/supabase/repositories/supabase-dashboard-repository.ts`
//   (demographics — grade/gender/age/capacity)
//   `elimtiyaz-desktop/src/domain/model/student.ts` (GRADE_LEVEL_LABELS_FR)
//
// Same units + rounding discipline as the rest of this file: centimes,
// mathRound (dimensionless) / dzRound (money-valued), NEVER integer division.
// ============================================================================

/** An inclusive [from, to] ISO yyyy-mm-dd date range (desktop DateRange). */
data class StatsDateRange(val from: String, val to: String)

/**
 * Is the payment inside [range.from 00:00, range.to 23:59:59] (UTC)?
 * (desktop analytics-derivations.ts inRange — verbatim).
 */
fun inRange(p: StatsPayment, range: StatsDateRange?): Boolean {
    if (range == null) return true
    val from = parseIsoMs(range.from.take(10).let { if (it.length == 10) "${it}T00:00:00Z" else it }) ?: return false
    val to = parseIsoMs("${range.to.take(10)}T23:59:59Z") ?: parseIsoMs(range.to) ?: return false
    val t = parseIsoMs(p.collectedAt) ?: return false
    if (t < from) return false
    if (t > to) return false
    return true
}

/**
 * Shift an ISO yyyy-mm-dd back one calendar year (leap-day safe: Feb 29 →
 * Feb 28). Desktop shiftIsoYearBack — verbatim.
 */
fun shiftIsoYearBack(iso: String): String {
    val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(iso) ?: return iso
    val (year, month, day) = m.destructured
    val safeDay = if (month == "02" && day == "29") "28" else day
    return "${year.toInt() - 1}-$month-$safeDay"
}

/** "2025-2026" → "2024-2025" (null when the pattern doesn't match). */
fun previousAcademicYear(code: String): String? {
    val m = Regex("^(\\d{4})-(\\d{4})$").find(code) ?: return null
    val (a, b) = m.destructured
    return "${a.toInt() - 1}-${b.toInt() - 1}"
}

// ============================================================================
// Slicer filtering (the cross-filtering engine — desktop Power BI semantics)
// ============================================================================

/**
 * The canonical analytics slice: PAID payments, inside the range, matching
 * the slicer selections. Empty sets = ALL methods/categories included.
 * (desktop applyAnalyticsFilters — verbatim semantics.)
 */
fun applyAnalyticsFilters(
    payments: List<StatsPayment>,
    range: StatsDateRange?,
    methods: Set<String>,
    categories: Set<String>,
): List<StatsPayment> = payments.filter { p ->
    if (p.status != "paid") return@filter false
    if (!inRange(p, range)) return@filter false
    if (methods.isNotEmpty() && p.method !in methods) return@filter false
    if (categories.isNotEmpty() && p.category !in categories) return@filter false
    true
}

/**
 * Categories actually present in the unfiltered paid slice (slicer chips),
 * sorted by FR label (desktop localeCompare("fr") ≈ accent-folded,
 * case-insensitive comparison — deterministic in Kotlin).
 */
fun presentCategories(payments: List<StatsPayment>, range: StatsDateRange?): List<String> {
    val set = applyAnalyticsFilters(payments, range, emptySet(), emptySet())
        .mapTo(mutableSetOf()) { it.category }
    return set.sortedWith(compareBy { frenchLabelCollationKey(paymentCategoryLabelFr(it)) })
}

/** Accent-folded lowercase comparator key (the Kotlin stand-in for localeCompare("fr")). */
internal fun frenchLabelCollationKey(label: String): String = label
    .lowercase()
    .replace("é", "e").replace("è", "e").replace("ê", "e").replace("ë", "e")
    .replace("à", "a").replace("â", "a")
    .replace("î", "i").replace("ï", "i")
    .replace("ô", "o")
    .replace("ù", "u").replace("û", "u")
    .replace("ç", "c")

// ============================================================================
// T-243 — weekly operating rhythm (the Algerian school week, stacked by method)
// ============================================================================

/** The Algerian school week — Dimanche à Jeudi (desktop SCHOOL_WEEK — verbatim). */
val SCHOOL_WEEK_ROWS: List<Pair<String, Int>> = listOf(
    "Dim" to 0, "Lun" to 1, "Mar" to 2, "Mer" to 3, "Jeu" to 4,
)

/** The canonical stacked-method series (desktop METHODS order). */
val WEEKLY_METHODS: List<String> = listOf("cash", "check", "transfer")

/** Payment-method FR labels (desktop PAYMENT_METHOD_LABELS_FR — verbatim). */
fun paymentMethodLabelFr(method: String): String = when (method) {
    "cash" -> "Espèces"
    "check" -> "Chèque"
    "transfer" -> "Virement"
    else -> method
}

data class WeeklyRhythmDatum(
    val day: String,          // "Dim" | "Lun" | "Mar" | "Mer" | "Jeu"
    val cash: Long,           // centimes
    val check: Long,
    val transfer: Long,
) {
    val total: Long get() = cash + check + transfer
}

/**
 * Derive the weekday × method collection matrix from REAL payments.
 * (desktop deriveWeeklyRhythm — verbatim, including its CONVENTION:)
 * counter-activity view — a payment is counted when recorded at the
 * counter; ONLY `status === "refunded"` is excluded (pending/partial ARE
 * counted — deliberately different from the paid-only encaissé slice of
 * the Analytics tab; the two views answer different questions).
 * Friday/Saturday fall outside the Algerian school week and are dropped.
 */
fun deriveWeeklyRhythm(
    payments: List<StatsPayment>,
    range: StatsDateRange?,
): List<WeeklyRhythmDatum> {
    val fromTs = range?.let { parseIsoMs("${it.from.take(10)}T00:00:00Z") }
    val toTs = range?.let { parseIsoMs("${it.to.take(10)}T23:59:59Z") }
    val cells = Array(5) { LongArray(3) } // [dayIdx][methodIdx]
    for (p in payments) {
        if (p.status == "refunded") continue
        val ts = parseIsoMs(p.collectedAt) ?: continue
        if (fromTs != null && ts < fromTs) continue
        if (toTs != null && ts > toTs) continue
        // JS getUTCDay(): 0=Sunday…6=Saturday
        val jsDay = Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC).dayOfWeek.let { it.value % 7 }
        val dayIdx = SCHOOL_WEEK_ROWS.indexOfFirst { it.second == jsDay }
        if (dayIdx == -1) continue // Fri/Sat — outside the school week
        val methodIdx = WEEKLY_METHODS.indexOf(p.method)
        if (methodIdx == -1) continue // non-canonical method — no stack slot (desktop adds only named keys)
        cells[dayIdx][methodIdx] += p.amount
    }
    return SCHOOL_WEEK_ROWS.mapIndexed { i, (day, _) ->
        WeeklyRhythmDatum(day = day, cash = cells[i][0], check = cells[i][1], transfer = cells[i][2])
    }
}

// ============================================================================
// T-256 — collection heatmap (weekday × calendar-month matrix)
// ============================================================================

data class HeatmapCellStat(
    val amount: Long,   // centimes
    val count: Int,
    /** 0–4 intensity level (0 = empty). Quantized against the matrix max. */
    val level: Int,
)

data class HeatmapRowStat(
    val day: String,                    // "Dim"… "Jeu"
    val cells: List<HeatmapCellStat>,   // parallel to monthLabels
    val rowTotal: Long,
)

data class CollectionHeatmapStat(
    /** Month columns in range order ("Sep", "Oct", …). */
    val monthLabels: List<String>,
    /** Parallel year-month keys ("2025-09") for honest tooltips. */
    val monthKeys: List<String>,
    val rows: List<HeatmapRowStat>,
    val max: Long,
    val monthTotals: List<Long>,
)

/**
 * The Power BI matrix heatmap: encaissé per (school-weekday × calendar
 * month) over the range. Rows follow the Algerian school week (Dim→Jeu);
 * Friday/Saturday collections are excluded. Cell level quantizes the
 * amount against the matrix max in 5 steps.
 * (desktop deriveCollectionHeatmap — verbatim.)
 */
fun deriveCollectionHeatmap(
    slice: List<StatsPayment>,
    range: StatsDateRange?,
): CollectionHeatmapStat {
    // Month columns: cursor over the range (Sep 2025 → Jun 2026 …).
    val monthLabels = mutableListOf<String>()
    val monthKeys = mutableListOf<String>()
    val monthIndexByKey = HashMap<String, Int>()
    if (range != null) {
        val from = parseIsoMs("${range.from.take(10)}T00:00:00Z")
        val to = parseIsoMs("${range.to.take(10)}T23:59:59Z") ?: parseIsoMs(range.to)
        if (from != null && to != null && to > from) {
            var cursor = Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1)
            var guard = 0
            while (guard < 24 && !cursor.atStartOfDay(ZoneOffset.UTC).toInstant()
                .isAfter(Instant.ofEpochMilli(to))
            ) {
                val key = "${cursor.year}-${"%02d".format(cursor.monthValue)}"
                monthIndexByKey[key] = monthLabels.size
                monthLabels.add(MONTH_LABELS_FR[cursor.monthValue - 1])
                monthKeys.add(key)
                cursor = cursor.plusMonths(1)
                guard++
            }
        }
    }

    val nCols = monthLabels.size
    val cells = Array(5) { Array(nCols) { longArrayOf(0L, 0) } } // [amount, count]
    val monthTotals = LongArray(nCols)
    var max = 0L

    for (p in slice) {
        val t = parseIsoMs(p.collectedAt) ?: continue
        val d = Instant.ofEpochMilli(t).atZone(ZoneOffset.UTC)
        val jsDay = d.dayOfWeek.value % 7
        val dayIdx = SCHOOL_WEEK_ROWS.indexOfFirst { it.second == jsDay }
        if (dayIdx == -1) continue
        val key = "${d.year}-${"%02d".format(d.monthValue)}"
        val col = monthIndexByKey[key] ?: continue
        cells[dayIdx][col][0] += p.amount
        cells[dayIdx][col][1] += 1
        monthTotals[col] += p.amount
        if (cells[dayIdx][col][0] > max) max = cells[dayIdx][col][0]
    }

    val rows = SCHOOL_WEEK_ROWS.mapIndexed { i, (day, _) ->
        HeatmapRowStat(
            day = day,
            cells = cells[i].map { c ->
                HeatmapCellStat(
                    amount = c[0],
                    count = c[1].toInt(),
                    level = if (max > 0 && c[0] > 0) maxOf(1, kotlin.math.ceil(c[0].toDouble() / max * 4).toInt()) else 0,
                )
            },
            rowTotal = cells[i].sumOf { it[0] },
        )
    }
    return CollectionHeatmapStat(monthLabels, monthKeys, rows, max, monthTotals.toList())
}

// ============================================================================
// T-255 — filtered monthly overlay (the slicers' visible effect on the trend)
// ============================================================================

/**
 * Monthly encaissé derived from the FILTERED payments slice, aligned to the
 * repository series' month labels (by calendar-month index — academic-year
 * ranges never repeat a calendar month). (desktop deriveFilteredMonthly.)
 */
fun deriveFilteredMonthly(
    slice: List<StatsPayment>,
    monthLabels: List<String>,
): List<Long> {
    val positionByMonthIndex = HashMap<Int, Int>()
    monthLabels.forEachIndexed { i, label ->
        val idx = MONTH_INDEX_BY_LABEL[label]
        if (idx != null) positionByMonthIndex[idx] = i
    }
    val out = LongArray(monthLabels.size)
    for (p in slice) {
        val t = parseIsoMs(p.collectedAt) ?: continue
        val monthIndex = Instant.ofEpochMilli(t).atZone(ZoneOffset.UTC).monthValue - 1
        val pos = positionByMonthIndex[monthIndex] ?: continue
        out[pos] += p.amount
    }
    return out.toList()
}

// ============================================================================
// T-257 — year-over-year comparison (like-for-like months)
// ============================================================================

data class YoYPoint(
    val label: String,
    val current: Long,      // centimes
    val previous: Long,
    val deltaPercent: Int?, // null when previous == 0 (never a −100% trend)
)

data class YoYSummary(
    val points: List<YoYPoint>,
    val totalCurrent: Long,
    val totalPrevious: Long,
    val deltaPercent: Int?,
)

/**
 * Align the current-year monthly series with the PREVIOUS year's series by
 * month label. Delta is null where the previous amount is 0 (a divide-by-
 * zero is not a −100% trend — §15.16 honesty). (desktop deriveYearOverYear.)
 */
fun deriveYearOverYear(
    current: List<RevenuePointInput>,
    previous: List<RevenuePointInput>,
): YoYSummary {
    val prevByLabel = previous.associate { it.label to it.amount }
    val points = current.map { c ->
        val prev = prevByLabel[c.label] ?: 0L
        YoYPoint(
            label = c.label,
            current = c.amount,
            previous = prev,
            deltaPercent = if (prev > 0) mathRound((c.amount - prev).toDouble() / prev * 100).toInt() else null,
        )
    }
    val totalCurrent = points.sumOf { it.current }
    val totalPrevious = points.sumOf { it.previous }
    return YoYSummary(
        points = points,
        totalCurrent = totalCurrent,
        totalPrevious = totalPrevious,
        deltaPercent = if (totalPrevious > 0) {
            mathRound((totalCurrent - totalPrevious).toDouble() / totalPrevious * 100).toInt()
        } else null,
    )
}

// ============================================================================
// T-248 — tranche wave progress (T1 / T2 / T3 collection health)
// ============================================================================

/**
 * The minimal installment projection for the tranche waves (desktop
 * Installment fields used by deriveTrancheWaves: label + the three sums).
 */
data class StatsTrancheRow(
    val label: String,          // "Tranche 1", "Tranche 2 (Jan–Mar)", "Année complète", …
    val amountDue: Long,        // centimes
    val amountPaid: Long,       // Σ allocated (includes uncleared checks)
    val amountPending: Long,    // uncleared non-cash funds on the tranche
)

/**
 * T-248 — canonical tranche-number matcher (labels: "Tranche 1",
 * "Tranche 2 (Jan–Mar)", … — never a bare substring match, which would
 * also catch "Tranche 10" or "Année complète 1"). Returns 1/2/3 or null.
 * (desktop trancheNumberOf — verbatim.)
 */
fun trancheNumberOf(label: String): Int? {
    val m = Regex("^\\s*Tranche\\s*([1-3])\\b", RegexOption.IGNORE_CASE).find(label) ?: return null
    return m.groupValues[1].toInt()
}

data class TrancheWave(
    val index: Int,             // 1 | 2 | 3
    val label: String,          // "Tranche 1 (Septembre)" …
    val hint: String,           // due-window hint (display-only)
    val due: Long,              // Σ amountDue (centimes)
    val paid: Long,             // Σ amountPaid (includes uncleared checks)
    val pending: Long,          // Σ amountPending
    val pct: Int,               // min(100, Math.round(paid/due×100))
    val isNextTarget: Boolean,  // first wave with remaining > 0
)

/** Canonical remaining per INV-4 over a wave's rows (desktop totalOutstanding). */
private fun waveOutstanding(rows: List<StatsTrancheRow>): Long =
    (rows.sumOf { it.amountDue } - rows.sumOf { it.amountPaid } - rows.sumOf { it.amountPending })
        .coerceAtLeast(0L)

/**
 * T-248 — derive the T1/T2/T3 collection waves from REAL rows (desktop
 * deriveTrancheWaves — verbatim): per wave due (Σ amountDue), paid (Σ
 * amountPaid — INCLUDES uncleared checks, the display convention), pending
 * (Σ amountPending), pct = min(100, Math.round(paid/due×100));
 * isNextTarget marks the first wave with a canonical remaining balance.
 */
fun deriveTrancheWaves(rows: List<StatsTrancheRow>): List<TrancheWave> {
    val groups = HashMap<Int, MutableList<StatsTrancheRow>>()
    for (r in rows) {
        val n = trancheNumberOf(r.label) ?: continue
        groups.getOrPut(n) { mutableListOf() }.add(r)
    }
    val firstWithRemaining = groups.entries
        .filter { waveOutstanding(it.value) > 0L }
        .map { it.key }
        .sorted()
        .firstOrNull()
    val meta = listOf(
        Triple(1, "Tranche 1 (Septembre)", "échéance 15 sep — à l'inscription"),
        Triple(2, "Tranche 2 (Décembre)", "échéance 15 déc"),
        Triple(3, "Tranche 3 (Mars)", "échéance 15 mars"),
    )
    return meta.map { (index, label, hint) ->
        val list = groups[index] ?: emptyList()
        val due = list.sumOf { it.amountDue }
        val paid = list.sumOf { it.amountPaid }
        val pending = list.sumOf { it.amountPending }
        val pct = if (due > 0) minOf(100, mathRound(paid.toDouble() / due * 100).toInt()) else 0
        TrancheWave(
            index = index, label = label, hint = hint,
            due = due, paid = paid, pending = pending,
            pct = pct, isNextTarget = index == firstWithRemaining,
        )
    }
}

// ============================================================================
// Class demographics & capacity (desktop SupabaseDashboardRepository.demographics)
// ============================================================================

/** The canonical grade-level FR labels (desktop GRADE_LEVEL_LABELS_FR — verbatim). */
val GRADE_LEVEL_LABELS_FR: Map<String, String> = mapOf(
    "prescolaire_1" to "Préscolaire 01",
    "prescolaire_2" to "Préscolaire 02",
    "1ap" to "1AP",
    "2ap" to "2AP",
    "3ap" to "3AP",
    "4ap" to "4AP",
    "5ap" to "5AP",
    "1am" to "1AM",
    "2am" to "2AM",
    "3am" to "3AM",
    "4am" to "4AM",
    "1ere_annee" to "1ère Année",
    "2eme_annee" to "2ème Année",
    "3eme_annee" to "3ème Année",
)

/** The minimal student projection demographics consume (desktop students row). */
data class StatsStudentRow(
    val gender: String,        // "male" | "female" | other → Non spécifié
    val birthDate: String?,    // ISO date (nullable — skipped when null)
    val classId: String?,      // null → "Non assigné" grade
)

/** The minimal class projection demographics consume (desktop classes row). */
data class StatsClassRow(
    val id: String,
    val name: String,
    val gradeCode: String?,    // canonical code when present, else name fallback
    val capacity: Int?,        // null or ≤0 → default 30 (desktop convention)
)

data class DemographicSlice(
    val label: String,
    val count: Int,
    val percent: Int,          // Math.round(count/total × 100)
)

data class DemographicsStats(
    val grade: List<DemographicSlice>,
    val gender: List<DemographicSlice>,
    val age: List<DemographicSlice>,
    val capacity: List<DemographicSlice>,
)

/**
 * Desktop demographics() — verbatim: grade distribution derived from each
 * student's CLASS (GRADE_LEVEL_LABELS_FR[code] → class name → "Non assigné"
 * fallback chain); gender (Garçons / Filles / Non spécifié added only when
 * > 0); age buckets (< 6 / 6–8 / 9–11 / 12–14 / 15–17 / 18+ ans, year-only
 * arithmetic on birth YEAR); capacity per class (cap ≤ 0 or null → 30,
 * percent = Math.round(count/cap × 100)). totalStudents = active students,
 * or 1 when empty (desktop divides by `students.length || 1`).
 */
fun deriveDemographics(
    students: List<StatsStudentRow>,
    classes: List<StatsClassRow>,
    currentYear: Int = java.time.Year.now(ZoneOffset.UTC).value,
): DemographicsStats {
    val totalStudents = students.size.let { if (it > 0) it else 1 }

    val classMap = classes.associateBy(
        keySelector = { it.id },
        valueTransform = { c -> StatsClassRow(c.id, c.name.ifBlank { c.id }, c.gradeCode, c.capacity) },
    )
    val classStudentCounts = HashMap<String, Int>()
    for (s in students) {
        if (s.classId != null) {
            classStudentCounts[s.classId] = (classStudentCounts[s.classId] ?: 0) + 1
        }
    }

    // Grade distribution (derived from the student's class)
    val gradeCounts = LinkedHashMap<String, Int>()
    for (s in students) {
        val cls = s.classId?.let { classMap[it] }
        val gradeKey = when {
            cls == null -> "Non assigné"
            cls.gradeCode != null && GRADE_LEVEL_LABELS_FR.containsKey(cls.gradeCode) ->
                GRADE_LEVEL_LABELS_FR.getValue(cls.gradeCode!!)
            else -> cls.name
        }
        gradeCounts[gradeKey] = (gradeCounts[gradeKey] ?: 0) + 1
    }
    val grade = gradeCounts.map { (label, count) ->
        DemographicSlice(label, count, mathRound(count.toDouble() / totalStudents * 100).toInt())
    }

    // Gender distribution
    var maleCount = 0
    var femaleCount = 0
    var unspecifiedCount = 0
    for (s in students) {
        when (s.gender) {
            "male" -> maleCount++
            "female" -> femaleCount++
            else -> unspecifiedCount++
        }
    }
    val gender = mutableListOf(
        DemographicSlice("Garçons", maleCount, mathRound(maleCount.toDouble() / totalStudents * 100).toInt()),
        DemographicSlice("Filles", femaleCount, mathRound(femaleCount.toDouble() / totalStudents * 100).toInt()),
    )
    if (unspecifiedCount > 0) {
        gender.add(DemographicSlice("Non spécifié", unspecifiedCount, mathRound(unspecifiedCount.toDouble() / totalStudents * 100).toInt()))
    }

    // Age distribution (year-only arithmetic — desktop convention)
    val ageBuckets = listOf(
        Triple("< 6 ans", 0, 5), Triple("6–8 ans", 6, 8), Triple("9–11 ans", 9, 11),
        Triple("12–14 ans", 12, 14), Triple("15–17 ans", 15, 17), Triple("18+ ans", 18, 120),
    ).map { it to 0 }.toMutableList() // (Triple<label,min,max>, count)

    for (s in students) {
        val dob = s.birthDate ?: continue
        val birthYear = parseIsoMs(dob.take(10).let { if (it.length == 10) "${it}T00:00:00Z" else it })
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).year } ?: continue
        val ageYears = currentYear - birthYear
        val idx = ageBuckets.indexOfFirst { (bucket, _) -> ageYears >= bucket.second && ageYears <= bucket.third }
        if (idx != -1) ageBuckets[idx] = ageBuckets[idx].first to (ageBuckets[idx].second + 1)
    }
    val age = ageBuckets.map { (bucket, count) ->
        DemographicSlice(bucket.first, count, mathRound(count.toDouble() / totalStudents * 100).toInt())
    }

    // Capacity distribution (per class, ordered by name — desktop classes order)
    val capacity = classes.map { c ->
        val count = classStudentCounts[c.id] ?: 0
        val cap = if (c.capacity != null && c.capacity > 0) c.capacity else 30
        DemographicSlice(
            label = c.name.ifBlank { c.id },
            count = count,
            percent = mathRound(count.toDouble() / cap * 100).toInt(),
        )
    }

    return DemographicsStats(grade, gender, age, capacity)
}

