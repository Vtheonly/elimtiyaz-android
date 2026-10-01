package com.example.core

/**
 * Debt Aging — the Kotlin mirror of the desktop's canonical
 * `src/domain/calc/ledger/debt-aging.ts` (§15.1/§15.2 attribution + the
 * T-429 4-tier status evaluation). Ported for T-456/T-457 (128th session).
 *
 * THE canonical status is a 4-TIER, PURELY DUE-DATE-BASED aging hierarchy
 * over the INV-4 remaining, with CONFIGURABLE thresholds
 * (financial-rules.md §15.1 as amended by T-429/DEBT-100):
 *
 *   1. outstanding ≤ 0.001 DZD           → GREEN  (Soldé)
 *   2. debtAgeDays ≤ grace (5)           → GREEN  (À échoir / En cours)
 *   3. debtAgeDays ≤ yellow (15)         → YELLOW (À surveiller)
 *   4. debtAgeDays ≤ red (60)            → ORANGE (Retard soutenu)
 *   5. debtAgeDays > red (60)            → RED    (Critique / Contentieux)
 *
 * Payment behaviour NEVER dominates a tier (INV-16a) — the active-payer
 * note is an ANNOTATION on the explanation only.
 *
 * The attribution half (INV-14 + the T-436 persisted precedence,
 * INV-18a) is shared with the year-history engine (`core/YearHistory.kt`)
 * exactly as the desktop's year-history.ts imports it from debt-aging.ts.
 *
 * Platform adaptation (documented, semantics-preserving):
 *   - The desktop's persisted column is `installments.academic_year_id`
 *     (an `academic_years` row id, resolved through the year windows).
 *     Android's persisted column is `installments.academic_cycle`, which
 *     already carries the YEAR CODE string ("2025-2026"). The precedence
 *     is identical: persisted column first, INV-14 date rule as the
 *     documented fallback — only the id→code lookup step is unnecessary.
 *   - Amounts are Long centimes (the Android convention; the desktop
 *     engine's DZD doubles × 100). 0.001 DZD = 0.1 centime, and a Long
 *     centime value ≤ 0.1 is exactly 0 — the epsilon check degenerates
 *     to `<= 0L` (YEAR_HISTORY_EPSILON semantics preserved exactly).
 */

// ============================================================================
// Academic-year attribution (INV-14 + the T-436 persisted precedence)
// ============================================================================

/** A tenant `academic_years` row, reduced to the attribution window
 *  (the desktop's `AcademicYearWindow`). */
data class AcademicYearWindow(
    val code: String,
    val startDate: String,
    val endDate: String,
    val id: String? = null,
)

enum class AttributionSource(val code: String) {
    PERSISTED("persisted"),
    DUE_DATE("due_date"),
    PAYMENT_DATE("payment_date"),
    ALLOCATION("allocation"),
}

/** The resolved academic-year attribution of one financial row (INV-18a). */
data class AcademicYearAttribution(
    val code: String,
    val id: String?,
    val source: AttributionSource,
)

/**
 * Parse an ISO date-or-instant to epoch millis (the desktop's
 * `new Date(iso).getTime()` semantics: "2025-09-15" is UTC midnight;
 * "2025-11-01T10:00:00.000Z" parses as the instant). Null when unparseable
 * (the desktop's NaN path).
 */
private fun parseIsoMillis(iso: String): Long? {
    runCatching { return java.time.Instant.parse(iso).toEpochMilli() }
    return runCatching { java.time.LocalDate.parse(iso).atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }.getOrNull()
}

/**
 * Attribute a date to an academic year (INV-14):
 *   1. a tenant `academic_years` row whose [start, end] contains the date;
 *   2. else the Algerian school-year convention: July–December belongs to
 *      `YYYY-(YYYY+1)`, January–June to `(YYYY-1)-YYYY`.
 */
