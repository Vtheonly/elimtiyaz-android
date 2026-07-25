package com.elimtiyaz.feature.financials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.AgingBucket
import com.elimtiyaz.domain.model.DebtSummary
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
 * DebtDashboardViewModel — powers Route.DebtDashboard.
 *
 * Loads all debt summaries (with aging buckets), exposes:
 *  - A filter by aging bucket (5 buckets per master plan §07.06).
 *  - A sort selector: by amount desc, or by days-overdue desc.
 *  - A send-reminder action that delegates to [DebtRepository.sendReminder]
 *    AND surfaces the parent's phone so the screen can fire a WhatsApp intent.
 */
@HiltViewModel
class DebtDashboardViewModel @Inject constructor(
    private val debt: DebtRepository,
    auth: AuthRepository,
) : ViewModel() {

    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val _state = MutableStateFlow(DebtDashboardUiState())
    val state: StateFlow<DebtDashboardUiState> = _state.asStateFlow()

    init { reload() }

    /** Re-fetch the full debt summary list. */
    fun reload() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            debt.debtSummary().collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(isLoading = false, debtors = result.data, error = null)
                    }
                    is Result.Failure -> _state.update {
                        it.copy(isLoading = false, error = result.error, debtors = emptyList())
                    }
                }
            }
        }
    }

    /** Filter the list by aging bucket (or null for "all"). */
    fun filterByBucket(bucket: AgingBucket?) {
        _state.update { it.copy(bucketFilter = bucket) }
    }

    /** Toggle the sort: by amount desc ⇄ by days-overdue desc. */
    fun toggleSort() {
        _state.update {
            val next = if (it.sortBy == DebtSort.ByAmount) DebtSort.ByDaysOverdue else DebtSort.ByAmount
            it.copy(sortBy = next)
        }
    }

    /**
     * Send an in-app reminder (audit-logged) — UI gates this on
     * [Permission.SendReminder]. Returns the parent's phone so the screen can
     * also fire a WhatsApp intent (master plan §07.06).
     */
    fun sendReminder(parentId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val r = debt.sendReminder(parentId)) {
                is Result.Success -> onResult(true, null)
                is Result.Failure -> onResult(false, r.error.userMessage)
            }
        }
    }
}

/** Sort key for the debt dashboard list. */
enum class DebtSort { ByAmount, ByDaysOverdue }

/** Debt dashboard screen state. */
data class DebtDashboardUiState(
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val debtors: List<DebtSummary> = emptyList(),
    val bucketFilter: AgingBucket? = null,
    val sortBy: DebtSort = DebtSort.ByAmount,
) {
    /** Aging-bucket totals — used by the chart at the top of the screen. */
    val bucketTotals: Map<AgingBucket, Double>
        get() = AgingBucket.values().associateWith { b ->
            debtors.filter { it.agingBucket == b }.sumOf { it.outstandingAmount }
        }

    /** Max bucket total — used to scale the horizontal bars. */
    val maxBucketTotal: Double get() = bucketTotals.values.maxOrNull() ?: 0.0

    /** The visible (filtered + sorted) list. */
    val visibleDebtors: List<DebtSummary>
        get() {
            val filtered = if (bucketFilter == null) debtors else debtors.filter { it.agingBucket == bucketFilter }
            return when (sortBy) {
                DebtSort.ByAmount      -> filtered.sortedByDescending { it.outstandingAmount }
                DebtSort.ByDaysOverdue -> filtered.sortedByDescending { it.daysOverdue }
            }
        }
}
