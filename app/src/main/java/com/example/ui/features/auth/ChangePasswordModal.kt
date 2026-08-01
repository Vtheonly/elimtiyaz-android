package com.example.ui.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.domain.repository.AuthRepository
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElDialog
import com.example.ui.components.ElTextField
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun changePassword(current: String, new: String, confirm: String) {
        if (current.isBlank()) { _uiState.value = _uiState.value.copy(error = "Mot de passe actuel requis"); return }
        if (new != confirm) { _uiState.value = _uiState.value.copy(error = "Les mots de passe ne correspondent pas"); return }

        val strength = passwordStrength(new)
        if (!strength.allMet) { _uiState.value = _uiState.value.copy(error = "Mot de passe trop faible", strength = strength); return }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = authRepository.changePassword(current, new)) {
                is Result.Ok -> _uiState.value = _uiState.value.copy(isLoading = false, success = true)
                is Result.Err -> _uiState.value = _uiState.value.copy(isLoading = false, error = result.error.userMessage)
            }
        }
    }
}

data class ChangePasswordUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val strength: PasswordStrength = PasswordStrength(),
)

data class PasswordStrength(
    val minLength: Boolean = false,
    val hasLower: Boolean = false,
    val hasUpper: Boolean = false,
    val hasDigit: Boolean = false,
) {
    val allMet: Boolean get() = minLength && hasLower && hasUpper && hasDigit
}

fun passwordStrength(pw: String): PasswordStrength = PasswordStrength(
    minLength = pw.length >= 8,
    hasLower = pw.any { it.isLowerCase() },
    hasUpper = pw.any { it.isUpperCase() },
    hasDigit = pw.any { it.isDigit() },
)

@Composable
fun ChangePasswordModal(
    onDismiss: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    LaunchedEffect(state.success) {
        if (state.success) onDismiss()
    }

    ElDialog(
        onDismiss = onDismiss,
        title = "Changer le mot de passe",
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ElTextField(
                    value = current, onValueChange = { current = it },
                    label = "Mot de passe actuel",
                    leadingIcon = Icons.Default.Lock,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ElTextField(
                    value = new, onValueChange = { new = it },
                    label = "Nouveau mot de passe",
                    leadingIcon = Icons.Default.Lock,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ElTextField(
                    value = confirm, onValueChange = { confirm = it },
                    label = "Confirmer",
                    leadingIcon = Icons.Default.Lock,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                val strength = passwordStrength(new)
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    StrengthRow("8 caractères minimum", strength.minLength)
                    StrengthRow("Une lettre minuscule", strength.hasLower)
                    StrengthRow("Une lettre majuscule", strength.hasUpper)
                    StrengthRow("Un chiffre", strength.hasDigit)
                }

                Text(
                    "⚠ Toutes les sessions seront révoquées sur tous vos appareils.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            ElButton(
                text = if (state.isLoading) "..." else "Changer",
                onClick = { viewModel.changePassword(current, new, confirm) },
                enabled = !state.isLoading,
                loading = state.isLoading,
            )
        },
        dismissButton = {
            ElButton(
                text = "Annuler",
                onClick = onDismiss,
                style = ElButtonStyle.Secondary,
            )
        },
    )
}

@Composable
private fun StrengthRow(label: String, met: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Icon(
            if (met) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.height(16.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}