fun resolveAcademicYearForDate(isoDate: String, years: List<AcademicYearWindow> = emptyList()): String {
    val t = parseIsoMillis(isoDate)
    if (t != null) {
        for (y in years) {
            val start = parseIsoMillis(y.startDate)
            val end = parseIsoMillis(y.endDate)
            if (start != null && end != null && t >= start && t <= end) return y.code
        }
    }
    val inst = (t?.let { java.time.Instant.ofEpochMilli(it) })
        ?: return ""
    val zdt = inst.atZone(java.time.ZoneOffset.UTC)
    val year = zdt.year
    val month = zdt.monthValue
    return if (month >= 7) "$year-${year + 1}" else "${year - 1}-$year"
}

/** Numeric sort key of a "YYYY-YYYY" code (the start year; -infinity when
 *  unparseable — the desktop's Number.NEGATIVE_INFINITY). */
fun academicYearStart(code: String): Int {
    val prefix = code.take(4)
    val parsed = prefix.toIntOrNull() ?: return Int.MIN_VALUE
    return parsed
}

/** INV-18a — attribute an INSTALLMENT (a charge) to an academic year:
 *  the persisted column first, then INV-14 on the due date. The persisted
 *  attribution NEVER changes with a due-date edit (INV-18b — the freeze). */
fun attributeInstallmentAcademicYear(
    dueDate: String,
    academicCycle: String?,
    years: List<AcademicYearWindow> = emptyList(),
): AcademicYearAttribution {
    // Android's persisted column (`academic_cycle`) IS the year code; the
    // desktop resolves its `academic_year_id` through these windows.
    if (!academicCycle.isNullOrBlank()) {
        return AcademicYearAttribution(code = academicCycle, id = null, source = AttributionSource.PERSISTED)
    }
    return AcademicYearAttribution(
        code = resolveAcademicYearForDate(dueDate, years),
        id = null,
        source = AttributionSource.DUE_DATE,
    )
}

/** INV-18 — attribute a PAYMENT to the year it was MADE in. Android's
 *  Payment model has no persisted year column: the INV-14 rule on the
 *  collection date (the desktop's documented fallback, verbatim). */
fun attributePaymentAcademicYear(
    collectedAt: String,
    years: List<AcademicYearWindow> = emptyList(),
): AcademicYearAttribution = AcademicYearAttribution(
    code = resolveAcademicYearForDate(collectedAt, years),
    id = null,
    source = AttributionSource.PAYMENT_DATE,
)

// ============================================================================
// The 4-tier status evaluation (INV-16 — ordered, configurable)
// ============================================================================

enum class DebtAgingStatusLevel { GREEN, YELLOW, ORANGE, RED }

enum class DebtAgingReasonCode {
    RESOLVED, NOT_DUE, WATCH, SUSTAINED_DELINQUENCY, CRITICAL_DELINQUENCY,
}

/**
 * §15.3 — the canonical FR status labels, IDENTICAL wording on every
 * surface that shows a debt status (the T-429 wording; green covers both
 * the resolved and the not-yet-due tiers, the reason code disambiguates).
 */
val DEBT_AGING_STATUS_LABELS_FR: Map<DebtAgingStatusLevel, String> = mapOf(
    DebtAgingStatusLevel.GREEN to "Soldé / À échoir",
    DebtAgingStatusLevel.YELLOW to "À surveiller",
    DebtAgingStatusLevel.ORANGE to "Retard soutenu",
    DebtAgingStatusLevel.RED to "Critique / Contentieux",
)

/** The desktop's `DEBT_AGING_STATUS_TONE` (success/warning/warning/danger). */
enum class DebtStatusTone { SUCCESS, WARNING, DANGER }

val DEBT_AGING_STATUS_TONE: Map<DebtAgingStatusLevel, DebtStatusTone> = mapOf(
    DebtAgingStatusLevel.GREEN to DebtStatusTone.SUCCESS,
    DebtAgingStatusLevel.YELLOW to DebtStatusTone.WARNING,
    DebtAgingStatusLevel.ORANGE to DebtStatusTone.WARNING,
    DebtAgingStatusLevel.RED to DebtStatusTone.DANGER,
)

