package com.example.ui.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * El-Imtiyaz Design System — Elevation Tokens
 *
 * Bold geometric language uses crisp, tinted shadows rather than diffuse grey
 * halos. Light theme shadows are tinted with the primary violet for a premium
 * feel; dark theme shadows are deep black for depth.
 *
 * Each level is a triple of (blur, y-offset, alpha). Use [shadow] modifier in
 * component code via [ElElevationSpec].
 */
@Immutable
data class ElElevationSpec(
    val blur: Dp,
    val y: Dp,
    val alpha: Float,
)

@Immutable
data class ElElevation(
    val none: ElElevationSpec     = ElElevationSpec(0.dp, 0.dp, 0f),
    val low: ElElevationSpec      = ElElevationSpec(4.dp, 2.dp, 0.06f),
    val medium: ElElevationSpec   = ElElevationSpec(8.dp, 4.dp, 0.08f),
    val high: ElElevationSpec     = ElElevationSpec(16.dp, 8.dp, 0.10f),
    val highest: ElElevationSpec  = ElElevationSpec(24.dp, 12.dp, 0.12f),
    val floating: ElElevationSpec = ElElevationSpec(32.dp, 16.dp, 0.14f),
    val overlay: ElElevationSpec  = ElElevationSpec(40.dp, 20.dp, 0.18f),
)

val LocalElElevation = staticCompositionLocalOf { ElElevation() }

/**
 * The shadow color used for tinted shadows. Set by theme based on light/dark.
 */
val LocalElShadowColor = staticCompositionLocalOf { LightShadowColor }
