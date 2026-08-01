package com.example.ui.designsystem.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Single entry point for all design tokens inside composables.
 *
 * Example:
 *   val c = ElTheme.colors          // ElColors
 *   val s = ElTheme.spacing         // ElSpacing
 *   val m = ElTheme.motion          // ElMotion
 *   val t = ElTheme.typography      // Material 3 Typography
 *
 * Every component in the system reads tokens through this object — never
 * through direct constant access — so light/dark switching is automatic.
 */
object ElTheme {
    val colors: ElColors
        @Composable @ReadOnlyComposable get() = LocalElColors.current

    val spacing: ElSpacing
        @Composable @ReadOnlyComposable get() = LocalElSpacing.current

    val elevation: ElElevation
        @Composable @ReadOnlyComposable get() = LocalElElevation.current

    val borders: ElBorders
        @Composable @ReadOnlyComposable get() = LocalElBorders.current

    val motion: ElMotion
        @Composable @ReadOnlyComposable get() = LocalElMotion.current

    val textStyles: ElTextStyles
        @Composable @ReadOnlyComposable get() = LocalElTextStyles.current

    val shadowColor: Color
        @Composable @ReadOnlyComposable get() = LocalElShadowColor.current

    val shapes: Shapes
        @Composable @ReadOnlyComposable get() = androidx.compose.material3.MaterialTheme.shapes

    val typography: Typography
        @Composable @ReadOnlyComposable get() = androidx.compose.material3.MaterialTheme.typography
}
