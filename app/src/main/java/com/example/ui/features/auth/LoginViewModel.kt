package com.example.ui.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.domain.repository.AuthRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Login ViewModel.
 *
 * FIX (login-blocks):
 *  - `updateEmail` / `updatePassword` now mutate the VM state directly so the
 *    LoginScreen can bind to a single source of truth (no more local
 *    `mutableStateOf` copies that desync after a demo-account tap).
 *  - `signIn` launches on `viewModelScope` (non-blocking) and clears the
 *    `signedIn` flag after consumption so a re-entry doesn't double-fire.
 *
 * CROSS-100 (T-002 session): the `fillDemoAccount` quick-fill list was
 * REMOVED — with the auth rework (SEC-101/102) the listed role emails no
 * longer imply any role (roles come from role_assignments server-side), the
 * shared "demo1234" password never works against a configured server, and
 * the debug demo sandbox signs in with ANY typed credentials, so the chips
 * were misleading UI. The divergence problem itself is closed: no demo
 * credentials ship in this client any more.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun updateEmail(v: String) {
        _uiState.value = _uiState.value.copy(email = v, error = null)
    }

    fun updatePassword(v: String) {
        _uiState.value = _uiState.value.copy(password = v, error = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Email et mot de passe sont requis")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = _uiState.value.copy(error = "Email invalide")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, signedIn = false)
            when (val result = authRepository.signIn(email, password)) {
                is Result.Ok -> {
                    // Propagate to SessionManager — AppNavHost observes this
                    // and will navigate to Main. Then flip signedIn so the
                    // LoginScreen's safety-net LaunchedEffect also fires.
                    sessionManager.setSession(result.value)
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null, signedIn = true)
                }
                is Result.Err -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error.userMessage.ifBlank { "Échec de la connexion." },
                    )
                }
            }
        }
    }
}
