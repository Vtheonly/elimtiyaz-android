package com.example.ui.designsystem.components.display

import androidx.compose.ui.graphics.Color
import com.example.ui.designsystem.theme.ElColors

/** Resolved palette for a badge — foreground, background, border. */
internal data class BadgePalette(
    val fg: Color,
    val bg: Color,
    val border: Color,
)

/** Maps an [ElBadgeTone] to its saturated palette using the active [ElColors]. */
internal fun ElColors.badgePalette(tone: ElBadgeTone): BadgePalette = when (tone) {
    ElBadgeTone.PRIMARY   -> BadgePalette(primary, primaryContainer, primary)
    ElBadgeTone.SECONDARY -> BadgePalette(primaryAccent, warningContainer, primaryAccent)
    ElBadgeTone.TERTIARY  -> BadgePalette(tertiary, tertiary, tertiary)
    ElBadgeTone.NEUTRAL   -> BadgePalette(textSecondary, surfaceVariant, outline)
    ElBadgeTone.SUCCESS   -> BadgePalette(success, successContainer, success)
    ElBadgeTone.WARNING   -> BadgePalette(warning, warningContainer, warning)
    ElBadgeTone.DANGER    -> BadgePalette(danger, dangerContainer, danger)
    ElBadgeTone.INFO      -> BadgePalette(info, infoContainer, info)
}
