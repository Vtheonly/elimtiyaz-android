package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = DeepBlue,
    onPrimaryContainer = Color(0xFFD1ECF8),
    secondary = WarmGold,
    onSecondary = Color(0xFF1A1B1C),
    secondaryContainer = Color(0xFF4A3D2A),
    onSecondaryContainer = Color(0xFFF5E6D3),
    tertiary = LightBlue,
    onTertiary = Color(0xFF1A1B1C),
    tertiaryContainer = Color(0xFF1E4863),
    onTertiaryContainer = Color(0xFFD1ECF8),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = PrimaryBlue,
    inverseSurface = Color(0xFFE6E6E6),
    inverseOnSurface = Color(0xFF313234),
    error = DangerRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF8C3A38),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = DarkOutline,
    outlineVariant = Color(0xFF3A3F4B),
    scrim = Color(0xFF000000),
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1ECF8),
    onPrimaryContainer = Color(0xFF0A3A52),
    secondary = WarmGold,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5E6D3),
    onSecondaryContainer = Color(0xFF3A2D1A),
    tertiary = LightBlue,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1ECF8),
    onTertiaryContainer = Color(0xFF1E4863),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = PrimaryBlue,
    inverseSurface = Color(0xFF313234),
    inverseOnSurface = Color(0xFFF5F6F7),
    error = DangerRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF5C1A1A),
    outline = LightOutline,
    outlineVariant = Color(0xFFC7CACE),
    scrim = Color(0xFF000000),
)

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

data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val info: Color,
    val onInfo: Color,
)

val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(
        success = SuccessGreen, onSuccess = Color.White,
        warning = WarningOrange, onWarning = Color.White,
        info = InfoBlue, onInfo = Color.White,
    )
}

@Composable
fun ElImtiyazTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val designTokens = if (darkTheme) {
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
    } else {
        ElDesignTokens(
            isDark = false,
            primaryGradient = PrimaryGradient,
            primaryGradientDiagonal = PrimaryGradientDiagonal,
            goldGradient = GoldGradient,
            successGradient = SuccessGradient,
            dangerGradient = DangerGradient,
            surfaceGradient = LightSurfaceGradient,
            heroGradient = LightHeroGradient,
            textMuted = LightTextMuted,
            cardBorder = LightOutline,
            shimmerBase = LightSurfaceVariant,
            shimmerHighlight = LightElevatedSurface,
            glassTint = LightGlassTint,
            glassBorder = LightGlassBorder,
            shadowColor = LightShadowColor,
            dividerColor = LightOutline.copy(alpha = 0.5f),
        )
    }

    CompositionLocalProvider(
        LocalElDesignTokens provides designTokens,
        LocalSemanticColors provides SemanticColors(
            success = SuccessGreen, onSuccess = Color.White,
            warning = WarningOrange, onWarning = Color.White,
            info = InfoBlue, onInfo = Color.White,
        ),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ElImtiyazTypography,
            shapes = ElShapes,
            content = content,
        )
    }
}

// ── Convenience accessors ──────────────────────────────────────────────────

@Composable
fun elDesignTokens(): ElDesignTokens = LocalElDesignTokens.current