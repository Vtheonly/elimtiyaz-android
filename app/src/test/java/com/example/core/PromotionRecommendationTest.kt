package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vault §06.04 — One-Click Batch Promotion Engine, Step 2 (auto-flag)
 * regression tests for [derivePromotionRecommendation].
 *
 * Verifies the canonical auto-flag rules:
 *   - GPA >= 10.00/20.00 → APPROVED_FOR_PROMOTION
 *   - GPA >= 10.00 + final year (3eme_annee) → GRADUATED (no "Year 4")
 *   - GPA < 10.00 → RETAINED_SAME_YEAR
 *   - No grades (null GPA) → the engine never guesses promotion; the queue
 *     forces manual review (derivation returns RETAINED as the safe default).
 *   - Configurable passing threshold is honored.
 */
class PromotionRecommendationTest {

    // ── Passing GPAs ─────────────────────────────────────────────────────

    @Test
    fun `GPA at exactly the threshold is promoted`() {
        assertEquals(
            PromotionDecisions.PROMOTED,
            derivePromotionRecommendation(gpa = 10.00, isFinalYear = false),
        )
    }

    @Test
    fun `GPA above the threshold is promoted`() {
        assertEquals(
            PromotionDecisions.PROMOTED,
            derivePromotionRecommendation(gpa = 15.13, isFinalYear = false),
        )
    }

    @Test
    fun `perfect GPA is promoted`() {
        assertEquals(
            PromotionDecisions.PROMOTED,
            derivePromotionRecommendation(gpa = 20.00, isFinalYear = false),
        )
    }

    @Test
    fun `passing GPA with trailing precision stays promoted`() {
        // 9.999 must NOT be promoted (below), 10.001 must be (above).
        assertEquals(
            PromotionDecisions.REPEATED,
            derivePromotionRecommendation(gpa = 9.999, isFinalYear = false),
        )
        assertEquals(
            PromotionDecisions.PROMOTED,
            derivePromotionRecommendation(gpa = 10.001, isFinalYear = false),
        )
    }

    // ── Failing GPAs ─────────────────────────────────────────────────────

    @Test
    fun `GPA just below the threshold is retained`() {
        assertEquals(
            PromotionDecisions.REPEATED,
            derivePromotionRecommendation(gpa = 9.99, isFinalYear = false),
        )
    }

    @Test
    fun `zero GPA is retained`() {
        assertEquals(
            PromotionDecisions.REPEATED,
            derivePromotionRecommendation(gpa = 0.0, isFinalYear = false),
        )
    }

    @Test
    fun `null GPA (no grades) never auto-promotes`() {
        assertEquals(
            PromotionDecisions.REPEATED,
            derivePromotionRecommendation(gpa = null, isFinalYear = false),
        )
    }

    // ── Final year (Lycee Year 3) — graduation, never "Year 4" ───────────

    @Test
    fun `passing final-year student graduates instead of promoting`() {
        assertEquals(
            PromotionDecisions.GRADUATED,
            derivePromotionRecommendation(gpa = 12.5, isFinalYear = true),
        )
    }

    @Test
    fun `failing final-year student is retained (not graduated)`() {
        assertEquals(
            PromotionDecisions.REPEATED,
            derivePromotionRecommendation(gpa = 8.0, isFinalYear = true),
        )
    }

    @Test
    fun `null GPA final-year student is retained pending review`() {
        assertEquals(
            PromotionDecisions.REPEATED,
            derivePromotionRecommendation(gpa = null, isFinalYear = true),
        )
    }

    // ── Configurable threshold ───────────────────────────────────────────

    @Test
    fun `custom passing grade is honored`() {
        // Institution configures 12.00/20.00 as the minimum.
        assertEquals(
            PromotionDecisions.REPEATED,
            derivePromotionRecommendation(gpa = 11.5, isFinalYear = false, passingGrade = 12.0),
        )
        assertEquals(
            PromotionDecisions.PROMOTED,
            derivePromotionRecommendation(gpa = 12.0, isFinalYear = false, passingGrade = 12.0),
        )
    }

    // ── Canonical ladder sanity (execution side, unchanged business logic) ─

    @Test
    fun `ladder maps final year to graduation`() {
        val progression = getNextGradeProgression("3eme_annee")
        assert(progression.isGraduation)
        assertEquals(null, progression.nextGradeCode)
    }

    @Test
    fun `ladder advances primary grade 5 to cem year 1`() {
        val progression = getNextGradeProgression("5ap")
        assertEquals("1am", progression.nextGradeCode)
        assertEquals("cem", progression.nextLevel)
        assert(!progression.isGraduation)
    }

    @Test
    fun `ladder advances cem year 4 to lycee year 1`() {
        val progression = getNextGradeProgression("4am")
        assertEquals("1ere_annee", progression.nextGradeCode)
        assertEquals("lycee", progression.nextLevel)
    }

    @Test
    fun `unknown grade code keeps state (no crash, no move)`() {
        val progression = getNextGradeProgression("not_a_grade")
        assert(!progression.isGraduation)
        assertEquals(null, progression.nextGradeCode)
    }
}
