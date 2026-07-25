package com.elimtiyaz.feature.financials

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.Installment
import com.elimtiyaz.domain.model.ParentFinancialProfile
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.DebtRepository
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
 * InstallmentScheduleViewModel — powers Route.Installments.
 *
 * Loads a parent's full financial profile (totals + installments). Each row
 * exposes an "Encaisser" action that navigates to the CounterPayment screen
 * pre-filled with that installment's data.
 */
@HiltViewModel
class InstallmentScheduleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val debt: DebtRepository,
    auth: AuthRepository,
) : ViewModel() {

    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val parentId: String = savedStateHandle.get<String>("parentId").orEmpty()

    private val _state = MutableStateFlow(InstallmentScheduleUiState())
    val state: StateFlow<InstallmentScheduleUiState> = _state.asStateFlow()

    init { reload() }

    /** Re-fetch the parent's financial profile. */
    fun reload() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            debt.parentFinancialProfile(parentId).collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(isLoading = false, profile = result.data)
                    }
                    is Result.Failure -> _state.update {
                        it.copy(isLoading = false, error = result.error, profile = null)
                    }
                }
            }
        }
    }
}

/** Installment schedule screen state. */
data class InstallmentScheduleUiState(
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val profile: ParentFinancialProfile? = null,
) {
    val installments: List<Installment> get() = profile?.installments.orEmpty()
    val parentName: String get() = profile?.parentName ?: "—"
    val totalDue: Double get() = profile?.totalDue ?: 0.0
    val totalPaid: Double get() = profile?.totalPaid ?: 0.0
    val outstanding: Double get() = profile?.totalOutstanding ?: 0.0
    val overdue: Double get() = profile?.overdueAmount ?: 0.0
}
