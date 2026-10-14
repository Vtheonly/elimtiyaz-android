package com.example.ui.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.domain.repository.AuthRepository
import com.example.infrastructure.supabase.SupabaseClientProvider
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val supabaseProvider: SupabaseClientProvider,
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

    /**
     * Standard authentication attempt.
     * Sanitizes inputs and calls the backend. If rejected by the remote server,
     * suggests the instant local/demo bypass.
     */
    fun signIn(email: String, password: String) {
        val cleanEmail = email.trim()
        val cleanPassword = password.trim()

        if (cleanEmail.isBlank() || cleanPassword.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Email et mot de passe sont requis")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            _uiState.value = _uiState.value.copy(error = "Format d'email invalide")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, signedIn = false)
            try {
                when (val result = authRepository.signIn(cleanEmail, cleanPassword)) {
                    is Result.Ok -> {
                        sessionManager.setSession(result.value)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = null,
                            signedIn = true,
                        )
                    }
                    is Result.Err -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Identifiants non reconnus par le serveur Supabase. Utilisez le bouton 'Accès Démo' ci-dessous pour déverrouiller l'espace immédiatement.",
                        )
                    }
                }
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    signedIn = false,
                    error = "Connexion au serveur impossible (${t.message ?: "erreur réseau"}). Utilisez le mode démo ci-dessous pour continuer.",
                )
            }
        }
    }

    /**
     * Instant Demo / Local Sandbox Login.
     * Temporarily switches to local mode so anyone testing or evaluating the app
     * can log in immediately as SuperAdmin, Finance, or Teacher without being blocked.
     */
    fun signInDemo(roleEmail: String = "admin@elimtiyaz.dz") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, signedIn = false)
            try {
                val wasConfigured = supabaseProvider.isConfigured()
                val savedUrl = supabaseProvider.getActiveUrl()
                val savedKey = supabaseProvider.getActiveAnonKey()

                // Switch to local mode so the local demo sandbox provider signs in
                if (wasConfigured) {
                    supabaseProvider.saveConfig("", "")
                }

                when (val result = authRepository.signIn(roleEmail.trim(), "demo1234")) {
                    is Result.Ok -> {
                        sessionManager.setSession(result.value)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = null,
                            signedIn = true,
                        )
                    }
                    is Result.Err -> {
                        // Restore previous config if sign-in failed
                        if (wasConfigured && savedUrl.isNotBlank()) {
                            supabaseProvider.saveConfig(savedUrl, savedKey)
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.error.userMessage.ifBlank { "Échec de la connexion démo." },
                        )
                    }
                }
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    signedIn = false,
                    error = "Connexion impossible : ${t.message ?: "erreur inattendue"}",
                )
            }
        }
    }
}