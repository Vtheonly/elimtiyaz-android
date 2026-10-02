package com.example.ui.designsystem.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.R

/**
 * ElInter — the app typeface (T-462, UI-327: the owner's "clean, modern
 * sans-serif (like Inter)" mandate; the owner's own commit 2bb4a9b subject:
 * "fix the font it looks wierd").
 *
 * Inter (v4.1, SIL OFL 1.1 — https://rsms.me/inter/) bundled at the six
 * weights the design system's typography scale actually uses: Regular for
 * body, Medium for captions, SemiBold for labels/titles, Bold/ExtraBold for
 * headlines and KPI figures, Black for the display tier. Any weight outside
 * the table (e.g. Thin) resolves to the nearest bundled cut via Compose's
 * font-resolution, never a crash.
 *
 * The family is LATIN-ONLY by design: Arabic/CJK glyphs and any code point
 * Inter misses fall through Compose's per-glyph fallback to the system
 * fonts (the previous FontFamily.Default behavior for those scripts is
 * unchanged — this file replaces the LATIN backbone, not the fallback
 * chain).
 *
 * Consumed by [ElTypography] and [ElTextStyles] — feature code never
 * references it directly (the typography styles are the single source).
 */
val ElInter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    Font(R.font.inter_black, FontWeight.Black),
)
