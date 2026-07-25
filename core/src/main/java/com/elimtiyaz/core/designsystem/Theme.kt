package com.elimtiyaz.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = ElImtiyazColors.PrimaryBlue,
    onPrimary = ElImtiyazColors.OffWhite,
    primaryContainer = ElImtiyazColors.DeepBlue,
    onPrimaryContainer = ElImtiyazColors.OffWhite,
    secondary = ElImtiyazColors.LightBlue,
    onSecondary = ElImtiyazColors.DarkBackground,
    secondaryContainer = ElImtiyazColors.ElevatedSurface,
    onSecondaryContainer = ElImtiyazColors.OffWhite,
    tertiary = ElImtiyazColors.WarmGold,
    onTertiary = ElImtiyazColors.DarkBackground,
    background = ElImtiyazColors.DarkBackground,
    onBackground = ElImtiyazColors.OffWhite,
    surface = ElImtiyazColors.PanelBackground,
    onSurface = ElImtiyazColors.OffWhite,
    surfaceVariant = ElImtiyazColors.ElevatedSurface,
    onSurfaceVariant = ElImtiyazColors.OffWhite,
    surfaceTint = ElImtiyazColors.PrimaryBlue,
    inverseSurface = ElImtiyazColors.OffWhite,
    inverseOnSurface = ElImtiyazColors.DarkBackground,
    error = ElImtiyazColors.DangerRed,
    onError = ElImtiyazColors.OffWhite,
    errorContainer = ElImtiyazColors.DangerRed,
    onErrorContainer = ElImtiyazColors.OffWhite,
    outline = ElImtiyazColors.SlateGray,
    outlineVariant = ElImtiyazColors.ElevatedSurface,
    scrim = ElImtiyazColors.Scrim,
)

private val LightColors = lightColorScheme(
    primary = ElImtiyazColors.PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = ElImtiyazColors.LightBlue,
    onPrimaryContainer = ElImtiyazColors.OnLightPrimary,
    secondary = ElImtiyazColors.DeepBlue,
    onSecondary = Color.White,
    secondaryContainer = ElImtiyazColors.LightBackground,
    onSecondaryContainer = ElImtiyazColors.OnLightPrimary,
    tertiary = ElImtiyazColors.WarmGold,
    onTertiary = ElImtiyazColors.OnLightPrimary,
    background = ElImtiyazColors.LightBackground,
    onBackground = ElImtiyazColors.OnLightPrimary,
    surface = ElImtiyazColors.LightPanel,
    onSurface = ElImtiyazColors.OnLightPrimary,
    surfaceVariant = ElImtiyazColors.LightBackground,
    onSurfaceVariant = ElImtiyazColors.OnLightSecondary,
    surfaceTint = ElImtiyazColors.PrimaryBlue,
    inverseSurface = ElImtiyazColors.OnLightPrimary,
    inverseOnSurface = Color.White,
    error = ElImtiyazColors.DangerRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFE1E0),
    onErrorContainer = ElImtiyazColors.DangerRed,
    outline = ElImtiyazColors.LightBorder,
    outlineVariant = ElImtiyazColors.LightBorder,
    scrim = ElImtiyazColors.Scrim,
)

/** Exposes semantic status colors that don't fit Material's color roles. */
data class ElImtiyazStatusColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val neutral: Color,
)

val LocalElImtiyazStatusColors = staticCompositionLocalOf {
    ElImtiyazStatusColors(
        success = ElImtiyazColors.SuccessGreen,
        warning = ElImtiyazColors.WarmGold,
        danger = ElImtiyazColors.DangerRed,
        info = ElImtiyazColors.PrimaryBlue,
        neutral = ElImtiyazColors.SlateGray,
    )
}

/**
 * The El-Imtiyaz root theme. Apply once at the top of [androidx.activity.ComponentActivity.setContent].
 *
 * @param darkTheme Whether to use the dark color scheme. Defaults to system.
 * @param content   The Composable subtree to theme.
 */
@Composable
fun ElImtiyazTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val status = ElImtiyazStatusColors(
        success = ElImtiyazColors.SuccessGreen,
        warning = ElImtiyazColors.WarmGold,
        danger = ElImtiyazColors.DangerRed,
        info = if (darkTheme) ElImtiyazColors.LightBlue else ElImtiyazColors.PrimaryBlue,
        neutral = ElImtiyazColors.SlateGray,
    )
    CompositionLocalProvider(LocalElImtiyazStatusColors provides status) {
        MaterialTheme(
            colorScheme = colors,
            typography = ElImtiyazTypography,
            shapes = ElImtiyazShapes,
            content = content,
        )
    }
}
