package com.example.ui.designsystem.components.nav

import androidx.compose.ui.graphics.vector.ImageVector

/** A single destination in a navigation bar / rail. */
data class ElNavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badge: String? = null,
)
