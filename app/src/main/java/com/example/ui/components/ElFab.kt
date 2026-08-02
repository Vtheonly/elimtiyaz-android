package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElFabShape
import com.example.ui.theme.elDesignTokens
import androidx.compose.runtime.getValue

// ── ElFab ───────────────────────────────────────────────────────────────────

/**
 * Custom floating action button with gradient fill, shadow, and press animation.
 * Replaces stock [androidx.compose.material3.FloatingActionButton].
 */
@Composable
fun ElFab(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    gradient: Brush? = null,
) {
    val tokens = elDesignTokens()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(120),
        label = "fabScale",
    )
    val bgBrush = gradient ?: tokens.primaryBrush

    Box(
        modifier = modifier
            .size(56.dp)
            .shadow(8.dp, ElFabShape, ambientColor = tokens.shadowColor, spotColor = tokens.shadowColor)
            .clip(ElFabShape)
            .background(bgBrush)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}
