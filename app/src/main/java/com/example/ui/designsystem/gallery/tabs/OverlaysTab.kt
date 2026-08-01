package com.example.ui.designsystem.gallery.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.card.ElCardVariant
import com.example.ui.designsystem.components.display.ElDivider
import com.example.ui.designsystem.gallery.GallerySection
import com.example.ui.designsystem.overlays.ElBottomSheet
import com.example.ui.designsystem.overlays.ElConfirmationDialog
import com.example.ui.designsystem.overlays.ElContextMenu
import com.example.ui.designsystem.overlays.ElContextMenuItem
import com.example.ui.designsystem.overlays.ElDialogContent
import com.example.ui.designsystem.overlays.ElDialogShell
import com.example.ui.designsystem.overlays.ElSheetContent
import com.example.ui.designsystem.theme.ElColors
import com.example.ui.designsystem.theme.ElTheme

/** Overlays tab — dialogs, sheets, context menus, dividers. */
fun LazyListScope.overlaysTab(c: ElColors) {
    item { OverlaysSection(c) }
    item { DividersSection(c) }
}

@Composable
private fun OverlaysSection(c: ElColors) {
    GallerySection(
        title = "Overlays",
        description = "Every overlay shares the same shape family, motion, elevation, and scrim. Tap a button below to preview.",
    ) {
        var dialog by remember { mutableStateOf(false) }
        var confirm by remember { mutableStateOf(false) }
        var sheet by remember { mutableStateOf(false) }
        var menu by remember { mutableStateOf(false) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ElButton("Open Dialog", onClick = { dialog = true }, variant = ElButtonVariant.PRIMARY, icon = Icons.Default.Settings)
            ElButton("Open Confirmation", onClick = { confirm = true }, variant = ElButtonVariant.SECONDARY)
            ElButton("Open Bottom Sheet", onClick = { sheet = true }, variant = ElButtonVariant.TONAL)
            ElButton("Open Context Menu", onClick = { menu = true }, variant = ElButtonVariant.OUTLINED)
        }

        if (dialog) {
            DialogPreview(onDismiss = { dialog = false })
        }
        if (confirm) {
            ConfirmPreview(onDismiss = { confirm = false })
        }
        if (sheet) {
            SheetPreview(c = c, onDismiss = { sheet = false })
        }
        if (menu) {
            MenuPreview(onDismiss = { menu = false })
        }
    }
}

@Composable
private fun DialogPreview(onDismiss: () -> Unit) {
    ElDialogShell(onDismissRequest = onDismiss) {
        ElDialogContent(
            title = "Settings",
            message = "This is a standard El-Imtiyaz dialog. Same motion, elevation, and shape as every other overlay.",
            icon = Icons.Default.Settings,
            actions = {
                ElButton("Close", onClick = onDismiss, variant = ElButtonVariant.GHOST, size = ElButtonSize.MEDIUM)
            },
        )
    }
}

@Composable
private fun ConfirmPreview(onDismiss: () -> Unit) {
    ElConfirmationDialog(
        title = "Delete student?",
        message = "This will permanently remove the student and their payment history. This cannot be undone.",
        icon = Icons.Default.Delete,
        confirmLabel = "Delete",
        destructive = true,
        onConfirm = {},
        onDismiss = onDismiss,
    )
}

@Composable
private fun SheetPreview(c: ElColors, onDismiss: () -> Unit) {
    ElBottomSheet(onDismissRequest = onDismiss, title = "Filter Students") {
        ElSheetContent(
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Filter options go here. The sheet uses ElSheetShape (32dp top radius) and ElSheetHandleShape for the drag bar.",
                        color = c.textSecondary,
                        style = ElTheme.typography.bodyMedium,
                    )
                }
            },
            actions = {
                ElButton("Apply", onClick = onDismiss, fullWidth = true)
            },
        )
    }
}

@Composable
private fun MenuPreview(onDismiss: () -> Unit) {
    ElContextMenu(
        items = listOf(
            ElContextMenuItem("Edit", Icons.Default.Settings) {},
            ElContextMenuItem("Duplicate", Icons.Default.Add) {},
            ElContextMenuItem("Delete", Icons.Default.Delete, destructive = true) {},
        ),
        onDismiss = onDismiss,
    )
}

@Composable
private fun DividersSection(c: ElColors) {
    GallerySection(title = "Dividers") {
        ElCard(variant = ElCardVariant.OUTLINED, size = ElCardSize.STANDARD) {
            Text("Above divider", color = c.textPrimary, style = ElTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            ElDivider()
            Spacer(Modifier.height(12.dp))
            Text("Below divider", color = c.textPrimary, style = ElTheme.typography.bodyMedium)
        }
    }
}
