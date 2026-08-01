package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElAvatarShape
import com.example.ui.theme.elDesignTokens

// ── ElAvatar ───────────────────────────────────────────────────────────────

/**
 * Circular avatar with initials, gradient background, and configurable size.
 */
@Composable
fun ElAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Int = 44,
    gradient: Brush? = null,
) {
    val tokens = elDesignTokens()
    val bgBrush = gradient ?: tokens.primaryBrush
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(ElAvatarShape)
            .background(bgBrush),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.take(2).uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = (size * 0.35f).sp,
            ),
        )
    }
}
