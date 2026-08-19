package com.example.core

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Discount Engine — Kotlin port of the desktop
 * `src/domain/calc/pricing/discount-engine.ts` + `discount-rules.ts`.
 *
 * CANONICAL-FINANCIAL-LOGIC.md §5 — implements the 5 canonical discount
 * rules in a SINGLE PASS on the gross annual tuition. Percentage rules
 * apply to the gross amount, NOT to the running total (no compounding).
 *
 * Every money value is in **centimes (Long)** to avoid floating-point
 * rounding. The desktop's DZD value is multiplied by 100 here.
 *
 * Pure: zero I/O, zero side effects. The same inputs produce the same
 * outputs on Android and desktop.
 */

// ── Canonical amounts (centimes — DZD value × 100) ──────────────────────
const val PASSAGE_DE_PALIER_AMOUNT: Long = -1_000_000L         // −10,000 DZD
const val SIBLING_PER_CHILD_AMOUNT: Long = 500_000L            // 5,000 DZD per additional child
const val EARLY_ANNUAL_RATE: Double = 0.10                      // −10%
const val HIGHEST_AVERAGE_RATE: Double = 0.10                  // −10%
const val SENIORITY_RATE: Double = 0.05                         // −5%
const val SENIORITY_YEARS: Int = 5

private const val MS_PER_DAY: Long = 86_400_000L
private const val DAYS_PER_YEAR_AVG: Double = 365.25

/**
 * Grade-level transitions that qualify a student for the `passage_palier`
 * discount. Mirrors the desktop's `CYCLE_TRANSITIONS` array.
 */
private val CYCLE_TRANSITIONS: List<Pair<String, String>> = listOf(
    "5ap" to "1am",
    "4am" to "1ere_annee",
)

/**
 * Evaluate the `passage_palier` discount (−10,000 DZD when student
 * transitions between academic cycles).
 *
 * @param previousGradeLevel The student's grade level in the previous
 *        academic year (null if first enrollment).
 * @param currentGradeLevel  The student's grade level for the upcoming year.
 * @return The discount amount in centimes (negative Long). 0 if no
 *         transition matches.
 */
fun evaluatePassageDePalier(previous: String?, current: String): Long {
    if (previous == null) return 0L
    val crossed = CYCLE_TRANSITIONS.any { (from, to) -> previous == from && current == to }
    return if (crossed) PASSAGE_DE_PALIER_AMOUNT else 0L
}

/**
 * Evaluate the `sibling_fixed` discount (−5,000 DZD per additional child).
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
 * Evaluate the `full_annual` early-payment discount (−10% of gross tuition).
 *
 * Applies only when:
 *   - `paymentPlan == FULL_ANNUAL`, AND
 *   - `paymentDate` is on or before June 30 (end of day, UTC) of the
 *     `academicYearStartYear`.
 *
 * @param paymentDate     ISO-8601 string or epoch-millis when the payment
 *                        is collected.
 * @param grossTuition    Gross annual tuition in centimes (before any discount).
 * @param paymentPlan      The student's payment plan (full_annual / tranches).
 * @param academicYearStartYear  The calendar year in which the academic year starts.
 * @return The discount amount in centimes (negative Long). 0 if conditions not met.
 */
fun evaluateEarlyAnnualDiscount(
    paymentDate: String,
    grossTuition: Long,
    paymentPlan: PaymentPlan,
    academicYearStartYear: Int,
): Long {
    if (paymentPlan != PaymentPlan.FULL_ANNUAL) return 0L
    val cutoff = OffsetDateTime.of(academicYearStartYear, 6, 30, 23, 59, 59, 0, ZoneOffset.UTC).toInstant()
    val whenInstant = parseIsoInstantSafe(paymentDate)
    if (whenInstant.isAfter(cutoff)) return 0L
    val gross = grossTuition.toDouble()
    return -Math.round(gross * EARLY_ANNUAL_RATE)
}

/**
 * Evaluate the `highest_average` academic-excellence discount (−10% of
 * gross tuition) when the student was rank 1 in their previous palier.
 *
 * @param previousRank  The student's class rank last year (1 = top).
 *                      null if unknown / not ranked.
 * @param grossTuition  Gross annual tuition in centimes.
 * @return The discount amount in centimes (negative Long). 0 if not rank 1.
 */
fun evaluateAcademicExcellenceDiscount(previousRank: Int?, grossTuition: Long): Long {
    if (previousRank == null || previousRank != 1) return 0L
    val gross = grossTuition.toDouble()
    return -Math.round(gross * HIGHEST_AVERAGE_RATE)
}

/**
 * Evaluate the `seniority_5y` discount (−5% of gross tuition) when the
 * student has been enrolled for ≥ 5 years before the academic year start.
 *
 * @param enrollmentDate      ISO-8601 string of the student's enrollment date.
 * @param academicYearStart   ISO-8601 string of the academic year start.
 * @param grossTuition        Gross annual tuition in centimes.
 * @return The discount amount in centimes (negative Long). 0 if seniority < 5 years.
 */
