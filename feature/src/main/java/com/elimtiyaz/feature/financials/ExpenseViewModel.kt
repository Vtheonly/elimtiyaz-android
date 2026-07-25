package com.elimtiyaz.feature.financials

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.CreateExpenseInput
import com.elimtiyaz.domain.model.Expense
import com.elimtiyaz.domain.model.ExpenseCategory
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.ExpenseRepository
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
 * ExpenseViewModel — shared by Route.ExpenseDetail and Route.ExpenseSubmit.
 *
 * Powers the two-tier expense workflow (master plan §08):
 *  - Submit (anyone with [Permission.SubmitExpense])
 *  - Approve / Reject ([Permission.ApproveExpense])
 *  - Disburse ([Permission.DisburseExpense])
 *  - Settle proof via camera capture ([Permission.SettleExpenseProof])
 *
 * The list of all expenses is exposed via [listState] (used by the Financials
 * hub's Dépenses tab indirectly; this VM also feeds the detail screen).
 */
@HiltViewModel
class ExpenseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val expenses: ExpenseRepository,
    auth: AuthRepository,
) : ViewModel() {

    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val expenseId: String? = savedStateHandle.get<String>("expenseId")

    private val _listState = MutableStateFlow(ExpenseListUiState())
    val listState: StateFlow<ExpenseListUiState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow(ExpenseDetailUiState())
    val detailState: StateFlow<ExpenseDetailUiState> = _detailState.asStateFlow()

    private val _submitState = MutableStateFlow(ExpenseSubmitUiState())
    val submitState: StateFlow<ExpenseSubmitUiState> = _submitState.asStateFlow()

    init {
        loadList()
        expenseId?.let { loadDetail(it) }
    }

    /** Load the full expense list (the hub Dépenses tab filters client-side). */
    fun loadList() {
        _listState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            expenses.expenses().collect { result ->
                when (result) {
                    is Result.Success -> _listState.update {
                        it.copy(isLoading = false, items = result.data.sortedByDescending { e -> e.submittedAt })
                    }
                    is Result.Failure -> _listState.update {
                        it.copy(isLoading = false, error = result.error, items = emptyList())
                    }
                }
            }
        }
    }

    /** Load a single expense by id. */
    fun loadDetail(id: String) {
        _detailState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            expenses.expense(id).collect { result ->
                when (result) {
                    is Result.Success -> _detailState.update { it.copy(isLoading = false, expense = result.data) }
                    is Result.Failure -> _detailState.update { it.copy(isLoading = false, error = result.error, expense = null) }
                }
            }
        }
    }

    // -------- Form fields for the Submit screen --------
    fun titleChanged(v: String)       = _submitState.update { it.copy(title = v) }
    fun descriptionChanged(v: String) = _submitState.update { it.copy(description = v) }
    fun amountChanged(v: String) {
        val filtered = v.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
        _submitState.update { it.copy(amount = filtered) }
    }
    fun categoryChanged(c: ExpenseCategory) = _submitState.update { it.copy(category = c) }
    fun payeeChanged(v: String)      = _submitState.update { it.copy(payee = v) }
    fun clearSubmitError()           = _submitState.update { it.copy(error = null) }

    /**
     * Submit a new expense request. UI gates this on [Permission.SubmitExpense].
     * Returns true on success.
     */
    fun submit(onResult: (Boolean, String?) -> Unit) {
        val s = _submitState.value
        if (s.title.isBlank()) { _submitState.update { it.copy(error = "Titre requis.") }; return }
        if (s.payee.isBlank()) { _submitState.update { it.copy(error = "Bénéficiaire requis.") }; return }
        val amount = s.amount.toDoubleOrNull()
        if (amount == null || amount <= 0.0) { _submitState.update { it.copy(error = "Montant invalide.") }; return }
        val actorId = session.value?.userId.orEmpty()
        _submitState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val input = CreateExpenseInput(
                title = s.title.trim(),
                description = s.description.trim(),
                amount = amount,
                category = s.category,
                payee = s.payee.trim(),
            )
            when (val r = expenses.submit(input, submittedBy = actorId)) {
                is Result.Success -> {
                    _submitState.update { it.copy(isSubmitting = false) }
                    onResult(true, r.data.id)
                }
                is Result.Failure -> {
                    _submitState.update { it.copy(isSubmitting = false, error = r.error.userMessage) }
                    onResult(false, null)
                }
            }
        }
    }

    /**
     * Approve an expense (Submitted → Approved). UI gates this on
     * [Permission.ApproveExpense]. The optional note is audited.
     */
    fun approve(id: String, note: String?, onResult: (Boolean, String?) -> Unit) {
        val actorId = session.value?.userId.orEmpty()
        viewModelScope.launch {
            when (val r = expenses.approve(id, approver = actorId, note = note)) {
                is Result.Success -> { _detailState.update { it.copy(expense = r.data) }; onResult(true, null) }
                is Result.Failure -> onResult(false, r.error.userMessage)
            }
        }
    }

    /** Reject an expense — note is mandatory (UI enforces). */
    fun reject(id: String, note: String, onResult: (Boolean, String?) -> Unit) {
        val actorId = session.value?.userId.orEmpty()
        viewModelScope.launch {
            when (val r = expenses.reject(id, approver = actorId, note = note)) {
                is Result.Success -> { _detailState.update { it.copy(expense = r.data) }; onResult(true, null) }
                is Result.Failure -> onResult(false, r.error.userMessage)
            }
        }
    }

    /** Disburse an approved expense (Approved → Disbursed). */
    fun disburse(id: String, onResult: (Boolean, String?) -> Unit) {
        val actorId = session.value?.userId.orEmpty()
        viewModelScope.launch {
            when (val r = expenses.disburse(id, disbursedBy = actorId)) {
                is Result.Success -> { _detailState.update { it.copy(expense = r.data) }; onResult(true, null) }
                is Result.Failure -> onResult(false, r.error.userMessage)
            }
        }
    }

    /**
     * Settle the proof of a disbursed expense (Disbursed → Settled). The
     * [proofUrl] is captured via camera per master plan §18.03 and uploaded
     * by the repository.
     */
    fun settleProof(id: String, proofUrl: String, onResult: (Boolean, String?) -> Unit) {
        val actorId = session.value?.userId.orEmpty()
        viewModelScope.launch {
            when (val r = expenses.settleProof(id, proofUrl, uploadedBy = actorId)) {
                is Result.Success -> { _detailState.update { it.copy(expense = r.data) }; onResult(true, null) }
                is Result.Failure -> onResult(false, r.error.userMessage)
            }
        }
    }
}

/** Expense list state (used by the Financials hub Dépenses tab via direct VM injection). */
data class ExpenseListUiState(
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val items: List<Expense> = emptyList(),
)

/** Expense detail screen state. */
data class ExpenseDetailUiState(
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val expense: Expense? = null,
)

/** Expense submit form state. */
data class ExpenseSubmitUiState(
    val title: String = "",
    val description: String = "",
    val amount: String = "",
    val category: ExpenseCategory = ExpenseCategory.Supplies,
    val payee: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = title.isNotBlank() && payee.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0.0
}
