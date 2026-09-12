package com.example.core

import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Discount Engine — Kotlin port of the desktop
 * `src/domain/calc/pricing/discount-engine.ts` + `discount-rules.ts`.
 *
 * CALC-001 (2026-09-12): verified against `Suivis clients  2026_2027.xlsx`,
 * only TWO discount rules are REAL at the school:
 *
 *   1. `sibling_fixed` — −5 000 DZD per additional child (the default
 *      component visible inside the workbook's J-column remise
 *      decompositions, e.g. `=13000+22000+5000`).
 *   2. `full_annual` — early annual payment before June 30: −5% of the
 *      FRAIS DE SCOLARISATION ONLY (the Devis sheet formula
 *      `=+SUM(F15:F26)*0.05` — never of FI or transport).
 *
 * The previously-mirrored rules NEVER EXISTED at the school and are removed:
 *   ❌ passage_palier (−10 000 DZD on 5AP→1AM / 4AM→1ère) — the workbook's
 *      10 000 DZD remises decompose as two 5 000 sibling components.
 *   ❌ highest_average (rank 1 → −10%).
 *   ❌ seniority_5y (−5%).
 *
 * Every money value is in **centimes (Long)** to avoid floating-point
 * rounding. The desktop's DZD value is multiplied by 100 here.
 *
 * Pure: zero I/O, zero side effects. The same inputs produce the same
 * outputs on Android and desktop.
 */

// ── Canonical amounts (centimes — DZD value × 100) ──────────────────────
/** Group thousands with a plain space (fr-FR style, canonical byte-identical
 *  across desktop / Android / reports). Mirrors the desktop's groupAmountFr. */
internal fun groupAmountFr(n: Long): String {
    var rest = kotlin.math.abs(n)
    if (rest == 0L) return "0"
    val parts = mutableListOf<String>()
    while (rest > 0) {
        parts.add(0, (rest % 1000).toString())
        rest /= 1000
    }
    return parts.joinToString(" ")
}

const val SIBLING_PER_CHILD_AMOUNT: Long = 500_000L            // 5,000 DZD per additional child
const val EARLY_ANNUAL_RATE: Double = 0.05                     // −5% of the SCOLARITÉ (workbook SUM(F)*0.05)

// ── REMOVED (CALC-001) ───────────────────────────────────────────────────
// PASSAGE_DE_PALIER_AMOUNT / HIGHEST_AVERAGE_RATE / SENIORITY_RATE /
// SENIORITY_YEARS / CYCLE_TRANSITIONS / evaluatePassageDePalier /
// evaluateAcademicExcellenceDiscount / evaluateSeniorityDiscount /
// isCycleTransition — the rules never existed at the school (see the
// workbook evidence in the desktop discount-rules.ts header).

/**
 * Evaluate the `sibling_fixed` discount (−5 000 DZD per additional child).
 *
 * @param childIndex 1-based index of this child within the family
 *                   (1 = first child = no discount; 2 = second child =
 *                   one discount; 3 = third child = two discounts; etc.)
 * @param perChild   Override the canonical per-child amount (centimes).
 *                   Defaults to [SIBLING_PER_CHILD_AMOUNT].
 * @return The discount amount in centimes (negative Long). 0 if `childIndex <= 1`.
 */
fun evaluateSiblingDiscount(childIndex: Int, perChild: Long = SIBLING_PER_CHILD_AMOUNT): Long {
    if (childIndex <= 1) return 0L
    return -(perChild * (childIndex - 1).toLong())
}

/**
 * Evaluate the `full_annual` early-payment discount (−5% of the SCOLARITÉ).
 *
 * Applies only when:
 *   - `paymentPlan == FULL_ANNUAL`, AND
 *   - `paymentDate` is on or before June 30 (end of day, UTC) of the
 *     `academicYearStartYear`.
 *
 * CALC-002: the base is the scolarité ONLY — the workbook's
 * `=+SUM(F…)*0.05` sums the Frais Scolarisation column, never the F I
 * (registration) or Services (transport) columns.
 *
 * @param paymentDate     ISO-8601 string or epoch-millis when the payment
 *                        is collected.
 * @param grossScolarite  Gross annual SCOLARITÉ in centimes (FI and
 *                        transport excluded).
 * @param paymentPlan      The student's payment plan (full_annual / tranches).
 * @param academicYearStartYear  The calendar year in which the academic year starts.
 * @return The discount amount in centimes (negative Long). 0 if conditions not met.
 */
