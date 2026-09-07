package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElCardShape
import com.example.ui.theme.ElCardShapeSmall
import com.example.ui.theme.elDesignTokens

@Composable
fun ElCard(
    modifier: Modifier = Modifier,
    gradient: Boolean = true,
    accent: Color? = null,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tokens = elDesignTokens()
    val shape = if (compact) ElCardShapeSmall else ElCardShape
    val bgBrush = if (gradient) {
        tokens.surfaceBrush
    } else {
        Brush.verticalGradient(
            listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)
        )
    }

    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else Modifier

    val borderColor = if (accent != null) {
        accent.copy(alpha = 0.35f)
    } else {
        tokens.cardBorder.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(clickMod)
            .background(bgBrush, shape)
            .border(1.dp, borderColor, shape)
            .drawBehind {
                if (accent != null) {
                    drawRect(
                        color = accent,
                        topLeft = Offset.Zero,
                        size = Size(4.dp.toPx(), size.height),
                    )
                }
            },
    ) {
        Box(
            modifier = if (accent != null) Modifier.padding(start = 4.dp) else Modifier
        ) {
            content()
        }
    }
}