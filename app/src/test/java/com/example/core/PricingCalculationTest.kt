package com.example.core

import com.example.domain.model.Assessment
import com.example.domain.model.PricingConfig
import com.example.domain.model.PricingDiscount
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for pricing + GPA calculations — mirrors the desktop's
 * `tests/domain/pricing/discounts.test.ts` and `tests/domain/academics/gpa.test.ts`.
 */
class PricingCalculationTest {

    private val config = PricingConfig(
        id = "prc-test", tenantId = "ten-test", isActive = true,
        registrationFee = 0L, latePenaltyPerDay = 200L, secondApronFee = 200_000L,
        updatedAt = "2026-09-01T00:00:00Z",
        discounts = listOf(
            PricingDiscount("dsc-sib", "ten-test", "sibling_fixed", "Sibling", -500_000L, "fixed_amount"),
            PricingDiscount("dsc-pal", "ten-test", "passage_palier", "Palier", -1_000_000L, "fixed_amount"),
        ),
    )

    @Test fun `sibling discount is zero for single child`() {
        assertEquals(0L, computeSiblingDiscount(config, 1))
        assertEquals(0L, computeSiblingDiscount(config, 0))
    }

    @Test fun `sibling discount scales linearly with children count`() {
        // 2 children → 1 × -500,000 = -500,000
        assertEquals(-500_000L, computeSiblingDiscount(config, 2))
        // 3 children → 2 × -500,000 = -1,000,000
        assertEquals(-1_000_000L, computeSiblingDiscount(config, 3))
        // 4 children → 3 × -500,000 = -1,500,000
        assertEquals(-1_500_000L, computeSiblingDiscount(config, 4))
    }

    @Test fun `subject average uses examen weighted 2x`() {
        // (D1 + D2 + 2×Ex) / 4
        assertEquals(15.0, computeSubjectAverage(14.0, 10.0, 18.0)!!, 0.001) // (14+10+36)/4 = 15
        assertEquals(10.0, computeSubjectAverage(10.0, 10.0, 10.0)!!, 0.001)
        assertEquals(0.0, computeSubjectAverage(0.0, 0.0, 0.0)!!, 0.001)
    }

    @Test fun `subject average is null when ANY mark is missing - canonical all-3 rule`() {
        // CANONICAL (cross-platform equivalence fix): the average is only
        // computable when all three marks exist — mirrors the SQL trigger
        // compute_grade_subject_average() (persistence authority).
        assertNull(computeSubjectAverage(null, null, 10.0))
        assertNull(computeSubjectAverage(5.0, null, 2.5))
        assertNull(computeSubjectAverage(null, 16.0, 18.0))
        assertNull(computeSubjectAverage(14.0, null, null))
    }

    @Test fun `subject average rounds xx5 boundaries with decimal half-up - SQL parity`() {
        // (12 + 13 + 2*14.75)/4 = 54.5/4 = 13.625 → 13.63
        assertEquals(13.63, computeSubjectAverage(12.0, 13.0, 14.75)!!, 1e-9)
        // (11 + 13 + 2*14.775)/4 = 53.55/4 = 13.3875 → 13.39
        assertEquals(13.39, computeSubjectAverage(11.0, 13.0, 14.775)!!, 1e-9)
    }

    @Test fun `subject average returns null when all null`() {
        assertNull(computeSubjectAverage(null, null, null))
    }

    // ── Vault §06.02 (iteration 2) — per-COMPONENT coefficients ───────────
    //
    // The previous build hard-coded (D1 + D2 + 2×Ex) / 4. Iteration 2 lets
    // the admin override each component's coefficient per subject; the
    // defaults (1, 1, 2) MUST stay bit-identical to the historical recipe
    // so the platform doesn't drift on existing GPAs.

    @Test fun `per-component coefficients default to historical 1 1 2 recipe`() {
        // Defaults: (D1×1 + D2×1 + Ex×2) / (1+1+2) == (D1 + D2 + 2×Ex) / 4.
        val explicit = computeSubjectAverage(14.0, 16.0, 18.0, 1.0, 1.0, 2.0)
        val defaulted = computeSubjectAverage(14.0, 16.0, 18.0)
        assertEquals(16.5, explicit!!, 1e-9)
        assertEquals(explicit, defaulted, 1e-12)
    }

    @Test fun `per-component coefficients override the historical recipe`() {
        // c1=2, c2=1, c3=3 → (14×2 + 16×1 + 18×3) / (2+1+3) = (28+16+54)/6 = 98/6 = 16.333…
        val avg = computeSubjectAverage(14.0, 16.0, 18.0, 2.0, 1.0, 3.0)!!
        assertEquals(16.33, avg, 1e-9)
    }

    @Test fun `per-component zero coefficient disables that component`() {
        // c1=0, c2=1, c3=2 → (16 + 2×18) / (1+2) = 52/3 = 17.333… → 17.33
        // D1's mark is ignored entirely (numerator + denominator both skip it).
        val avg = computeSubjectAverage(14.0, 16.0, 18.0, 0.0, 1.0, 2.0)!!
        assertEquals(17.33, avg, 1e-9)
    }

    @Test fun `per-component all-zero coefficients return null - avoids divide-by-zero`() {
        assertNull(computeSubjectAverage(14.0, 16.0, 18.0, 0.0, 0.0, 0.0))
    }

