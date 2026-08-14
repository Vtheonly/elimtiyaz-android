package com.example.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.infrastructure.room.DatabaseSeeder
import com.example.infrastructure.sync.PullSyncRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
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
        // Seed default catalogs/pricing if DB is empty, then trigger pull sync
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { databaseSeeder.seedIfEmpty() }
            runCatching { pullSyncRepository.pullAll() }
        }

        // Whenever an active session appears or changes, pull the latest data
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
}
