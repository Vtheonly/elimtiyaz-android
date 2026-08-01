package com.example.ui.features.settings

import androidx.compose.ui.graphics.Color
import com.example.core.Role
import com.example.ui.theme.PrimaryBlue

internal fun roleColor(role: Role): Color = when (role) {
    Role.SUPER_ADMIN, Role.MANAGER -> PrimaryBlue
    Role.FINANCIAL_OFFICER -> Color(0xFF2E7D32)
    Role.TEACHER -> Color(0xFF6A1B9A)
    Role.SUPPORT_STAFF -> Color(0xFFEF6C00)
    else -> Color(0xFF546E7A)
}

/** ISO 639-1 → display label. */
