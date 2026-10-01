package com.example.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TreeMap

/**
 * ExecutiveStatistics — the Kotlin mirror of the desktop's canonical
 * executive-statistics derivation layer (T-340, 61st session, 2026-09-14 —
 * STATS-400).
 *
 * MIRRORED FROM (ADR-002 — ported verbatim, divergent hand-copies forbidden):
 *   `elimtiyaz-desktop/src/features/dashboard/components/analytics/executive-statistics.ts`
 *   (source commit 256bfa4, T-338 / 61st session)
 *   `elimtiyaz-desktop/src/features/dashboard/components/analytics/operational-query-engine.ts`
 *   (evaluateStudentRiskProfiles — the triple-risk thresholds)
 *   `elimtiyaz-desktop/src/domain/calc/pricing/transport.ts`
 *   (TOWN_ALIASES + normalizeTransportTier)
 *
 * The owner's mandate: identical statistics on BOTH platforms from ONE
 * canonical derivation — the corpus category `executive_statistics`
 * (financial-tests/equivalence/scenarios/executive_statistics_*.json)
 * pins desktop ≡ android centime-exact on EVERY value.
 *
 * UNITS: all monetary values are CENTIMES (Long) — the Android engine's
 * native representation. Percentages replicate the desktop's Math.round
 * (round-half-up — [mathRound], PARITY-001: NEVER Kotlin integer division).
 * Every "now"-dependent derivation takes an EXPLICIT nowEpochMs parameter
 * (deterministic — the corpus pins it; Date.now() inside a derivation is
 * non-reproducible across runners).
 *
 * VANITY-PURGE COMPANION (T-339 desktop / T-340 Android): the payment-amount
 * histogram, the weekday collection heatmap, the smooth 12-month revenue
 * spline, and the class-capacity gauges were REMOVED on both platforms per
 * the owner's kill list. This file carries their REPLACEMENTS.
 */

// ============================================================================
// Input contracts (centimes)
// ============================================================================

/**
 * The installment projection the executive statistics consume. Extends the
 * aging contract with the wave identity (trancheNumber — the canonical
 * `installments.tranche_number` column, NEVER label parsing) and the billing
 * category.
 */
data class ExecInstallment(
    val id: String,
    val parentId: String,
    val category: String = "tuition",
    val trancheNumber: Int = 1,     // 1 | 2 | 3
    val amountDue: Long,
    val amountPaid: Long,
    val amountPending: Long,
    val dueDate: String,
    val status: String,
)

/** The ledger projection the discount-erosion derivation consumes. */
data class ExecLedgerEntry(
    val id: String,
    val parentId: String,
    val category: String,
    val amount: Long,               // centimes, signed (+ debit / − credit)
    val type: String,               // "charge" | "payment" | "adjustment" | …
    val description: String,
    /** Parsed metadata (Room stores JSON text; the mapper parses). */
    val metadata: Map<String, Any?> = emptyMap(),
)

/** The student projection (transport + family identity). */
data class ExecStudent(
    val id: String,
    val parentId: String,
    val status: String = "active",
    val transportTier: String? = null,
)

/** The class projection (section-imbalance detector). */
data class ExecClass(
    val id: String,
    val name: String,
    val gradeCode: String,
    val isActive: Boolean = true,
    val enrolledCount: Int = 0,
)

/** The payment projection (service yield). */
data class ExecPayment(
    val id: String,
    val amount: Long,               // centimes
    val status: String,
    val category: String,
    val studentId: String? = null,
)

// ============================================================================
// Shared helpers (mirrors of the TS file's exported helpers)
// ============================================================================

private const val DAY_MS = 86_400_000L

/** Parse an ISO date/timestamp to epoch-ms; null when invalid. */
internal fun execTsOf(iso: String): Long? = try {
    if (iso.length > 10) Instant.parse(iso).toEpochMilli()
    else LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
} catch (_: Exception) {
    null
}

/**
 * The desktop's `new Date(ms).toISOString()` mirror — ALWAYS 3-digit
 * milliseconds ("2025-09-15T00:00:00.000Z"). Kotlin's Instant.toString()
 * drops trailing zero millis, which broke the corpus string comparison —
 * this formatter pins the desktop convention.
 */
private val ISO_MILLIS_FORMATTER = java.time.format.DateTimeFormatter
    .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
    .withZone(ZoneOffset.UTC)

internal fun formatIsoMillis(epochMs: Long): String = ISO_MILLIS_FORMATTER.format(Instant.ofEpochMilli(epochMs))

/**
 * Whole days between an ISO date and an epoch-ms "now", floored toward zero
 * (a tranche due TODAY is 0 days overdue, never 1 — the T-284/T-285
 * daysBetweenFloor convention).
 */
fun execDaysBetweenFloor(earlierIso: String, laterEpochMs: Long): Long {
    val earlier = execTsOf(earlierIso) ?: return 0L
    return Math.floor((laterEpochMs - earlier).toDouble() / DAY_MS).toLong()
}

/** INV-4 canonical remaining (mirrors the TS installmentRemaining). */
fun execInstallmentRemaining(
    amountDue: Long,
    amountPaid: Long,
    amountPending: Long,
    status: String,
): Long =
    if (status == "paid") 0L
    else (amountDue - amountPaid - amountPending).coerceAtLeast(0L)

/** Round-half-up percentage share (the PARITY-001 pinned convention). */
fun execSharePct(part: Long, total: Long): Int =
    if (total > 0) mathRound(part.toDouble() / total * 100).toInt() else 0

// ============================================================================
// 1. Tranche-wave collection velocity (Vélocité par Vague)
// ============================================================================

enum class WavePhase { NOT_DUE, IN_WINDOW, OVERDUE }

data class ExecTrancheWave(
    val key: String,                // "tuition#1" …
    val category: String,
    val wave: Int,                  // 1 | 2 | 3
    val installmentCount: Int,
    val paidCount: Int,
    val familyCount: Int,
    val debtorFamilyCount: Int,
    /**
     * T-451 (T-427/DATA-048 mirror): distinct families with an unsettled,
     * STILL-OWING row whose due date is STRICTLY PAST — the wave's
     * actually-late families. `debtorFamilyCount` counts every owing
     * family regardless of due date (a future T2/T3 tranche's current
     * balance is "non soldée", NOT "en retard").
     */
    val overdueDebtorFamilyCount: Int,
    val dueTotal: Long,             // centimes
    val paidTotal: Long,
    val remainingTotal: Long,
    val collectedPct: Int,          // round(paidTotal / dueTotal × 100)
    val clearedPct: Int,            // round(paidCount / installmentCount × 100)
    val dueDate: String?,           // earliest due date ISO (null when empty)
    /**
     * T-451 (T-435/UI-317 mirror): LATEST due date in the wave (ISO) —
     * with [dueDate] the wave's due-date RANGE (the spread the card
     * renders when rows drifted off the official schedule; equal on the
     * official schedule where every row of a wave shares one date).
     */
    val dueDateMax: String?,
    val phase: WavePhase,
)

/**
 * T-451 (T-424/DATA-042 mirror) — THE canonical tranche-settled predicate,
 * INV-4 family: a tranche is settled when its status says paid OR nothing
 * remains to collect (`due − paid − pending` clamped at 0 — uncleared
 * pending funds count as coverage, exactly like the remaining). One
 * predicate for EVERY surface — per-surface status-only checks render
 * DIFFERENT verdicts for the same row (an uncleared cheque covering a
 * tranche: "settled" on one surface, "not paid" on another).
 */
fun execIsInstallmentSettled(
    status: String,
    amountDue: Long,
    amountPaid: Long,
    amountPending: Long,
): Boolean {
    if (status == "paid") return true
    return (amountDue - amountPaid - amountPending).coerceAtLeast(0L) == 0L
}

private fun waveCategoryRank(category: String): Int = when (category) {
    "tuition" -> 0
    "transport" -> 1
    else -> 2
}

/**
 * The three seasonal cash surges — per (category × trancheNumber) wave
 * (mirrors deriveTrancheWaves + the canonical deriveTrancheWaveStats
 * grouping, T-424/T-425/T-427/T-435): groups by the CANONICAL
 * tranche_number, never label parsing, and — the T-425 official model —
 * NON-WAVE rows (tranche 0 = the registration fee FI, out-of-range
 * legacy values) are EXCLUDED, never coerced into a wave. A wave is
 * OVERDUE when any unsettled row's due date is past `now`; NOT_DUE when
 * every unsettled remainder is in the future; IN_WINDOW otherwise
 * (fully-collected waves report IN_WINDOW — complete, 100%). Settled
 * follows the ONE canonical predicate [execIsInstallmentSettled], never
 * a per-surface status check.
 */
