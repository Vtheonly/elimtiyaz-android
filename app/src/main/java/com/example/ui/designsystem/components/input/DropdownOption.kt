package com.example.ui.designsystem.components.input

import androidx.compose.ui.graphics.vector.ImageVector

/** A single selectable option in an [ElDropdown]. */
data class ElDropdownOption(
    val value: String,
    val label: String,
    val icon: ImageVector? = null,
)
