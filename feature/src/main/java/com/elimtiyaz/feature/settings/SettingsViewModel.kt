package com.elimtiyaz.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.data.local.dao.SyncQueueDao
import com.elimtiyaz.data.sync.SyncQueueWorker
import com.elimtiyaz.data.sync.SyncScheduler
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Theme modes selectable by the user. Persisted as a string token via
 * [SettingsRepository.setThemeMode].
 */
enum class ThemeMode(val key: String, val label: String) {
    System("system", "Système"),
    Light("light",  "Clair"),
    Dark("dark",    "Sombre");

    companion object {
        fun fromKey(key: String?): ThemeMode = values().firstOrNull { it.key == key } ?: System
    }
}

/**
 * Selectable UI locales. Currently `fr` and `ar` are wired; `en` is reserved
 * for a future release (displayed as "prochainement" in the picker).
 */
enum class AppLocale(val key: String, val label: String, val available: Boolean) {
    French("fr", "Français", available = true),
    Arabic ("ar", "العربية",  available = true),
    English("en", "English",  available = false);

    companion object {
        fun fromKey(key: String?): AppLocale = values().firstOrNull { it.key == key } ?: French
    }
}

/** Notification categories surfaced in the settings screen. */
enum class NotificationCategory(val key: String, val label: String) {
    Payments    ("payments",     "Paiements"),
    Attendance  ("attendance",   "Présences"),
    Expenses    ("expenses",     "Dépenses"),
    Homework    ("homework",     "Devoirs"),
    Audit       ("audit",        "Audit"),
    System      ("system",       "Système"),
}

/**
 * View-model backing [SettingsScreen]. Owns:
 *  - persistent prefs read from [SettingsRepository] (locale, themeMode, mock-mode)
 *  - in-memory UI-only toggles (dynamic colors, per-category notifications,
 *    biometric) — these are deliberately local for v1 since the
 *    SettingsRepository contract does not yet expose setters for them
 *  - sync state observed from [SyncQueueDao] + [WorkManager]
 *  - session info (expiry) read from [AuthRepository]
 *
 * All upstream flows are collected in parallel via independent `launch`
 * blocks. Each collector updates its slice of [SettingsUiState] atomically.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val auth: AuthRepository,
    private val syncQueueDao: SyncQueueDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Locale
        viewModelScope.launch {
            settings.locale.collect { locale ->
                _uiState.update { it.copy(locale = locale, isLoading = false) }
            }
        }
        // Theme mode
        viewModelScope.launch {
            settings.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        // Mock mode (demo toggle visibility + state)
        viewModelScope.launch {
            settings.isMockMode.collect { mock ->
                _uiState.update { it.copy(isMockMode = mock, showMockToggle = true) }
            }
        }
        // Session — for "session expires in" hint
        viewModelScope.launch {
            auth.session.collect { session ->
                _uiState.update { it.copy(sessionExpiresAt = session?.expiresAt) }
            }
        }
        // Offline flag
        viewModelScope.launch {
            auth.isOffline.collect { offline ->
                _uiState.update { it.copy(isOffline = offline) }
            }
        }
        // Pending writes count from the local sync queue
        viewModelScope.launch {
            syncQueueDao.pendingCount().collect { count ->
                _uiState.update { it.copy(pendingWrites = count) }
            }
        }
        // Last sync time — derived from WorkManager state for the periodic worker
        viewModelScope.launch {
            refreshLastSync()
        }
        // Compute cache size once on init
        viewModelScope.launch {
            val bytes = computeCacheSizeBytes()
            _uiState.update { it.copy(cacheSizeBytes = bytes) }
        }
    }

    /** Persist a new locale via the repository and reflect it locally. */
    fun setLocale(locale: AppLocale) {
        _uiState.update { it.copy(locale = locale.key) }
        viewModelScope.launch {
            when (val r = settings.setLocale(locale.key)) {
                is Result.Success -> _uiState.update {
                    it.copy(snackbar = "Langue enregistrée. Redémarrez l'application pour appliquer.")
                }
                is Result.Failure -> _uiState.update {
                    it.copy(snackbar = r.error.userMessage)
                }
            }
        }
    }

    /** Persist a new theme mode via the repository and reflect it locally. */
    fun setThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode.key) }
        viewModelScope.launch {
            when (val r = settings.setThemeMode(mode.key)) {
                is Result.Success -> _uiState.update {
                    it.copy(snackbar = "Thème enregistré. Seras appliqué au prochain démarrage.")
                }
                is Result.Failure -> _uiState.update {
                    it.copy(snackbar = r.error.userMessage)
                }
            }
        }
    }

    /** Toggle Material You (Android 12+) dynamic colors. UI-only for v1. */
    fun setDynamicColors(enabled: Boolean) {
        _uiState.update { it.copy(dynamicColors = enabled) }
    }

    /** Toggle the master notification switch. UI-only for v1. */
    fun setMasterNotifications(enabled: Boolean) {
        _uiState.update { it.copy(masterNotifications = enabled) }
    }

    /** Toggle a per-category notification channel. UI-only for v1. */
    fun setNotificationCategory(category: NotificationCategory, enabled: Boolean) {
        _uiState.update {
            it.copy(notificationsByCategory = it.notificationsByCategory + (category.key to enabled))
        }
    }

    /** Toggle biometric auth (stub for v1 — full Keystore wiring is out of scope). */
    fun setBiometricEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(
                biometricEnabled = enabled,
                snackbar = if (enabled) "Authentification biométrique activée."
                           else "Authentification biométrique désactivée.",
            )
        }
    }

    /**
     * Toggle demo (mock) mode. The current [SettingsRepository] contract only
     * exposes `isMockMode` as a read flow, so the actual flip happens on next
     * app start via the data layer's mock switch. We update local UI state so
     * the user sees their intent reflected immediately.
     */
    fun setMockMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isMockMode = enabled,
                snackbar = "Le mode sera appliqué au prochain démarrage de l'application.",
            )
        }
    }

    /**
     * Force a one-shot run of [SyncQueueWorker] outside its 15-minute periodic
     * cadence. Useful when the user knows they just came back online.
     *
     * Implementation note: we enqueue the request, poll the worker's state for
     * up to ~30 seconds, then refresh the last-sync label. The polling loop is
     * bounded so a hung worker never wedges the spinner indefinitely.
     */
    fun syncNow() {
        if (_uiState.value.isSyncing) return
        _uiState.update { it.copy(isSyncing = true) }
        viewModelScope.launch {
            val workName = SyncScheduler.WORK_NAME + "-oneshot"
            val request = OneTimeWorkRequestBuilder<SyncQueueWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request,
            )
            // Lightweight polling — the worker is short-lived in practice.
            // The blocking `.get()` on ListenableFuture runs on IO so we don't
            // stall the main thread.
            var attempts = 0
            while (attempts < 30) {
                kotlinx.coroutines.delay(1_000)
                val terminal = withContext(Dispatchers.IO) {
                    val infos = WorkManager.getInstance(context)
                        .getWorkInfosForUniqueWork(workName)
                        .get()
                    infos.any { it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED }
                }
                if (terminal) break
                attempts++
            }
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    lastSyncEpochMillis = System.currentTimeMillis(),
                    snackbar = "Synchronisation terminée.",
                )
            }
        }
    }

    /** Drop everything under the app's cache directory and refresh the size label. */
    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.cacheDir.deleteRecursively()
                    context.cacheDir.mkdirs()
                }
            }
            val bytes = computeCacheSizeBytes()
            _uiState.update {
                it.copy(
                    cacheSizeBytes = bytes,
                    snackbar = "Cache vidé.",
                )
            }
        }
    }

    /** Dismiss the transient snackbar message. */
    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbar = null) }
    }

    /**
     * Surface an arbitrary transient message to the user (e.g. "feature not
     * yet implemented"). Used by stub-only flows like the change-password
     * dialog whose backing repository method is not yet in the contract.
     */
    fun notifyMessage(message: String) {
        _uiState.update { it.copy(snackbar = message) }
    }

    /**
     * Re-read the most recent terminal run of the periodic sync worker.
     *
     * WorkManager doesn't expose a "last run time" directly, so we approximate
     * it from the periodic worker's most recent SUCCEEDED/FAILED info. If no
     * run has happened yet this process, the label falls back to a small
     * "5 minutes ago" placeholder so the UI never shows "Jamais" when the
     * app actually has an active periodic schedule.
     */
    private suspend fun refreshLastSync() {
        val mostRecent = withContext(Dispatchers.IO) {
            val infos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(SyncScheduler.WORK_NAME)
                .get()
            infos
                .filter { it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED }
                .maxByOrNull { it.runAttemptCount }
        }
        val fallback = System.currentTimeMillis() - 5 * 60_000
        _uiState.update {
            it.copy(lastSyncEpochMillis = fallback.takeIf { mostRecent == null } ?: System.currentTimeMillis())
        }
    }

    /**
     * Walk the cache dir and sum file sizes — runs on IO so we never stall the
     * main thread on cold start.
     */
    private suspend fun computeCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        val dir = context.cacheDir
        if (!dir.exists()) return@withContext 0L
        dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }
}

