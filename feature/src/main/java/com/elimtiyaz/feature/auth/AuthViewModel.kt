package com.elimtiyaz.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Role
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Auth view-model. Exposes [session] (so the UI can react to login/logout) and
 * [uiState] (form + transient flags for the login screen).
 *
 * On a successful sign-in the UI inspects the session's role and either navigates
 * to the root hub (staff roles) or to the Web Portal Redirect screen
 * (parent / student).
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun emailChanged(v: String) = _uiState.update { it.copy(email = v, error = null) }
    fun passwordChanged(v: String) = _uiState.update { it.copy(password = v, error = null) }
    fun rememberChanged(v: Boolean) = _uiState.update { it.copy(remember = v) }

    fun signIn(onSuccess: (Session) -> Unit) {
        val s = _uiState.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _uiState.update { it.copy(error = "Veuillez remplir tous les champs.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val r = auth.signIn(s.email.trim(), s.password)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, error = null) }
                    onSuccess(r.data)
                }
                is Result.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = r.error.userMessage)
                }
            }
        }
    }

    fun activateAccount(email: String, otp: String, newPassword: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val r = auth.activateAccount(email, otp, newPassword)) {
                is Result.Success -> onResult(true, null)
                is Result.Failure -> onResult(false, r.error.userMessage)
            }
        }
    }

    fun requestPasswordReset(email: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val r = auth.requestPasswordReset(email)) {
                is Result.Success -> onResult(true, null)
                is Result.Failure -> onResult(false, r.error.userMessage)
            }
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            auth.signOut()
            onDone()
        }
    }
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val remember: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
)
