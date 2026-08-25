package com.example.ui.features.academics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Permission
import com.example.core.Role
import com.example.domain.model.AcademicClass
import com.example.domain.repository.ClassRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

// ── 4. Classes Directory ──────────────────────────────────────────────────

/**
 * Vault §06.04 — the blind `promoteClass` entry point (promote every ACTIVE
 * student regardless of GPA) was REMOVED. Batch promotion now goes through
 * [PromotionReviewViewModel] (Steps 1–4: yearly GPAs → auto-flag → admin
 * review queue with overrides → one-click canonical execution). The
 * repository-level `promoteStudents` (canonical ladder + audit + sync) is
 * unchanged.
 */
@HiltViewModel
class ClassesDirectoryViewModel @Inject constructor(
    private val classRepository: ClassRepository,
    sessionManager: SessionManager,
) : ViewModel() {
    val classes: StateFlow<List<AcademicClass>> = classRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** RBAC gate — only PROMOTE_STUDENT holders may open the promotion queue. */
    val canPromote: Boolean
        get() = sessionManager.current()?.can(Permission.PROMOTE_STUDENT) == true ||
            sessionManager.current()?.role in listOf(Role.SUPER_ADMIN, Role.MANAGER)

    fun clearMessages() {
        _error.value = null
        _message.value = null
    }
}
