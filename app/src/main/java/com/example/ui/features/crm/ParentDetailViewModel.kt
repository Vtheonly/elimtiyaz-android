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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ParentDetailViewModel @Inject constructor(
    private val parentRepository: ParentRepository,
    private val studentRepository: StudentRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    private val _parent = MutableStateFlow<Parent?>(null)
    val parent: StateFlow<Parent?> = _parent.asStateFlow()

    private val _children = MutableStateFlow<List<Student>>(emptyList())
    val children: StateFlow<List<Student>> = _children.asStateFlow()

    private val _summary = MutableStateFlow<ParentLedgerSummary?>(null)
    val summary: StateFlow<ParentLedgerSummary?> = _summary.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(parentId: String) {
        viewModelScope.launch {
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
        viewModelScope.launch {
            studentRepository.observeByParent(parentId).collect { kids ->
                _children.value = kids
            }
        }
    }
}
