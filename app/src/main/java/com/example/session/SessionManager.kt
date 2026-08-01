package com.example.session

import com.example.core.Permission
import com.example.core.Role
import com.example.core.Session
import com.example.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session manager — holds the current session as a StateFlow.
 *
 * The session is loaded at app start (via [restoreSession]) and updated
 * whenever sign-in / sign-out / refresh succeeds. The UI observes
 * [state] to drive navigation (auth gate) and RBAC checks.
 *
 * JWT persistence is handled by the Supabase Auth plugin internally
 * (EncryptedSharedPreferences configured in [com.example.di.SupabaseModule]).
 * This class is a thin wrapper that exposes the session as a flow.
 */
@Singleton
class SessionManager @Inject constructor(
    private val authRepository: AuthRepository,
) {
    private val _state = MutableStateFlow<Session?>(null)
    val state: StateFlow<Session?> = _state.asStateFlow()

    /** Current session value, or null if not signed in. */
    fun current(): Session? = _state.value

    /** Update the session (called by AuthRepository after sign-in). */
    fun setSession(session: Session?) {
        _state.value = session
    }

    /**
     * Restore the session at app start. Called from the splash gate.
     *
     * If the JWT is still valid, the Supabase Auth plugin will have
     * restored it from encrypted storage; we just need to re-derive
     * the Session value from the current user.
     *
     * BUGFIX (iter 2): previously this method returned the result without
     * updating [_state], so the app always cold-started at the Login screen
     * even when a valid session existed. Now we propagate the restored
     * session via [setSession] so the auth gate can route to Main.
     */
    suspend fun restoreSession(): com.example.core.Result<Session?> {
        val result = authRepository.refreshSession()
        if (result is com.example.core.Result.Ok && result.value != null) {
            setSession(result.value)
        }
        return result
    }

    /** Check if the current session has the given permission. */
    fun can(permission: Permission): Boolean = _state.value?.can(permission) ?: false

    /** Check if the current session has the given role. */
    fun hasRole(role: Role): Boolean = _state.value?.hasRole(role) ?: false

    /** Current tenant ID, or null if not signed in. */
    fun currentTenantId(): String? = _state.value?.tenantId

    /** Current user ID, or null if not signed in. */
    fun currentUserId(): String? = _state.value?.userId

    /** Current user display name, or null if not signed in. */
    fun currentDisplayName(): String? = _state.value?.displayName
}
