package com.elimtiyaz.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.AuditEntry
import com.elimtiyaz.domain.repository.AuditRepository
import com.elimtiyaz.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View-model backing [ProfileScreen]. Owns:
 *  - the current [Session] (display name, email, role, tenant, expiry)
 *  - the list of permissions the user holds (rendered as chips)
 *  - the 10 most recent [AuditEntry] rows authored by this user
 *  - the sign-out action (with confirmation handled by the screen)
 *
 * The audit list is filtered client-side by `actorId == session.userId` so we
 * don't need a dedicated repository method. The list is re-emitted whenever
 * the underlying `AuditRepository.recent()` flow updates.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val audit: AuditRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /** Actor id for which we currently have an active audit collector. */
    private var observedActorId: String? = null

    init {
        // Session + offline flag
        viewModelScope.launch {
            auth.session.collect { session ->
                _uiState.update {
                    it.copy(session = session, isLoading = false)
                }
                if (session != null && observedActorId != session.userId) {
                    // Only (re)start the audit collector when the actor actually
                    // changes — avoids stacking collectors on every session re-emit.
                    observedActorId = session.userId
                    loadRecentActivity(session.userId)
                }
            }
        }
    }

    /**
     * Subscribe to the recent-audit flow and keep only entries authored by
     * [actorId]. Called once per session — if the user re-logs-in, the new
     * session will trigger another call with the new id.
     */
    private fun loadRecentActivity(actorId: String) {
        viewModelScope.launch {
            audit.recent(limit = 100).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val mine = result.data
                            .filter { it.actorId == actorId }
                            .sortedByDescending { it.at }
                            .take(10)
                        _uiState.update {
                            it.copy(recentActivity = mine, activityError = null)
                        }
                    }
                    is Result.Failure -> _uiState.update {
                        it.copy(recentActivity = emptyList(), activityError = result.error)
                    }
                }
            }
        }
    }

    /**
     * Sign out the current user. Calls [AuthRepository.signOut] and notifies
     * the caller via [onDone] when the operation completes (success or
     * failure). The screen uses this callback to navigate to the login route.
     */
    fun signOut(onDone: () -> Unit) {
        if (_uiState.value.isSigningOut) return
        _uiState.update { it.copy(isSigningOut = true) }
        viewModelScope.launch {
            auth.signOut()
            _uiState.update { it.copy(isSigningOut = false) }
            onDone()
        }
    }

    /** Clear the transient snackbar message. */
    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbar = null) }
    }
}

/**
 * Immutable state for the profile screen.
 *
 * - [session] drives the header card, role badge, and permission chips.
 * - [recentActivity] is the 10 most-recent audit entries authored by the
 *   current user; it may be empty even when the session is valid (new account).
 * - [activityError] is reported separately so a failure to load audit does not
 *   blank out the header — the user can still see and edit their profile.
 */
data class ProfileUiState(
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val session: Session? = null,
    val recentActivity: List<AuditEntry> = emptyList(),
    val activityError: AppError? = null,
    val isSigningOut: Boolean = false,
    val snackbar: String? = null,
) {
    /** Total number of permission tokens granted to the user's role. */
    val permissionCount: Int
        get() = session?.permissions?.size ?: 0

    /** Total number of permission tokens defined by the platform (the "Y"). */
    val permissionTotal: Int
        get() = Permission.values().size

    /** True when the session is missing or expired. */
    val isUnauthenticated: Boolean
        get() = session == null || session.isExpired

    /** French label for the user's role badge. */
    val roleLabel: String
        get() = session?.role?.displayFr ?: "—"

    /** Initials for the avatar circle, derived from the display name. */
    val avatarInitial: String
        get() {
            val name = session?.displayName.orEmpty()
            val parts = name.trim().split(" ")
            val first = parts.firstOrNull()?.firstOrNull()?.toString() ?: ""
            val last = parts.getOrNull(1)?.firstOrNull()?.toString() ?: ""
            return (first + last).uppercase().ifEmpty { "?" }
        }
}
