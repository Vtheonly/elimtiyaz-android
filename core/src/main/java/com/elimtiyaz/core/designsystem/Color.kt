package com.elimtiyaz.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * El-Imtiyaz design tokens — dark-first palette per master plan §03.01.
 *
 * Hex values are the canonical source of truth. Light-theme variants
 * are derived in [ElImtiyazTheme] by swapping surface/text roles.
 */
object ElImtiyazColors {
    // Brand
    val PrimaryBlue = Color(0xFF349BD4)
    val DeepBlue = Color(0xFF2B7FB0)
    val LightBlue = Color(0xFF6EC1E4)
    val CyanGlow = Color(0xFF6EC1E4)

    // Neutrals
    val SlateGray = Color(0xFF3B464C)
    val MutedBrown = Color(0xFF836C68)
    val OffWhite = Color(0xFFEFF2F3)

    // Accents
    val WarmGold = Color(0xFFC8A98C)
    val SuccessGreen = Color(0xFF3FA66E)
    val WarningGold = Color(0xFFC8A98C)
    val DangerRed = Color(0xFFC0504D)

    // Dark surfaces
    val DarkBackground = Color(0xFF242526)
    val PanelBackground = Color(0xFF1E1F20)
    val ElevatedSurface = Color(0xFF2A2B2D)

    // Light surfaces (for light theme)
    val LightBackground = Color(0xFFF7F9FB)
    val LightPanel = Color(0xFFFFFFFF)
    val LightElevated = Color(0xFFFFFFFF)
    val LightBorder = Color(0xFFE2E8F0)
    val OnLightPrimary = Color(0xFF1B2A3A)
    val OnLightSecondary = Color(0xFF5A6B7B)

    // Transparent
    val Transparent = Color(0x00000000)
    val Scrim = Color(0x88000000)
}

typealias ElimtiyazColors = ElImtiyazColors
