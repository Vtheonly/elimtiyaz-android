package com.example.ui.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Light-theme [ElColors] instance.
 * Centralizes the entire light palette in one place — no scattered color literals.
 */
val LightElColors = ElColors(
    isDark = false,
    primary = Violet600, onPrimary = Color.White,
    primaryContainer = Violet50, onPrimaryContainer = Violet700,
    primaryAccent = Amber500, onPrimaryAccent = Color.White,
    tertiary = Pink500, onTertiary = Color.White,

    background = LightBackground, onBackground = LightTextPrimary,
    surface = LightSurface, onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant, onSurfaceVariant = LightTextSecondary,
    surfaceElevated = LightSurfaceElevated,
    inverseSurface = LightInverseSurface, inverseOnSurface = Color(0xFFF5F6FA),

    textPrimary = LightTextPrimary, textSecondary = LightTextSecondary,
    textMuted = LightTextMuted, textOnColor = Color.White,

    outline = LightOutline, outlineStrong = LightOutlineStrong,
    outlineVariant = LightOutlineVariant,

    success = Emerald500, onSuccess = Color.White, successContainer = Emerald100,
    warning = Tangerine500, onWarning = Color.White, warningContainer = Tangerine100,
    danger = Rose500, onDanger = Color.White, dangerContainer = Rose100,
    info = Sky500, onInfo = Color.White, infoContainer = Sky100,

    scrim = LightScrim, shadowColor = LightShadowColor,
    glassTint = LightGlassTint, glassBorder = LightGlassBorder,

    roleAdmin = RoleAdmin, roleFinancial = RoleFinancial, roleTeacher = RoleTeacher,
    roleSupport = RoleSupport, roleManager = RoleManager, roleBuyer = RoleBuyer,
    roleDriver = RoleDriver, roleWarehouse = RoleWarehouse, roleWorker = RoleWorker,
)

/** Dark-theme [ElColors] instance. */
val DarkElColors = ElColors(
    isDark = true,
    primary = Violet400, onPrimary = Violet700,
    primaryContainer = Violet700, onPrimaryContainer = Violet50,
    primaryAccent = Amber400, onPrimaryAccent = Color(0xFF3A2D1A),
    tertiary = Pink400, onTertiary = Color(0xFF5B0F3A),

    background = DarkBackground, onBackground = DarkTextPrimary,
    surface = DarkSurface, onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = DarkTextSecondary,
    surfaceElevated = DarkSurfaceElevated,
    inverseSurface = DarkInverseSurface, inverseOnSurface = Color(0xFF1A1B22),

    textPrimary = DarkTextPrimary, textSecondary = DarkTextSecondary,
    textMuted = DarkTextMuted, textOnColor = Color(0xFF0F0F14),

    outline = DarkOutline, outlineStrong = DarkOutlineStrong,
    outlineVariant = DarkOutlineVariant,

    success = Emerald400, onSuccess = Color(0xFF003420), successContainer = Color(0xFF0B3D2A),
    warning = Tangerine400, onWarning = Color(0xFF3D1F00), warningContainer = Color(0xFF3D2400),
    danger = Rose400, onDanger = Color(0xFF4A0E0E), dangerContainer = Color(0xFF4A1A1A),
    info = Sky400, onInfo = Color(0xFF002A3D), infoContainer = Color(0xFF0A2F45),

    scrim = DarkScrim, shadowColor = DarkShadowColor,
    glassTint = DarkGlassTint, glassBorder = DarkGlassBorder,

    roleAdmin = RoleAdmin, roleFinancial = RoleFinancial, roleTeacher = RoleTeacher,
    roleSupport = RoleSupport, roleManager = RoleManager, roleBuyer = RoleBuyer,
    roleDriver = RoleDriver, roleWarehouse = RoleWarehouse, roleWorker = RoleWorker,
)

/**
 * Maps an [ElColors] instance to a Material 3 [ColorScheme] so any stock
 * Material component picks up our brand colors automatically.
 */
fun ElColors.toMaterialScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = primaryAccent, onSecondary = onPrimaryAccent,
        secondaryContainer = warningContainer, onSecondaryContainer = onWarning,
        tertiary = tertiary, onTertiary = onTertiary,
        tertiaryContainer = tertiary, onTertiaryContainer = onTertiary,
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
        error = danger, onError = onDanger,
        errorContainer = dangerContainer, onErrorContainer = onDanger,
        outline = outline, outlineVariant = outlineVariant,
        scrim = scrim,
    )
} else {
    lightColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = primaryAccent, onSecondary = onPrimaryAccent,
        secondaryContainer = warningContainer, onSecondaryContainer = onWarning,
        tertiary = tertiary, onTertiary = onTertiary,
        tertiaryContainer = tertiary, onTertiaryContainer = onTertiary,
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
        error = danger, onError = onDanger,
        errorContainer = dangerContainer, onErrorContainer = onDanger,
        outline = outline, outlineVariant = outlineVariant,
        scrim = scrim,
    )
}
