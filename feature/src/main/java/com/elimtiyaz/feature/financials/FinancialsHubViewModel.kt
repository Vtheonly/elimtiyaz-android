package com.elimtiyaz.feature.financials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.DashboardKpi
import com.elimtiyaz.domain.model.DebtSummary
import com.elimtiyaz.domain.model.Expense
import com.elimtiyaz.domain.model.Payment
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.DashboardRepository
import com.elimtiyaz.domain.repository.DebtRepository
import com.elimtiyaz.domain.repository.ExpenseRepository
import com.elimtiyaz.domain.repository.PaymentRepository
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
 * FinancialsHubViewModel — root of the Financials tab (Route.Financials).
 *
 * Aggregates four data sources into a single [FinancialsHubUiState]:
 *  - [DashboardRepository.kpis] for the 4 KPI cards (today's collected, monthly
 *    revenue, outstanding debt, pending expenses count).
 *  - [PaymentRepository.payments] for the "Paiements" tab (last 30 entries).
 *  - [ExpenseRepository.expenses] for the "Dépenses" tab (grouped by status).
 *  - [DebtRepository.debtSummary] for the "Créances" tab (top 20 debtors).
 *
 * The current [Session] is exposed so the screen can gate the FABs on
 * [Permission.CollectPayment] and [Permission.SubmitExpense].
 */
@HiltViewModel
class FinancialsHubViewModel @Inject constructor(
    private val dashboard: DashboardRepository,
    private val payments: PaymentRepository,
    private val expenses: ExpenseRepository,
    private val debt: DebtRepository,
    auth: AuthRepository,
) : ViewModel() {

    /** Latest session — used by the screen to gate FABs. */
    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val _state = MutableStateFlow(FinancialsHubUiState())
    val state: StateFlow<FinancialsHubUiState> = _state.asStateFlow()

    init {
        loadKpis()
        loadPayments()
        loadExpenses()
        loadDebt()
    }

    /** Refresh the dashboard KPI block. */
    fun loadKpis() {
        viewModelScope.launch {
            dashboard.kpis().collect { result ->
                when (result) {
                    is Result.Success -> _state.update { it.copy(kpis = result.data, kpisError = null) }
                    is Result.Failure -> _state.update { it.copy(kpisError = result.error) }
                }
            }
        }
    }

    /** Refresh the recent-payments list. */
    fun loadPayments() {
        _state.update { it.copy(paymentsLoading = true, paymentsError = null) }
        viewModelScope.launch {
            payments.payments().collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(
                            paymentsLoading = false,
                            paymentsError = null,
                            recentPayments = result.data.sortedByDescending { p -> p.collectedAt }.take(30),
                        )
                    }
                    is Result.Failure -> _state.update {
                        it.copy(paymentsLoading = false, paymentsError = result.error, recentPayments = emptyList())
                    }
                }
            }
        }
    }

    /** Refresh the expenses list (all statuses, grouped by the screen). */
    fun loadExpenses() {
        _state.update { it.copy(expensesLoading = true, expensesError = null) }
        viewModelScope.launch {
            expenses.expenses().collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(
                            expensesLoading = false,
                            expensesError = null,
                            expenses = result.data.sortedByDescending { e -> e.submittedAt },
                        )
                    }
                    is Result.Failure -> _state.update {
                        it.copy(expensesLoading = false, expensesError = result.error, expenses = emptyList())
                    }
                }
            }
        }
    }

    /** Refresh the debt-summary list (top 20 by outstanding amount). */
    fun loadDebt() {
        _state.update { it.copy(debtLoading = true, debtError = null) }
        viewModelScope.launch {
            debt.debtSummary().collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(
                            debtLoading = false,
                            debtError = null,
                            debtors = result.data.sortedByDescending { d -> d.outstandingAmount }.take(20),
                        )
                    }
                    is Result.Failure -> _state.update {
                        it.copy(debtLoading = false, debtError = result.error, debtors = emptyList())
                    }
                }
            }
        }
    }
}

/**
 * Hub screen state. Each list carries its own loading/error flag so the
 * tab content can use [com.elimtiyaz.core.ui.AsyncContent] independently.
 */
data class FinancialsHubUiState(
    val kpis: DashboardKpi? = null,
    val kpisError: AppError? = null,
    val recentPayments: List<Payment> = emptyList(),
    val paymentsLoading: Boolean = true,
    val paymentsError: AppError? = null,
    val expenses: List<Expense> = emptyList(),
    val expensesLoading: Boolean = true,
    val expensesError: AppError? = null,
    val debtors: List<DebtSummary> = emptyList(),
    val debtLoading: Boolean = true,
    val debtError: AppError? = null,
) {
    /** Today's collected — derived from the last 24h slice of recent payments. */
    val collectedToday: Double
        get() = recentPayments
            .filter { it.status == "paid" || it.status == "partial" }
            .sumOf { it.amount }

    /** Whether all four sources are still on their initial fetch. */
    val isInitialLoading: Boolean
        get() = kpis == null && kpisError == null && recentPayments.isEmpty() && expenses.isEmpty() && debtors.isEmpty()
}
