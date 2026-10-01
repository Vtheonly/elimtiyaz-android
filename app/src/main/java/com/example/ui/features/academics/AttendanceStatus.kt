package com.example.ui.features.academics

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.ui.designsystem.theme.ElTheme

// ── 1. Roll Call ──────────────────────────────────────────────────────────

/**
 * Maps the desktop's 4 attendance statuses (per plan §09.02 — no 5th
 * "CUSTOM" status allowed) to the mobile UI labels.
 *
 * T-460 pass D: the legacy Color constants (SuccessGreen/DangerRed/WarmGold/
 * LightBlue) are replaced by the DS semantic palette, resolved at composition
 * time via [resolvedColor] so light/dark theming flows through the tokens.
 */
enum class AttendanceStatus(val label: String, val wireCode: String) {
    PRESENT("Présent", "present"),
    ABSENT("Absent", "absent_unexcused"),
    EXCUSED("Excusé", "absent_excused"),
    LATE("Retard", "late"),
}

/** Resolves the status color from the live DS palette (light/dark aware). */
@Composable
fun AttendanceStatus.resolvedColor(): Color = when (this) {
    AttendanceStatus.PRESENT -> ElTheme.colors.success
    AttendanceStatus.ABSENT -> ElTheme.colors.danger
    AttendanceStatus.EXCUSED -> ElTheme.colors.warning
    AttendanceStatus.LATE -> ElTheme.colors.info
}