fun deriveExecTrancheWaves(
    installments: List<ExecInstallment>,
    nowEpochMs: Long,
): List<ExecTrancheWave> {
    data class Acc(
        val category: String,
        val wave: Int,
    ) {
        var installmentCount = 0
        var paidCount = 0
        val families = mutableSetOf<String>()
        val debtorFamilies = mutableSetOf<String>()
        // T-427 (DATA-048): the wave's actually-late families (PAST-DUE only).
        val overdueDebtorFamilies = mutableSetOf<String>()
        var dueTotal = 0L
        var paidTotal = 0L
        var remainingTotal = 0L
        var dueDateMin: Long? = null
        var dueDateMax: Long? = null
        var anyUnpaidOverdue = false
        var anyUnpaidFuture = false
    }

    val byWave = LinkedHashMap<String, Acc>()
    for (i in installments) {
        // Rule 1 (T-425, the official model) — non-wave rows are EXCLUDED,
        // never coerced: tranche 0 (the registration fee FI), out-of-range
        // legacy values never form a wave. There is NO 4th tranche.
        val wave = i.trancheNumber
        if (wave != 1 && wave != 2 && wave != 3) continue
        val key = "${i.category}#$wave"
        val acc = byWave.getOrPut(key) { Acc(i.category, wave) }
        acc.installmentCount += 1
        acc.families.add(i.parentId)
        acc.dueTotal += i.amountDue
        acc.paidTotal += i.amountPaid
        val dueTs = execTsOf(i.dueDate)
        if (dueTs != null && (acc.dueDateMin == null || dueTs < acc.dueDateMin!!)) {
            acc.dueDateMin = dueTs
        }
        // T-435 (UI-317): the range's other bound — the LATEST due date in
        // the wave (the spread's far edge; equals dueDateMin on the official
        // schedule where every row of a wave shares one date).
        if (dueTs != null && (acc.dueDateMax == null || dueTs > acc.dueDateMax!!)) {
            acc.dueDateMax = dueTs
        }
        // Rule 3 — the INV-4 remaining over every row (settled rows add 0).
        val remaining = execInstallmentRemaining(i.amountDue, i.amountPaid, i.amountPending, i.status)
        acc.remainingTotal += remaining
        // Rule 2 — the canonical settled predicate (never a status check).
        if (execIsInstallmentSettled(i.status, i.amountDue, i.amountPaid, i.amountPending)) {
            acc.paidCount += 1
        } else {
            if (remaining > 0) acc.debtorFamilies.add(i.parentId)
            // T-427 (DATA-048): an owing family whose row is PAST DUE is
            // "en retard"; a future-dated owing family is only "non soldée".
            if (remaining > 0 && dueTs != null && dueTs < nowEpochMs) {
                acc.overdueDebtorFamilies.add(i.parentId)
            }
            if (dueTs != null) {
                if (dueTs < nowEpochMs) acc.anyUnpaidOverdue = true
                else acc.anyUnpaidFuture = true
            }
        }
    }

    val waves = byWave.values.map { acc ->
        val phase = when {
            acc.anyUnpaidOverdue -> WavePhase.OVERDUE
            acc.anyUnpaidFuture && acc.remainingTotal > 0 -> WavePhase.NOT_DUE
            else -> WavePhase.IN_WINDOW
        }
        ExecTrancheWave(
            key = "${acc.category}#${acc.wave}",
            category = acc.category,
            wave = acc.wave,
            installmentCount = acc.installmentCount,
            paidCount = acc.paidCount,
            familyCount = acc.families.size,
            debtorFamilyCount = acc.debtorFamilies.size,
            overdueDebtorFamilyCount = acc.overdueDebtorFamilies.size,
            dueTotal = acc.dueTotal,
            paidTotal = acc.paidTotal,
            remainingTotal = acc.remainingTotal,
            collectedPct = execSharePct(acc.paidTotal, acc.dueTotal),
            clearedPct = execSharePct(acc.paidCount.toLong(), acc.installmentCount.toLong()),
            dueDate = acc.dueDateMin?.let { formatIsoMillis(it) },
            dueDateMax = acc.dueDateMax?.let { formatIsoMillis(it) },
            phase = phase,
        )
    }
    return waves.sortedWith(
        compareBy({ waveCategoryRank(it.category) }, { it.wave }),
    )
}

// ============================================================================
// T-453 (T-447 / STATS-401 mirror, PARITY-006 item 7) — the POOLED
// all-categories T1/T2/T3 waves + the non-wave summary. The mirror of the
// desktop's canonical `src/domain/calc/payment/tranche-waves.ts`
// (derivePooledTrancheWaves / deriveNonWaveSummary — the T-447 rounds).
//
// The reconciliation invariant every pooled row carries (the mandate's
// "Total Due = Paid + Pending + Remaining", exact per row):
//   dueTotal + overCoverageTotal = paidTotal + pendingTotal + remainingTotal
// where overCoverageTotal = Σ per-row max(0, paid + pending − due) — the
// uncleared+cleared funds exceeding the row's due (parent credit sitting
// ON the row). Family counts are SET UNIONS across categories (a family
// owing tuition T1 AND transport T1 counts ONCE — never the sum of
// per-category counts, the double-count trap).
// ============================================================================

/** The per-(category × wave) stats row (the TrancheWaveStats mirror). */
data class ExecWaveStatsRow(
    val category: String,
    val wave: Int,                 // 1 | 2 | 3
    val installmentCount: Int,
    val settledCount: Int,
    val familyCount: Int,
    val debtorFamilyCount: Int,
    val overdueDebtorFamilyCount: Int,
    val dueTotal: Long,            // centimes
    val paidTotal: Long,
    val pendingTotal: Long,
    val remainingTotal: Long,
    val dueDateMin: Long?,         // epoch ms (null when no row carries a date)
    val dueDateMax: Long?,
    val anyUnsettledOverdue: Boolean,
    val anyUnsettledFuture: Boolean,
)

/** The POOLED per-wave statistics (the PooledTrancheWave mirror). */
data class ExecPooledTrancheWave(
    val wave: Int,                 // 1 | 2 | 3
    val installmentCount: Int,
    val settledCount: Int,
    val familyCount: Int,          // SET UNION across categories
    val debtorFamilyCount: Int,    // SET UNION across categories
    val overdueDebtorFamilyCount: Int, // SET UNION across categories
    val dueTotal: Long,            // centimes
    val paidTotal: Long,
    val pendingTotal: Long,
    val remainingTotal: Long,
    /** Σ per-row max(0, paid + pending − due) — the parent credit ON the rows. */
    val overCoverageTotal: Long,
    /** round(paidTotal / dueTotal × 100) — PARITY-001, never clamped. */
    val collectedPct: Int,
    val dueDateMin: Long?,
    val dueDateMax: Long?,
    val anyUnsettledOverdue: Boolean,
    val anyUnsettledFuture: Boolean,
    /**
     * The wave's per-category breakdown (the canonical stats rows of THIS
     * wave, stable order: tuition → transport → others, then category name).
     * Every category with a row in the wave appears — the audit trail that
     * no revenue category is silently excluded.
     */
    val perCategory: List<ExecWaveStatsRow>,
)

/** The non-wave row classes (the NonWaveKind mirror). */
enum class ExecNonWaveKind { FI, UNNUMBERED, OUT_OF_RANGE }

/** The non-wave (kind × category) group (the NonWaveCategoryStats mirror). */
data class ExecNonWaveCategoryStat(
    val kind: ExecNonWaveKind,
    val category: String,
    val installmentCount: Int,
    val settledCount: Int,
    val familyCount: Int,
    val debtorFamilyCount: Int,
    val overdueDebtorFamilyCount: Int,
    val dueTotal: Long,            // centimes
    val paidTotal: Long,
    val pendingTotal: Long,
    val remainingTotal: Long,
    val overCoverageTotal: Long,
    val dueDateMin: Long?,
    val dueDateMax: Long?,
    val anyUnsettledOverdue: Boolean,
)

/** The internal grouping accumulator (the WaveAcc mirror — never exported). */
private open class WaveGroupAcc(
    val category: String,
    val wave: Int,
) {
    var installmentCount = 0
    var settledCount = 0
    val families = mutableSetOf<String>()
    val debtorFamilies = mutableSetOf<String>()
    val overdueDebtorFamilies = mutableSetOf<String>()
    var dueTotal = 0L
    var paidTotal = 0L
    var pendingTotal = 0L
    var remainingTotal = 0L
    var overCoverageTotal = 0L
    var dueDateMin: Long? = null
    var dueDateMax: Long? = null
    var anyUnsettledOverdue = false
    var anyUnsettledFuture = false
}

