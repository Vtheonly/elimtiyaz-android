package com.example.core

import java.time.LocalDate

/**
 * Current-term window (T-063 / ATT-103) — Kotlin mirror of the desktop's
 * `src/domain/calc/academics/terms.ts`, ported verbatim per the
 * cross-platform rule (AGENTS.md §10; ADR-002):
 *
 *   T1: Sep 1  – Dec 15   (label "T1 <year>-<year+1>")
 *   T2: Dec 16 – Mar 15   (spans the year boundary)
 *   T3: Mar 16 – Jun 30   (spans the year boundary)
 *   January–August → the tail of the PREVIOUS school year's T3
 *     (Dec 16 of year-1 – Jun 30 of year; label "T3 <year-1>-<year>").
 *
 * The label matches the desktop format exactly so notification text is
 * byte-identical across platforms for the same date.
 */
data class TermWindow(
    val term: String,
    val start: LocalDate,
    val end: LocalDate,
    val label: String,
)

fun currentTermWindow(now: LocalDate = LocalDate.now()): TermWindow {
    val year = now.year

    // Jan–Aug (and before Sep 1) → tail of the previous school year's T3.
    if (now.monthValue < 9) {
        return TermWindow(
            term = "T3",
            start = LocalDate.of(year - 1, 12, 16),
            end = LocalDate.of(year, 6, 30),
            label = "T3 ${year - 1}-$year",
        )
    }

    return when {
        now <= LocalDate.of(year, 12, 15) -> TermWindow(
            "T1",
            LocalDate.of(year, 9, 1),
            LocalDate.of(year, 12, 15),
            "T1 $year-${year + 1}",
        )
        now <= LocalDate.of(year + 1, 3, 15) -> TermWindow(
            "T2",
            LocalDate.of(year, 12, 16),
            LocalDate.of(year + 1, 3, 15),
            "T2 $year-${year + 1}",
        )
        else -> TermWindow(
            "T3",
            LocalDate.of(year + 1, 3, 16),
            LocalDate.of(year + 1, 6, 30),
            "T3 $year-${year + 1}",
        )
    }
}

/**
 * Absence-alert threshold decision (T-063 / ATT-103) — mirrors the desktop's
 * `SupabaseAttendanceRepository.alertAbsences` (THRESHOLD = 3, current term,
 * LATE excluded): returns the students whose current-term absence count
 * reached the threshold, in input order.
 */
data class AbsenceCount(
    val studentId: String,
    val count: Int,
)

const val ABSENCE_ALERT_THRESHOLD: Int = 3

fun absenceAlertThreshold(
    records: List<Pair<String, String>>, // (studentId, status)
    dates: List<String>,                  // parallel YYYY-MM-DD record dates
    window: TermWindow,
): List<AbsenceCount> {
    val counts = linkedMapOf<String, Int>()
    records.forEachIndexed { i, (studentId, status) ->
        if (status != "absent_unexcused" && status != "absent_excused") return@forEachIndexed
        val date = dates.getOrNull(i)?.takeIf { it.length >= 10 } ?: return@forEachIndexed
        val local = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return@forEachIndexed
        if (local < window.start || local > window.end) return@forEachIndexed
        counts[studentId] = (counts[studentId] ?: 0) + 1
    }
    return counts.entries
        .filter { it.value >= ABSENCE_ALERT_THRESHOLD }
        .map { AbsenceCount(it.key, it.value) }
}