    @Test fun `per-component half-up rounding at xx5 boundaries - SQL parity`() {
        // c1=1, c2=1, c3=3, marks 12.0/13.0/14.75
        // (12 + 13 + 3×14.75) / (1+1+3) = (12+13+44.25)/5 = 69.25/5 = 13.85
        assertEquals(13.85, computeSubjectAverage(12.0, 13.0, 14.75, 1.0, 1.0, 3.0)!!, 1e-9)
        // c1=1, c2=2, c3=1, marks 10.0/10.0/10.0 → (10+20+10)/4 = 10.0
        assertEquals(10.0, computeSubjectAverage(10.0, 10.0, 10.0, 1.0, 2.0, 1.0)!!, 1e-9)
    }

    @Test fun `overall GPA honors per-component snapshot on assessment rows`() {
        // Vault §06.02 — when subjectAverage is null on a legacy row,
        // computeOverallGpa falls back to the per-row coefficient SNAPSHOT
        // (a.coefficientDevoir1/2/Examen) instead of the historical (1,1,2).
        // Build two assessments with subjectAverage=null and an explicit
        // per-component snapshot. The fallback must honor those weights.
        val a1 = Assessment(
            id = "a1", tenantId = "t", studentId = "s1", subjectId = "sub1",
            classId = "c1", term = "T1", academicYear = "2026",
            devoir1 = 14.0, devoir2 = 16.0, examen = 18.0,
            subjectAverage = null, coefficient = 4.0, isExtracurricular = false,
            enteredBy = "u", enteredAt = "now",
            // Override: Examen counts 3× (instead of the historical 2×).
            coefficientDevoir1 = 1.0, coefficientDevoir2 = 1.0, coefficientExamen = 3.0,
        )
        // (14 + 16 + 3×18) / 5 = 84/5 = 16.8
        val a2 = Assessment(
            id = "a2", tenantId = "t", studentId = "s1", subjectId = "sub2",
            classId = "c1", term = "T1", academicYear = "2026",
            devoir1 = 10.0, devoir2 = 10.0, examen = 10.0,
            subjectAverage = null, coefficient = 2.0, isExtracurricular = false,
            enteredBy = "u", enteredAt = "now",
            coefficientDevoir1 = 1.0, coefficientDevoir2 = 1.0, coefficientExamen = 1.0,
        )
        // (10 + 10 + 10) / 3 = 10.0
        // GPA = (16.8×4 + 10.0×2) / (4+2) = (67.2+20)/6 = 87.2/6 = 14.533… → 14.53
        assertEquals(14.53, computeOverallGpa(listOf(a1, a2))!!, 1e-9)
    }

    @Test fun `overall GPA is weighted average of subject averages`() {
        val assessments = listOf(
            Assessment("a1", "t", "s1", "sub1", "c1", "T1", "2026", 14.0, 10.0, 18.0, null, 4.0, false, "t", "now"),
            Assessment("a2", "t", "s1", "sub2", "c1", "T1", "2026", 12.0, 12.0, 12.0, null, 2.0, false, "t", "now"),
        )
        // Subject averages: 15.0 (coef 4), 12.0 (coef 2)
        // GPA = (15×4 + 12×2) / (4+2) = (60+24)/6 = 14.0
        assertEquals(14.0, computeOverallGpa(assessments)!!, 0.001)
    }

    @Test fun `overall GPA skips null subject averages`() {
        val assessments = listOf(
            Assessment("a1", "t", "s1", "sub1", "c1", "T1", "2026", 14.0, 10.0, 18.0, null, 4.0, false, "t", "now"),
            Assessment("a2", "t", "s1", "sub2", "c1", "T1", "2026", null, null, null, null, 2.0, false, "t", "now"),
        )
        // Only sub1 has a valid average (15.0, coef 4)
        assertEquals(15.0, computeOverallGpa(assessments)!!, 0.001)
    }

    @Test fun `overall GPA returns null when no valid assessments`() {
        val assessments = listOf(
            Assessment("a1", "t", "s1", "sub1", "c1", "T1", "2026", null, null, null, null, 4.0, false, "t", "now"),
        )
        assertNull(computeOverallGpa(assessments))
    }

    @Test fun `passing threshold is 10 by default`() {
        assertTrue(isPassing(10.0))
        assertTrue(isPassing(15.0))
        assertFalse(isPassing(9.99))
        assertFalse(isPassing(0.0))
    }

    @Test fun `score validation enforces 0-20 range`() {
        assertTrue(validateScore(0.0))
        assertTrue(validateScore(10.5))
        assertTrue(validateScore(20.0))
        assertFalse(validateScore(-0.1))
        assertFalse(validateScore(20.1))
        assertFalse(validateScore(Double.NaN))
    }

    @Test fun `find discount by code returns correct entry`() {
        val sibling = findDiscountByCode(config, "sibling_fixed")
        assertNotNull(sibling)
        assertEquals(-500_000L, sibling!!.amount)

        val palier = findDiscountByCode(config, "passage_palier")
        assertNotNull(palier)
        assertEquals(-1_000_000L, palier!!.amount)

        assertNull(findDiscountByCode(config, "nonexistent"))
    }
}
