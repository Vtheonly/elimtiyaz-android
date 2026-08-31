package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * T-063 (ATT-103) — absence-alert threshold = the desktop rule.
 *
 * The Android `alertAbsences` used to alert for EVERY student in the input
 * (effective threshold 1). The desktop rule (canonical,
 * `SupabaseAttendanceRepository.alertAbsences`) is: ≥3 absences
 * (absent_unexcused + absent_excused, LATE excluded) within the CURRENT
 * TERM. These tests pin the ported term window (core/Terms.kt — verbatim
 * mirror of the desktop's terms.ts) and the threshold decision
 * (`absenceAlertThreshold`), which `alertAbsences` now consumes.
 */
class TermsT063Test {

    // ── currentTermWindow: mirror of the desktop's terms.ts ──────────────

    @Test
    fun `mid-September is T1 (Sep 1 - Dec 15)`() {
        val w = currentTermWindow(LocalDate.of(2026, 9, 15))
        assertEquals("T1", w.term)
        assertEquals(LocalDate.of(2026, 9, 1), w.start)
        assertEquals(LocalDate.of(2026, 12, 15), w.end)
        assertEquals("T1 2026-2027", w.label)
    }

    @Test
    fun `December 20 is T2 (Dec 16 - Mar 15, spanning the year boundary)`() {
        val w = currentTermWindow(LocalDate.of(2026, 12, 20))
        assertEquals("T2", w.term)
        assertEquals(LocalDate.of(2026, 12, 16), w.start)
        assertEquals(LocalDate.of(2027, 3, 15), w.end)
        assertEquals("T2 2026-2027", w.label)
    }

    @Test
    fun `February maps to the previous school year's T3 tail (desktop month-below-9 branch)`() {
        // Verbatim desktop behavior: months Jan-Aug return the T3 tail of the
        // school year that just ended (Dec 16 - Jun 30), NOT the running T2.
        val w = currentTermWindow(LocalDate.of(2027, 2, 1))
        assertEquals("T3", w.term)
        assertEquals(LocalDate.of(2026, 12, 16), w.start)
        assertEquals(LocalDate.of(2027, 6, 30), w.end)
        assertEquals("T3 2026-2027", w.label)
    }

    @Test
    fun `May also maps to the previous school year's T3 tail`() {
        val w = currentTermWindow(LocalDate.of(2027, 5, 10))
        assertEquals("T3", w.term)
        assertEquals(LocalDate.of(2026, 12, 16), w.start)
        assertEquals(LocalDate.of(2027, 6, 30), w.end)
    }

    @Test
    fun `July falls back to the PREVIOUS year's T3 tail`() {
        val jul = currentTermWindow(LocalDate.of(2026, 7, 20))
        assertEquals("T3", jul.term)
        assertEquals(LocalDate.of(2025, 12, 16), jul.start)
        assertEquals(LocalDate.of(2026, 6, 30), jul.end)
        assertEquals("T3 2025-2026", jul.label)
    }

    // ── absenceAlertThreshold: ≥3 in-term absences only ─────────────────

    private val t1 = currentTermWindow(LocalDate.of(2026, 10, 1))

    @Test
    fun `three in-term absences flag the student`() {
        val records = listOf("s1" to "absent_unexcused", "s1" to "absent_excused", "s1" to "absent_unexcused")
        val dates = listOf("2026-09-10", "2026-10-01", "2026-11-20")
        val flagged = absenceAlertThreshold(records, dates, t1)
        assertEquals(listOf(AbsenceCount("s1", 3)), flagged)
    }

    @Test
    fun `two absences do NOT flag (threshold is 3, not 1)`() {
        val records = listOf("s1" to "absent_unexcused", "s1" to "absent_unexcused")
        val dates = listOf("2026-09-10", "2026-10-01")
        assertTrue(absenceAlertThreshold(records, dates, t1).isEmpty())
    }

    @Test
    fun `LATE and PRESENT never count`() {
        val records = listOf(
            "s1" to "absent_unexcused", "s1" to "late", "s1" to "present",
            "s1" to "absent_excused", "s1" to "late",
        )
        val dates = listOf("2026-09-10", "2026-09-11", "2026-09-12", "2026-09-13", "2026-09-14")
        // Only the 2 real absences count → below the threshold → no flag.
        assertTrue(absenceAlertThreshold(records, dates, t1).isEmpty())
    }

    @Test
    fun `out-of-term absences do not count toward the threshold`() {
        val records = listOf(
            "s1" to "absent_unexcused", // T1 in-term
            "s1" to "absent_unexcused", // BEFORE the window (last year)
            "s1" to "absent_unexcused", // AFTER the window (next T2)
        )
        val dates = listOf("2026-10-01", "2026-01-15", "2027-01-10")
        assertTrue(absenceAlertThreshold(records, dates, t1).isEmpty())
    }

    @Test
    fun `each student is evaluated independently`() {
        val records = listOf(
            "s1" to "absent_unexcused", "s1" to "absent_unexcused", "s1" to "absent_unexcused",
            "s2" to "absent_unexcused", "s2" to "absent_excused",
        )
        val dates = listOf("2026-09-10", "2026-09-11", "2026-09-12", "2026-09-13", "2026-09-14")
        val flagged = absenceAlertThreshold(records, dates, t1)
        assertEquals(1, flagged.size)
        assertEquals("s1", flagged[0].studentId)
        assertEquals(3, flagged[0].count)
    }
}
