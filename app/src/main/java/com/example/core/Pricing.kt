package com.example.core

import com.example.domain.model.Assessment
import com.example.domain.model.PricingConfig
import com.example.domain.model.PricingDiscount

/**
 * Pricing + academic financial calculations — mirrors the desktop's
 * `domain/model/pricing.ts` and `domain/model/academic.ts` formulas
 * exactly so that mobile and desktop produce identical numbers.
 *
 * Every money value is in **centimes (Long)** to avoid floating-point
 * rounding issues. The signed-amount convention (`+charge, -payment`)
 * is enforced in [LedgerEntryFactory]; this file is purely for
 * discount + GPA computation.
 */

/**
 * Compute the sibling discount for a family with [childrenCount] children.
 *
 * Per plan §06.04 + desktop `computeSiblingDiscount(config, childrenCount)`:
 *   - 0 or 1 child → 0 discount
 *   - N > 1        → (N − 1) × `sibling_fixed.amount`
 *
 * `sibling_fixed.amount` is stored as a **negative** number for fixed-amount
 * discounts (e.g. −5,000 DZD = −500,000 centimes per additional child).
 *
 * @return The discount amount in centimes — a **negative** Long (reduces the
 *         total due). Returns 0 if the `sibling_fixed` discount is missing
 *         from the config or if `childrenCount <= 1`.
 */
fun computeSiblingDiscount(config: PricingConfig, childrenCount: Int): Long {
    if (childrenCount <= 1) return 0L
    val entry = config.discounts.firstOrNull { it.code == "sibling_fixed" } ?: return 0L
    // `amount` is negative for fixed-amount discounts (e.g. -500_000 centimes).
    return entry.amount * (childrenCount - 1).toLong()
}

/**
 * Compute the family tuition total before sibling discount.
 *
 * Mirrors desktop's Excel formula L = registration + tuition + transport − discount
 * (per `Entire_Project_Plan.txt` §1.5 + `Clients_Sheet_Merged.txt` columns L/P/Q).
 *
 * @param registrationFee   Registration fee in centimes (per child, flat).
 * @param tuitionFee        Tuition in centimes (per child, by grade level).
 * @param transportFee      Transport fee in centimes (0 if not using transport).
 * @param discountAmount    Pre-computed discount in centimes (negative for reductions).
 */
fun computeTuitionTotal(
    registrationFee: Long,
    tuitionFee: Long,
    transportFee: Long,
    discountAmount: Long = 0L,
): Long = registrationFee + tuitionFee + transportFee + discountAmount

/**
 * Compute the overall GPA for a student from a list of [Assessment] entries.
 *
 * Mirrors desktop `computeOverallGpa(assessments)`:
 *   - Skip assessments where `subjectAverage == null`.
 *   - `weightedSum += subjectAverage × coefficient`
 *   - `coefSum += coefficient`
 *   - Return `weightedSum / coefSum` (or null if coefSum == 0).
 *
 * Each `subjectAverage` is on a 0–20 scale; the GPA is also 0–20.
 * Passing grade is 10.0/20.0 by default (see [isPassing]).
 */
fun computeOverallGpa(assessments: List<Assessment>): Double? {
    // CANONICAL (cross-platform equivalence fix):
    //   1. EXTRACURRICULAR modules are excluded from the official GPA —
    //      matches desktop academic.ts and SQL fn_calculate_student_term_gpa.
    //      Previously Android had no isExtracurricular flag on Assessment and
    //      contaminated the GPA with chess / speech therapy / sports marks.
    //   2. Integer-scaled math (centi-coefficients) with Math.round so the
    //      result is bit-identical to the desktop engine AND the SQL
    //      ROUND(numeric, 2) at .xx5 boundaries.
    var weightedSumCents = 0L   // Σ(avg_cents × coef_cents)
    var coefSumCents = 0L
    for (a in assessments) {
        if (a.isExtracurricular) continue
        val avg = a.subjectAverage ?: computeSubjectAverage(a.devoir1, a.devoir2, a.examen) ?: continue
        weightedSumCents += Math.round(avg * 100.0).toLong() * Math.round(a.coefficient * 100.0).toLong()
        coefSumCents += Math.round(a.coefficient * 100.0).toLong()
    }
    if (coefSumCents == 0L) return null
    val gpaCents = Math.round(weightedSumCents.toDouble() / coefSumCents.toDouble())
    return gpaCents / 100.0
}

/**
 * Compute the subject average for a single assessment.
 *
 * Mirrors desktop `computeSubjectAverage(devoir1, devoir2, examen)`:
 *   - Returns `null` if all three are null.
 *   - Nulls are treated as 0.
 *   - `average = (d1 + d2 + 2 × ex) / 4`  — Examen is weighted 2×.
 *   - The server-side trigger `compute_grade_subject_average()` is the
 *     authoritative computation; this client-side function is for
 *     instant UI preview before submit.
 */
fun computeSubjectAverage(devoir1: Double?, devoir2: Double?, examen: Double?): Double? {
    // CANONICAL (cross-platform equivalence fix): the subject average is only
    // computable when ALL THREE marks exist. This matches the SQL trigger
    // compute_grade_subject_average() (the persistence-layer authority), which
    // leaves subject_average NULL while any mark is missing. The previous
    // coerce-nulls-to-0 rule deflated partial assessments and diverged from
    // the backend.
    if (devoir1 == null || devoir2 == null || examen == null) return null
    // Integer-scaled rounding — matches desktop + SQL ROUND(numeric, 2) at
    // .xx5 boundaries (score cents ÷ 4 is exact in binary).
    val d1c = Math.round(devoir1 * 100.0)
    val d2c = Math.round(devoir2 * 100.0)
    val exc = Math.round(examen * 100.0)
    val avgCents = Math.round((d1c + d2c + 2 * exc) / 4.0)
    return avgCents / 100.0
}

/**
 * Whether a GPA meets the passing threshold.
 *
 * Default passing grade is 10.0 / 20.0 per plan §09.01.
 * Admins can configure a different threshold via the system_settings table.
 */
fun isPassing(gpa: Double, passingGrade: Double = 10.0): Boolean = gpa >= passingGrade

/**
 * Validate that a score (devoir/examen) is in the legal 0–20 range.
 * Mirrors desktop `validateScore(value)`.
 */
fun validateScore(value: Double): Boolean = value.isFinite() && value in 0.0..20.0

/**
 * Find a discount by its canonical code in the pricing config.
 * Mirrors desktop `findDiscountByCode(config, code)`.
 */
fun findDiscountByCode(config: PricingConfig, code: String): PricingDiscount? =
    config.discounts.firstOrNull { it.code == code }
