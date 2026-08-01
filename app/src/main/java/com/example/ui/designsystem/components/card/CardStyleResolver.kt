package com.example.ui.designsystem.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElColors
import com.example.ui.designsystem.theme.ElTheme

/**
 * Resolved visual style for a card — background brush, optional border.
 *
 * Single source of truth for variant → appearance mapping. The card
 * composable stays a thin shell; this is where the "look" lives.
 */
internal data class CardStyle(
    val background: Brush,
    val border: BorderStroke?,
)

/**
 * Maps an [ElCardVariant] to its [CardStyle] using the active [ElColors].
 * Honors caller overrides for [backgroundOverride] and [gradientOverride].
 *
 * Composable so it can read the border-width token from the theme.
 */
@Composable
internal fun resolveCardStyle(
    variant: ElCardVariant,
    backgroundOverride: Color? = null,
    gradientOverride: Brush? = null,
    colors: ElColors = ElTheme.colors,
): CardStyle {
    val bg: Brush = when {
        gradientOverride != null -> gradientOverride
        backgroundOverride != null -> SolidColor(backgroundOverride)
        variant == ElCardVariant.GRADIENT -> colors.primaryBrush
        variant == ElCardVariant.GLASS -> SolidColor(colors.glassTint)
        variant == ElCardVariant.FILLED -> SolidColor(colors.surfaceVariant)
        else -> SolidColor(colors.surface)
    }
    val border: BorderStroke? = when (variant) {
        ElCardVariant.OUTLINED -> BorderStroke(ElTheme.borders.thin, colors.outline)
        ElCardVariant.GLASS -> BorderStroke(1.dp, colors.glassBorder)
        else -> null
    }
    return CardStyle(background = bg, border = border)
}
