package com.example.ui.designsystem.components.button

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.pressClickable
import com.example.ui.designsystem.theme.ElFabShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Floating action button — chunky, with the brand gradient. Optional [label]
 * extends it into a wider "extended FAB".
 */
@Composable
fun ElFab(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    label: String? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = ElTheme.colors
    val shape = ElFabShape
    val alpha = if (enabled) 1f else 0.4f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .then(if (label != null) Modifier else Modifier.size(56.dp))
            .clip(shape)
            .background(colors.primaryBrush, shape)
            .pressClickable(
                pressedScale = 0.92f,
                enabled = enabled,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .alpha(alpha)
            .padding(
                horizontal = if (label != null) 18.dp else 0.dp,
                vertical = if (label != null) 16.dp else 0.dp,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.textOnColor,
            modifier = Modifier.size(24.dp),
        )
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = colors.textOnColor,
                style = ElTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}
