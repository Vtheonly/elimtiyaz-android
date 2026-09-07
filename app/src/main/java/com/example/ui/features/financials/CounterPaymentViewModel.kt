package com.example.ui.features.financials

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.Result
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.model.Student
import com.example.domain.repository.CollectPaymentInput
import com.example.domain.repository.InstallmentRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.PaymentRepository
import com.example.domain.repository.StudentRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CounterPaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val paymentRepository: PaymentRepository,
    private val parentRepository: ParentRepository,
    private val studentRepository: StudentRepository,
    private val installmentRepository: InstallmentRepository,
    private val ledgerRepository: LedgerRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val routeParentId: String? = savedStateHandle["parentId"]
    private val routeStudentId: String? = savedStateHandle["studentId"]

    val parents: StateFlow<List<Parent>> = parentRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedParent = MutableStateFlow<Parent?>(null)
    val selectedParent: StateFlow<Parent?> = _selectedParent.asStateFlow()

    private val _students = MutableStateFlow<List<Student>>(emptyList())
    val students: StateFlow<List<Student>> = _students.asStateFlow()

    private val _selectedStudent = MutableStateFlow<Student?>(null)
    val selectedStudent: StateFlow<Student?> = _selectedStudent.asStateFlow()

    private val _parentOutstanding = MutableStateFlow(0L)
    val parentOutstanding: StateFlow<Long> = _parentOutstanding.asStateFlow()

    private val _installments = MutableStateFlow<List<Installment>>(emptyList())
    val installments: StateFlow<List<Installment>> = _installments.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _receiptNumber = MutableStateFlow<String?>(null)
    val receiptNumber: StateFlow<String?> = _receiptNumber.asStateFlow()

    private var studentJob: Job? = null
    private var installmentJob: Job? = null
    private var ledgerJob: Job? = null

    init {
        if (!routeParentId.isNullOrBlank()) {
            initialize(routeParentId, routeStudentId)
        }
    }

    fun initialize(parentId: String?, studentId: String?) {
        if (parentId.isNullOrBlank()) return
        if (_selectedParent.value?.id == parentId) {
            if (!studentId.isNullOrBlank() && _selectedStudent.value?.id != studentId) {
                viewModelScope.launch {
                    val s = studentRepository.observeById(studentId).firstOrNull()
                    if (s != null) _selectedStudent.value = s
                }
            }
            return
        }
        viewModelScope.launch {
            val p = parentRepository.observeById(parentId).firstOrNull() ?: return@launch
            selectParent(p)
            if (!studentId.isNullOrBlank()) {
                val s = studentRepository.observeById(studentId).firstOrNull()
                if (s != null) _selectedStudent.value = s
            }
        }
    }

    fun selectParent(parent: Parent?) {
        studentJob?.cancel()
        installmentJob?.cancel()
        ledgerJob?.cancel()

        _selectedParent.value = parent
        _selectedStudent.value = null
        _receiptNumber.value = null
        _error.value = null

        if (parent == null) {
            _students.value = emptyList()
            _installments.value = emptyList()
            _parentOutstanding.value = 0L
            return
        }

        studentJob = viewModelScope.launch {
            studentRepository.observeByParent(parent.id).collect {
                _students.value = it
            }
        }
        installmentJob = viewModelScope.launch {
            installmentRepository.observeByParent(parent.id).collect {
                _installments.value = it
            }
        }
        ledgerJob = viewModelScope.launch {
            when (val res = ledgerRepository.summary(parent.id)) {
                is Result.Ok -> _parentOutstanding.value = res.value.totalOutstanding.coerceAtLeast(0L)
                is Result.Err -> _parentOutstanding.value = 0L
            }
        }
    }

    fun selectStudent(student: Student?) {
        _selectedStudent.value = student
    }

    fun collect(input: CollectPaymentInput, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = paymentRepository.collect(input, actorId, actorName)) {
                is Result.Ok -> {
                    _isLoading.value = false
                    _receiptNumber.value = result.value.receiptNumber
                    _selectedParent.value?.let { selectParent(it) }
                    onResult(result.value.receiptNumber)
                }
                is Result.Err -> {
                    _isLoading.value = false
                    _error.value = result.error.userMessage
                    onResult(null)
                }
            }
        }
    }
}
