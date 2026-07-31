package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = DeepBlue,
    onPrimaryContainer = Color(0xFFE1F0FA),
    secondary = WarmGold,
    onSecondary = Color(0xFF1A1B1C),
    secondaryContainer = Color(0xFF5D4E3A),
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
    outlineVariant = Color(0xFF44464A),
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ElImtiyazTypography,
        content = content,
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
