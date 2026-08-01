package com.example.ui.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * El-Imtiyaz Design System — Border Tokens
 *
 * Bold geometric language uses crisp 1dp borders for structure and 2dp borders
 * for emphasis/focus. Hairline (0.5dp) borders are reserved for dense tables.
 *
 * Border colors come from the active color scheme — use [ElTheme.borders].
 */
@Immutable
data class ElBorders(
    val hairline: Dp = 0.5.dp,
    val thin: Dp     = 1.dp,
    val thick: Dp    = 2.dp,
    val heavy: Dp    = 3.dp,
) {
    /** Standard structural border width. */
    val standard: Dp get() = thin

    /** Focus / selected border width. */
    val focus: Dp get() = thick
}

val LocalElBorders = staticCompositionLocalOf { ElBorders() }