/** The accumulateWaveRow mirror (rule 2 + rule 3 of the canonical doc). */
private fun accumulateWaveGroupRow(acc: WaveGroupAcc, i: ExecInstallment, nowEpochMs: Long) {
    acc.installmentCount += 1
    acc.families.add(i.parentId)
    acc.dueTotal += i.amountDue
    acc.paidTotal += i.amountPaid
    acc.pendingTotal += i.amountPending
    val overCoverage = i.amountPaid + i.amountPending - i.amountDue
    if (overCoverage > 0L) acc.overCoverageTotal += overCoverage
    val dueTs = execTsOf(i.dueDate)
    if (dueTs != null && (acc.dueDateMin == null || dueTs < acc.dueDateMin!!)) {
        acc.dueDateMin = dueTs
    }
    if (dueTs != null && (acc.dueDateMax == null || dueTs > acc.dueDateMax!!)) {
        acc.dueDateMax = dueTs
    }
    // Rule 3 — the INV-4 remaining over every row (settled rows add 0).
    val remaining = execInstallmentRemaining(i.amountDue, i.amountPaid, i.amountPending, i.status)
    acc.remainingTotal += remaining
    // Rule 2 — the canonical settled predicate.
    if (execIsInstallmentSettled(i.status, i.amountDue, i.amountPaid, i.amountPending)) {
        acc.settledCount += 1
    } else {
        if (remaining > 0L) acc.debtorFamilies.add(i.parentId)
        if (remaining > 0L && dueTs != null && dueTs < nowEpochMs) {
            acc.overdueDebtorFamilies.add(i.parentId)
        }
        if (dueTs != null) {
            if (dueTs < nowEpochMs) acc.anyUnsettledOverdue = true
            else acc.anyUnsettledFuture = true
        }
    }
}

/** The groupWaves mirror: (category × wave 1..3) accumulators, non-wave rows excluded. */
private fun groupWaveAccs(installments: List<ExecInstallment>, nowEpochMs: Long): LinkedHashMap<String, WaveGroupAcc> {
    val byWave = LinkedHashMap<String, WaveGroupAcc>()
    for (i in installments) {
        val wave = i.trancheNumber
        if (wave != 1 && wave != 2 && wave != 3) continue
        val key = "${i.category}#$wave"
        val acc = byWave.getOrPut(key) { WaveGroupAcc(i.category, wave) }
        accumulateWaveGroupRow(acc, i, nowEpochMs)
    }
    return byWave
}

/** The accToStats mirror. */
private fun waveGroupAccToStats(acc: WaveGroupAcc): ExecWaveStatsRow = ExecWaveStatsRow(
    category = acc.category,
    wave = acc.wave,
    installmentCount = acc.installmentCount,
    settledCount = acc.settledCount,
    familyCount = acc.families.size,
    debtorFamilyCount = acc.debtorFamilies.size,
    overdueDebtorFamilyCount = acc.overdueDebtorFamilies.size,
    dueTotal = acc.dueTotal,
    paidTotal = acc.paidTotal,
    pendingTotal = acc.pendingTotal,
    remainingTotal = acc.remainingTotal,
    dueDateMin = acc.dueDateMin,
    dueDateMax = acc.dueDateMax,
    anyUnsettledOverdue = acc.anyUnsettledOverdue,
    anyUnsettledFuture = acc.anyUnsettledFuture,
)

/** The stable category order for breakdowns: tuition → transport → others. */
private fun pooledCategoryRank(category: String): Int = when (category) {
    "tuition" -> 0
    "transport" -> 1
    else -> 2
}

/**
 * T-453 — the canonical POOLED T1/T2/T3 analysis (the derivePooledTrancheWaves
 * mirror): every billing category's rows pooled per wave index — the object
 * both the Statistics main wave cards and the Finance Tranches strip consume
 * (each keeps its own PRESENTATION, never its own math). Only waves with at
 * least one row are returned (honest-empty discipline); the presentation
 * layers fill the fixed T1/T2/T3 slots when a wave has no rows.
 */
fun deriveExecPooledTrancheWaves(
    installments: List<ExecInstallment>,
    nowEpochMs: Long,
): List<ExecPooledTrancheWave> {
    val byWave = groupWaveAccs(installments, nowEpochMs)
    val byIndex = HashMap<Int, MutableList<WaveGroupAcc>>()
    for (acc in byWave.values) {
        byIndex.getOrPut(acc.wave) { mutableListOf() }.add(acc)
    }
    return listOf(1, 2, 3)
        .filter { byIndex.containsKey(it) }
        .map { wave ->
            val accs = byIndex.getValue(wave)
            // SET UNIONS across categories — a family owing tuition T1 AND
            // transport T1 is ONE family in the pooled row (summing
            // per-category counts would double-count them).
            val families = mutableSetOf<String>()
            val debtorFamilies = mutableSetOf<String>()
            val overdueDebtorFamilies = mutableSetOf<String>()
            var installmentCount = 0
            var settledCount = 0
            var dueTotal = 0L
            var paidTotal = 0L
            var pendingTotal = 0L
            var remainingTotal = 0L
            var overCoverageTotal = 0L
            var dueDateMin: Long? = null
            var dueDateMax: Long? = null
            var anyUnsettledOverdue = false
            var anyUnsettledFuture = false
            for (acc in accs) {
                installmentCount += acc.installmentCount
                settledCount += acc.settledCount
                families.addAll(acc.families)
                debtorFamilies.addAll(acc.debtorFamilies)
                overdueDebtorFamilies.addAll(acc.overdueDebtorFamilies)
                dueTotal += acc.dueTotal
                paidTotal += acc.paidTotal
                pendingTotal += acc.pendingTotal
                remainingTotal += acc.remainingTotal
                overCoverageTotal += acc.overCoverageTotal
                if (acc.dueDateMin != null && (dueDateMin == null || acc.dueDateMin!! < dueDateMin!!)) {
                    dueDateMin = acc.dueDateMin
                }
                if (acc.dueDateMax != null && (dueDateMax == null || acc.dueDateMax!! > dueDateMax!!)) {
                    dueDateMax = acc.dueDateMax
                }
                anyUnsettledOverdue = anyUnsettledOverdue || acc.anyUnsettledOverdue
                anyUnsettledFuture = anyUnsettledFuture || acc.anyUnsettledFuture
            }
            val perCategory = accs.map { waveGroupAccToStats(it) }.sortedWith(
                compareBy({ pooledCategoryRank(it.category) }, { it.category }),
            )
            ExecPooledTrancheWave(
                wave = wave,
                installmentCount = installmentCount,
                settledCount = settledCount,
                familyCount = families.size,
                debtorFamilyCount = debtorFamilies.size,
                overdueDebtorFamilyCount = overdueDebtorFamilies.size,
                dueTotal = dueTotal,
                paidTotal = paidTotal,
                pendingTotal = pendingTotal,
                remainingTotal = remainingTotal,
                overCoverageTotal = overCoverageTotal,
                // PARITY-001 — the ONE percentage formula (round, never clamped):
                // a pooled rate above 100% is honest (over-covered rows).
                collectedPct = execSharePct(paidTotal, dueTotal),
                dueDateMin = dueDateMin,
                dueDateMax = dueDateMax,
                anyUnsettledOverdue = anyUnsettledOverdue,
                anyUnsettledFuture = anyUnsettledFuture,
                perCategory = perCategory,
            )
        }
}

/** The nonWaveKindOf mirror (the ExecInstallment trancheNumber is never null — the live DB is NOT NULL 0..3 — so UNNUMBERED is the legacy-safety leg). */
private fun execNonWaveKindOf(trancheNumber: Int): ExecNonWaveKind = when (trancheNumber) {
    0 -> ExecNonWaveKind.FI
    else -> ExecNonWaveKind.OUT_OF_RANGE
}

private val EXEC_NON_WAVE_KIND_RANK = mapOf(
    ExecNonWaveKind.FI to 0,
    ExecNonWaveKind.UNNUMBERED to 1,
    ExecNonWaveKind.OUT_OF_RANGE to 2,
)

/**
 * T-453 — the non-wave rows (tranche 0 / unnumbered / out-of-range — the rows
 * rule 1 excludes from every wave), grouped by (kind × category) so the
 * analysis surfaces them explicitly instead of silently dropping them: the
 * registration fee (FI — category tuition, tranche 0) first, then unnumbered
 * commitments, then legacy out-of-range rows (the pre-T-425 phantom T4).
 * Only (kind × category) groups with at least one row are returned.
 */
fun deriveExecNonWaveSummary(
    installments: List<ExecInstallment>,
    nowEpochMs: Long,
): List<ExecNonWaveCategoryStat> {
    class NonWaveAcc(category: String, wave: Int, val kind: ExecNonWaveKind) : WaveGroupAcc(category, wave)

    val groups = LinkedHashMap<String, NonWaveAcc>()
    for (i in installments) {
        val n = i.trancheNumber
        if (n == 1 || n == 2 || n == 3) continue
        val kind = execNonWaveKindOf(n)
        val key = "$kind#${i.category}"
        val acc = groups.getOrPut(key) { NonWaveAcc(i.category, 0, kind) }
        accumulateWaveGroupRow(acc, i, nowEpochMs)
    }
    return groups.values
        .map { acc ->
            ExecNonWaveCategoryStat(
                kind = acc.kind,
                category = acc.category,
                installmentCount = acc.installmentCount,
                settledCount = acc.settledCount,
                familyCount = acc.families.size,
                debtorFamilyCount = acc.debtorFamilies.size,
                overdueDebtorFamilyCount = acc.overdueDebtorFamilies.size,
                dueTotal = acc.dueTotal,
                paidTotal = acc.paidTotal,
                pendingTotal = acc.pendingTotal,
                remainingTotal = acc.remainingTotal,
                overCoverageTotal = acc.overCoverageTotal,
                dueDateMin = acc.dueDateMin,
                dueDateMax = acc.dueDateMax,
                anyUnsettledOverdue = acc.anyUnsettledOverdue,
            )
        }
        .sortedWith(
            compareBy(
                { EXEC_NON_WAVE_KIND_RANK.getValue(it.kind) },
                { pooledCategoryRank(it.category) },
                { it.category },
            ),
        )
}

