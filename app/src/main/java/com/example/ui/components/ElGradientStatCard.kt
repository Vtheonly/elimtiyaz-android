package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElCardShape
import com.example.ui.theme.elDesignTokens

// ── ElGradientStatCard ──────────────────────────────────────────────────────

/**
 * Gradient-filled stat card for hero metrics. Uses the primary gradient
 * as background with white text — for prominent dashboard figures.
 */
@Composable
fun ElGradientStatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    gradient: Brush? = null,
) {
    val tokens = elDesignTokens()
    val bgBrush = gradient ?: tokens.primaryBrush

    Box(
        modifier = modifier
            .clip(ElCardShape)
            .background(bgBrush, ElCardShape)
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                ),
                color = Color.White,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}
