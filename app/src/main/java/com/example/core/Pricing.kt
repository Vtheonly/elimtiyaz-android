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
    var weightedSum = 0.0
    var coefSum = 0
    for (a in assessments) {
        val avg = a.subjectAverage ?: continue
        weightedSum += avg * a.coefficient
        coefSum += a.coefficient
    }
    return if (coefSum == 0) null else weightedSum / coefSum
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
    if (devoir1 == null && devoir2 == null && examen == null) return null
    val d1 = devoir1 ?: 0.0
    val d2 = devoir2 ?: 0.0
    val ex = examen ?: 0.0
    return (d1 + d2 + 2 * ex) / 4.0
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