// ============================================================================
// T-454 (PARITY-007, 127th session — the desktop T-447 UI-surface mirror):
// the FINANCE-STRIP presentation adapter + the strip totals.
//
// MIRRORED FROM (ADR-002 — the desktop's own post-T-447 construction):
//   `elimtiyaz-desktop/src/features/financials/installment-schedule-tab.tsx`
//   — `deriveTrancheWaves(rows)` maps the canonical
//   `derivePooledTrancheWaves(rows, now)` rows (T-447: "the strip's
//   per-index cards map the domain module's PooledTrancheWave rows — the
//   SAME object the Statistics main wave cards consume... adding only this
//   surface's presentation [label/hint/isNextTarget/tuitionPct]. The
//   hand-rolled pooling... is retired") and the totals block
//   (`sumInstallmentsDue` / `sumInstallmentsPaid` / `totalOutstanding` +
//   the T-426 dynamic-overdue count).
//
// The Android's pre-T-454 feed (`StatisticsEngine.deriveTrancheWaves` — the
// label-REGEX grouping + the clamped pct) is RETIRED from the production
// path: a row whose label drifts off "Tranche N" silently vanished from the
// meters, and an over-covered wave rendered 100% while the desktop rendered
// the honest >100 rate.
// ============================================================================

/**
 * T-454 — the FINANCE-STRIP view model (the desktop tab's `TrancheWave`
 * mirror): per wave the POOLED all-categories totals, the canonical
 * PARITY-001 rate (round, NEVER clamped — over-covered waves are honest),
 * the derived due-date range, the T-427 overdue flag, the T-432
 * tuition-isolated rate, and the active-collection target flag.
 */
data class ExecTrancheWaveStripRow(
    val index: Int,               // 1 | 2 | 3 (the fixed slots)
    val label: String,            // "Tranche 1 (Septembre)" …
    val hint: String,             // the static schedule hint (display-only fallback)
    val dueDate: String? = null,  // ISO — the wave's DERIVED earliest due date
    val dueDateMax: String? = null, // ISO — the wave's derived LATEST due date
    val isOverdue: Boolean = false, // T-427: any unsettled row past due
    val due: Long = 0L,           // Σ amountDue (centimes — the POOLED basis)
    val paid: Long = 0L,          // Σ amountPaid (includes uncleared checks)
    val pending: Long = 0L,       // Σ amountPending (uncleared non-cash)
    val remaining: Long = 0L,     // Σ canonical INV-4 remaining
    val pct: Int = 0,             // w.collectedPct — the canonical unclamped rate
    val tuitionPct: Int? = null,  // T-432: the tuition-isolated rate (null when no tuition rows)
    val isNextTarget: Boolean = false, // the first wave with a remaining balance
)

/** The desktop's TRANCHE_WAVE_META — the fixed T1/T2/T3 slots + the static hints (T-447 state). */
private val EXEC_TRANCHE_WAVE_META: List<Triple<Int, String, String>> = listOf(
    Triple(1, "Tranche 1 (Septembre)", "échéance 15 sep"),
    Triple(2, "Tranche 2 (Décembre)", "échéance 15 déc"),
    Triple(3, "Tranche 3 (Mars)", "échéance 15 mars"),
)

/** The zero-row pooled wave (the desktop `emptyPooledWave` mirror — the "no rows billed" slot state). */
fun emptyExecPooledWave(wave: Int): ExecPooledTrancheWave = ExecPooledTrancheWave(
    wave = wave,
    installmentCount = 0,
    settledCount = 0,
    familyCount = 0,
    debtorFamilyCount = 0,
    overdueDebtorFamilyCount = 0,
    dueTotal = 0L,
    paidTotal = 0L,
    pendingTotal = 0L,
    remainingTotal = 0L,
    overCoverageTotal = 0L,
    collectedPct = 0,
    dueDateMin = null,
    dueDateMax = null,
    anyUnsettledOverdue = false,
    anyUnsettledFuture = false,
    perCategory = emptyList(),
)

/**
 * T-454 — the Finance-strip view model (the desktop tab's `deriveTrancheWaves`
 * mirror): the fixed T1/T2/T3 slots mapped from the canonical POOLED rows —
 * one derivation, this surface's presentation only (never its own math).
 */
fun deriveExecTrancheWaveStrip(
    installments: List<ExecInstallment>,
    nowEpochMs: Long,
): List<ExecTrancheWaveStripRow> {
    val pooled = deriveExecPooledTrancheWaves(installments, nowEpochMs)
    val byIndex = HashMap<Int, ExecPooledTrancheWave>()
    for (w in pooled) byIndex[w.wave] = w
    val firstWithRemaining = pooled
        .filter { it.remainingTotal > 0L }
        .map { it.wave }
        .sorted()
        .firstOrNull()
    return EXEC_TRANCHE_WAVE_META.map { (index, label, hint) ->
        val w = byIndex[index] ?: emptyExecPooledWave(index)
        // T-432 (DATA-049): the tuition-isolated rate — the SAME number the
        // Statistics per-category breakdown carries, so the strip's pooled
        // basis and the wave cards' isolated basis reconcile at a glance.
        val tuition = w.perCategory.firstOrNull { it.category == "tuition" }
        val tuitionPct = if (tuition != null && tuition.dueTotal > 0L) {
            mathRound(tuition.paidTotal.toDouble() / tuition.dueTotal * 100).toInt()
        } else null
        ExecTrancheWaveStripRow(
            index = index,
            label = label,
            hint = hint,
            dueDate = w.dueDateMin?.let { formatIsoMillis(it) },
            dueDateMax = w.dueDateMax?.let { formatIsoMillis(it) },
            isOverdue = w.anyUnsettledOverdue,
            due = w.dueTotal,
            paid = w.paidTotal,
            pending = w.pendingTotal,
            remaining = w.remainingTotal,
            pct = w.collectedPct,
            tuitionPct = tuitionPct,
            isNextTarget = index == firstWithRemaining,
        )
    }
}

/**
 * T-454 — the canonical dynamic-overdue predicate over the Exec rows (the
 * desktop `isInstallmentOverdue` mirror): only not-paid, STRICTLY-past-due,
 * still-owing rows are "en retard" (a future T2/T3 tranche is "à échoir",
 * never late; a settled row adds 0 by construction).
 */
fun isExecInstallmentOverdueRow(i: ExecInstallment, nowEpochMs: Long): Boolean {
    if (i.status == "paid") return false
    val dueTs = execTsOf(i.dueDate) ?: return false
    if (dueTs >= nowEpochMs) return false
    return execInstallmentRemaining(i.amountDue, i.amountPaid, i.amountPending, i.status) > 0L
}

/**
 * T-454 — the Finance-strip TOTALS (the desktop tab's totals block mirror):
 * the canonical sum helpers over the CURRENT selection (including the
 * non-wave rows — FI is billed money too), never a re-derivation:
 * `sumInstallmentsDue` / `sumInstallmentsPaid` / `totalOutstanding` /
 * the dynamic overdue count.
 */
data class ExecTrancheStripTotals(
    val totalDue: Long,          // Σ amountDue (centimes)
    val totalPaid: Long,         // Σ amountPaid
    val totalRemaining: Long,    // clampNonNegative(Σdue − Σpaid − Σpending)
    val overdueCount: Int,       // the dynamic-predicate row count
)

fun deriveExecTrancheStripTotals(
    installments: List<ExecInstallment>,
    nowEpochMs: Long,
): ExecTrancheStripTotals {
    val totalDue = installments.sumOf { it.amountDue }
    val totalPaid = installments.sumOf { it.amountPaid }
    val totalPending = installments.sumOf { it.amountPending }
    val totalRemaining = (totalDue - totalPaid - totalPending).coerceAtLeast(0L)
    val overdueCount = installments.count { isExecInstallmentOverdueRow(it, nowEpochMs) }
    return ExecTrancheStripTotals(
        totalDue = totalDue,
        totalPaid = totalPaid,
        totalRemaining = totalRemaining,
        overdueCount = overdueCount,
    )
}

// ============================================================================
// 2. Discount erosion (Le Taux d'Érosion des Remises)
// ============================================================================

data class ExecDiscountErosion(
    val remiseCount: Int,
    val remiseTotal: Long,          // centimes
    val cancelCount: Int,
    val cancelTotal: Long,
    val netRemiseTotal: Long,
    val grossCharges: Long,         // Σ positive charge entries
    val stickerTotal: Long,         // grossCharges + remiseTotal
    val erosionPct: Int,            // round(remiseTotal / stickerTotal × 100)
    val averageRemise: Long,
    val maxRemise: Long,
    val minRemise: Long,
    val remiseFamilyCount: Int,
)

