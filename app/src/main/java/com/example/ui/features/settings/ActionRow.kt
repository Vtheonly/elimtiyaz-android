package com.example.ui.features.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.core.Role
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle

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
        style = if (danger) ElButtonStyle.Danger else ElButtonStyle.Secondary,
        fullWidth = true,
    )
}

/** Human-readable label for a [Role]. */
