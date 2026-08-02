package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Material 3 color schemes derived from the brand palette in [Color.kt].
 *
 * Used by [ElImtiyazTheme] to configure the M3 [MaterialTheme] so that all
 * stock M3 components (TopAppBar, Card, Button, etc.) inherit the El-Imtiyaz
 * brand identity without needing a custom design-system wrapper.
 */

val DarkColorScheme = darkColorScheme(
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

val LightColorScheme = lightColorScheme(
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
