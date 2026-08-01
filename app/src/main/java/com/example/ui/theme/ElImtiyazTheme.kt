package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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
