package com.example.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Extended design tokens beyond Material 3 — gradients, semantic colors,
 * and custom surface treatments that give the app its unique identity.
 */
data class ElDesignTokens(
    val isDark: Boolean,
    val primaryGradient: List<Color>,
    val primaryGradientDiagonal: List<Color>,
    val goldGradient: List<Color>,
    val successGradient: List<Color>,
    val dangerGradient: List<Color>,
    val surfaceGradient: List<Color>,
    val heroGradient: List<Color>,
    val textMuted: Color,
    val cardBorder: Color,
    val shimmerBase: Color,
    val shimmerHighlight: Color,
    val glassTint: Color,
    val glassBorder: Color,
    val shadowColor: Color,
    val dividerColor: Color,
) {
    val primaryBrush: Brush get() = Brush.horizontalGradient(primaryGradient)
    val primaryDiagonalBrush: Brush get() = Brush.linearGradient(primaryGradientDiagonal)
    val goldBrush: Brush get() = Brush.horizontalGradient(goldGradient)
    val successBrush: Brush get() = Brush.horizontalGradient(successGradient)
    val dangerBrush: Brush get() = Brush.horizontalGradient(dangerGradient)
    val surfaceBrush: Brush get() = Brush.verticalGradient(surfaceGradient)
    val heroBrush: Brush get() = Brush.verticalGradient(heroGradient)
}

val LocalElDesignTokens = staticCompositionLocalOf {
    ElDesignTokens(
        isDark = true,
        primaryGradient = PrimaryGradient,
        primaryGradientDiagonal = PrimaryGradientDiagonal,
        goldGradient = GoldGradient,
        successGradient = SuccessGradient,
        dangerGradient = DangerGradient,
        surfaceGradient = DarkSurfaceGradient,
        heroGradient = DarkHeroGradient,
        textMuted = DarkTextMuted,
        cardBorder = DarkOutline,
        shimmerBase = DarkSurfaceVariant,
        shimmerHighlight = DarkElevatedSurface,
        glassTint = DarkGlassTint,
        glassBorder = DarkGlassBorder,
        shadowColor = DarkShadowColor,
        dividerColor = DarkOutline.copy(alpha = 0.5f),
    )
}
