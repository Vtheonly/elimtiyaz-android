package com.example.ui.designsystem.components.button

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.pressClickable
import com.example.ui.designsystem.theme.ElButtonShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * The unified El-Imtiyaz button. Every CTA in the app should use this.
 *
 * Variants share:
 *  - Shape: [ElButtonShape] (14dp rounded)
 *  - Motion: playful press-scale (0.96) using [ElTheme.motion.standard]
 *  - Min touch target 44dp (MEDIUM), 56dp (LARGE)
 *  - Loading state replaces content with a spinner and disables interaction
 *  - Disabled state applies 0.4 alpha
 */
@Composable
fun ElButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ElButtonVariant = ElButtonVariant.PRIMARY,
    size: ElButtonSize = ElButtonSize.MEDIUM,
    icon: ImageVector? = null,
    iconEnd: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    shape: Shape = ElButtonShape,
    fullWidth: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val style = resolveButtonStyle(variant = variant)
    val isClickable = enabled && !loading
    val alpha = if (enabled) 1f else 0.4f

    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minHeight = buttonMinHeight(size))
            .clip(shape)
            .background(style.background, shape)
            .then(if (style.border != null) Modifier.border(style.border, shape) else Modifier)
            .pressClickable(
                pressedScale = 0.96f,
                enabled = isClickable,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(buttonPadding(size)),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = style.contentColor,
                    modifier = Modifier.size(buttonIconSize(size).dp),
                )
            } else {
                ButtonContent(
                    text = text,
                    icon = icon,
                    iconEnd = iconEnd,
                    contentColor = style.contentColor,
                    iconSize = buttonIconSize(size),
                    textStyle = buttonTextStyle(size),
                )
            }
        }
    }
}

/** Renders the icon + text + trailing-icon row inside a button. */
@Composable
private fun ButtonContent(
    text: String,
    icon: ImageVector?,
    iconEnd: ImageVector?,
    contentColor: androidx.compose.ui.graphics.Color,
    iconSize: Int,
    textStyle: androidx.compose.ui.text.TextStyle,
) {
    if (icon != null) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor,
            modifier = Modifier.size(iconSize.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text = text, color = contentColor, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (iconEnd != null) {
        Spacer(Modifier.width(8.dp))
        Icon(imageVector = iconEnd, contentDescription = null, tint = contentColor,
            modifier = Modifier.size(iconSize.dp))
    }
}
