package com.elimtiyaz.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The single DataStore instance — declared at file scope so it's a singleton per process. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "elimtiyaz_settings")

/**
 * DataStore-backed [SettingsRepository]. Persists the user's preferred locale,
 * theme mode and the mock-mode flag (so the app remembers if the user opted
 * to keep using the mock data even after real credentials are added).
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val context: Context,
) : SettingsRepository {

    private val log = Logger.withTag("Data.Settings")

    /** Stream the current locale ("fr" by default, "ar" supported). */
    override val locale: Flow<String> = context.settingsDataStore.data.map { it[LOCALE_KEY] ?: DEFAULT_LOCALE }

    /** Stream the theme mode ("system", "light", "dark"). */
    override val themeMode: Flow<String> = context.settingsDataStore.data.map { it[THEME_KEY] ?: DEFAULT_THEME }

    /** Stream the mock-mode flag (forced on when Supabase keys are absent). */
    override val isMockMode: Flow<Boolean> = context.settingsDataStore.data.map { it[MOCK_KEY] ?: false }

    /** Persist the locale. */
    override suspend fun setLocale(locale: String): Result<Unit> = Result.runCatching {
        context.settingsDataStore.edit { it[LOCALE_KEY] = locale }
        log.i { "Locale set to $locale" }
    }

    /** Persist the theme mode. */
    override suspend fun setThemeMode(mode: String): Result<Unit> = Result.runCatching {
        context.settingsDataStore.edit { it[THEME_KEY] = mode }
        log.i { "Theme set to $mode" }
    }

    private companion object {
        val LOCALE_KEY = stringPreferencesKey("locale")
        val THEME_KEY = stringPreferencesKey("theme_mode")
        val MOCK_KEY = booleanPreferencesKey("mock_mode")
        const val DEFAULT_LOCALE = "fr"
        const val DEFAULT_THEME = "system"
    }
}
