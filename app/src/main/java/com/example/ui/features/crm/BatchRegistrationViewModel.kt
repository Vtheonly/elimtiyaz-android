package com.example.ui.features.crm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.domain.repository.CreateParentInput
import com.example.domain.repository.CreateStudentInput
import com.example.domain.repository.StudentRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BatchRegistrationViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _activationCode = MutableStateFlow<String?>(null)
    val activationCode: StateFlow<String?> = _activationCode.asStateFlow()

    fun register(parent: CreateParentInput, students: List<CreateStudentInput>, onSuccess: () -> Unit) {
        // A parent is valid when EITHER (firstName + lastName) is non-blank OR
        // displayName is non-blank. The importer path stores the full NOM
        // column as `displayName` with empty firstName (migration 0027), so
        // a parent with only `displayName` set is a legitimate record that
        // must pass validation. The previous check rejected these.
        val hasName = parent.displayName?.isNotBlank() == true ||
            (parent.firstName.isNotBlank() && parent.lastName.isNotBlank())
        if (!hasName || parent.phone.isBlank()) {
            _error.value = "Veuillez renseigner le nom complet (ou prénom + nom) et téléphone du parent"
            return
        }
        if (students.isEmpty()) {
            _error.value = "Au moins un élève est requis"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = studentRepository.batchRegister(parent, students, actorId, actorName)) {
                is Result.Ok -> {
                    _isLoading.value = false
                    _activationCode.value = result.value.activationCode
                    onSuccess()
                }
                is Result.Err -> {
                    _isLoading.value = false
                    _error.value = result.error.userMessage
                }
            }
        }
    }
}
