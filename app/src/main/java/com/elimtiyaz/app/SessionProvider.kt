package com.elimtiyaz.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.elimtiyaz.core.rbac.LocalFeatureFlagProvider
import com.elimtiyaz.core.rbac.LocalSession
import com.elimtiyaz.core.rbac.NoOpFeatureFlagProvider
import com.elimtiyaz.domain.repository.AuthRepository
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.elimtiyaz.core.common.Session
import javax.inject.Inject

/**
 * Root-level session provider. Wraps the entire app's Compose tree in a
 * [CompositionLocalProvider] that installs the current [Session] (or NULL)
 * into [LocalSession]. This lets every screen below read the session via
 * `LocalSession.current` or via the [com.elimtiyaz.core.rbac.accessStateOf]
 * helper without each one having to inject [AuthRepository].
 *
 * The session is collected from [AuthRepository.session] as a [StateFlow] so
 * recomposition happens automatically on login / logout / role switch.
 *
 * Also installs [LocalFeatureFlagProvider] with [NoOpFeatureFlagProvider] —
 * replace this with a real provider (remote config / paid-plan check) when
 * non-RBAC feature flags are needed.
 */
@HiltViewModel
class SessionProviderViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {
    val session: StateFlow<Session?> = authRepository.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Wrap [content] with the session + feature-flag composition locals.
 * Place directly inside [ElimtiyazTheme] so the entire Compose tree has access.
 */
@Composable
fun SessionProvider(content: @Composable () -> Unit) {
    val vm: SessionProviderViewModel = hiltViewModel()
    val session by vm.session.collectAsState()
    val flags = remember { NoOpFeatureFlagProvider }
    CompositionLocalProvider(
        LocalSession provides session,
        LocalFeatureFlagProvider provides flags,
    ) {
        content()
    }
}
