package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val bgBrush = if (gradient) tokens.surfaceBrush else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))

    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .then(clickMod)
            .background(bgBrush, shape)
            .border(1.dp, tokens.cardBorder.copy(alpha = 0.5f), shape)
            .then(if (accent != null) Modifier.padding(start = 3.dp) else Modifier),
    ) {
        if (accent != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = 3.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface),
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .matchParentSize()
                    .background(accent),
            )
        }
        content()
    }
}
