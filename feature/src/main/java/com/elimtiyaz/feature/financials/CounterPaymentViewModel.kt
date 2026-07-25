package com.elimtiyaz.feature.financials

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.PaymentMethod
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.CreatePaymentInput
import com.elimtiyaz.domain.model.Installment
import com.elimtiyaz.domain.model.Parent
import com.elimtiyaz.domain.model.Payment
import com.elimtiyaz.domain.model.PaymentCategory
import com.elimtiyaz.domain.model.Receipt
import com.elimtiyaz.domain.model.Student
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.InstallmentRepository
import com.elimtiyaz.domain.repository.PaymentRepository
import com.elimtiyaz.domain.repository.ParentRepository
import com.elimtiyaz.domain.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CounterPaymentViewModel — powers Route.CounterPayment.
 *
 * Form flow:
 *  1. The agent types into the parent picker; the VM debounces and queries
 *     [ParentRepository.search].
 *  2. Once a parent is selected, the VM loads its children via
 *     [StudentRepository.studentsByParent] and its installments via
 *     [InstallmentRepository.installmentsByParent].
 *  3. The agent picks a category (Tuition / Transport / …); the VM auto-suggests
 *     the oldest unpaid installment matching that category (master plan §07.03).
 *  4. The agent fills amount, method, optional proof (camera), optional notes.
 *     Proof is **required** for Check and Transfer per master plan §18.03.
 *  5. On submit the VM calls [PaymentRepository.collect]; on success it
 *     automatically calls [PaymentRepository.generateReceipt] and exposes the
 *     receipt so the screen can show a preview + share button.
 *
 * The screen may also be opened pre-filled from the Installments schedule
 * (via nav args `parentId`, `studentId`, `installmentId`, `category`, `amount`)
 * — see [SavedStateHandle] keys below.
 */
