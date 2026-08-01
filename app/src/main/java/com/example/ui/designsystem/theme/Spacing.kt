package com.example.ui.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * El-Imtiyaz Design System — Spacing Scale
 *
 * Built on a 4dp grid. Every padding, margin, gap, and inset in the app should
 * come from [ElSpacing]. This guarantees visual rhythm across screens.
 *
 * Access via [ElTheme.spacing] inside composables.
 */
@Immutable
data class ElSpacing(
    val none: Dp   = 0.dp,
    val xs: Dp     = 4.dp,
    val sm: Dp     = 8.dp,
    val md: Dp     = 12.dp,
    val lg: Dp     = 16.dp,
    val xl: Dp     = 24.dp,
    val xxl: Dp    = 32.dp,
    val xxxl: Dp   = 48.dp,
    val huge: Dp   = 64.dp,
) {
    /** Screen-edge horizontal padding for full-bleed content. */
    val screenHorizontal: Dp get() = lg

    /** Screen-edge vertical padding for first/last item. */
    val screenVertical: Dp get() = lg

    /** Standard gap between stacked items in a section. */
    val itemGap: Dp get() = sm

    /** Standard gap between sections on a screen. */
    val sectionGap: Dp get() = xl

    /** Touch-target min size (Material accessibility guideline). */
    val touchTarget: Dp get() = 48.dp
}

val LocalElSpacing = staticCompositionLocalOf { ElSpacing() }
