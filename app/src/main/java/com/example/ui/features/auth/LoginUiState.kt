package com.example.ui.features.auth

data class LoginUiState(
    val email: String = "admin@elimtiyaz.dz",
    val password: String = "demo1234",
    val isLoading: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
)