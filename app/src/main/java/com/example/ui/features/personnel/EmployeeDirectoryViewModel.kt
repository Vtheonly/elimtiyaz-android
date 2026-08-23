package com.example.ui.features.personnel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Permission
import com.example.core.Result
import com.example.core.Role
import com.example.domain.model.Department
import com.example.domain.model.Personnel
import com.example.domain.repository.CreatePersonnelInput
import com.example.domain.repository.DepartmentRepository
import com.example.domain.repository.PersonnelRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ── 1. Personnel Directory ──────────────────────────────────────────────────

@HiltViewModel
class EmployeeDirectoryViewModel @Inject constructor(
    private val personnelRepository: PersonnelRepository,
    private val departmentRepository: DepartmentRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    val personnel: StateFlow<List<Personnel>> = personnelRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Departments for the create-employee form (optional assignment). */
    val departments: StateFlow<List<Department>> = departmentRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Whether the current session may manage personnel (RBAC: MANAGE_PERSONNEL). */
    val canManage: Boolean
        get() = sessionManager.current()?.can(Permission.MANAGE_PERSONNEL) == true ||
            sessionManager.current()?.role in listOf(Role.SUPER_ADMIN, Role.MANAGER)

    /** UI entry for [PersonnelRepository.createPersonnel] (previously repo-only). */
    fun createPersonnel(input: CreatePersonnelInput) {
        viewModelScope.launch {
            _busy.value = true
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = personnelRepository.createPersonnel(input, actorId, actorName)) {
                is Result.Ok -> _message.value = "Employé ajouté : ${result.value.fullName}"
                is Result.Err -> _error.value = result.error.userMessage
            }
            _busy.value = false
        }
    }

    fun clearMessages() {
        _error.value = null
        _message.value = null
    }
}
