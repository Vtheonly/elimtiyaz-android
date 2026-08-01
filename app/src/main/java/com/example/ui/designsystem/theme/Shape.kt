package com.example.ui.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * El-Imtiyaz Design System — Shape Tokens
 *
 * Bold geometric language uses chunky, confident corner radii. The signature
 * card radius is 24dp — large enough to feel modern and tactile, small enough
 * to retain structural clarity for data-dense surfaces.
 *
 * Components MUST pull shapes from [ElTheme.shapes] (the [ElShapes] instance)
 * or from the named semantic shapes below. Do not hardcode RoundedCornerShape
 * in component code.
 */
val ElShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(24.dp),   // signature card radius
    extraLarge = RoundedCornerShape(32.dp),
)

// ── Semantic shapes ─────────────────────────────────────────────────────────

/** Signature card surface — used by all primary cards. */
val ElCardShape       = RoundedCornerShape(24.dp)

/** Compact card / list-item surface. */
val ElCardShapeSmall  = RoundedCornerShape(16.dp)

/** Pill / chip / tag — fully rounded. */
val ElPillShape: Shape = RoundedCornerShape(50)

/** Button — slightly tighter than cards for tactile feel. */
val ElButtonShape     = RoundedCornerShape(14.dp)

/** Text field — matches [ElButtonShape]. */
val ElFieldShape      = RoundedCornerShape(14.dp)

/** Bottom sheet — top corners only, extra-large radius. */
val ElSheetShape      = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

/** Sheet handle shape. */
val ElSheetHandleShape = RoundedCornerShape(50)

/** Avatar — fully circular. */
val ElAvatarShape: Shape = RoundedCornerShape(50)

/** FAB — chunky, slightly squircle. */
val ElFabShape        = RoundedCornerShape(20.dp)

/** Modal / dialog — extra-large radius. */
val ElDialogShape     = RoundedCornerShape(28.dp)

/** Notification / toast — pill-ish. */
val ElNotificationShape = RoundedCornerShape(16.dp)

/** Tooltip — small, sharp. */
val ElTooltipShape    = RoundedCornerShape(10.dp)

/** Context menu — medium. */
val ElContextMenuShape = RoundedCornerShape(16.dp)

/** Top-only rounded (flatter sheet variant). */
val ElSheetShapeFlat  = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/** Rectangle (for full-bleed images / dividers). */
val ElRectangleShape: Shape = RectangleShape
