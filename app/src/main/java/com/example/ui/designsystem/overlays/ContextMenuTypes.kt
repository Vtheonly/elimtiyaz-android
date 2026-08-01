package com.example.ui.designsystem.overlays

import androidx.compose.ui.graphics.vector.ImageVector

/** A single item in an [ElContextMenu]. */
data class ElContextMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)