/**
 * Discount erosion from the ledger adjustment stream (mirrors
 * deriveDiscountErosion). Remise identification: STRUCTURED metadata
 * marker `field === "REMISE"` on a negative adjustment, with the
 * "Remise sur devis…" description prefix as the documented legacy
 * fallback; cancels carry `reason === "double_remise_cancel"` (the
 * reconciliation-0063 marker). The NET of remises and cancels is ~0 by
 * design (imported devis amounts are already net) — the erosion metric
 * reports the RAW negotiated remise volume against the reconstructed
 * sticker total.
 */
fun deriveExecDiscountErosion(ledger: List<ExecLedgerEntry>): ExecDiscountErosion {
    var remiseCount = 0
    var remiseTotal = 0L
    var cancelCount = 0
    var cancelTotal = 0L
    var grossCharges = 0L
    val remiseFamilies = mutableSetOf<String>()
    var maxRemise = 0L
    var minRemise = Long.MAX_VALUE

    for (e in ledger) {
        val amount = e.amount
        if (e.type == "charge") {
            if (amount > 0) grossCharges += amount
            continue
        }
        if (e.type != "adjustment") continue
        val fieldMarker = e.metadata["field"]
        val reasonMarker = e.metadata["reason"]
        val isRemise = amount < 0 && fieldMarker == "REMISE"
        val isRemiseByDescription = amount < 0 && e.description.startsWith("Remise sur devis")
        val isCancel = reasonMarker == "double_remise_cancel"
        if (isRemise || isRemiseByDescription) {
            remiseCount += 1
            remiseTotal += -amount
            remiseFamilies.add(e.parentId)
            if (-amount > maxRemise) maxRemise = -amount
            if (-amount < minRemise) minRemise = -amount
        } else if (isCancel && amount > 0) {
            cancelCount += 1
            cancelTotal += amount
        }
    }

    val stickerTotal = grossCharges + remiseTotal
    return ExecDiscountErosion(
        remiseCount = remiseCount,
        remiseTotal = remiseTotal,
        cancelCount = cancelCount,
        cancelTotal = cancelTotal,
        netRemiseTotal = remiseTotal - cancelTotal,
        grossCharges = grossCharges,
        stickerTotal = stickerTotal,
        erosionPct = execSharePct(remiseTotal, stickerTotal),
        averageRemise = if (remiseCount > 0) dzRound(remiseTotal.toDouble() / remiseCount) else 0L,
        maxRemise = if (remiseCount > 0) maxRemise else 0L,
        minRemise = if (remiseCount > 0) minRemise else 0L,
        remiseFamilyCount = remiseFamilies.size,
    )
}

// ============================================================================
// 3. Debt triage — chronic vs transitory (Créances par Ancienneté Réelle)
// ============================================================================

enum class ExecTriageBucket { NOT_DUE, CURRENT, REMINDER, CHRONIC }

/**
 * T-450 (PARITY-006, the owner's issue-#1 mandate) — the mirror of the
 * desktop's canonical `DebtAgingThresholds`
 * (src/domain/calc/ledger/debt-aging.ts, T-429/DEBT-100 — migration 0125's
 * owner-specified seed values, read live from `system_settings` category
 * `debt` by `observeThresholds()` on the desktop). The bucket KEYS stay
 * stable; the edges move with the tenant's configuration.
 */
data class ExecDebtAgingThresholds(
    /** Days past due still counted as "en cours" (the tolerance window). */
    val gracePeriodDays: Int,
    /** Past this many days late the account is "À surveiller" (yellow). */
    val yellowDays: Int,
    /** Past this many days late the account is "Critique / Contentieux" (red). */
    val redDays: Int,
    /**
     * A payment within the last N days marks the parent a "payeur actif" —
     * an ANNOTATION on the explanation, NEVER a status input (the T-429
     * decoupling: a recent payment must not mask past-due debt).
     */
    val activePayerGraceDays: Int,
) {
    companion object {
        /** The owner-specified defaults (migration 0125's seed values). */
        val DEFAULT = ExecDebtAgingThresholds(
            gracePeriodDays = 5,
            yellowDays = 15,
            redDays = 60,
            activePayerGraceDays = 15,
        )
    }
}

/**
 * T-450 — the mirror of the desktop's `debtTriageLabels(thresholds)`
 * (T-443/DEBT-101): the labels DERIVE from the configured thresholds so
 * the numbers the user sees always match the edges actually applied.
 * The semantic mapping onto the canonical 4-tier status
 * (financial-rules §15.1 INV-16f):
 *
 *   not_due  ← age ≤ 0                      (the future-due tail)
 *   current  ← 0 < age ≤ yellowDays         (the grace + yellow tiers)
 *   reminder ← yellowDays < age ≤ redDays   (the orange tier — relance)
 *   chronic  ← age > redDays                (the red tier — intervention)
 */
fun execDebtTriageLabels(thresholds: ExecDebtAgingThresholds): Map<ExecTriageBucket, String> = mapOf(
    ExecTriageBucket.NOT_DUE to "Non échue",
    ExecTriageBucket.CURRENT to "Retard ≤ ${thresholds.yellowDays} j (à surveiller)",
    ExecTriageBucket.REMINDER to "Retard ${thresholds.yellowDays}–${thresholds.redDays} j (relance)",
    ExecTriageBucket.CHRONIC to "Retard > ${thresholds.redDays} j (intervention)",
)

/**
 * The DEFAULTS-tier labels (the documented seed values — grace 5 / yellow
 * 15 / red 60). Retained for display-only consumers; the derivation
 * always carries ITS OWN labels derived from the thresholds it was
 * handed (the desktop `TRIAGE_BUCKET_LABELS_FR` convention).
 */
val EXEC_TRIAGE_LABELS_FR: Map<ExecTriageBucket, String> = execDebtTriageLabels(ExecDebtAgingThresholds.DEFAULT)

data class ExecTriageBucketStat(
    val bucket: ExecTriageBucket,
    val label: String,
    val amount: Long,               // centimes
    val installmentCount: Int,
    val familyCount: Int,
    val share: Int,                 // share of total outstanding (by value)
)

data class ExecCallListEntry(
    val parentId: String,
    val outstanding: Long,          // the family's FULL outstanding (all buckets)
    val worstDaysOverdue: Long,
)

data class ExecDebtTriage(
    val buckets: List<ExecTriageBucketStat>,
    val totalOutstanding: Long,
    /** The beyond-RED (worst days > redDays) families ranked by full exposure — the immediate call list. */
    val callList: List<ExecCallListEntry>,
)

/**
 * Real debt aging split into the owner-mandated action tiers (mirrors
 * deriveDebtTriage — T-443/DEBT-101): not_due (future due date) /
 * current (0 < age ≤ yellowDays) / reminder (yellowDays < age ≤ redDays) /
 * chronic (age > redDays). Days overdue uses [execDaysBetweenFloor]; the
 * chronic boundary is STRICT (> redDays). The edges derive from the SAME
 * configurable thresholds as the canonical 4-tier debt status
 * (financial-rules §15.1 INV-16f — `system_settings` category `debt`,
 * migration 0125's seed: grace 5 / yellow 15 / red 60 / active-payer 15);
 * the call list is the beyond-RED families (worstDaysOverdue > redDays),
 * ranked by full exposure. A family appears in the call list when ANY
 * unpaid installment is beyond the RED threshold; their exposure is their
 * FULL outstanding (all buckets), ranked descending.
 */
fun deriveExecDebtTriage(
    installments: List<ExecInstallment>,
    nowEpochMs: Long,
    thresholds: ExecDebtAgingThresholds = ExecDebtAgingThresholds.DEFAULT,
): ExecDebtTriage {
    val bucketOrder = listOf(
        ExecTriageBucket.NOT_DUE,
        ExecTriageBucket.CURRENT,
        ExecTriageBucket.REMINDER,
        ExecTriageBucket.CHRONIC,
    )
    class Acc {
        var amount = 0L
        var installmentCount = 0
        val families = mutableSetOf<String>()
    }
    val acc = bucketOrder.associateWith { Acc() }
    val perFamily = HashMap<String, LongArray>() // [outstanding, worstDaysOverdue]
    val labels = execDebtTriageLabels(thresholds)

    for (i in installments) {
        val remaining = execInstallmentRemaining(i.amountDue, i.amountPaid, i.amountPending, i.status)
        if (remaining <= 0L) continue
        val days = execDaysBetweenFloor(i.dueDate, nowEpochMs)
        val bucket = when {
            days <= 0L -> ExecTriageBucket.NOT_DUE
            days <= thresholds.yellowDays -> ExecTriageBucket.CURRENT
            days <= thresholds.redDays -> ExecTriageBucket.REMINDER
            else -> ExecTriageBucket.CHRONIC
        }
        val a = acc.getValue(bucket)
        a.amount += remaining
        a.installmentCount += 1
        a.families.add(i.parentId)
        val fam = perFamily.getOrPut(i.parentId) { longArrayOf(0L, 0L) }
        fam[0] += remaining
        if (days > fam[1]) fam[1] = days
    }

    val totalOutstanding = acc.values.sumOf { it.amount }
    val buckets = bucketOrder.map { bucket ->
        val a = acc.getValue(bucket)
        ExecTriageBucketStat(
            bucket = bucket,
            label = labels.getValue(bucket),
            amount = a.amount,
            installmentCount = a.installmentCount,
            familyCount = a.families.size,
            share = execSharePct(a.amount, totalOutstanding),
        )
    }

    val callList = perFamily.entries
        .filter { it.value[1] > thresholds.redDays }
        .map { (parentId, v) -> ExecCallListEntry(parentId, v[0], v[1]) }
        .sortedByDescending { it.outstanding }
        .take(10)

    return ExecDebtTriage(buckets, totalOutstanding, callList)
}

