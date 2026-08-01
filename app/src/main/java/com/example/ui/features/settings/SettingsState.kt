package com.example.ui.features.settings

data class SettingsState(
    val darkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val forceOffline: Boolean = false,
    val language: String = "fr",
)
