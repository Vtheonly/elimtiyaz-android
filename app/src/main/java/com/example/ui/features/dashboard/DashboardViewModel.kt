package com.example.ui.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.domain.model.AppNotification
import com.example.domain.model.DashboardKpi
import com.example.domain.model.DebtSummary
import com.example.domain.repository.DashboardRepository
import com.example.domain.repository.NotificationRepository
import com.example.domain.repository.RevenuePoint
import com.example.ui.designsystem.components.data.ElLineChartPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    notificationRepository: NotificationRepository,
) : ViewModel() {

    // ── Reactive state ────────────────────────────────────────────────────
    // All StateFlows seed with `null` / `emptyList()` so the UI shows the
    // correct loading / empty states until the REAL Supabase-backed
    // repository emits. There is NO demo data fallback — every number the
    // user sees on the dashboard comes from the real backend.
    val kpis: StateFlow<DashboardKpi?> = dashboardRepository.observeKpis()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val revenue: StateFlow<List<RevenuePoint>> = dashboardRepository.observeRevenueLast12Months()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val debtAging: StateFlow<List<DebtSummary>> = dashboardRepository.observeDebtByAging()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val notifications: StateFlow<List<AppNotification>> = notificationRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Attendance trend (last 7 days).
     *
     * Returns an empty list until the dashboard repository exposes a proper
     * 7-day attendance trend RPC (mirroring `mv_dashboard_kpis`). When the
     * RPC is available, swap this for a direct repository flow. Until then,
     * the attendance chart will show its empty state — there is NO fake
     * historical baseline anymore.
     */
    val attendanceTrend: StateFlow<List<ElLineChartPoint>> = kpis
        .map { _ -> emptyList<ElLineChartPoint>() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Pull fresh KPIs from the backend on screen open.
        // NOTE: refresh() is non-blocking — it launches on viewModelScope.
        // The StateFlows above seed with `null` / `emptyList()` so the
        // dashboard shows loading skeletons while the network call is in
        // flight. If the network is slow, `refreshKpis()` returns within
        // ~2.5s (NetworkTimeouts.guard) and the UI shows the empty state.
        refresh()
    }

    /**
     * Re-fetch dashboard KPIs from the backend (refreshes materialized views).
     *
     * This is fire-and-forget. It NEVER blocks the UI — the StateFlows above
     * seed with `null` / `emptyList()` so the dashboard renders loading
     * skeletons instantly. The refresh just updates those flows when (if)
     * the backend responds.
     */
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                when (val r = dashboardRepository.refreshKpis()) {
                    is Result.Ok -> Unit
                    is Result.Err -> _error.value = r.error.userMessage.ifBlank {
                        r.error.message.ifBlank { "Erreur de chargement du tableau de bord" }
                    }
                }
            } catch (t: Throwable) {
                // Defensive: never let refresh() throw into the coroutine context.
                _error.value = null
            }
            _isLoading.value = false
        }
    }
}
