package com.example.ui.designsystem.components.button

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElColors
import com.example.ui.designsystem.theme.ElTheme

/**
 * Resolved visual style for a button — background brush, content color, border.
 *
 * Single source of truth for variant → appearance mapping. The button
 * composable stays a thin shell; this is where the "look" lives.
 */
internal data class ButtonStyle(
    val background: Brush,
    val contentColor: Color,
    val border: BorderStroke?,
)

/**
 * Maps an [ElButtonVariant] to its [ButtonStyle] using the active [ElColors].
 *
 * Composable so it can read the border-width token from the theme.
 */
@Composable
internal fun resolveButtonStyle(
    colors: ElColors = ElTheme.colors,
    variant: ElButtonVariant,
): ButtonStyle {
    val borders = ElTheme.borders
    return when (variant) {
        ElButtonVariant.PRIMARY -> ButtonStyle(
            background = colors.primaryBrush,
            contentColor = colors.textOnColor,
            border = null,
        )
        ElButtonVariant.SECONDARY -> ButtonStyle(
            background = SolidColor(colors.primaryAccent),
            contentColor = colors.onPrimaryAccent,
            border = null,
        )
        ElButtonVariant.TONAL -> ButtonStyle(
            background = SolidColor(colors.primaryContainer),
            contentColor = colors.onPrimaryContainer,
            border = null,
        )
        ElButtonVariant.OUTLINED -> ButtonStyle(
            background = SolidColor(Color.Transparent),
            contentColor = colors.primary,
            border = BorderStroke(borders.thick, colors.primary),
        )
        ElButtonVariant.GHOST -> ButtonStyle(
            background = SolidColor(Color.Transparent),
            contentColor = colors.primary,
            border = null,
        )
        ElButtonVariant.DANGER -> ButtonStyle(
            background = colors.dangerBrush,
            contentColor = colors.onDanger,
            border = null,
        )
    }
}
