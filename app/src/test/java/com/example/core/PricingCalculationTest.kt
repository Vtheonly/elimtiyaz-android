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

    @Test fun `subject average handles nulls as zero`() {
        assertEquals(5.0, computeSubjectAverage(null, null, 10.0)!!, 0.001) // (0+0+20)/4 = 5
        assertEquals(2.5, computeSubjectAverage(5.0, null, 2.5)!!, 0.001) // (5+0+5)/4 = 2.5
    }

    @Test fun `subject average returns null when all null`() {
        assertNull(computeSubjectAverage(null, null, null))
    }

    @Test fun `overall GPA is weighted average of subject averages`() {
        val assessments = listOf(
            Assessment("a1", "t", "s1", "sub1", "c1", "T1", "2026", 14.0, 10.0, 18.0, null, 4, "t", "now"),
            Assessment("a2", "t", "s1", "sub2", "c1", "T1", "2026", 12.0, 12.0, 12.0, null, 2, "t", "now"),
        )
        // Subject averages: 15.0 (coef 4), 12.0 (coef 2)
        // GPA = (15×4 + 12×2) / (4+2) = (60+24)/6 = 14.0
        assertEquals(14.0, computeOverallGpa(assessments)!!, 0.001)
    }

    @Test fun `overall GPA skips null subject averages`() {
        val assessments = listOf(
            Assessment("a1", "t", "s1", "sub1", "c1", "T1", "2026", 14.0, 10.0, 18.0, null, 4, "t", "now"),
            Assessment("a2", "t", "s1", "sub2", "c1", "T1", "2026", null, null, null, null, 2, "t", "now"),
        )
        // Only sub1 has a valid average (15.0, coef 4)
        assertEquals(15.0, computeOverallGpa(assessments)!!, 0.001)
    }

    @Test fun `overall GPA returns null when no valid assessments`() {
        val assessments = listOf(
            Assessment("a1", "t", "s1", "sub1", "c1", "T1", "2026", null, null, null, null, 4, "t", "now"),
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