@HiltViewModel
class CounterPaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val parents: ParentRepository,
    private val students: StudentRepository,
    private val installments: InstallmentRepository,
    private val payments: PaymentRepository,
    auth: AuthRepository,
) : ViewModel() {

    /** Latest session — collectedBy is read from it on submit. */
    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    /** Optional pre-fill from the Installments screen ("Encaisser" row action). */
    private val prefillParentId: String? = savedStateHandle.get<String>("parentId")
    private val prefillStudentId: String? = savedStateHandle.get<String>("studentId")
    private val prefillInstallmentId: String? = savedStateHandle.get<String>("installmentId")
    private val prefillCategory: String? = savedStateHandle.get<String>("category")
    private val prefillAmount: String? = savedStateHandle.get<String>("amount")

    private val _state = MutableStateFlow(CounterPaymentUiState())
    val state: StateFlow<CounterPaymentUiState> = _state.asStateFlow()

    init {
        // Apply pre-fill
        prefillCategory?.let { cat ->
            PaymentCategory.values().firstOrNull { it.name.equals(cat, ignoreCase = true) }
                ?.let { _state.update { s -> s.copy(category = it) } }
        }
        prefillAmount?.toDoubleOrNull()?.let { amt ->
            _state.update { it.copy(amount = amt.toString()) }
        }
        prefillParentId?.let { pid -> loadParentAndStudents(pid) }
    }

    /** Debounced parent search; called as the user types in the picker. */
    fun searchParents(query: String) {
        _state.update { it.copy(parentQuery = query) }
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            parents.search(query).collect { result ->
                when (result) {
                    is Result.Success -> _state.update { it.copy(searchResults = result.data) }
                    is Result.Failure -> _state.update { it.copy(searchResults = emptyList()) }
                }
            }
        }
    }

    /** Select a parent from the picker dropdown. Loads students + installments. */
    fun selectParent(parent: Parent) {
        _state.update {
            it.copy(
                selectedParent = parent,
                parentQuery = "${parent.firstName} ${parent.lastName}",
                searchResults = emptyList(),
                selectedStudent = null,
                installments = emptyList(),
            )
        }
        loadParentAndStudents(parent.id)
    }

    /** Load students + installments + financial profile for [parentId]. */
    private fun loadParentAndStudents(parentId: String) {
        viewModelScope.launch {
            students.studentsByParent(parentId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val list = result.data
                        val pre = prefillStudentId?.let { id -> list.firstOrNull { it.id == id } }
                        _state.update { it.copy(students = list, selectedStudent = pre ?: list.firstOrNull()) }
                    }
                    is Result.Failure -> _state.update { it.copy(students = emptyList()) }
                }
            }
        }
        viewModelScope.launch {
            installments.installmentsByParent(parentId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val list = result.data
                        _state.update { it.copy(installments = list) }
                        applyInstallmentSuggestion(it = _state.value, installments = list)
                    }
                    is Result.Failure -> _state.update { it.copy(installments = emptyList()) }
                }
            }
        }
        // Resolve the parent object if we only had an id (pre-fill case).
        if (_state.value.selectedParent == null) {
            viewModelScope.launch {
                parents.parent(parentId).collect { result ->
                    when (result) {
                        is Result.Success -> _state.update {
                            it.copy(selectedParent = result.data, parentQuery = "${result.data.firstName} ${result.data.lastName}")
                        }
                        is Result.Failure -> Unit
                    }
                }
            }
        }
    }

    /** Select a student from the picker. */
    fun selectStudent(student: Student?) {
        _state.update { it.copy(selectedStudent = student) }
    }

    /** Pick a payment category; recomputes installment suggestion. */
    fun selectCategory(category: PaymentCategory) {
        _state.update { it.copy(category = category) }
        applyInstallmentSuggestion(_state.value, _state.value.installments)
    }

    /** Pick an installment manually (overrides the auto-suggestion). */
    fun selectInstallment(installment: Installment?) {
        _state.update { it.copy(selectedInstallment = installment) }
    }

    fun amountChanged(v: String) {
        // Accept digits + decimal separator only
        val filtered = v.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
        _state.update { it.copy(amount = filtered) }
    }

    fun methodChanged(method: PaymentMethod) {
        _state.update { it.copy(method = method) }
    }

    fun notesChanged(v: String) {
        _state.update { it.copy(notes = v) }
    }

    /** Set the proof URI captured by the camera. */
    fun setProofUri(uri: String?) {
        _state.update { it.copy(proofUri = uri) }
    }

    /**
     * Validate + submit. Returns true on success; the screen then shows the
     * receipt preview. On failure the screen reads [CounterPaymentUiState.error].
     */
    fun submit(onSuccess: (Receipt) -> Unit) {
        val s = _state.value
        // Validate
        val parent = s.selectedParent
        if (parent == null) { _state.update { it.copy(error = "Veuillez sélectionner un parent.") }; return }
        val amount = s.amount.toDoubleOrNull()
        if (amount == null || amount <= 0.0) { _state.update { it.copy(error = "Montant invalide.") }; return }
        if (s.method != PaymentMethod.Cash && s.proofUri.isNullOrBlank()) {
            _state.update { it.copy(error = "Justificatif obligatoire pour ${s.method.displayFr}.") }
            return
        }
        // If pre-fill installment was provided, honour it.
        val installmentId = s.selectedInstallment?.id ?: prefillInstallmentId

        _state.update { it.copy(isSubmitting = true, error = null) }
        val actorId = session.value?.userId.orEmpty()
        viewModelScope.launch {
            val input = CreatePaymentInput(
                parentId = parent.id,
                studentId = s.selectedStudent?.id,
                amount = amount,
                method = s.method.key,
                category = s.category,
                installmentId = installmentId,
                proofUrl = s.proofUri,
                notes = s.notes.ifBlank { null },
            )
            val collected = when (val r = payments.collect(input, collectedBy = actorId)) {
                is Result.Success -> r.data
                is Result.Failure -> {
                    _state.update { it.copy(isSubmitting = false, error = r.error.userMessage) }
                    return@launch
                }
            }
            // Mark the installment paid if applicable
            if (installmentId != null) {
                installments.markPaid(installmentId, collected.id)
            }
            // Generate receipt
            val receipt: Receipt = when (val r = payments.generateReceipt(collected.id, actorId)) {
                is Result.Success -> r.data
                is Result.Failure -> {
                    // Payment succeeded but receipt generation failed — show the
                    // preview with a soft warning so the agent can still share
                    // the receipt number and try regenerating later.
                    Receipt(
                        id = "",
                        paymentId = collected.id,
                        receiptNumber = collected.receiptNumber,
                        pdfUrl = "",
                        generatedAt = Formatters.nowIso(),
                        generatedBy = actorId,
                    )
                }
            }
            val warning = if (receipt.pdfUrl.isBlank())
                "Paiement enregistré. Reçu non généré — réessayez plus tard."
            else null
            _state.update {
                it.copy(isSubmitting = false, payment = collected, receipt = receipt, error = warning)
            }
            onSuccess(receipt)
        }
    }

    /** Reset the form to its initial state (used after a successful submit). */
    fun resetForm() {
        _state.update { CounterPaymentUiState() }
    }

    /** Clear the visible error. */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Auto-suggest the oldest unpaid/partial installment matching the selected
     * category — but never overrides an explicit pre-fill (master plan §07.03)
     * or a manual selection.
     */
    private fun applyInstallmentSuggestion(it: CounterPaymentUiState, installments: List<Installment>) {
        // Don't override an explicit pre-fill or manual selection.
        if (prefillInstallmentId != null) {
            val pre = installments.firstOrNull { inst -> inst.id == prefillInstallmentId }
            _state.update { st -> st.copy(selectedInstallment = pre) }
            return
        }
        val candidates = installments
            .filter { inst -> inst.category == it.category && inst.status != "paid" }
            .sortedBy { inst -> inst.dueDate }
        val suggested = candidates.firstOrNull()
        // Only update if the user hasn't manually picked one.
        if (it.selectedInstallment == null || it.selectedInstallment?.status == "paid") {
            _state.update { st -> st.copy(selectedInstallment = suggested) }
        }
    }
}

/** Form + transient state for the Counter Payment screen. */
data class CounterPaymentUiState(
    val parentQuery: String = "",
    val searchResults: List<Parent> = emptyList(),
    val selectedParent: Parent? = null,
    val students: List<Student> = emptyList(),
    val selectedStudent: Student? = null,
    val installments: List<Installment> = emptyList(),
    val selectedInstallment: Installment? = null,
    val amount: String = "",
    val method: PaymentMethod = PaymentMethod.Cash,
    val category: PaymentCategory = PaymentCategory.Tuition,
    val proofUri: String? = null,
    val notes: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val payment: Payment? = null,
    val receipt: Receipt? = null,
) {
    /** True when the proof attachment is required (Check or Transfer). */
    val proofRequired: Boolean get() = method != PaymentMethod.Cash

    /** True when the form is ready to submit. */
    val canSubmit: Boolean
        get() = selectedParent != null &&
            (amount.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (!proofRequired || !proofUri.isNullOrBlank())
}
