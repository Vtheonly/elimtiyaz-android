package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElPillShape
import com.example.ui.theme.elDesignTokens

// ── ElScrollableTabRow ──────────────────────────────────────────────────────

/**
 * Custom scrollable tab row for sub-screen navigation with many tabs.
 * Replaces stock [androidx.compose.material3.ScrollableTabRow].
 */
@Composable
fun ElScrollableTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = elDesignTokens()
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tabs) { tab ->
            val index = tabs.indexOf(tab)
            val isSelected = selectedTabIndex == index
            val bgAlpha = if (isSelected) 1f else 0.08f
            val textAlpha = if (isSelected) 1f else 0.7f

            Box(
                modifier = Modifier
                    .clip(ElPillShape)
                    .background(
                        if (isSelected) tokens.primaryBrush else Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.primary.copy(alpha = bgAlpha), MaterialTheme.colorScheme.primary.copy(alpha = bgAlpha))
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onTabSelected(index) },
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = tab,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                    ),
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                    maxLines = 1,
                )
            }
        }
    }
}
