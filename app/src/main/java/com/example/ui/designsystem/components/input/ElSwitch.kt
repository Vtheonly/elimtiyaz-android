package com.example.ui.designsystem.components.input

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.pressClickable
import com.example.ui.designsystem.theme.ElPillShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Toggle switch — replaces Material 3 [androidx.compose.material3.Switch].
 *
 * Animates three things for a tactile feel:
 *  - Track color (primary when checked, surfaceVariant when unchecked).
 *  - Thumb size (slightly larger when pressed).
 *  - Thumb position (slides across the track).
 *
 * The press-scale modifier gives a subtle squish when the user taps.
 *
 * @param checked         Current toggle state.
 * @param onCheckedChange Callback receiving the new state.
 * @param modifier        Outer modifier.
 * @param enabled         When false, dims the control and disables interaction.
 */
@Composable
fun ElSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = ElTheme.colors

    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()

    // Track dims — wider than tall, fully pill-rounded.
    val trackWidth = 52.dp
    val trackHeight = 32.dp
    val thumbDiameter = 24.dp
    val trackPadding = (trackHeight - thumbDiameter) / 2

    // Animated thumb offset: 0 when unchecked, max when checked.
    val maxOffset = trackWidth - thumbDiameter - trackPadding * 2
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) maxOffset else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "el-switch-offset",
    )

    // Animated press scale on the thumb.
    val thumbScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.90f else 1f,
        label = "el-switch-thumb-scale",
    )

    // Track + thumb colors.
    val trackColor = when {
        !enabled -> c.surfaceVariant.copy(alpha = 0.5f)
        checked -> c.primary
        else -> c.surfaceVariant
    }
    val thumbColor = when {
        !enabled -> c.outline.copy(alpha = 0.5f)
        checked -> c.onPrimary
        else -> c.outline
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = trackWidth, minHeight = trackHeight)
            .clip(ElPillShape)
            .background(trackColor)
            .pressClickable(
                pressedScale = 0.96f,
                enabled = enabled,
                interactionSource = interaction,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(trackPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(thumbDiameter)
                .scale(thumbScale)
                .clip(CircleShape)
                .background(thumbColor)
                // Slide the thumb horizontally within the track.
                .offset(x = thumbOffset),
        )
    }
}
