package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewModelScope
import com.example.session.SessionManager
import javax.inject.Inject
import kotlinx.coroutines.launch

@dagger.hilt.android.lifecycle.HiltViewModel
class AppNavViewModel @Inject constructor(
    val sessionManager: SessionManager,
) : androidx.lifecycle.ViewModel() {

    val sessionState = sessionManager.state

    /** Restore the session at app start (called once from [AppNavHost]). */
    fun restoreSession() {
        viewModelScope.launch {
            sessionManager.restoreSession()
        }
    }
}
