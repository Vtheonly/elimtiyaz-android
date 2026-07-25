package com.elimtiyaz.feature.crm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.domain.model.AccountAdjustment
import com.elimtiyaz.domain.model.Installment
import com.elimtiyaz.domain.model.Parent
import com.elimtiyaz.domain.model.ParentFinancialProfile
import com.elimtiyaz.domain.model.Payment
import com.elimtiyaz.domain.model.Student
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.DebtRepository
import com.elimtiyaz.domain.repository.PaymentRepository
import com.elimtiyaz.domain.repository.ParentRepository
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
 * ParentDetailViewModel — powers Route.ParentDetail.
 *
 * Loads a single parent plus its financial profile (recent payments, installments,
 * adjustments) and exposes mutate functions for the optional "Ajustement de compte"
 * bottom sheet (gated by [Permission.AdjustAccount]).
 *
 * The parent id is read from the navigation [SavedStateHandle] so the screen can
 * simply call `hiltViewModel()` without forwarding args.
 */
@HiltViewModel
class ParentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val parentRepo: ParentRepository,
    private val debtRepo: DebtRepository,
    private val paymentRepo: PaymentRepository,
    auth: AuthRepository,
) : ViewModel() {

    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val parentId: String = savedStateHandle.get<String>("parentId").orEmpty()

    private val _state = MutableStateFlow(ParentDetailUiState())
    val state: StateFlow<ParentDetailUiState> = _state.asStateFlow()

    init { reload() }

    /** Re-fetch parent + financial profile from repositories. */
    fun reload() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            parentRepo.parent(parentId).collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(isLoading = false, parent = result.data, error = null)
                    }
                    is Result.Failure -> _state.update {
                        it.copy(isLoading = false, error = result.error)
                    }
                }
            }
        }
        viewModelScope.launch {
            debtRepo.parentFinancialProfile(parentId).collect { result ->
                when (result) {
                    is Result.Success -> _state.update { it.copy(financial = result.data) }
                    is Result.Failure -> _state.update { it.copy(financial = null) }
                }
            }
        }
    }

    /**
     * Apply a discretionary account adjustment (credit or debit). The
     * [Permission.AdjustAccount] gate is enforced by the UI before calling this.
     * Returns the created adjustment on success or an error message on failure.
     */
    fun adjustAccount(amount: Double, reason: String, onResult: (Boolean, String?) -> Unit) {
        val s = _state.value
        val actorId = session.value?.userId.orEmpty()
        if (amount == 0.0) { onResult(false, "Le montant ne peut pas être nul."); return }
        if (reason.isBlank()) { onResult(false, "Veuillez indiquer un motif."); return }
        viewModelScope.launch {
            when (val r = paymentRepo.adjust(parentId, amount, reason.trim(), actorId)) {
                is Result.Success -> {
                    _state.update {
                        it.copy(
                            adjustments = listOf(r.data) + it.adjustments,
                            adjustError = null,
                        )
                    }
                    onResult(true, null)
                }
                is Result.Failure -> onResult(false, r.error.userMessage)
            }
        }
    }

    /** Delete the parent — UI gates this on [Permission.DeleteParent]. */
    fun deleteParent(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val r = parentRepo.deleteParent(parentId)) {
                is Result.Success -> onResult(true, null)
                is Result.Failure -> onResult(false, r.error.userMessage)
            }
        }
    }
}

/** Parent detail screen state. */
data class ParentDetailUiState(
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val parent: Parent? = null,
    val financial: ParentFinancialProfile? = null,
    val adjustments: List<AccountAdjustment> = emptyList(),
    val adjustError: String? = null,
) {
    /** Convenience: the parent's children (1→N). */
    val students: List<Student> get() = parent?.students.orEmpty()

    /** Last 5 payments for the "Paiements récents" section. */
    val recentPayments: List<Payment> get() = financial?.recentPayments?.take(5).orEmpty()

    /** Installment table rows. */
    val installments: List<Installment> get() = financial?.installments.orEmpty()
}