// ============================================================================
// 4. Family-level exposure concentration (the 80/20 rule)
// ============================================================================

data class ExecFamilyExposure(
    val parentId: String,
    val parentName: String,
    val outstanding: Long,          // centimes
    val childCount: Int,            // ACTIVE children
    val shareOfTotalDebt: Int,
    val worstDaysOverdue: Long,
)

data class ExecFamilyConcentration(
    val totalOutstanding: Long,
    val debtorFamilyCount: Int,
    val topFamilies: List<ExecFamilyExposure>,
    val topTotal: Long,
    val topConcentrationPct: Int,   // round(topTotal / total × 100)
)

/**
 * Family-level debt concentration (mirrors deriveFamilyConcentration):
 * per-family INV-4 outstanding ranked descending, active child counts,
 * and the top-N share of the total school debt.
 */
fun deriveExecFamilyConcentration(
    installments: List<ExecInstallment>,
    parents: List<Pair<String, String>>, // (id, displayName)
    students: List<ExecStudent>,
    topN: Int = 10,
    nowEpochMs: Long,
): ExecFamilyConcentration {
    val parentNameById = parents.toMap()
    val childCountByParent = HashMap<String, Int>()
    for (s in students) {
        if (s.status != "active") continue
        childCountByParent[s.parentId] = (childCountByParent[s.parentId] ?: 0) + 1
    }

    val outstandingByFamily = HashMap<String, Long>()
    val worstOverdueByFamily = HashMap<String, Long>()
    for (i in installments) {
        val remaining = execInstallmentRemaining(i.amountDue, i.amountPaid, i.amountPending, i.status)
        if (remaining <= 0L) continue
        outstandingByFamily[i.parentId] = (outstandingByFamily[i.parentId] ?: 0L) + remaining
        val days = execDaysBetweenFloor(i.dueDate, nowEpochMs)
        if (days > (worstOverdueByFamily[i.parentId] ?: 0L)) {
            worstOverdueByFamily[i.parentId] = days
        }
    }

    val totalOutstanding = outstandingByFamily.values.sum()
    val ranked = outstandingByFamily.entries
        .map { (parentId, outstanding) ->
            ExecFamilyExposure(
                parentId = parentId,
                parentName = parentNameById[parentId] ?: "Famille inconnue",
                outstanding = outstanding,
                childCount = childCountByParent[parentId] ?: 0,
                shareOfTotalDebt = execSharePct(outstanding, totalOutstanding),
                worstDaysOverdue = worstOverdueByFamily[parentId] ?: 0L,
            )
        }
        .sortedByDescending { it.outstanding }

    val topFamilies = ranked.take(topN)
    val topTotal = topFamilies.sumOf { it.outstanding }
    return ExecFamilyConcentration(
        totalOutstanding = totalOutstanding,
        debtorFamilyCount = ranked.size,
        topFamilies = topFamilies,
        topTotal = topTotal,
        topConcentrationPct = execSharePct(topTotal, totalOutstanding),
    )
}

// ============================================================================
// 5. Transport route yield (Logistiques) — the TOWN_ALIASES mirror
// ============================================================================

/**
 * The canonical town-alias table — VERBATIM mirror of the desktop's
 * `src/domain/calc/pricing/transport.ts` TOWN_ALIASES (which the Excel
 * DISTINATION mapper also delegates to). Input is normalized: trimmed,
 * uppercased, ALL whitespace removed.
 */
val TOWN_ALIASES: Map<String, String> = mapOf(
    // 40k — Boumerdès centre
    "BOUMERDES" to "boumerdes",
    "BOUMRDES" to "boumerdes",
    "BOUMREDES" to "boumerdes",
    "BOUMERDES20000" to "boumerdes",
    "CHABAT" to "chabat",
    "CHABET" to "chabet",
    // 43k — Corso / Sahel / Figuier / Tidjelabine
    "CORSO" to "corso",
    "SAHEL" to "sahel",
    "FIGUIER" to "figuier",
    "TIDJELABINE" to "tidjelabine",
    // 52k — Boudouaou / Thénia
    "BOUDOUAOU" to "boudouaou",
    "THENIA" to "thenia",
    // 57k — Zemmouri (REF-sheet typo "ZEMOURI" included)
    "ZEMMOURI" to "zemmouri",
    "ZEMOURI" to "zemmouri",
    // 55k — the "medium ring" towns
    "DJENAT" to "djenet",
    "DJENET" to "djenet",
    "CAPDJENET" to "cap_djenet",
    "BORDJMNAIL" to "bordj_menaiel",
    "SIMUSTAPHA" to "si_mustapha",
    "ISSER" to "isser",
    "OULEDMOUSSA" to "ouled_moussa",
    "KHEMISKHECHNA" to "khemis_el_khechna",
    "KHEMISELKHCHNA" to "khemis_el_khechna",
    "KHEMISKHCHNA" to "khemis_el_khechna",
    "KHEMISKHENCHELA" to "khemis_el_khechna", // REF-sheet typo
    "BENYOUNES" to "benyounes",
    "SOUKELHAD" to "souk_elhad",
    // 65k — the far ring
    "BENIAMRAN" to "beni_amrane",
    "REGHAIA" to "reghaia",
    "REGHIAA" to "reghaia", // REF-sheet typo
    "ROUIBA" to "rouiba",
    "OULEDHEDADJ" to "ouled_heddadj",
    "OULEDHDADJ" to "ouled_heddadj",
    "OULEDHEDDAJ/HOUCHEMEKHEFI" to "ouled_heddadj", // REF compound spelling
    "OULEDHADADJ" to "ouled_heddadj",
    "LAGATA" to "lagata",
    // Legacy grouped-zone codes that appear as transport_tier values
    "TIDJELABINE_SAHEL_FIGUIER_CORSO" to "tidjelabine_sahel_figuier_corso",
    "BOUDOUAOU_THENIA_ZEMMOURI" to "boudouaou_thenia_zemmouri",
    "VILLEBOUMERDES" to "ville_boumerdes",
)

/**
 * Normalize a raw transport-tier/town string (mirrors normalizeTransportTier):
 * null/blank → null (NO transport — not a rider); known spelling → the
 * canonical real-town key; unknown non-empty → "autres" (a rider from an
 * unrecognized locality).
 */
fun normalizeTransportTier(raw: String?): String? {
    if (raw == null) return null
    val s = raw.trim().uppercase().replace(Regex("\\s+"), "")
    if (s.isEmpty()) return null
    return TOWN_ALIASES[s] ?: "autres"
}

data class ExecTransportRouteStat(
    val destination: String,        // canonical TransportDestination key
    val riders: Int,                // distinct rider PARENTS on the route
    val dueTotal: Long,
    val paidTotal: Long,
    val remainingTotal: Long,
    val collectedPct: Int,
)

data class ExecTransportYield(
    val riders: Int,
    val nonRiders: Int,
    val unresolvedRawValues: List<String>,
    val routes: List<ExecTransportRouteStat>,
    val dueTotal: Long,
    val paidTotal: Long,
    val remainingTotal: Long,
    val collectedPct: Int,
)

/**
 * Transport yield per normalized route (mirrors deriveTransportYield):
 * route keys are the UNION of rider destinations and installment-attributed
 * destinations (rider-only routes appear with due 0 — the fill-rate view
 * matters before the first bill); transport installments with no active
 * rider family are bucketed under "autres" so Σ reconciles the stream.
 */
