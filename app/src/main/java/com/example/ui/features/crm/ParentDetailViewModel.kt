package com.example.ui.features.crm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ParentLedgerSummary
import com.example.core.PaymentCategory
import com.example.core.Permission
import com.example.core.Result
import com.example.domain.model.Parent
import com.example.domain.model.Student
import com.example.domain.repository.AdjustAccountInput
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.PaymentRepository
import com.example.domain.repository.PdfRepository
import com.example.domain.repository.StudentRepository
import com.example.domain.repository.UpdateParentInput
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
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
    private val paymentRepository: PaymentRepository,
    private val pdfRepository: PdfRepository,
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

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** One-shot share request: the freshly generated account-statement PDF. */
    private val _pdfFile = MutableStateFlow<File?>(null)
    val pdfFile: StateFlow<File?> = _pdfFile.asStateFlow()

    /** Whether the current session may adjust accounts (RBAC: ADJUST_ACCOUNT). */
    val canAdjust: Boolean
        get() = sessionManager.current()?.can(Permission.ADJUST_ACCOUNT) == true ||
            sessionManager.current()?.role in listOf(
                com.example.core.Role.SUPER_ADMIN,
                com.example.core.Role.FINANCIAL_OFFICER,
            )

    /** Whether the current session may export financial documents. */
    val canGenerateStatement: Boolean
        get() = sessionManager.current()?.can(Permission.VIEW_FINANCIALS) == true ||
            sessionManager.current()?.can(Permission.GENERATE_RECEIPT) == true

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
                    refreshSummary(parentId)
                }
            }
        }
        childrenJob = viewModelScope.launch {
            studentRepository.observeByParent(parentId).collect { kids ->
                _children.value = kids
            }
        }
    }

    /** Re-compute the ledger summary (called on load + after each mutation). */
    private fun refreshSummary(parentId: String) {
        viewModelScope.launch {
            when (val result = ledgerRepository.summary(parentId)) {
                is Result.Ok -> _summary.value = result.value
                is Result.Err -> _error.value = result.error.userMessage
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

    /**
     * Manual account adjustment — UI entry for
     * [PaymentRepository.adjust]. Signs follow the canonical engine
     * (core/Ledger.kt): POSITIVE = debit (penalty / late fee, kept on the
     * input category), NEGATIVE = credit (waiver / remise, auto-routed by
     * the repository to the parent-scoped `parent_credit` account).
     */
    fun adjustAccount(parentId: String, amountCentimes: Long, category: PaymentCategory, reason: String) {
        if (amountCentimes == 0L) {
            _error.value = "Le montant de l'ajustement doit être différent de zéro."
            return
        }
        if (reason.isBlank()) {
            _error.value = "Le motif de l'ajustement est obligatoire."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            val result = paymentRepository.adjust(
                AdjustAccountInput(
                    parentId = parentId,
                    studentId = null, // family-level adjustment
                    category = category,
                    amount = amountCentimes,
                    reason = reason,
                ),
                actorId,
                actorName,
            )
            _busy.value = false
            when (result) {
                is Result.Ok -> {
                    _saveMessage.value = "Ajustement appliqué."
                    refreshSummary(parentId)
                }
                is Result.Err -> _error.value = result.error.userMessage
            }
        }
    }

    /** Render the account-statement PDF and emit a share request. */
    fun generateStatementPdf(parentId: String) {
        viewModelScope.launch {
            _busy.value = true
            when (val result = pdfRepository.generateAccountStatement(parentId)) {
                is Result.Ok -> _pdfFile.value = result.value
                is Result.Err -> _error.value = result.error.userMessage
            }
            _busy.value = false
        }
    }

    /** Called by the UI once the share intent has been dispatched. */
    fun consumePdf() {
        _pdfFile.value = null
    }

    fun clearMessages() {
        _error.value = null
        _saveMessage.value = null
    }
}
