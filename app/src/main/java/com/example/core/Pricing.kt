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
        // Vault §06.02 (iteration 2) — when the persisted subjectAverage is
        // missing (e.g. legacy rows or marks that haven't been recomputed),
        // recompute it from the per-row coefficient SNAPSHOT (the values
        // that were in effect when the marks were entered). Previously this
        // fallback called computeSubjectAverage(d1, d2, ex) with the hard-
        // coded (1, 1, 2) recipe — that path now honors the per-component
        // coefficients configured on the subject at entry time.
        val avg = a.subjectAverage
            ?: computeSubjectAverage(a.devoir1, a.devoir2, a.examen,
                                      a.coefficientDevoir1, a.coefficientDevoir2, a.coefficientExamen)
            ?: continue
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
 * ITERATION-2 RECIPE (vault §06.02 — "we still don't know the actual
 * formula"): each component (Devoir 1, Devoir 2, Examen) carries its OWN
 * coefficient, admin-configurable per subject. The canonical Android
 * formula is now:
 *     subjectAverage = (D1×c1 + D2×c2 + Ex×c3) / (c1 + c2 + c3)
 *
 * Defaults (c1 = 1, c2 = 1, c3 = 2) preserve the historical recipe
 * `(D1 + D2 + 2×Ex) / 4` bit-identically — same numerator, same denominator.
 * When an admin overrides any of the three per-component coefficients on a
 * subject, the new weights take effect without a code change.
 *
 * Canonical invariants preserved:
 *   - The average is only computable when ALL THREE marks are present
 *     (mirrors the SQL trigger `compute_grade_subject_average()` — the
 *     persistence-layer authority — which leaves `subject_average` NULL
 *     while any mark is missing).
 *   - Integer-scaled centime math + Math.round → bit-identical to desktop
 *     + SQL `ROUND(numeric, 2)` at .xx5 boundaries.
 *   - A coefficient of 0 means "skip this component" — both the
 *     numerator and the denominator exclude it. This lets an admin disable
 *     a component without deleting the mark.
 *   - All three coefficients at 0 (degenerate) → returns null (avoids
 *     divide-by-zero).
 *
 * @param devoir1         Devoir 1 score (0–20) or null.
 * @param devoir2         Devoir 2 score (0–20) or null.
 * @param examen          Examen score (0–20) or null.
 * @param coefDevoir1     Devoir 1 coefficient (default 1.0 — historical).
 * @param coefDevoir2     Devoir 2 coefficient (default 1.0 — historical).
 * @param coefExamen     Examen coefficient (default 2.0 — historical,
 *                       the "Examen weighted 2×" rule).
 */
fun computeSubjectAverage(
    devoir1: Double?,
    devoir2: Double?,
    examen: Double?,
    coefDevoir1: Double = 1.0,
    coefDevoir2: Double = 1.0,
    coefExamen: Double = 2.0,
): Double? {
    // CANONICAL (cross-platform equivalence fix): the subject average is only
    // computable when ALL THREE marks exist. This matches the SQL trigger
    // compute_grade_subject_average() (the persistence-layer authority),
    // which leaves subject_average NULL while any mark is missing. The
    // previous coerce-nulls-to-0 rule deflated partial assessments and
    // diverged from the backend.
    if (devoir1 == null || devoir2 == null || examen == null) return null
    // Integer-scaled rounding — matches desktop + SQL ROUND(numeric, 2) at
    // .xx5 boundaries. The numerator is Σ(score_cents × coef_cents) and the
    // denominator is Σ(coef_cents); both are exact in integer arithmetic,
    // so the final Math.round matches the SQL ROUND(numeric, 2) bit-for-bit.
    val d1c = Math.round(devoir1 * 100.0)
    val d2c = Math.round(devoir2 * 100.0)
    val exc = Math.round(examen * 100.0)
    val c1c = Math.round(coefDevoir1 * 100.0).toLong()
    val c2c = Math.round(coefDevoir2 * 100.0).toLong()
    val c3c = Math.round(coefExamen * 100.0).toLong()
    // Skip components whose coefficient is 0 — both numerator and
    // denominator exclude them, so an admin can disable a component without
    // losing the others. (A 0-coef component contributes 0 to the numerator
    // anyway, but excluding it from the denominator keeps the math correct
    // when the others carry non-zero weights.)
    var numeratorCents = 0L
    var denominatorCents = 0L
    if (c1c != 0L) { numeratorCents += d1c * c1c; denominatorCents += c1c }
    if (c2c != 0L) { numeratorCents += d2c * c2c; denominatorCents += c2c }
    if (c3c != 0L) { numeratorCents += exc * c3c; denominatorCents += c3c }
    if (denominatorCents == 0L) return null  // all three coefs degenerate
    val avgCents = Math.round(numeratorCents.toDouble() / denominatorCents.toDouble())
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
