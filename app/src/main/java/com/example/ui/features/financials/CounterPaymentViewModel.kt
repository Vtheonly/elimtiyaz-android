package com.example.ui.features.financials

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.Result
import com.example.core.AllocationResult
import com.example.core.WaterfallInstallment
import com.example.core.allocatePaymentToInstallments
import com.example.core.PaymentStatus
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

    /** T-321 (UI-311): dismissible error banner support. */
    fun clearError() {
        _error.value = null
    }

    /**
     * T-321 (UI-311) — engine-derived allocation preview for the payment
     * form. Uses the SAME canonical client engine (`allocatePaymentToInstallments`)
     * with the SAME inputs the repository's `collect` will use: ALL family
     * installments, the selected category filter, and PAID for CASH vs
     * PENDING for CHECK/TRANSFER. Never re-derived in the UI.
     */
    fun computeAllocationPreview(
        amountCents: Long,
        category: PaymentCategory,
        method: PaymentMethod,
    ): AllocationResult? {
        val parent = _selectedParent.value ?: return null
        if (amountCents <= 0L) return null
        val familyInstallments = _installments.value.map {
            // The engine compares lowercase status codes ("paid"/"overdue"/…)
            // — map the domain enum to its canonical code, matching what the
            // repository feeds the engine from the entity column.
            WaterfallInstallment(
                id = it.id,
                category = it.category,
                amountDue = it.amountDue,
                amountPaid = it.amountPaid,
                amountPending = it.amountPending,
                dueDate = it.dueDate,
                status = it.status.code,
            )
        }
        return allocatePaymentToInstallments(
            installments = familyInstallments,
            paymentAmount = amountCents,
            categoryFilter = category,
            paymentStatus = if (method == PaymentMethod.CASH) PaymentStatus.PAID else PaymentStatus.PENDING,
        )
    }

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

        startFamilyJobs(parent.id)
    }

    /**
     * T-321 fix: `collect`'s success path used to call `selectParent(current)`
     * to refresh the outstanding/installments — but selectParent RESETS
     * `_receiptNumber`, so the success state was erased in the same frame and
     * the receipt never displayed. The refresh now runs WITHOUT touching the
     * result state (receipt/error/student selection).
     */
    private fun refreshFamilyData(parentId: String) {
        studentJob?.cancel()
        installmentJob?.cancel()
        ledgerJob?.cancel()
        startFamilyJobs(parentId)
    }

    private fun startFamilyJobs(parentId: String) {
        studentJob = viewModelScope.launch {
            studentRepository.observeByParent(parentId).collect {
                _students.value = it
            }
        }
        installmentJob = viewModelScope.launch {
            installmentRepository.observeByParent(parentId).collect {
                _installments.value = it
            }
        }
        ledgerJob = viewModelScope.launch {
            when (val res = ledgerRepository.summary(parentId)) {
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
                    _selectedParent.value?.let { refreshFamilyData(it.id) }
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
