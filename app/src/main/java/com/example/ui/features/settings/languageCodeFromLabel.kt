package com.example.ui.features.settings

import androidx.compose.runtime.Composable

internal fun languageCodeFromLabel(label: String): String = when (label) {
    "العربية" -> "ar"
    "English" -> "en"
    else -> "fr"
}

/**
 * Thin wrapper around the [ChangePasswordModal] composable so the settings
 * screen can display it inline. Kept as a separate function for clarity
 * and to keep the [SettingsScreen] body readable.
 */
@Composable
