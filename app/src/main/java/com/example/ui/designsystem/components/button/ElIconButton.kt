package com.example.ui.designsystem.components.button

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.pressClickable
import com.example.ui.designsystem.theme.ElTheme

/**
 * Compact icon-only button — used in toolbars, list trailing, top bars.
 * Circle by default; pass [shape] for variants.
 */
@Composable
fun ElIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    tint: Color = ElTheme.colors.textPrimary,
    background: Color = ElTheme.colors.surfaceVariant,
    size: Int = 44,
    iconSize: Int = 20,
    shape: Shape = CircleShape,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .background(background, shape)
            .pressClickable(
                pressedScale = 0.90f,
                enabled = enabled,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize.dp),
        )
    }
}
