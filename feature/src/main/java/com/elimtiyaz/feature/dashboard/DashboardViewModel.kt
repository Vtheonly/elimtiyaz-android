package com.elimtiyaz.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.AppNotification
import com.elimtiyaz.domain.model.DashboardKpi
import com.elimtiyaz.domain.model.DebtByAgingBucket
import com.elimtiyaz.domain.model.DemographicSlice
import com.elimtiyaz.domain.model.RevenuePoint
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.DashboardRepository
import com.elimtiyaz.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Unified view-model backing every screen in the dashboard feature:
 *  - [DashboardScreen]   — KPI grid, revenue chart, debt aging, demographics, recent alerts
 *  - [AlertsScreen]      — full notification list, day-grouped, with filter chips
 *
 * All upstream flows (KPIs, revenue, debt aging, demographics, notifications,
 * session, offline flag) are collected in parallel via independent `launch`
 * blocks. Each collector updates its slice of [DashboardUiState] atomically via
 * `MutableStateFlow.update`, so the dashboard renders progressively as data
 * arrives rather than blocking on the slowest source.
 *
 * `load()` is idempotent — calling it again (e.g. via [refresh]) cancels any
 * previous collection job before starting fresh ones, so we never accumulate
 * duplicate collectors.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboard: DashboardRepository,
    private val notifications: NotificationRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /** Active collection job — cancelled and replaced on each [load] / [refresh]. */
    private var loadJob: Job? = null

    init { load() }

    /**
     * Start (or restart) parallel collection from every upstream flow.
     * Safe to call repeatedly — previous collectors are cancelled first.
     */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Session + offline flag — short-lived, no loading indicator on these
            launch {
                auth.session.collect { session ->
                    _uiState.update { it.copy(currentSession = session) }
                }
            }
            launch {
                auth.isOffline.collect { offline ->
                    _uiState.update { it.copy(isOffline = offline) }
                }
            }

            // KPIs — drive the top-level loading flag
            launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                dashboard.kpis().collect { result ->
                    when (result) {
                        is Result.Success -> _uiState.update {
                            it.copy(isLoading = false, error = null, kpis = result.data)
                        }
                        is Result.Failure -> _uiState.update {
                            it.copy(isLoading = false, error = result.error, kpis = null)
                        }
                    }
                }
            }

            // Revenue series (12 months) — silent update
            launch {
                dashboard.revenueLast12Months().collect { result ->
                    when (result) {
                        is Result.Success -> _uiState.update { it.copy(revenueSeries = result.data) }
                        is Result.Failure -> _uiState.update {
                            it.copy(revenueSeries = emptyList())
                        }
                    }
                }
            }

            // Debt by aging bucket — silent update
            launch {
                dashboard.debtByAging().collect { result ->
                    when (result) {
                        is Result.Success -> _uiState.update { it.copy(debtByAging = result.data) }
                        is Result.Failure -> _uiState.update { it.copy(debtByAging = emptyList()) }
                    }
                }
            }

            // Demographics by level — silent update
            launch {
                dashboard.demographics().collect { result ->
                    when (result) {
                        is Result.Success -> _uiState.update { it.copy(demographics = result.data) }
                        is Result.Failure -> _uiState.update { it.copy(demographics = emptyList()) }
                    }
                }
            }

            // Notifications — keep the full list (recentNotifications holds
            // everything; the dashboard renders the top 3, AlertsScreen shows all)
            launch {
                notifications.notifications().collect { result ->
                    when (result) {
                        is Result.Success -> {
                            val list = result.data
                            val unread = list.count { it.readAt == null }
                            _uiState.update {
                                it.copy(
                                    recentNotifications = list,
                                    unreadAlertsCount = unread,
                                )
                            }
                        }
                        is Result.Failure -> _uiState.update {
                            it.copy(recentNotifications = emptyList(), unreadAlertsCount = 0)
                        }
                    }
                }
            }
        }
    }

    /** Pull-to-refresh / retry — equivalent to [load]. */
    fun refresh() = load()

    /** Mark a single notification as read by id. */
    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            notifications.markRead(id)
            // Optimistic local update — the notifications() flow will also re-emit
            _uiState.update { state ->
                val updated = state.recentNotifications.map {
                    if (it.id == id) it.copy(readAt = com.elimtiyaz.core.common.Formatters.nowIso()) else it
                }
                state.copy(
                    recentNotifications = updated,
                    unreadAlertsCount = updated.count { it.readAt == null },
                )
            }
        }
    }

    /** Mark every notification as read. */
    fun markAllNotificationsRead() {
        viewModelScope.launch {
            notifications.markAllRead()
            _uiState.update { state ->
                val now = com.elimtiyaz.core.common.Formatters.nowIso()
                val updated = state.recentNotifications.map { it.copy(readAt = it.readAt ?: now) }
                state.copy(recentNotifications = updated, unreadAlertsCount = 0)
            }
        }
    }
}

/**
 * Immutable state for the dashboard feature. Every field has a default so the
 * initial empty state renders cleanly.
 */
data class DashboardUiState(
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val kpis: DashboardKpi? = null,
    val revenueSeries: List<RevenuePoint> = emptyList(),
    val debtByAging: List<DebtByAgingBucket> = emptyList(),
    val demographics: List<DemographicSlice> = emptyList(),
    val recentNotifications: List<AppNotification> = emptyList(),
    val unreadAlertsCount: Int = 0,
    val isOffline: Boolean = false,
    val currentSession: Session? = null,
)