fun deriveExecTransportYield(
    students: List<ExecStudent>,
    installments: List<ExecInstallment>,
): ExecTransportYield {
    val riderParentsByDestination = HashMap<String, MutableSet<String>>()
    var riders = 0
    var nonRiders = 0
    val unresolved = LinkedHashMap<String, Int>()
    for (s in students) {
        if (s.status != "active") continue
        val dest = normalizeTransportTier(s.transportTier)
        if (dest == null) {
            nonRiders += 1
            continue
        }
        riders += 1
        if (dest == "autres" && s.transportTier != null) {
            val raw = s.transportTier!!.trim()
            if (raw.isNotEmpty()) unresolved[raw] = (unresolved[raw] ?: 0) + 1
        }
        riderParentsByDestination.getOrPut(dest) { mutableSetOf() }.add(s.parentId)
    }

    class RouteAcc {
        var due = 0L
        var paid = 0L
        var remaining = 0L
    }
    // Route keys = UNION (rider destinations seeded first — rider-only routes).
    val routeAcc = LinkedHashMap<String, RouteAcc>()
    for (dest in riderParentsByDestination.keys) routeAcc[dest] = RouteAcc()
    for (i in installments) {
        if (i.category != "transport") continue
        var attributed: String? = null
        for ((dest, parents) in riderParentsByDestination) {
            if (i.parentId in parents) {
                attributed = dest
                break
            }
        }
        val dest = attributed ?: "autres" // no active rider family → autres
        val a = routeAcc.getOrPut(dest) { RouteAcc() }
        a.due += i.amountDue
        a.paid += i.amountPaid
        a.remaining += execInstallmentRemaining(i.amountDue, i.amountPaid, i.amountPending, i.status)
    }

    val routes = routeAcc.entries
        .map { (destination, a) ->
            ExecTransportRouteStat(
                destination = destination,
                riders = riderParentsByDestination[destination]?.size ?: 0,
                dueTotal = a.due,
                paidTotal = a.paid,
                remainingTotal = a.remaining,
                collectedPct = execSharePct(a.paid, a.due),
            )
        }
        .sortedWith(compareByDescending<ExecTransportRouteStat> { it.riders }.thenByDescending { it.remainingTotal })

    val dueTotal = routes.sumOf { it.dueTotal }
    val paidTotal = routes.sumOf { it.paidTotal }
    val remainingTotal = routes.sumOf { it.remainingTotal }
    return ExecTransportYield(
        riders = riders,
        nonRiders = nonRiders,
        unresolvedRawValues = unresolved.keys.toList().sorted(),
        routes = routes,
        dueTotal = dueTotal,
        paidTotal = paidTotal,
        remainingTotal = remainingTotal,
        collectedPct = execSharePct(paidTotal, dueTotal),
    )
}

// ============================================================================
// 6. Specialized-service yield (PSY / ORTH / E-PLANT / …)
// ============================================================================

val EXEC_SERVICE_CATEGORIES: List<String> = listOf(
    "therapy_psychology",
    "therapy_speech",
    "extracurricular",
    "canteen",
    "uniform",
    "books",
    "second_apron",
    "other",
)

data class ExecServiceStat(
    val category: String,
    val label: String,
    val paymentCount: Int,
    val revenue: Long,              // centimes (PAID stream only)
    val studentCount: Int,          // distinct students billed
)

/** Service yield from the PAID payment stream (mirrors deriveServiceYield). */
fun deriveExecServiceYield(payments: List<ExecPayment>): List<ExecServiceStat> {
    class Acc {
        var revenue = 0L
        var paymentCount = 0
        val students = mutableSetOf<String>()
    }
    val acc = LinkedHashMap<String, Acc>()
    for (p in payments) {
        if (p.status != "paid") continue
        if (p.category !in EXEC_SERVICE_CATEGORIES) continue
        val a = acc.getOrPut(p.category) { Acc() }
        a.revenue += p.amount
        a.paymentCount += 1
        p.studentId?.let { a.students.add(it) }
    }
    return acc.entries
        .map { (category, a) ->
            ExecServiceStat(
                category = category,
                label = paymentCategoryLabelFr(category),
                paymentCount = a.paymentCount,
                revenue = a.revenue,
                studentCount = a.students.size,
            )
        }
        .sortedByDescending { it.revenue }
}

// ============================================================================
// 7. Enrollment dynamics — sibling index + section imbalance
// ============================================================================

data class ExecFamilySizeSlice(
    val label: String,              // "1 enfant" / "2 enfants" / … / "5+ enfants"
    val familyCount: Int,
    val studentCount: Int,
)

data class ExecSectionInfo(
    val classId: String,
    val className: String,
    val enrolled: Int,
)

data class ExecSectionImbalanceRow(
    val gradeLabel: String,
    val sectionCount: Int,
    val sections: List<ExecSectionInfo>,
    val minEnrolled: Int,
    val maxEnrolled: Int,
    val averageEnrolled: Int,       // Math.round'd
    val spread: Int,                // maxEnrolled − minEnrolled
    /** spread ≥ 10 OR max ≥ 1.5 × min (min > 0) — NO capacity ceilings anywhere. */
    val imbalanced: Boolean,
)

data class ExecEnrollmentDynamics(
    val totalStudents: Int,
    val totalFamilies: Int,
    val siblingIndex: Double?,      // totalStudents / totalFamilies, 2 decimals; null when no families
    val multiChildFamilyCount: Int,
    val multiChildFamilyPct: Int,
    val familySizes: List<ExecFamilySizeSlice>,
    val imbalances: List<ExecSectionImbalanceRow>,  // grades with 2+ active sections, spread desc
)

/**
 * Enrollment dynamics (mirrors deriveEnrollmentDynamics): the sibling
 * multiplier, the family-size distribution (5+ merged), and the SECTION
 * IMBALANCE detector that replaced the capacity gauges. Families count
 * when they have ≥ 1 ACTIVE child.
 */
fun deriveExecEnrollmentDynamics(
    students: List<ExecStudent>,
    classes: List<ExecClass>,
): ExecEnrollmentDynamics {
    val activeStudents = students.filter { it.status == "active" }
    val childCountByParent = HashMap<String, Int>()
    for (s in activeStudents) {
        childCountByParent[s.parentId] = (childCountByParent[s.parentId] ?: 0) + 1
    }
    val familyChildCounts = childCountByParent.values.toList()
    val totalStudents = activeStudents.size
    val totalFamilies = familyChildCounts.size

    val sizeBuckets = TreeMap<Int, IntArray>() // size → [families, students]
    for (c in familyChildCounts) {
        val key = minOf(c, 5)
        val b = sizeBuckets.getOrPut(key) { intArrayOf(0, 0) }
        b[0] += 1
        b[1] += c
    }
    val familySizes = sizeBuckets.entries
        .sortedBy { it.key }
        .map { (size, b) ->
            ExecFamilySizeSlice(
                label = if (size < 5) "$size enfant${if (size > 1) "s" else ""}" else "5+ enfants",
                familyCount = b[0],
                studentCount = b[1],
            )
        }

    val multiChildFamilyCount = familyChildCounts.count { it >= 2 }

    // Section imbalance: group ACTIVE classes by gradeCode (LinkedHashMap —
    // insertion order, matching the JS Map iteration the desktop sorts from;
    // Kotlin's sortedByDescending is stable, so tied spreads keep the same
    // relative order on both platforms).
    val sectionsByGrade = LinkedHashMap<String, MutableList<ExecClass>>()
    for (c in classes) {
        if (!c.isActive) continue
        sectionsByGrade.getOrPut(c.gradeCode) { mutableListOf() }.add(c)
    }
    val imbalances = ArrayList<ExecSectionImbalanceRow>()
    for ((gradeCode, secs) in sectionsByGrade) {
        if (secs.size < 2) continue
        val sections = secs
            .map { ExecSectionInfo(it.id, it.name, it.enrolledCount) }
            .sortedByDescending { it.enrolled }
        val minEnrolled = sections.last().enrolled
        val maxEnrolled = sections.first().enrolled
        val total = sections.sumOf { it.enrolled }
        val spread = maxEnrolled - minEnrolled
        imbalances.add(
            ExecSectionImbalanceRow(
                gradeLabel = "${sections.first().className} (${gradeCode.uppercase()})",
                sectionCount = sections.size,
                sections = sections,
                minEnrolled = minEnrolled,
                maxEnrolled = maxEnrolled,
                averageEnrolled = if (sections.isNotEmpty()) mathRound(total.toDouble() / sections.size).toInt() else 0,
                spread = spread,
                imbalanced = spread >= 10 || (minEnrolled > 0 && maxEnrolled >= 1.5 * minEnrolled),
            ),
        )
    }
    imbalances.sortByDescending { it.spread }

    return ExecEnrollmentDynamics(
        totalStudents = totalStudents,
        totalFamilies = totalFamilies,
        siblingIndex = if (totalFamilies > 0)
            mathRound(totalStudents.toDouble() / totalFamilies * 100).toDouble() / 100.0
        else null,
        multiChildFamilyCount = multiChildFamilyCount,
        multiChildFamilyPct = execSharePct(multiChildFamilyCount.toLong(), totalFamilies.toLong()),
        familySizes = familySizes,
        imbalances = imbalances,
    )
}

// ============================================================================
// 8. Triple-risk radar (the categorization + summary)
// ============================================================================

enum class ExecRiskCategory { TRIPLE_CRITICAL, ACADEMIC_ALERT, ATTENDANCE_ALERT, FINANCIAL_TENSION, HEALTHY }

/**
 * The triple-risk categorization thresholds — VERBATIM mirror of the
 * desktop operational-query-engine: GPA < 10/20 (null = no academic data,
 * NO alert), unexcused absences ≥ 3 OR attendance rate < 0.85, family
 * debt ≥ 25 000 DZD (2 500 000 centimes).
 */