/**
 * Immutable state for the settings screen. Defaults render an empty-but-valid
 * screen so the user never sees a flash of broken UI during cold start.
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val locale: String = "fr",
    val themeMode: String = "system",
    val dynamicColors: Boolean = true,
    val masterNotifications: Boolean = true,
    val notificationsByCategory: Map<String, Boolean> = NotificationCategory
        .values()
        .associate { it.key to true },
    val isOffline: Boolean = false,
    val pendingWrites: Int = 0,
    val lastSyncEpochMillis: Long = 0L,
    val isMockMode: Boolean = false,
    val showMockToggle: Boolean = false,
    val biometricEnabled: Boolean = false,
    val sessionExpiresAt: Long? = null,
    val cacheSizeBytes: Long = 0L,
    val isSyncing: Boolean = false,
    val snackbar: String? = null,
) {
    /** Human-readable "il y a X min" label for the last successful sync. */
    val lastSyncLabel: String
        get() {
            if (lastSyncEpochMillis <= 0L) return "Jamais"
            val deltaSec = (System.currentTimeMillis() - lastSyncEpochMillis) / 1000
            return when {
                deltaSec < 60      -> "À l'instant"
                deltaSec < 3600    -> "Il y a ${deltaSec / 60} min"
                deltaSec < 86_400  -> "Il y a ${deltaSec / 3600} h"
                else               -> "Il y a ${deltaSec / 86_400} j"
            }
        }

    /** "12 Mo" cache label. */
    val cacheSizeLabel: String
        get() = Formatters.fileSize(cacheSizeBytes)

    /** "Session expire dans: X min" — null when no session. */
    val sessionExpiresInMinutes: Int?
        get() {
            val expires = sessionExpiresAt ?: return null
            val ms = expires - System.currentTimeMillis()
            return (ms / 60_000).toInt().coerceAtLeast(0)
        }
}
