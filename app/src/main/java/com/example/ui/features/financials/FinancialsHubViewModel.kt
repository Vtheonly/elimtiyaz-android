package com.example.ui.features.financials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LedgerEntry
import com.example.domain.model.DashboardKpi
import com.example.domain.model.DebtSummary
import com.example.domain.model.Expense
import com.example.domain.model.Payment
import com.example.domain.repository.DashboardRepository
import com.example.domain.repository.DebtRepository
import com.example.domain.repository.ExpenseRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.PaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Financials hub ViewModel — aggregates state from 4 repositories.
 *
 * Restored behavior (commit a34333a):
 *  - KPIs from `DashboardRepository.observeKpis()`.
 *  - Recent payments (last 30, sorted by `collectedAt` DESC) from `PaymentRepository.observe()`.
 *  - Expenses (sorted by `submittedAt` DESC) from `ExpenseRepository.observe()`.
 *  - Top 20 debtors by outstanding from `DebtRepository.observeSummary()`.
 *  - `collectedToday` — derived from `kpis.todayRevenue` (TIER 4 FIX bypass #3:
 *    previously re-computed inline from `recentPayments`).
 */
@HiltViewModel
class FinancialsHubViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val paymentRepository: PaymentRepository,
    private val expenseRepository: ExpenseRepository,
    private val debtRepository: DebtRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    val kpis: StateFlow<DashboardKpi?> = dashboardRepository.observeKpis()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val recentPayments: StateFlow<List<Payment>> = paymentRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val expenses: StateFlow<List<Expense>> = expenseRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val debtors: StateFlow<List<DebtSummary>> = debtRepository.observeSummary()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val ledgerEntries: StateFlow<List<LedgerEntry>> = ledgerRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Sum of today's `paid` payments (in centimes).
     *
     * TIER 4 FIX (bypass #3): the previous implementation re-computed this
     * client-side from [recentPayments] using a `PAID + PARTIAL` filter —
     * duplicating the canonical `DashboardKpi.todayRevenue` produced by
     * `LocalDashboardRepository.observeKpis()` (which filters `paid` +
     * `collectedAt.startsWith(todayIso)`). The duplicate filter could drift
     * from the dashboard KPI (it included `PARTIAL` payments, which the
     * canonical rule excludes from cleared revenue). Now derived directly
     * from [kpis] so the hub and dashboard always agree.
     */
    val collectedToday: StateFlow<Long> = kpis.map { it?.todayRevenue ?: 0L }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    /** Top 20 debtors by outstanding amount (mirrors desktop `top20Debtors`). */
    val topDebtors: StateFlow<List<DebtSummary>> = debtors.map { all ->
        all
            .filter { it.outstandingAmount > 0 }
            .sortedByDescending { it.outstandingAmount }
            .take(20)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Pending expenses count (status = `submitted`). */
    val pendingExpensesCount: StateFlow<Int> = expenses.map { all ->
        all.count { it.status == "submitted" }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val isInitialLoading: StateFlow<Boolean> = combine(
        kpis, recentPayments, expenses, debtors,
    ) { kpisVal, payments, exps, debts ->
        kpisVal == null && payments.isEmpty() && exps.isEmpty() && debts.isEmpty()
    }.stateIn(viewModelScope, SharingStarted.Lazily, true)
}
