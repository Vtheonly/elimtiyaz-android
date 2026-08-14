package com.example.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.infrastructure.room.DatabaseSeeder
import com.example.infrastructure.sync.PullSyncRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppNavViewModel @Inject constructor(
    val sessionManager: SessionManager,
    private val databaseSeeder: DatabaseSeeder,
    private val pullSyncRepository: PullSyncRepository,
) : ViewModel() {

    val sessionState = sessionManager.state

    init {
        // Seed REFERENCE / CATALOG data only (pricing, subjects, classes,
        // personnel). Parents / students / payments / ledger entries are
        // NEVER seeded — they come from the real Supabase backend.
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { databaseSeeder.seedIfEmpty() }
            // Trigger an immediate Supabase pull so the UI shows real data
            // as soon as the app launches.
            runCatching { pullSyncRepository.pullAll() }
        }

        // Whenever an active session appears or changes, pull the latest data
        // from Supabase so the user sees fresh rows after sign-in.
        // (StateFlow already performs operator fusion equivalent to
        // distinctUntilChanged, so we don't need to apply it here.)
        viewModelScope.launch(Dispatchers.IO) {
            sessionState.collect { session ->
                if (session != null) {
                    runCatching { pullSyncRepository.pullAll() }
                }
            }
        }
    }

    /** Restore the session at app start (called once from [AppNavHost]). */
    fun restoreSession() {
        viewModelScope.launch {
            sessionManager.restoreSession()
        }
    }

    /**
     * Manually trigger a fresh pull from Supabase. Called by the dashboard's
     * "refresh" action so the user can force-reload the latest data without
     * restarting the app.
     */
    fun refreshFromSupabase() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { pullSyncRepository.pullAll() }
        }
    }
}
