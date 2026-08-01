package com.example.ui.designsystem.foundation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role

/**
 * Clickable modifier with no ripple indication — the bold geometric system
 * relies on press-scale animation, not Material ripples. Used everywhere a
 * tap target is needed.
 *
 * This is the single source of truth for "no-ripple clickable" across the
 * design system. Without it, every component would repeat the same
 * `clickable(interactionSource = remember { ... }, indication = null, ...)`
 * boilerplate.
 */
fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        role = role,
        onClick = onClick,
    )
}

/**
 * Combined press-scale + no-ripple clickable. The standard interactive
 * surface treatment for buttons, cards, chips, FABs.
 *
 * Pass the same [interactionSource] you use elsewhere if you need to read
 * the press state; otherwise a fresh one is created automatically.
 */
fun Modifier.pressClickable(
    pressedScale: Float = 0.96f,
    enabled: Boolean = true,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    this
        .pressScale(pressedScale, interactionSource = source)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}
