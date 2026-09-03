package com.example.ui.features.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.core.Role
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant

// T-044 pass 2 (2026-09-03): migrated to the design-system button
// (variant replaces the legacy ElButtonStyle).

@Composable
internal fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ElButton(
        text = label,
        onClick = onClick,
        icon = icon,
        variant = if (danger) ElButtonVariant.DANGER else ElButtonVariant.SECONDARY,
        fullWidth = true,
    )
}

/** Human-readable label for a [Role]. */
