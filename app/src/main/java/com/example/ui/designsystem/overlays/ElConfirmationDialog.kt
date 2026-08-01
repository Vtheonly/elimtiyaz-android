package com.example.ui.designsystem.overlays

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.button.ElButtonVariant

/**
 * Confirmation dialog — destructive or neutral. Pre-built for the most common
 * case (confirm / cancel). For richer layouts use [ElDialogShell] directly.
 *
 * Shares the exact same shell, motion, elevation, and padding as every other
 * overlay in the system.
 */
@Composable
fun ElConfirmationDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    confirmLabel: String = "Confirm",
    cancelLabel: String = "Cancel",
    destructive: Boolean = false,
) {
    ElDialogShell(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        ElDialogContent(
            title = title,
            message = message,
            icon = icon,
            actions = {
                ElButton(
                    text = cancelLabel,
                    onClick = onDismiss,
                    variant = ElButtonVariant.GHOST,
                    size = ElButtonSize.MEDIUM,
                )
                Spacer(Modifier.width(8.dp))
                ElButton(
                    text = confirmLabel,
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    variant = if (destructive) ElButtonVariant.DANGER else ElButtonVariant.PRIMARY,
                    size = ElButtonSize.MEDIUM,
                )
            },
        )
    }
}
