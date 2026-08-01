package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElPillShape

// ── ElChip / ElTag ─────────────────────────────────────────────────────────

/**
 * Pill-shaped tag/chip with configurable accent color. Used for status
 * badges, categories, and filter chips.
 */
@Composable
fun ElTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val bgAlpha = if (selected) 1f else 0.12f
    val textAlpha = if (selected) 1f else 0.85f

    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else Modifier

    Box(
        modifier = modifier
            .clip(ElPillShape)
            .background(color.copy(alpha = bgAlpha))
            .then(clickMod)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            ),
            color = if (selected) Color.White else color.copy(alpha = textAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
