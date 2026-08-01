package com.example.ui.designsystem.foundation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import com.example.ui.designsystem.theme.ElTheme

/**
 * Animated press-scale modifier — squishes the element slightly on tap.
 * Use on buttons, cards, chips for the playful bold-geometric feel.
 *
 * Pass the same [interactionSource] you use on the clickable so press state
 * stays in sync.
 */
fun Modifier.pressScale(
    pressedScale: Float = 0.96f,
    animationSpec: FiniteAnimationSpec<Float>? = null,
    interactionSource: MutableInteractionSource,
): Modifier = composed {
    val isPressed = interactionSource.collectIsPressedAsState().value
    val scale = remember { Animatable(1f) }
    val motion = animationSpec ?: ElTheme.motion.standard
    LaunchedEffect(isPressed) {
        scale.animateTo(if (isPressed) pressedScale else 1f, motion)
    }
    this.scale(scale.value)
}
