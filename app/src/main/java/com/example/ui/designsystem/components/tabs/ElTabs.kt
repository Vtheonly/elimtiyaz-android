package com.example.ui.designsystem.components.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.noRippleClickable
import com.example.ui.designsystem.theme.ElPillShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Unified tab row — animated indicator slides between tabs.
 * Bold geometric style: filled pill indicator on selected tab.
 */
@Composable
fun ElTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ElPillShape)
            .background(c.surfaceVariant)
            .padding(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            tabs.forEachIndexed { index, label ->
                TabItem(
                    label = label,
                    selected = index == selectedIndex,
                    onClick = { onSelected(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A single tab inside an [ElTabRow]. */
@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    val textColor by animateColorAsState(
        if (selected) c.textOnColor else c.textSecondary,
        label = "tab-fg",
    )
    Box(
        modifier = modifier
            .clip(ElPillShape)
            .then(if (selected) Modifier.background(c.primaryBrush) else Modifier)
            .noRippleClickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            style = ElTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/**
 * Vertical icon+label tab list — for settings sections, dashboards.
 */
@Composable
fun ElVerticalTabList(
    items: List<Triple<ImageVector, String, Boolean>>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, (icon, label, selected) ->
            VerticalTabItem(
                icon = icon,
                label = label,
                selected = selected,
                onClick = { onItemClick(index) },
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** A single vertical tab item with icon-in-circle + label. */
@Composable
private fun VerticalTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = ElTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ElPillShape)
            .then(if (selected) Modifier.background(c.primary.copy(alpha = 0.10f)) else Modifier)
            .noRippleClickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(ElPillShape)
                .then(
                    if (selected) Modifier.background(c.primary)
                    else Modifier.background(c.surfaceVariant)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) c.textOnColor else c.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = if (selected) c.primary else c.textPrimary,
            style = ElTheme.typography.labelLarge,
        )
    }
}
