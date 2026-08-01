package com.example.ui.designsystem.components.display

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElPillShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Unified chip — assist, filter, input, choice. Pill shape, playful tap scale.
 */
@Composable
fun ElChip(
    text: String,
    modifier: Modifier = Modifier,
    variant: ElChipVariant = ElChipVariant.ASSIST,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = ElTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.94f else 1f, label = "chip-scale")

    val (bg, fg, borderColor) = resolveChipColors(c, selected, enabled)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .scale(scale)
            .clip(ElPillShape)
            .background(bg)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, ElPillShape) else Modifier)
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.padding(end = 6.dp).size(16.dp),
            )
        }
        Text(text = text, color = fg, style = ElTheme.typography.labelMedium)
        if (onDismiss != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = fg,
                modifier = Modifier
                    .size(16.dp)
                    .clip(ElPillShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                        onClick = onDismiss,
                    )
                    .padding(2.dp),
            )
        }
    }
}

/** Resolves (bg, fg, border) for a chip based on state. */
private fun resolveChipColors(
    c: com.example.ui.designsystem.theme.ElColors,
    selected: Boolean,
    enabled: Boolean,
): Triple<Color, Color, Color?> = when {
    !enabled -> Triple(c.surfaceVariant.copy(alpha = 0.5f), c.textMuted, null)
    selected -> Triple(c.primary, c.textOnColor, null)
    else     -> Triple(c.surfaceVariant, c.textPrimary, c.outline)
}