fun evaluateEarlyAnnualDiscount(
    paymentDate: String,
    grossScolarite: Long,
    paymentPlan: PaymentPlan,
    academicYearStartYear: Int,
): Long {
    if (paymentPlan != PaymentPlan.FULL_ANNUAL) return 0L
    val cutoff = OffsetDateTime.of(academicYearStartYear, 6, 30, 23, 59, 59, 0, ZoneOffset.UTC).toInstant()
    val whenInstant = parseIsoInstantSafe(paymentDate)
    if (whenInstant.isAfter(cutoff)) return 0L
    val gross = grossScolarite.toDouble()
    return -Math.round(gross * EARLY_ANNUAL_RATE)
}

// ── Single-pass orchestrator ──────────────────────────────────────────────

/**
 * Discount evaluation result — one entry per rule that fired.
 */
data class DiscountEvaluation(
    val code: String,        // "sibling_fixed" / "full_annual"
    val label: String,
    val amount: Long,        // centimes — negative for reductions
    val applied: Boolean,
    val reason: String,
)

/**
 * Parameters for [evaluateAllSystemDiscounts].
 *
 * All money values are in centimes (Long) to match the rest of the
 * Android financial engine.
 */
data class EvaluateAllDiscountsParams(
    /**
     * CALC-001: the base for percentage rules = the gross SCOLARITÉ
     * (FI and transport excluded — mirrors the workbook's `SUM(F)*0.05`).
     */
    val grossScolarite: Long? = null,
    /**
     * DEPRECATED alias for [grossScolarite] (CALC-001 rename). Kept so the
     * shared cross-platform scenario fixtures and older call sites keep
     * compiling; new code must pass `grossScolarite`.
     */
    val grossTuition: Long = 0L,
    /**
     * CALC-001: previous-grade / previous-rank inputs are DEPRECATED and
     * ignored (the rules they fed never existed). Kept for call-site
     * compatibility.
     */
    val previousGradeLevel: String? = null,
    val currentGradeLevel: String = "1ap",
    val childIndex: Int,
    val paymentPlan: PaymentPlan,
    val paymentDate: String,
    val academicYearStartYear: Int,
    val academicYearStart: String = "2026-09-01T00:00:00Z",
    val enrollmentDate: String = "2026-09-01T00:00:00Z",
    val previousRank: Int? = null,
    val siblingPerChildAmount: Long = SIBLING_PER_CHILD_AMOUNT,
)

/**
 * Run the 2 REAL discount rules in a single pass on the gross scolarité.
 * Returns one entry per rule that fired (amount ≠ 0).
 *
 * CALC-001: the fictional rules (passage_palier, highest_average,
 * seniority_5y) never existed at the school and are intentionally absent.
 */
fun evaluateAllSystemDiscounts(params: EvaluateAllDiscountsParams): List<DiscountEvaluation> {
    val out = mutableListOf<DiscountEvaluation>()
    val grossScolarite = params.grossScolarite ?: params.grossTuition

    // Rule 1: sibling_fixed (per additional child)
    val sibling = evaluateSiblingDiscount(params.childIndex, params.siblingPerChildAmount)
    if (sibling != 0L) {
        out.add(DiscountEvaluation(
            code = "sibling_fixed",
            // CANONICAL (cross-platform equivalence): byte-identical to the
            // desktop label — plain-space fr-FR grouping.
            label = "Fratrie — enfant #${params.childIndex} (−${groupAmountFr(Math.abs(sibling) / 100)} DA)",
            amount = sibling,
            applied = true,
            reason = "Enfant ${params.childIndex} de la fratrie",
        ))
    }

    // Rule 2: full_annual (early annual payment before June 30 — 5% scolarité)
    val early = evaluateEarlyAnnualDiscount(
        params.paymentDate, grossScolarite, params.paymentPlan, params.academicYearStartYear,
    )
    if (early != 0L) {
        out.add(DiscountEvaluation(
            code = "full_annual",
            label = "Paiement annuel avant le 30 juin (−5% scolarité)",
            amount = early,
            applied = true,
            reason = "Paiement intégral avant le 30 juin",
        ))
    }

    return out.toList()
}

/** Sum the amounts of all fired discount rules. */
fun sumDiscounts(evaluations: List<DiscountEvaluation>): Long =
    evaluations.sumOf { it.amount }
