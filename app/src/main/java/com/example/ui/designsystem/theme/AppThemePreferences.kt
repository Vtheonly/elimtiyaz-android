package com.example.ui.designsystem.theme

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * The persisted app-wide theme preference.
 *
 * Keep this key shared between the root activity and SettingsViewModel: a
 * second key or a system-theme-only entry point would make the visible switch
 * disagree with the actual theme.
 */
internal object AppThemePreferences {
    val darkModeKey = booleanPreferencesKey("dark_mode")

    /** The existing settings default is light mode until the user opts in to dark mode. */
    fun isDarkMode(preferences: Preferences): Boolean = preferences[darkModeKey] ?: false
}
