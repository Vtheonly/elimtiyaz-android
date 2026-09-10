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
