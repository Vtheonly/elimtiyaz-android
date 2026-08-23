package com.example.ui.features.crm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ParentLedgerSummary
import com.example.core.Result
import com.example.domain.model.Parent
import com.example.domain.model.Student
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.StudentRepository
import com.example.domain.repository.UpdateParentInput
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ParentDetailViewModel @Inject constructor(
    private val parentRepository: ParentRepository,
    private val studentRepository: StudentRepository,
    private val ledgerRepository: LedgerRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _parent = MutableStateFlow<Parent?>(null)
    val parent: StateFlow<Parent?> = _parent.asStateFlow()

    private val _children = MutableStateFlow<List<Student>>(emptyList())
    val children: StateFlow<List<Student>> = _children.asStateFlow()

    private val _summary = MutableStateFlow<ParentLedgerSummary?>(null)
    val summary: StateFlow<ParentLedgerSummary?> = _summary.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    // FIX (coroutine leak): cancel previous collectors on re-load.
    private var parentJob: Job? = null
    private var childrenJob: Job? = null

    fun load(parentId: String) {
        parentJob?.cancel()
        childrenJob?.cancel()
        parentJob = viewModelScope.launch {
            parentRepository.observeById(parentId).collect { p ->
                _parent.value = p
                if (p != null) {
                    when (val result = ledgerRepository.summary(parentId)) {
                        is Result.Ok -> _summary.value = result.value
                        is Result.Err -> _error.value = result.error.userMessage
                    }
                }
            }
        }
        childrenJob = viewModelScope.launch {
            studentRepository.observeByParent(parentId).collect { kids ->
                _children.value = kids
            }
        }
    }

    /** FIX (missing edit feature): persist edits via updateParent. */
    fun updateParent(
        parentId: String,
        firstName: String,
        lastName: String,
        phone: String,
        email: String?,
        occupation: String?,
        address: String?,
    ) {
        viewModelScope.launch {
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            val result = parentRepository.updateParent(
                parentId,
                UpdateParentInput(
                    firstName = firstName.ifBlank { null },
                    lastName = lastName.ifBlank { null },
                    phone = phone.ifBlank { null },
                    email = email,
                    occupation = occupation,
                    address = address,
                ),
                actorId,
                actorName,
            )
            when (result) {
                is Result.Ok -> _saveMessage.value = "Parent mis à jour."
                is Result.Err -> _error.value = result.error.userMessage
            }
        }
    }

    fun clearMessages() {
        _error.value = null
        _saveMessage.value = null
    }
}
