package com.example.ui.designsystem.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.elShadow
import com.example.ui.designsystem.theme.ElContextMenuShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Context menu / dropdown menu — appears as a modal popup. Renders a scrim
 * that dismisses on tap-outside, and the menu anchored to the top-center
 * of the screen (caller can offset via Modifier.padding).
 */
@Composable
fun ElContextMenu(
    items: List<ElContextMenuItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.DropdownList,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .clip(ElContextMenuShape)
                .background(c.surface)
                .border(ElTheme.borders.thin, c.outlineVariant, ElContextMenuShape)
                .elShadow(ElTheme.elevation.high, ElContextMenuShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},  // swallow
                ),
        ) {
            items.forEachIndexed { index, item ->
                ContextMenuItemRow(item = item, onDismiss = onDismiss)
                if (index < items.lastIndex) ContextMenuDivider()
            }
        }
    }
}

/** A single item row inside a context menu. */
@Composable
private fun ContextMenuItemRow(item: ElContextMenuItem, onDismiss: () -> Unit) {
    val c = ElTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = {
                    item.onClick()
                    onDismiss()
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (item.icon != null) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (item.destructive) c.danger else c.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = item.label,
            color = if (item.destructive) c.danger else c.textPrimary,
            style = ElTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The thin divider between context menu items. */
@Composable
private fun ContextMenuDivider() {
    val c = ElTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .size(width = 0.dp, height = 1.dp)
            .background(c.outlineVariant),
    )
}
