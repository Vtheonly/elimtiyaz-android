package com.example.ui.features.academics

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LightBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold
import dagger.hilt.android.lifecycle.HiltViewModel

// ── 1. Roll Call ──────────────────────────────────────────────────────────

/**
 * Maps the desktop's 4 attendance statuses (per plan §09.02 — no 5th
 * "CUSTOM" status allowed) to the mobile UI labels + colors.
 */
enum class AttendanceStatus(val label: String, val color: Color, val wireCode: String) {
    PRESENT("Présent", SuccessGreen, "present"),
    ABSENT("Absent", DangerRed, "absent_unexcused"),
    EXCUSED("Excusé", WarmGold, "absent_excused"),
    LATE("Retard", LightBlue, "late"),
}

@HiltViewModel
