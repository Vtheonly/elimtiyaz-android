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
    val dueTotal: Long,             // centimes
    val paidTotal: Long,
    val remainingTotal: Long,
    val collectedPct: Int,          // round(paidTotal / dueTotal × 100)
    val clearedPct: Int,            // round(paidCount / installmentCount × 100)
    val dueDate: String?,           // earliest due date ISO (null when empty)
    val phase: WavePhase,
)

private fun waveCategoryRank(category: String): Int = when (category) {
    "tuition" -> 0
    "transport" -> 1
    else -> 2
}

/**
 * The three seasonal cash surges — per (category × trancheNumber) wave
 * (mirrors deriveTrancheWaves; groups by the CANONICAL tranche_number,
 * never label parsing). A wave is OVERDUE when any unpaid installment's
 * due date is past `now`; NOT_DUE when every unpaid remainder is in the
 * future; IN_WINDOW otherwise (fully-collected waves report IN_WINDOW —
 * complete, 100%).
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
        var dueTotal = 0L
        var paidTotal = 0L
        var remainingTotal = 0L
        var dueDateMin: Long? = null
        var anyUnpaidOverdue = false
        var anyUnpaidFuture = false
    }

    val byWave = LinkedHashMap<String, Acc>()
    for (i in installments) {
        val wave = i.trancheNumber.coerceIn(1, 3)
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
        if (i.status == "paid") {
            acc.paidCount += 1
        } else {
            val remaining = execInstallmentRemaining(i.amountDue, i.amountPaid, i.amountPending, i.status)
            acc.remainingTotal += remaining
            if (remaining > 0) acc.debtorFamilies.add(i.parentId)
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
            dueTotal = acc.dueTotal,
            paidTotal = acc.paidTotal,
            remainingTotal = acc.remainingTotal,
            collectedPct = execSharePct(acc.paidTotal, acc.dueTotal),
            clearedPct = execSharePct(acc.paidCount.toLong(), acc.installmentCount.toLong()),
            dueDate = acc.dueDateMin?.let { formatIsoMillis(it) },
            phase = phase,
        )
    }
    return waves.sortedWith(
        compareBy({ waveCategoryRank(it.category) }, { it.wave }),
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

val EXEC_TRIAGE_LABELS_FR: Map<ExecTriageBucket, String> = mapOf(
    ExecTriageBucket.NOT_DUE to "Non échue",
    ExecTriageBucket.CURRENT to "Retard < 15 j (à surveiller)",
    ExecTriageBucket.REMINDER to "Retard 15–45 j (relance)",
    ExecTriageBucket.CHRONIC to "Retard > 45 j (intervention)",
)

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
    /** The >45-day families ranked by full exposure — the immediate call list. */
    val callList: List<ExecCallListEntry>,
)

/**
 * Real debt aging split into the owner-mandated action tiers (mirrors
 * deriveDebtTriage): not_due (future due date) / current (<15 j) /
 * reminder (15–45 j) / chronic (>45 j). Days overdue uses
 * [execDaysBetweenFloor]; the >45 boundary is STRICT (> 45).
 */
fun deriveExecDebtTriage(
    installments: List<ExecInstallment>,
    nowEpochMs: Long,
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

    for (i in installments) {
        val remaining = execInstallmentRemaining(i.amountDue, i.amountPaid, i.amountPending, i.status)
        if (remaining <= 0L) continue
        val days = execDaysBetweenFloor(i.dueDate, nowEpochMs)
        val bucket = when {
            days <= 0L -> ExecTriageBucket.NOT_DUE
            days < 15L -> ExecTriageBucket.CURRENT
            days <= 45L -> ExecTriageBucket.REMINDER
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
            label = EXEC_TRIAGE_LABELS_FR.getValue(bucket),
            amount = a.amount,
            installmentCount = a.installmentCount,
            familyCount = a.families.size,
            share = execSharePct(a.amount, totalOutstanding),
        )
    }

    val callList = perFamily.entries
        .filter { it.value[1] > 45L }
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