fun execRiskCategoryOf(
    gpa: Double?,
    unexcusedAbsences: Int,
    attendanceRate: Double,
    debtAmountCentimes: Long,
): ExecRiskCategory {
    val hasAcademicAlert = gpa != null && gpa < 10.0
    val hasAttendanceAlert = unexcusedAbsences >= 3 || attendanceRate < 0.85
    val hasFinancialTension = debtAmountCentimes >= 2_500_000L
    return when {
        hasAcademicAlert && hasAttendanceAlert && hasFinancialTension -> ExecRiskCategory.TRIPLE_CRITICAL
        hasAcademicAlert -> ExecRiskCategory.ACADEMIC_ALERT
        hasAttendanceAlert -> ExecRiskCategory.ATTENDANCE_ALERT
        hasFinancialTension -> ExecRiskCategory.FINANCIAL_TENSION
        else -> ExecRiskCategory.HEALTHY
    }
}

data class ExecTripleRiskSummary(
    val tripleCriticalCount: Int,
    val academicAlertCount: Int,
    val attendanceAlertCount: Int,
    val financialTensionCount: Int,
    val healthyCount: Int,
    val tripleCriticalPct: Int,     // round(triple / total × 100)
)

/** Summary counts over risk categories (mirrors deriveTripleRiskSummary). */
fun deriveExecTripleRiskSummary(categories: List<ExecRiskCategory>): ExecTripleRiskSummary {
    val total = categories.size
    val triple = categories.count { it == ExecRiskCategory.TRIPLE_CRITICAL }
    return ExecTripleRiskSummary(
        tripleCriticalCount = triple,
        academicAlertCount = categories.count { it == ExecRiskCategory.ACADEMIC_ALERT },
        attendanceAlertCount = categories.count { it == ExecRiskCategory.ATTENDANCE_ALERT },
        financialTensionCount = categories.count { it == ExecRiskCategory.FINANCIAL_TENSION },
        healthyCount = categories.count { it == ExecRiskCategory.HEALTHY },
        tripleCriticalPct = execSharePct(triple.toLong(), total.toLong()),
    )
}

// ============================================================================
// 9. The triple-risk RADAR list (evaluateStudentRiskProfiles mirror)
// ============================================================================

/**
 * One student's cross-domain risk profile — the compact mirror of the
 * desktop `operational-query-engine.ts` StudentRiskProfile (the fields the
 * radar list renders; the desktop's scoring/vector fields stay desktop-side
 * presentation detail).
 */
data class ExecRiskProfile(
    val studentId: String,
    val studentName: String,
    val className: String,
    val parentId: String,
    val parentName: String,
    val gpa: Double?,               // null = no marks (NO academic alert)
    val attendanceRate: Double,     // 0..1 (present+late)/total; 1.0 when no records
    val unexcusedAbsences: Int,
    val debtAmount: Long,           // the family's outstanding (centimes)
    val riskCategory: ExecRiskCategory,
)

/** The minimal assessment projection the radar's GPA needs. */
data class ExecAssessment(
    val studentId: String,
    val subjectAverage: Double?,
    val devoir1: Double? = null,
    val devoir2: Double? = null,
    val examen: Double? = null,
    // T-348 (MATIERE-500 / ADR-018): the contrôle-continu mark + its weight
    // snapshot — defaults (null / 0.0) keep the projection bit-identical for
    // every existing call site that doesn't carry them.
    val cc: Double? = null,
    val coefficient: Double = 1.0,
    val isExtracurricular: Boolean = false,
    val coefficientCc: Double = 0.0,
)

/**
 * Evaluate every active student's risk profile — VERBATIM mirror of the
 * desktop evaluateStudentRiskProfiles semantics:
 *   - GPA via the canonical weighted average (extracurricular excluded,
 *     subjectAverage recomputed from the marks when missing);
 *   - attendanceRate = (present + late) / total, 1.0 when no records;
 *   - unexcusedAbsences = count(status == "absent_unexcused");
 *   - debtAmount = the FAMILY's Σ INV-4 remaining over unpaid installments;
 *   - riskCategory via [execRiskCategoryOf] (GPA < 10 + (absences ≥ 3 OR
 *     rate < 0.85) + debt ≥ 25 000 DZD).
 */
fun evaluateExecRiskProfiles(
    students: List<ExecRadarStudent>,
    parentNames: Map<String, String>,          // parentId → display name
    classNames: Map<String, String>,           // classId → name
    assessments: List<ExecAssessment>,
    attendance: List<ExecAttendanceRecord>,
    installmentDebtByParent: Map<String, Long>, // parentId → outstanding (centimes)
): List<ExecRiskProfile> {
    // Group assessments + attendance by student (insertion order preserved).
    val assessmentsByStudent = LinkedHashMap<String, MutableList<ExecAssessment>>()
    for (a in assessments) {
        assessmentsByStudent.getOrPut(a.studentId) { mutableListOf() }.add(a)
    }
    val attendanceByStudent = LinkedHashMap<String, MutableList<ExecAttendanceRecord>>()
    for (r in attendance) {
        attendanceByStudent.getOrPut(r.studentId) { mutableListOf() }.add(r)
    }

    return students.filter { it.status == "active" }.map { s ->
        // GPA — the canonical weighted average (mirrors computeOverallGpa).
        val rows = assessmentsByStudent[s.id] ?: emptyList()
        var gpa: Double? = null
        run {
            var weightedSumCents = 0L
            var coefSumCents = 0L
            for (a in rows) {
                if (a.isExtracurricular) continue
                val avg = a.subjectAverage
                    ?: computeSubjectAverage(
                        a.devoir1, a.devoir2, a.examen,
                        // T-348 (ADR-018): the cc mark + its weight — the
                        // other components keep the projection's defaults.
                        cc = a.cc, coefCc = a.coefficientCc,
                    )
                    ?: continue
                weightedSumCents += Math.round(avg * 100.0) * Math.round(a.coefficient * 100.0)
                coefSumCents += Math.round(a.coefficient * 100.0)
            }
            if (coefSumCents != 0L) {
                gpa = Math.round(weightedSumCents.toDouble() / coefSumCents.toDouble()) / 100.0
            }
        }

        // Attendance (mirrors calculateAttendanceRate: present+late / total).
        val records = attendanceByStudent[s.id] ?: emptyList()
        val attendanceRate = if (records.isEmpty()) 1.0
        else records.count { it.status == "present" || it.status == "late" }.toDouble() / records.size
        val unexcused = records.count { it.status == "absent_unexcused" }

        val debt = installmentDebtByParent[s.parentId] ?: 0L
        ExecRiskProfile(
            studentId = s.id,
            studentName = s.studentName,
            className = s.classId?.let { classNames[it] } ?: "Non assignée",
            parentId = s.parentId,
            parentName = parentNames[s.parentId] ?: "Famille inconnue",
            gpa = gpa,
            attendanceRate = attendanceRate,
            unexcusedAbsences = unexcused,
            debtAmount = debt,
            riskCategory = execRiskCategoryOf(gpa, unexcused, attendanceRate, debt),
        )
    }
}

/** The attendance projection the radar consumes. */
data class ExecAttendanceRecord(
    val studentId: String,
    val status: String,             // present | absent_excused | absent_unexcused | late
)

/** [ExecStudent] extended with the radar list's display fields. */
data class ExecRadarStudent(
    val id: String,
    val parentId: String,
    val status: String = "active",
    val transportTier: String? = null,
    val studentName: String,
    val classId: String? = null,
)

fun ExecStudent.withRadarFields(studentName: String, classId: String?): ExecRadarStudent =
    ExecRadarStudent(
        id = id,
        parentId = parentId,
        status = status,
        transportTier = transportTier,
        studentName = studentName,
        classId = classId,
    )

// ============================================================================
// Transport destination FR labels (desktop TRANSPORT_DESTINATION_LABELS_FR mirror)
// ============================================================================

/** The FR display labels for canonical transport destinations (verbatim mirror). */
fun transportDestinationLabelFr(destination: String): String = when (destination) {
    "ville_boumerdes" -> "Ville Boumerdès"
    "tidjelabine_sahel_figuier_corso" -> "Tidjelabine – Sahel – Figuier – Corso"
    "boudouaou_thenia_zemmouri" -> "Boudouaou – Thénia – Zemmouri"
    "autres" -> "Autres"
    "boumerdes" -> "Boumerdès (ville)"
    "chabat" -> "Chabet"
    "chabet" -> "Chabet (El Chabet)"
    "corso" -> "Corso"
    "sahel" -> "Sahel"
    "figuier" -> "Figuier"
    "tidjelabine" -> "Tidjelabine"
    "boudouaou" -> "Boudouaou"
    "thenia" -> "Thénia"
    "zemmouri" -> "Zemmouri"
    "djenet" -> "Cap Djinet"
    "cap_djenet" -> "Cap Djinet"
    "bordj_menaiel" -> "Bordj Menaïel"
    "si_mustapha" -> "Si Mustapha"
    "isser" -> "Isser"
    "ouled_moussa" -> "Ouled Moussa"
    "khemis_el_khechna" -> "Khemis El Khechna"
    "benyounes" -> "Benyounes"
    "souk_elhad" -> "Souk El Had"
    "beni_amrane" -> "Beni Amrane"
    "reghaia" -> "Reghaïa"
    "rouiba" -> "Rouiba"
    "ouled_heddadj" -> "Ouled Heddadj"
    "lagata" -> "Lagata"
    else -> destination
}
