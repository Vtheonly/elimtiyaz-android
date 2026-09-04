package com.example.ui.features.crm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ParentLedgerSummary
import com.example.core.PaymentCategory
import com.example.core.Permission
import com.example.core.Result
import com.example.core.BillingChildInfo
import com.example.core.BillingInstallmentRow
import com.example.core.LedgerEntry
import com.example.core.ParentBillingBreakdown
import com.example.core.PaymentStatus
import com.example.core.parentBillingBreakdown
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.model.Payment
import com.example.domain.model.Student
import com.example.domain.repository.AdjustAccountInput
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.CreateStudentInput
import com.example.domain.repository.InstallmentRepository
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ParentDetailViewModel @Inject constructor(
    private val parentRepository: ParentRepository,
    private val studentRepository: StudentRepository,
    private val ledgerRepository: LedgerRepository,
    private val paymentRepository: PaymentRepository,
    private val installmentRepository: InstallmentRepository,
    private val pdfRepository: PdfRepository,
    private val classRepository: ClassRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _parent = MutableStateFlow<Parent?>(null)
    val parent: StateFlow<Parent?> = _parent.asStateFlow()

    private val _children = MutableStateFlow<List<Student>>(emptyList())
    val children: StateFlow<List<Student>> = _children.asStateFlow()

    private val _summary = MutableStateFlow<ParentLedgerSummary?>(null)
    val summary: StateFlow<ParentLedgerSummary?> = _summary.asStateFlow()

    /** Vault §04.05 — itemized ledger of ALL historic payments by the parent. */
    private val _payments = MutableStateFlow<List<Payment>>(emptyList())
    val payments: StateFlow<List<Payment>> = _payments.asStateFlow()

    /** Vault §04.05 — installment schedules (upcoming + overdue tranches). */
    private val _installments = MutableStateFlow<List<Installment>>(emptyList())
    val installments: StateFlow<List<Installment>> = _installments.asStateFlow()

    /**
     * T-167 — the family's ledger charge entries, feeding the canonical
     * billing breakdown ("Prestations facturées") alongside the real
     * installment rows. Same derivation as the desktop parent-drawer
     * Finances tab and the website Facturation tab (core/BillingBreakdown.kt
     * mirrors domain/calc/payment/billing-breakdown.ts).
     */
    private val _ledgerEntries = MutableStateFlow<List<LedgerEntry>>(emptyList())
    val ledgerEntries: StateFlow<List<LedgerEntry>> = _ledgerEntries.asStateFlow()

    /** T-167 — canonical itemized billing breakdown (per child + per service). */
    private val _billingBreakdown = MutableStateFlow<ParentBillingBreakdown?>(null)
    val billingBreakdown: StateFlow<ParentBillingBreakdown?> = _billingBreakdown.asStateFlow()

    /** Whether the current session may add a child to an existing family. */
    val canAddChild: Boolean
        get() = sessionManager.current()?.can(Permission.CREATE_STUDENT) == true ||
            sessionManager.current()?.can(Permission.CREATE_PARENT) == true ||
            sessionManager.current()?.role in listOf(
                com.example.core.Role.SUPER_ADMIN,
                com.example.core.Role.MANAGER,
            )

    /** Vault §04.05 — class catalogue for the "Add Another Child" dialog. */
    val classes: StateFlow<List<com.example.domain.model.AcademicClass>> = classRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
    private var paymentsJob: Job? = null
    private var installmentsJob: Job? = null
    private var ledgerJob: Job? = null

    fun load(parentId: String) {
        parentJob?.cancel()
        childrenJob?.cancel()
        paymentsJob?.cancel()
        installmentsJob?.cancel()
        ledgerJob?.cancel()
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
                recomputeBilling()
            }
        }
        // Vault §04.05 — itemized payment ledger + installment schedules,
        // embedded INSIDE the parent drawer (never a separate top-level tab).
        paymentsJob = viewModelScope.launch {
            paymentRepository.observeByParent(parentId).collect {
                _payments.value = it
                recomputeBilling()
            }
        }
        installmentsJob = viewModelScope.launch {
            installmentRepository.observeByParent(parentId).collect {
                _installments.value = it
                recomputeBilling()
            }
        }
        ledgerJob = viewModelScope.launch {
            ledgerRepository.observeByParent(parentId).collect {
                _ledgerEntries.value = it
                recomputeBilling()
            }
        }
    }

    /**
     * T-167 — recompute the canonical billing breakdown from the current
     * streams (children / installments / payments / ledger). Pure derivation
     * (core/BillingBreakdown.kt): real installment rows are authoritative;
     * the 40/30/30 synthesis only fills display gaps for children without
     * physical tranche rows.
     */
    private fun recomputeBilling() {
        val kids = _children.value
        val installments = _installments.value
        val payments = _payments.value
        val ledger = _ledgerEntries.value
        if (kids.isEmpty()) {
            _billingBreakdown.value = null
            return
        }
        val clearedPaid = payments
            .filter { it.status == PaymentStatus.PAID }
            .sumOf { it.amount }
        val rows = installments.map { i ->
            BillingInstallmentRow(
                id = i.id,
                studentId = i.studentId,
                category = i.category,
                label = i.label,
                amountDue = i.amountDue,
                amountPaid = i.amountPaid,
                amountPending = i.amountPending,
                dueDate = i.dueDate,
                status = i.status.code,
            )
        }
        val children = kids.map { s ->
            BillingChildInfo(
                id = s.id,
                displayName = s.fullName,
                gradeLevelLabel = s.gradeLevel,
            )
        }
        _billingBreakdown.value = parentBillingBreakdown(
            ledgerEntries = ledger,
            installments = rows,
            clearedPaidTotal = clearedPaid,
            children = children,
            fallbackTotalDue = _summary.value?.totalCharged ?: 0L,
        )
    }

    /** Re-compute the ledger summary (called on load + after each mutation). */
    private fun refreshSummary(parentId: String) {
        viewModelScope.launch {
            when (val result = ledgerRepository.summary(parentId)) {
                is Result.Ok -> {
                    _summary.value = result.value
                    recomputeBilling()
                }
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
        secondaryPhone: String? = null,
        nationalId: String? = null,
        relationship: String? = null,
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
                    secondaryPhone = secondaryPhone,
                    nationalId = nationalId,
                    relationship = relationship,
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
     * Vault §04.05 / §04.01 — "Add Another Child" action embedded in the
     * Parent drawer. Uses the canonical [StudentRepository.createStudent]
     * which enforces the parent-first dependency (the parentId is mandatory
     * and passed explicitly — an orphan student can never be created).
     */
    fun addChild(
        parentId: String,
        firstName: String,
        lastName: String,
        birthDate: String,
        gender: String,
        gradeLevel: String,
        classId: String?,
    ) {
        if (firstName.isBlank() || birthDate.isBlank()) {
            _error.value = "Prénom et date de naissance sont requis."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            val result = studentRepository.createStudent(
                CreateStudentInput(
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    gender = gender.ifBlank { "unspecified" },
                    birthDate = birthDate.trim(),
                    level = com.example.core.academicLevelForGradeCode(gradeLevel),
                    gradeLevel = gradeLevel,
                    classId = classId,
                    parentId = parentId,
                ),
                actorId,
                actorName,
            )
            _busy.value = false
            when (result) {
                is Result.Ok -> _saveMessage.value = "${result.value.fullName} ajouté(e) à la famille."
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
