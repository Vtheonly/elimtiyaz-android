package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElPillShape
import com.example.ui.theme.elDesignTokens

// ── ElProgressBar ──────────────────────────────────────────────────────────

/**
 * Custom progress bar with gradient fill and rounded track.
 */
@Composable
fun ElProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    gradient: Brush? = null,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val tokens = elDesignTokens()
    val fillBrush = gradient ?: tokens.primaryBrush

    Box(
        modifier = modifier
            .height(6.dp)
            .clip(ElPillShape)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(ElPillShape)
                .background(fillBrush),
        )
    }
}
