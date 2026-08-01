package com.example.ui.features.settings

import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import com.example.core.Session
import com.example.infrastructure.sync.OnlineDetector
import com.example.infrastructure.sync.SyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

@Composable
internal fun ChangePasswordModalSheet(onDismiss: () -> Unit) {
    com.example.ui.features.auth.ChangePasswordModal(onDismiss = onDismiss)
}

/**
 * ViewModel for [SettingsScreen].
 *
 * Exposes the active [Session], the persisted [SettingsState], the live
 * [SyncState], and the [OnlineDetector]'s online flag. Each
 * toggle mutates the [DataStore] immediately so the next app launch
 * restores the user's choices.
 *
 * @param sessionManager   Source of the active session.
 * @param authRepository   Used for sign-out.
 * @param syncService      Used for sync-now + sync state observation.
 * @param onlineDetector   Used for the diagnostics online indicator.
 * @param dataStore        Backing store for the persisted toggles.
 * @param context          Used to read the package version.
 */
@HiltViewModel
