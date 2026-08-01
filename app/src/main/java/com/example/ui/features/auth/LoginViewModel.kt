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

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = authRepository.signIn(email, password)) {
                is Result.Ok -> {
                    sessionManager.setSession(result.value)
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null, signedIn = true)
                }
                is Result.Err -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.error.userMessage)
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun fillDemoAccount(role: String) {
        val (email, password) = when (role) {
            "admin"     -> "admin@elimtiyaz.dz" to "demo1234"
            "financial" -> "finance@elimtiyaz.dz" to "demo1234"
            "teacher"   -> "teacher@elimtiyaz.dz" to "demo1234"
            "support"   -> "support@elimtiyaz.dz" to "demo1234"
            "manager"   -> "manager@elimtiyaz.dz" to "demo1234"
            "buyer"     -> "buyer@elimtiyaz.dz" to "demo1234"
            "driver"    -> "driver@elimtiyaz.dz" to "demo1234"
            "warehouse" -> "warehouse@elimtiyaz.dz" to "demo1234"
            "worker"    -> "worker@elimtiyaz.dz" to "demo1234"
            else        -> "" to ""
        }
        _uiState.value = _uiState.value.copy(email = email, password = password, error = null)
    }
}
