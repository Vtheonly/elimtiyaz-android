package com.example.ui.designsystem.components.input

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.noRippleClickable
import com.example.ui.designsystem.foundation.pressScale
import com.example.ui.designsystem.theme.ElTheme

/**
 * Checkbox — replaces Material 3 [androidx.compose.material3.Checkbox].
 *
 * Custom-drawn box + check glyph so the colors exactly match the El-Imtiyaz
 * palette and the press-scale feedback is consistent with buttons and chips.
 *
 * @param checked         Current check state.
 * @param onCheckedChange Callback receiving the new state.
 * @param modifier        Outer modifier.
 * @param enabled         When false, dims the control and disables interaction.
 * @param label           Optional text rendered to the right of the box.
 */
@Composable
fun ElCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
) {
    val c = ElTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        label = "el-checkbox-scale",
    )

    val boxColor by animateColorAsState(
        targetValue = when {
            !enabled -> c.surfaceVariant.copy(alpha = 0.5f)
            checked -> c.primary
            else -> Color.Transparent
        },
        label = "el-checkbox-fill",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> c.outline.copy(alpha = 0.5f)
            checked -> c.primary
            else -> c.outline
        },
        label = "el-checkbox-border",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .then(
                if (enabled) {
                    Modifier.noRippleClickable(
                        role = Role.Checkbox,
                        onClick = { onCheckedChange(!checked) },
                    )
                } else Modifier
            )
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .scale(scale)
                .pressScale(pressedScale = 0.92f, interactionSource = interaction)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .background(boxColor)
                .border(
                    ElTheme.borders.thick,
                    borderColor,
                    androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = c.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (label != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                color = if (enabled) c.textPrimary else c.textMuted,
                style = ElTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Radio button — replaces Material 3 [androidx.compose.material3.RadioButton].
 *
 * Custom-drawn outer ring + inner dot so the colors exactly match the
 * El-Imtiyaz palette and the press-scale feedback is consistent with the
 * rest of the design system.
 *
 * @param selected  Whether this radio is the active option in its group.
 * @param onClick    Callback invoked on tap (the caller is responsible for
 *                   ensuring only one radio in a group is selected).
 * @param modifier   Outer modifier.
 * @param enabled    When false, dims the control and disables interaction.
 * @param label      Optional text rendered to the right of the circle.
 */
@Composable
fun ElRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
) {
    val c = ElTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        label = "el-radio-scale",
    )

    val ringColor by animateColorAsState(
        targetValue = when {
            !enabled -> c.outline.copy(alpha = 0.5f)
            selected -> c.primary
            else -> c.outline
        },
        label = "el-radio-ring",
    )
    val dotColor by animateColorAsState(
        targetValue = if (selected && enabled) c.primary else Color.Transparent,
        label = "el-radio-dot",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .then(
                if (enabled && onClick != null) {
                    Modifier.noRippleClickable(
                        role = Role.RadioButton,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .scale(scale)
                .pressScale(pressedScale = 0.92f, interactionSource = interaction)
                .clip(CircleShape)
                .background(Color.Transparent)
                .border(ElTheme.borders.thick, ringColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
        if (label != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                color = if (enabled) c.textPrimary else c.textMuted,
                style = ElTheme.typography.bodyMedium,
            )
        }
    }
}