fun evaluateSeniorityDiscount(
    enrollmentDate: String,
    academicYearStart: String,
    grossTuition: Long,
): Long {
    val enrolled = parseIsoInstantSafe(enrollmentDate)
    val yearStart = parseIsoInstantSafe(academicYearStart)
    val thresholdMs = (SENIORITY_YEARS.toLong() * DAYS_PER_YEAR_AVG * MS_PER_DAY).toLong()
    if (yearStart.toEpochMilli() - enrolled.toEpochMilli() <= thresholdMs) return 0L
    val gross = grossTuition.toDouble()
    return -Math.round(gross * SENIORITY_RATE)
}

/** Convenience: is the [previous] → [current] transition a cycle boundary? */
fun isCycleTransition(previous: String?, current: String): Boolean {
    if (previous == null) return false
    return CYCLE_TRANSITIONS.any { (from, to) -> previous == from && current == to }
}

// ── Single-pass orchestrator ──────────────────────────────────────────────

/**
 * Discount evaluation result — one entry per rule that fired.
 */
data class DiscountEvaluation(
    val code: String,        // "passage_palier" / "sibling_fixed" / "full_annual" / "highest_average" / "seniority_5y"
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
    val grossTuition: Long,
    val previousGradeLevel: String?,
    val currentGradeLevel: String,
    val childIndex: Int,
    val paymentPlan: PaymentPlan,
    val paymentDate: String,
    val academicYearStartYear: Int,
    val academicYearStart: String,
    val enrollmentDate: String,
    val previousRank: Int?,
    val siblingPerChildAmount: Long = SIBLING_PER_CHILD_AMOUNT,
)

/**
 * Run all 5 canonical discount rules in a single pass on the gross annual
 * tuition. Returns one entry per rule that fired (amount ≠ 0).
 *
 * CANONICAL-FINANCIAL-LOGIC.md §5 — discounts are applied ONCE on gross,
 * then the net is split into tranches. Calling this per-tranche triples
 * the discount.
 */
fun evaluateAllSystemDiscounts(params: EvaluateAllDiscountsParams): List<DiscountEvaluation> {
    val out = mutableListOf<DiscountEvaluation>()

    // Rule 1: passage_palier (cycle transition)
    val passage = evaluatePassageDePalier(params.previousGradeLevel, params.currentGradeLevel)
    if (passage != 0L) {
        out.add(DiscountEvaluation(
            code = "passage_palier",
            label = "Passage de palier (−10 000 DA)",
            amount = passage,
            applied = true,
            reason = "Transition ${params.previousGradeLevel ?: "—"} → ${params.currentGradeLevel}",
        ))
    }

    // Rule 2: sibling_fixed (per additional child)
    val sibling = evaluateSiblingDiscount(params.childIndex, params.siblingPerChildAmount)
    if (sibling != 0L) {
        out.add(DiscountEvaluation(
            code = "sibling_fixed",
            label = "Fratrie — enfant #${params.childIndex} (−${Math.abs(sibling) / 100} DA)",
            amount = sibling,
            applied = true,
            reason = "Enfant ${params.childIndex} de la fratrie",
        ))
    }

    // Rule 3: full_annual (early annual payment before June 30)
    val early = evaluateEarlyAnnualDiscount(
        params.paymentDate, params.grossTuition, params.paymentPlan, params.academicYearStartYear,
    )
    if (early != 0L) {
        out.add(DiscountEvaluation(
            code = "full_annual",
            label = "Paiement annuel avant le 30 juin (−10%)",
            amount = early,
            applied = true,
            reason = "Paiement intégral avant le 30 juin",
        ))
    }

    // Rule 4: highest_average (rank 1 last year)
    val excellence = evaluateAcademicExcellenceDiscount(params.previousRank, params.grossTuition)
    if (excellence != 0L) {
        out.add(DiscountEvaluation(
            code = "highest_average",
            label = "Meilleure moyenne du palier (−10%)",
            amount = excellence,
            applied = true,
            reason = "Rang 1 au palier l'année précédente",
        ))
    }

    // Rule 5: seniority_5y (5+ years enrolled)
    val seniority = evaluateSeniorityDiscount(
        params.enrollmentDate, params.academicYearStart, params.grossTuition,
    )
    if (seniority != 0L) {
        out.add(DiscountEvaluation(
            code = "seniority_5y",
            label = "Ancienneté > 5 ans (−5%)",
            amount = seniority,
            applied = true,
            reason = "Plus de 5 ans d'ancienneté",
        ))
    }

    return out.toList()
}

/** Sum the amounts of all fired discount rules. */
fun sumDiscounts(evaluations: List<DiscountEvaluation>): Long =
    evaluations.sumOf { it.amount }
