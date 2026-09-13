package com.example.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T-348 (MATIERE-500 / ADR-018) — the Kotlin mirror of the canonical
 * subject-configuration module. The SAME vectors as the desktop suite
 * (subject-config.test.ts), the website port (subject-config.test.ts) and
 * the corpus subject_configuration category.
 */
class SubjectConfigTest {

    private val legacy = LegacySubjectLayer(
        id = "subj-ar", code = "AR", coefficient = 3.0,
        passingGrade = 10.0, isExtracurricular = false,
    )

    private fun config(
        coefficient: Double = 4.0,
        levelId: String = "al-4am",
        direction: String = "general",
        subjectCode: String? = null,
        recipe: GradingRecipe? = null,
        isActive: Boolean = true,
    ) = SubjectConfiguration(
        subjectId = "subj-ar", academicYearId = "ay-1", academicLevelId = levelId,
        direction = direction, coefficient = coefficient, subjectCode = subjectCode,
        gradingRecipe = recipe, isActive = isActive,
    )

    // ─── resolveSubjectConfiguration (the ONE rule) ────────────────────────

    @Test
    fun `the configuration row wins when the context matches`() {
        val r = resolveSubjectConfiguration(legacy, listOf(config()), "al-4am", "ay-1")
        assertEquals(4.0, r.coefficient)
        assertEquals("configuration", r.source)
    }

    @Test
    fun `falls through to the legacy directory when no context row matches`() {
        val r = resolveSubjectConfiguration(legacy, listOf(config()), "al-1ap", "ay-1")
        assertEquals(3.0, r.coefficient)
        assertEquals("legacy-subject", r.source)
    }

    @Test
    fun `exact direction beats the general row`() {
        val configs = listOf(
            config(direction = "sciences", coefficient = 5.0),
            config(direction = "general", coefficient = 4.0),
        )
        val r = resolveSubjectConfiguration(legacy, configs, "al-4am", "ay-1", direction = "sciences")
        assertEquals(5.0, r.coefficient)
    }

    @Test
    fun `the snapshot wins over the live configuration (non-retroactive)`() {
        val r = resolveSubjectConfiguration(
            legacy, listOf(config(coefficient = 4.0)), "al-4am", "ay-1",
            snapshot = SubjectContextSnapshot(
                coefficient = 3.0, coefficientDevoir1 = 1.0,
                coefficientDevoir2 = 1.0, coefficientExamen = 2.0, coefficientCc = 0.0,
            ),
        )
        assertEquals(3.0, r.coefficient)
    }

    @Test
    fun `inactive rows are ignored`() {
        val r = resolveSubjectConfiguration(
            legacy, listOf(config(coefficient = 9.0, isActive = false)), "al-4am", "ay-1",
        )
        assertEquals(3.0, r.coefficient)
        assertEquals("legacy-subject", r.source)
    }

    // ─── computeSubjectAverageFromRecipe (the canonical engine) ────────────

    @Test
    fun `the DEFAULT recipe is bit-identical to the historical engine`() {
        val vectors = listOf(
            Triple(14.0, 16.0, 18.0),
            Triple(11.0, 11.0, 11.0),
            Triple(7.0, 7.0, 20.0),
            Triple(12.5, 13.25, 14.125),
            Triple(0.0, 20.0, 10.0),
        )
        for ((d1, d2, ex) in vectors) {
            assertEquals(
                computeSubjectAverage(d1, d2, ex),
                computeSubjectAverageFromRecipe(d1, d2, ex, null, DEFAULT_GRADING_RECIPE),
            )
        }
    }

    @Test
    fun `the migration-0094 probe values`() {
        // C2: the legacy-compat value 14.50.
        assertEquals(14.5, computeSubjectAverageFromRecipe(12.0, 14.0, 16.0, null, DEFAULT_GRADING_RECIPE))
        // C4: the cc recipe (15×1 + 12×2)/3 = 13.00.
        assertEquals(
            13.0,
            computeSubjectAverageFromRecipe(null, null, 12.0, 15.0, GradingRecipe(0.0, 0.0, 2.0, 1.0)),
        )
        // C5: cc weight 0 ignored — 14.50.
        assertEquals(14.5, computeSubjectAverageFromRecipe(12.0, 14.0, 16.0, 18.0, DEFAULT_GRADING_RECIPE))
        // D1: the four-component recipe (10+10+20+12)/5 = 10.4.
        assertEquals(10.4, computeSubjectAverageFromRecipe(10.0, 10.0, 10.0, 12.0, GradingRecipe(1.0, 1.0, 2.0, 1.0)))
    }

    @Test
    fun `a positive-weight component is required`() {
        assertNull(computeSubjectAverageFromRecipe(12.0, null, 16.0, null, DEFAULT_GRADING_RECIPE))
        assertNull(computeSubjectAverageFromRecipe(12.0, 14.0, 16.0, null, GradingRecipe(1.0, 1.0, 2.0, 1.0)))
        assertNull(computeSubjectAverageFromRecipe(null, null, null, null))
    }

    @Test
    fun `zero-sum recipe is not computable`() {
        assertNull(computeSubjectAverageFromRecipe(10.0, 10.0, 10.0, 10.0, GradingRecipe(0.0, 0.0, 0.0, 0.0)))
    }
}