/** The computed status: level + reason + rendered explanation (INV-16d). */
data class DebtAgingStatus(
    val level: DebtAgingStatusLevel,
    val reasonCode: DebtAgingReasonCode,
    val explanationFr: String,
    val labelFr: String = DEBT_AGING_STATUS_LABELS_FR.getValue(level),
)

/** The status factors (§15.1). `inactivityDays` is an ANNOTATION input
 *  since T-429 — never a status input (the decoupling). */
data class DebtAgingStatusFactors(
    val outstandingAmount: Long,      // centimes
    val debtAgeDays: Long,
    val inactivityDays: Long,
    val hasSubsequentYearPayments: Boolean = false,
)

/**
 * The canonical ordered evaluation — the verbatim port of the desktop's
 * `computeDebtAgingStatus` (the explanation strings included; the
 * thresholds' numbers render from the CONFIGURED values, never literals).
 * Reuses [ExecDebtAgingThresholds] (the T-450 mirror of the desktop's
 * `DebtAgingThresholds`) — ONE thresholds type on this platform.
 */
fun computeDebtAgingStatus(
    factors: DebtAgingStatusFactors,
    thresholds: ExecDebtAgingThresholds = ExecDebtAgingThresholds.DEFAULT,
): DebtAgingStatus {
    val (outstandingAmount, debtAgeDays, inactivityDays) = factors
    val subsequent = factors.hasSubsequentYearPayments
    // The active-payer annotation (presentation only — DEBT-100's
    // decoupling: never a status input, never a masking rule).
    val activePayer = inactivityDays <= thresholds.activePayerGraceDays
    val activePayerNote = if (activePayer) {
        " Payeur actif — dernier paiement il y a $inactivityDays j" +
            (if (subsequent) " ; paiements poursuivis sur les années suivantes." else ".")
    } else ""

    if (outstandingAmount <= 0L) {
        return DebtAgingStatus(
            DebtAgingStatusLevel.GREEN, DebtAgingReasonCode.RESOLVED,
            "Soldé — aucune créance en cours.",
        )
    }
    if (debtAgeDays <= thresholds.gracePeriodDays) {
        return DebtAgingStatus(
            DebtAgingStatusLevel.GREEN, DebtAgingReasonCode.NOT_DUE,
            "À échoir — l'échéance n'est pas dépassée au-delà du délai de grâce " +
                "(${thresholds.gracePeriodDays} j ; dette de $debtAgeDays j)." + activePayerNote,
        )
    }
    if (debtAgeDays <= thresholds.yellowDays) {
        return DebtAgingStatus(
            DebtAgingStatusLevel.YELLOW, DebtAgingReasonCode.WATCH,
            "À surveiller — échéance dépassée de $debtAgeDays j (seuil de " +
                "${thresholds.yellowDays} j ; dernière activité de paiement il y a " +
                "$inactivityDays j)." + activePayerNote,
        )
    }
    if (debtAgeDays <= thresholds.redDays) {
        return DebtAgingStatus(
            DebtAgingStatusLevel.ORANGE, DebtAgingReasonCode.SUSTAINED_DELINQUENCY,
            "Retard soutenu — échéance dépassée de $debtAgeDays j (entre les " +
                "seuils ${thresholds.yellowDays} et ${thresholds.redDays} j ; dernier " +
                "paiement il y a $inactivityDays j)." + activePayerNote,
        )
    }
    return DebtAgingStatus(
        DebtAgingStatusLevel.RED, DebtAgingReasonCode.CRITICAL_DELINQUENCY,
        "Critique — échéance dépassée de $debtAgeDays j au-delà du seuil de " +
            "${thresholds.redDays} j ; dernier paiement il y a $inactivityDays j." + activePayerNote,
    )
}
