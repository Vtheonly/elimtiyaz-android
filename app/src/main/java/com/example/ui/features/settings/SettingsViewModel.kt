package com.example.ui.features.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Session
import com.example.domain.repository.AuthRepository
import com.example.infrastructure.supabase.SupabaseClientProvider
import com.example.infrastructure.sync.OnlineDetector
import com.example.infrastructure.sync.SyncService
import com.example.infrastructure.sync.SyncState
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val syncService: SyncService,
    private val onlineDetector: OnlineDetector,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
    // FIX (out of context): DB connection configuration moved here from the
    // student roster — Settings is the canonical place for it.
    private val supabaseProvider: SupabaseClientProvider,
) : ViewModel() {

    /** Active session (null when signed out). */
    val session: StateFlow<Session?> = sessionManager.state

    /** Persisted user preferences — lazily started, with a sensible default. */
    val settings: StateFlow<SettingsState> = dataStore.data
        .map { p ->
            SettingsState(
                darkMode = p[DARK_MODE_KEY] ?: false,
                notificationsEnabled = p[NOTIFICATIONS_KEY] ?: true,
                forceOffline = p[FORCE_OFFLINE_KEY] ?: false,
                language = p[LANGUAGE_KEY] ?: "fr",
            )
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, SettingsState())

    /** Live sync state from [SyncService.observeSyncState]. */
    val syncState: StateFlow<SyncState> = syncService.observeSyncState()
        .stateIn(viewModelScope, SharingStarted.Lazily, SyncState(false, null, 0, null))

    /** Combined online flag from [OnlineDetector.observeOnline]. */
    val online: StateFlow<Boolean> = onlineDetector.observeOnline()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    /** Whether a Supabase database connection is configured. */
    private val _supabaseConfigured = kotlinx.coroutines.flow.MutableStateFlow(supabaseProvider.isConfigured())
    val supabaseConfigured: StateFlow<Boolean> = _supabaseConfigured.asStateFlow()

    fun getSavedSupabaseUrl(): String = supabaseProvider.getActiveUrl()
    fun getSavedSupabaseKey(): String = supabaseProvider.getActiveAnonKey()

    /** Save the Supabase connection and trigger an immediate sync. */
    fun saveSupabaseConfig(url: String, anonKey: String) {
        supabaseProvider.saveConfig(url, anonKey)
        _supabaseConfigured.value = supabaseProvider.isConfigured()
        syncService.syncNow()
    }

    /** Persist the dark-mode toggle. */
    fun setDarkMode(enabled: Boolean) = editAsync { it[DARK_MODE_KEY] = enabled }

    /** Persist the notifications-enabled toggle. */
    fun setNotifications(enabled: Boolean) = editAsync { it[NOTIFICATIONS_KEY] = enabled }

    /** Persist the force-offline toggle. */
    fun setForceOffline(enabled: Boolean) = editAsync { it[FORCE_OFFLINE_KEY] = enabled }

    /** Persist the language selection (ISO 639-1 code). */
    fun setLanguage(code: String) = editAsync { it[LANGUAGE_KEY] = code }

    /** Trigger an immediate one-shot sync (NOT via WorkManager). */
    fun syncNow() = syncService.syncNow()

    /**
     * Sign out — clears the auth session and the local [SessionManager]
     * state, then invokes [onComplete] so the UI can navigate to login.
     */
    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            sessionManager.setSession(null)
            onComplete()
        }
    }

    /** Read the package's versionName (e.g. "2.0.0"). */
    fun appVersion(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "unknown"
    }.getOrDefault("unknown")

    private fun editAsync(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        viewModelScope.launch {
            runCatching { dataStore.edit(block) }
        }
    }

    private companion object {
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")
        val FORCE_OFFLINE_KEY = booleanPreferencesKey("force_offline")
        val LANGUAGE_KEY = stringPreferencesKey("language")
    }
}

/**
 * Persisted settings state — the four toggles + language code.
 *
 * @property darkMode             True when the user has explicitly chosen dark mode.
 * @property notificationsEnabled True when push notifications are allowed.
 * @property forceOffline         True when the user has forced offline mode (no sync).
 * @property language             ISO 639-1 language code (`fr` / `ar` / `en`).
 */
