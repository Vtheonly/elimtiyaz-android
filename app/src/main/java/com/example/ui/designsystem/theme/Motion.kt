package com.example.ui.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset

/**
 * El-Imtiyaz Design System — Motion Tokens
 *
 * Bold geometric motion is springy and playful but never chaotic. We prefer
 * physics-based springs over fixed-duration tweens for most interactive
 * feedback, and reserve tweens for choreographed enter/exit transitions where
 * precise timing matters.
 *
 * Access via [ElTheme.motion] inside composables.
 */
@Immutable
data class ElMotion(
    // ── Springs (physics-based, for interactive feedback) ──────────────────
    /** Default spring — lively but settled. Use for taps, toggles, chips. */
    val standard: FiniteAnimationSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),

    /** Bouncy spring — playful overshoot. Use for FABs, success celebrations. */
    val bouncy: FiniteAnimationSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),

    /** Gentle spring — calm, no overshoot. Use for large surfaces, sheets. */
    val gentle: FiniteAnimationSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),

    /** Snappy spring — fast, decisive. Use for selection states, tabs. */
    val snappy: FiniteAnimationSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),

    // ── Tweens (duration-based, for choreographed transitions) ─────────────
    /** Short fade / scale — 150ms. */
    val quick: DurationBasedAnimationSpec<Float> = tween(150, easing = EmphasizedDecelerate),

    /** Standard fade / slide — 250ms. */
    val normal: DurationBasedAnimationSpec<Float> = tween(250, easing = EmphasizedDecelerate),

    /** Long enter / exit — 400ms. */
    val slow: DurationBasedAnimationSpec<Float> = tween(400, easing = EmphasizedStandard),

    // ── Offset specs (for slide-in / slide-out) ────────────────────────────
    val slideUp: FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),

    val slideDown: FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
)

// ── Easing curves (Material 3 motion-compatible) ────────────────────────────
val EmphasizedStandard   = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
val StandardCurve        = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

val LocalElMotion = staticCompositionLocalOf { ElMotion() }
