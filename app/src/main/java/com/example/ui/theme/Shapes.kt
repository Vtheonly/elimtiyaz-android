package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * El-Imtiyaz custom shape system (semantic shape constants).
 *
 * Uses a progressive corner-radius scale that gives the app a distinctive
 * rounded identity without being overly playful. The [ElCardShape] is the
 * signature shape — a 20dp rounded rectangle used for all primary surfaces.
 *
 * The Material3 `Shapes` scale (`ElShapes`) that used to live here was removed
 * with the legacy `ElImtiyazTheme` (DUP-004, T-044 pass 1, 2026-09-03): the
 * production theme is `com.example.ui.designsystem.theme.ElImtiyazTheme`,
 * which applies its OWN shape scale (`designsystem/theme/Shape.kt`). These
 * semantic constants stay because legacy `ui/components` + feature screens
 * import them directly.
 */

// ── Semantic shapes (used directly by custom components) ──────────────────

/** Primary card surface — the signature shape of the app. */
val ElCardShape = RoundedCornerShape(20.dp)

/** Compact card / list-item surface. */
val ElCardShapeSmall = RoundedCornerShape(14.dp)

/** Pill / chip / tag shape — fully rounded. */
val ElPillShape = RoundedCornerShape(50)

/** Button shape — slightly less rounded than cards for tactile feel. */
val ElButtonShape = RoundedCornerShape(14.dp)

/** Text field shape — matches [ElButtonShape] for visual consistency. */
val ElFieldShape = RoundedCornerShape(14.dp)

/** Bottom sheet / modal shape — top corners only. */
val ElSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Avatar shape — fully circular. */
val ElAvatarShape = RoundedCornerShape(50)

/** Squircle-ish shape for FABs and prominent buttons — large radius. */
val ElFabShape = RoundedCornerShape(20.dp)

/** Modal / dialog shape — extra large radius. */
val ElDialogShape = RoundedCornerShape(24.dp)

/** Notification / toast shape — medium radius. */
val ElNotificationShape = RoundedCornerShape(16.dp)

/** Top-only rounded shape for bottom sheets with a flatter look. */
val ElSheetShapeFlat = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
