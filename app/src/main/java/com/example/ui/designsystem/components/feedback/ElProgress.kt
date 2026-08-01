package com.example.ui.designsystem.components.feedback

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/**
 * Linear progress bar with the brand gradient fill and rounded caps.
 */
@Composable
fun ElLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = ElTheme.colors.surfaceVariant,
    gradient: List<Color> = ElTheme.colors.primaryGradient,
    height: Int = 8,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = ElTheme.motion.normal,
        label = "el-progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(50)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .background(trackColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(height.dp)
                .background(Brush.horizontalGradient(gradient)),
        )
    }
}

/**
 * Circular spinner — wraps Material CircularProgressIndicator with theme colors.
 */
@Composable
fun ElSpinner(
    modifier: Modifier = Modifier,
    size: Int = 32,
    strokeWidth: Int = 3,
    color: Color = ElTheme.colors.primary,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size.dp),
        strokeWidth = strokeWidth.dp,
        color = color,
        trackColor = ElTheme.colors.surfaceVariant,
    )
}
