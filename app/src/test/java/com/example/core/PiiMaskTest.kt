package com.example.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Unit tests for PII masking — mirrors the desktop
 * `src/test/unit/pii-mask.test.ts`.
 *
 * Verifies that:
 *   - Each of the 6 patterns (phone, email, IBAN, NN, parent, student) is masked
 *   - Same value reuses the same placeholder (deduplication)
 *   - Different values get different placeholders
 *   - The mask is reversible (unmaskPII restores original values)
 *   - Masking order matters (IBAN first so NN doesn't grab its digits)
 */
class PiiMaskTest {

    // ── Phone (Algerian formats) ──────────────────────────────────────────

    @Test fun `masks Algerian phone with country code`() {
        val text = "Contact: +213 555 123 456"
        val result = PiiMask.maskPII(text)
        assertTrue(result.masked.contains("[PHONE_1]"))
        assertFalse(result.masked.contains("+213 555 123 456"))
        assertEquals("+213 555 123 456", result.replacements["[PHONE_1]"])
    }

    @Test fun `masks Algerian phone without country code`() {
        val text = "Call 0555 123 456 now"
        val result = PiiMask.maskPII(text)
        assertTrue(result.masked.contains("[PHONE_1]"))
    }

    @Test fun `masks phone with dashes`() {
        val text = "Phone: 213-555-123-456"
        val result = PiiMask.maskPII(text)
        assertTrue(result.masked.contains("[PHONE_1]"))
    }

    @Test fun `masks phone without separators`() {
        val text = "Phone: 0555123456"
        val result = PiiMask.maskPII(text)
        assertTrue(result.masked.contains("[PHONE_1]"))
    }

    // ── Email ─────────────────────────────────────────────────────────────

    @Test fun `masks email address`() {
        val text = "Email: parent@example.com for details"
        val result = PiiMask.maskPII(text)
        assertTrue(result.masked.contains("[EMAIL_1]"))
        assertEquals("parent@example.com", result.replacements["[EMAIL_1]"])
    }

    // ── IBAN (Algerian) ───────────────────────────────────────────────────

    @Test fun `masks Algerian IBAN`() {
        val text = "IBAN: DZ35 0000 1111 2222 3333 4444"
        val result = PiiMask.maskPII(text)
        assertTrue(result.masked.contains("[IBAN_1]"))
        assertFalse(result.masked.contains("DZ35"))
    }

    @Test fun `masks Algerian IBAN without spaces`() {
        val text = "IBAN: DZ3500001111222233334444"
        val result = PiiMask.maskPII(text)
        assertTrue(result.masked.contains("[IBAN_1]"))
    }

    // ── National ID (NN) ──────────────────────────────────────────────────

    @Test fun `masks 10-digit national ID`() {
        val text = "NN: 1234567890"
        val result = PiiMask.maskPII(text)
        assertTrue(result.masked.contains("[NN_1]"))
        assertEquals("1234567890", result.replacements["[NN_1]"])
    }

    @Test fun `does NOT mask 9-digit or 11-digit numbers as NN`() {
        val text9 = "Code: 123456789"   // 9 digits — too short
        val text11 = "Code: 12345678901"  // 11 digits — too long
        val r9 = PiiMask.maskPII(text9)
        val r11 = PiiMask.maskPII(text11)
        assertFalse(r9.masked.contains("[NN_"))
        assertFalse(r11.masked.contains("[NN_"))
    }

    // ── Masking order: IBAN before NN ─────────────────────────────────────

    @Test fun `IBAN is masked before NN so NN regex doesn't grab its digits`() {
        // An IBAN without spaces has 24 characters of digits after "DZ".
        // If NN ran first, it would grab the first 10 digits as an NN.
        val text = "IBAN: DZ35 0000 1111 2222 3333 4444 and NN: 9988776655"
        val result = PiiMask.maskPII(text)
        // IBAN should be [IBAN_1]
        assertTrue(result.masked.contains("[IBAN_1]"))
        // NN should be [NN_1] (the actual NN, not a slice of the IBAN)
        assertTrue(result.masked.contains("[NN_1]"))
        assertEquals("9988776655", result.replacements["[NN_1]"])
    }

    // ── Parent and student names ──────────────────────────────────────────

    @Test fun `masks parent names`() {
        val text = "Parent BENALI Kamel called about payment."
        val result = PiiMask.maskPII(text, options = PiiMask.Options(
            parentNames = listOf("BENALI Kamel"),
        ))
        assertTrue(result.masked.contains("[PARENT_1]"))
        assertEquals("BENALI Kamel", result.replacements["[PARENT_1]"])
    }

    @Test fun `masks student names`() {
        val text = "Student Yacine BENALI has 3 absences."
        val result = PiiMask.maskPII(text, options = PiiMask.Options(
            studentNames = listOf("Yacine BENALI"),
        ))
        assertTrue(result.masked.contains("[STUDENT_1]"))
        assertEquals("Yacine BENALI", result.replacements["[STUDENT_1]"])
    }

    @Test fun `masks multiple parent names with different placeholders`() {
        val text = "BENALI Kamel and CHERIF Omar both owe money."
        val result = PiiMask.maskPII(text, options = PiiMask.Options(
            parentNames = listOf("BENALI Kamel", "CHERIF Omar"),
        ))
        assertTrue(result.masked.contains("[PARENT_1]"))
        assertTrue(result.masked.contains("[PARENT_2]"))
        assertEquals("BENALI Kamel", result.replacements["[PARENT_1]"])
        assertEquals("CHERIF Omar", result.replacements["[PARENT_2]"])
    }

    // ── Deduplication ─────────────────────────────────────────────────────

    @Test fun `same phone number appearing twice reuses same placeholder`() {
        val text = "Call +213 555 123 456 or text +213 555 123 456"
        val result = PiiMask.maskPII(text)
        // Both occurrences should be [PHONE_1]
        assertTrue(result.masked.contains("[PHONE_1]"))
        assertFalse(result.masked.contains("+213 555 123 456"))
        // No [PHONE_2] should exist
        assertFalse(result.masked.contains("[PHONE_2]"))
    }

    @Test fun `two different phone numbers get different placeholders`() {
        val text = "Home: +213 555 123 456, Work: +213 666 987 654"
        val result = PiiMask.maskPII(text)
        assertTrue(result.masked.contains("[PHONE_1]"))
        assertTrue(result.masked.contains("[PHONE_2]"))
    }

    // ── Reversibility (unmaskPII) ─────────────────────────────────────────

    @Test fun `unmaskPII restores original values`() {
        val original = "Email: parent@example.com, Phone: +213 555 123 456"
        val masked = PiiMask.maskPII(original)
        val unmasked = PiiMask.unmaskPII(masked.masked, masked.replacements)
        assertEquals(original, unmasked)
    }

    @Test fun `unmaskPII handles LLM response with placeholders`() {
        val llmResponse = "Bonjour [PARENT_1], votre enfant [STUDENT_1] a 3 absences."
        val replacements = mapOf(
            "[PARENT_1]" to "BENALI Kamel",
            "[STUDENT_1]" to "Yacine BENALI",
        )
        val unmasked = PiiMask.unmaskPII(llmResponse, replacements)
        assertEquals("Bonjour BENALI Kamel, votre enfant Yacine BENALI a 3 absences.", unmasked)
    }

    // ── Combined ──────────────────────────────────────────────────────────

    @Test fun `masks all 6 patterns in a single text`() {
        val text = """
            Parent: BENALI Kamel
            Student: Yacine BENALI
            Email: parent@example.com
            Phone: +213 555 123 456
            IBAN: DZ35 0000 1111 2222 3333 4444
            NN: 1234567890
        """.trimIndent()
        val result = PiiMask.maskPII(text, options = PiiMask.Options(
            parentNames = listOf("BENALI Kamel"),
            studentNames = listOf("Yacine BENALI"),
        ))
        val masked = result.masked
        assertTrue(masked.contains("[PARENT_1]"))
        assertTrue(masked.contains("[STUDENT_1]"))
        assertTrue(masked.contains("[EMAIL_1]"))
        assertTrue(masked.contains("[PHONE_1]"))
        assertTrue(masked.contains("[IBAN_1]"))
        assertTrue(masked.contains("[NN_1]"))
        // No original values leak through
        assertFalse(masked.contains("BENALI Kamel"))
        assertFalse(masked.contains("Yacine BENALI"))
        assertFalse(masked.contains("parent@example.com"))
        assertFalse(masked.contains("+213 555 123 456"))
        assertFalse(masked.contains("DZ35"))
        assertFalse(masked.contains("1234567890"))

        // Reversible
        val unmasked = PiiMask.unmaskPII(masked, result.replacements)
        assertEquals(text, unmasked)
    }

    @Test fun `text with no PII passes through unchanged`() {
        val text = "The attendance rate today is 96%."
        val result = PiiMask.maskPII(text)
        assertEquals(text, result.masked)
        assertTrue(result.replacements.isEmpty())
    }
}
