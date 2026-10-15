package com.example.ui.designsystem.theme

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Regression coverage for the persisted, app-wide theme switch. */
class AppThemePreferencesTest {

    @Test
    fun `light and dark palettes map to their matching Material color schemes`() {
        assertFalse(LightElColors.isDark)
        assertTrue(DarkElColors.isDark)
        assertNotEquals(LightElColors.background, DarkElColors.background)
        assertEquals(LightElColors.background, LightElColors.toMaterialScheme().background)
        assertEquals(DarkElColors.background, DarkElColors.toMaterialScheme().background)
        assertEquals(LightElColors.textPrimary, LightElColors.toMaterialScheme().onSurface)
        assertEquals(DarkElColors.textPrimary, DarkElColors.toMaterialScheme().onSurface)
    }

    @Test
    fun `theme defaults to light when no preference has been saved`() {
        assertFalse(AppThemePreferences.isDarkMode(emptyPreferences()))
    }

    @Test
    fun `a saved dark mode preference is restored`() {
        val preferences = mutablePreferencesOf(AppThemePreferences.darkModeKey to true)
        assertTrue(AppThemePreferences.isDarkMode(preferences))
    }

    @Test
    fun `a saved light mode preference is restored`() {
        val preferences = mutablePreferencesOf(AppThemePreferences.darkModeKey to false)
        assertFalse(AppThemePreferences.isDarkMode(preferences))
    }

    @Test
    fun `settings and the app root share the same persisted theme key`() {
        val moduleDir = listOf(File("."), File("app"), File(".."), File("../app"))
            .firstOrNull { File(it, "src/main/java/com/example/MainActivity.kt").isFile }
            ?: error("Cannot locate app module root")

        val mainActivity = File(moduleDir, "src/main/java/com/example/MainActivity.kt").readText()
        val settingsViewModel = File(moduleDir, "src/main/java/com/example/ui/features/settings/SettingsViewModel.kt").readText()

        assertTrue(
            "the root activity must feed the stored choice into the global theme",
            mainActivity.contains("ElImtiyazTheme(darkTheme = darkMode)"),
        )
        assertTrue(
            "all screen backgrounds must follow the active theme",
            mainActivity.contains(".background(ElTheme.colors.background)"),
        )
        assertFalse(
            "the app root must not hard-code the old dark-only background",
            mainActivity.contains("Color(0xFF0F0F14)"),
        )
        assertTrue(
            "Settings must persist using the same theme preference key",
            settingsViewModel.contains("it[AppThemePreferences.darkModeKey] = enabled"),
        )
    }
}
