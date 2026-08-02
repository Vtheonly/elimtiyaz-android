package com.example.ui.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Extended color container — every semantic, brand, surface, and role color
 * the design system needs, beyond what Material 3's [androidx.compose.material3.ColorScheme]
 * provides. Includes gradients and brushes.
 *
 * Components read these via [ElTheme.colors].
 */
@Immutable
data class ElColors(
    val isDark: Boolean,

    // Brand
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val primaryAccent: Color,
    val onPrimaryAccent: Color,
    val tertiary: Color,
    val onTertiary: Color,

    // Surfaces
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceElevated: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,

    // Text hierarchy
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textOnColor: Color,

    // Outlines
    val outline: Color,
    val outlineStrong: Color,
    val outlineVariant: Color,

    // Semantic
    val success: Color, val onSuccess: Color, val successContainer: Color,
    val warning: Color, val onWarning: Color, val warningContainer: Color,
    val danger: Color,  val onDanger: Color,  val dangerContainer: Color,
    val info: Color,    val onInfo: Color,    val infoContainer: Color,

    // Effects
    val scrim: Color,
    val shadowColor: Color,
    val glassTint: Color,
    val glassBorder: Color,

    // Role accents
    val roleAdmin: Color,
    val roleFinancial: Color,
    val roleTeacher: Color,
    val roleSupport: Color,
    val roleManager: Color,
    val roleBuyer: Color,
    val roleDriver: Color,
    val roleWarehouse: Color,
    val roleWorker: Color,
) {
    // ── Gradients ──────────────────────────────────────────────────────────
    val primaryGradient: List<Color> get() = listOf(primary, Violet700)
    val primaryGradientDiagonal: List<Color> get() = listOf(Violet700, primary, Violet400)
    val accentGradient: List<Color> get() = listOf(primaryAccent, Amber600)
    val tertiaryGradient: List<Color> get() = listOf(tertiary, Pink600)
    val successGradient: List<Color> get() = listOf(success, Emerald600)
    val dangerGradient: List<Color> get() = listOf(danger, Rose600)
    val warningGradient: List<Color> get() = listOf(warning, Tangerine600)
    val heroGradient: List<Color> get() =
        if (isDark) listOf(DarkSurface, DarkBackground) else listOf(LightBackground, LightSurface)

    // ── Brushes ────────────────────────────────────────────────────────────
    val primaryBrush: Brush get() = Brush.horizontalGradient(primaryGradient)
    val primaryDiagonalBrush: Brush get() = Brush.linearGradient(primaryGradientDiagonal)
    val accentBrush: Brush get() = Brush.horizontalGradient(accentGradient)
    val tertiaryBrush: Brush get() = Brush.horizontalGradient(tertiaryGradient)
    val successBrush: Brush get() = Brush.horizontalGradient(successGradient)
    val dangerBrush: Brush get() = Brush.horizontalGradient(dangerGradient)
    val warningBrush: Brush get() = Brush.horizontalGradient(warningGradient)
    val heroBrush: Brush get() = Brush.verticalGradient(heroGradient)

    // ── Role lookup ────────────────────────────────────────────────────────
    fun role(name: String): Color = when (name.lowercase()) {
        "superadmin", "admin" -> roleAdmin
        "financialofficer", "financial" -> roleFinancial
        "teacher" -> roleTeacher
        "supportstaff", "support" -> roleSupport
        "manager" -> roleManager
        "buyer" -> roleBuyer
        "driver" -> roleDriver
        "warehouseworker", "warehouse" -> roleWarehouse
        else -> roleWorker
    }
}

val LocalElColors = staticCompositionLocalOf<ElColors> {
    // FIX (login-blocks): fall back to DarkElColors instead of throwing.
    // If a composable is rendered outside the ElImtiyazTheme wrapper (e.g. in
    // a preview, or during a transient recomposition before the theme
    // installs), it now gets a valid color set instead of crashing with
    // "ElColors not provided". The proper theme is still required for
    // correct light/dark switching.
    DarkElColors
}
