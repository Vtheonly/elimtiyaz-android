package com.example.ui.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.domain.model.AppNotification
import com.example.domain.model.ClassRollCallStatus
import com.example.domain.model.DashboardKpi
import com.example.domain.model.DashboardOperationalAlert
import com.example.domain.model.DebtSummary
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethodSummary
import com.example.domain.repository.DashboardRepository
import com.example.domain.repository.NotificationRepository
import com.example.domain.repository.PaymentRepository
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
    private val paymentRepository: PaymentRepository,
    notificationRepository: NotificationRepository,
) : ViewModel() {

    // FIX (fabricated data): the initial KPI state was a fully invented
    // dataset (390 students, 185 parents, 1,245,000 DZD monthly revenue,
    // 96.5% attendance…) that rendered until Room emitted — and forever on
    // an empty database, misleading users into thinking real data existed.
    // The seed value is now a TRUTHFUL all-zero KPI; the reactive Room flow
    // replaces it as soon as real data is available.
    private val defaultKpi = DashboardKpi(
        totalStudents = 0,
        totalParents = 0,
        totalStaff = 0,
        monthlyRevenue = 0L,
        todayRevenue = 0L,
        todayPaymentsCount = 0,
        outstandingDebt = 0L,
        overdueDebt = 0L,
        overdueFamiliesCount = 0,
        pendingExpenses = 0,
        pendingExpensesAmount = 0L,
        attendanceRateToday = 0.0,
        todayPresentCount = 0,
        todayAbsentCount = 0,
        classesCompletedRollCall = 0,
        totalClassesCount = 0,
        pendingChecksCount = 0,
        pendingChecksAmount = 0L,
        overdueAlerts = 0,
    )

    val kpis: StateFlow<DashboardKpi?> = dashboardRepository.observeKpis()
        .stateIn(viewModelScope, SharingStarted.Lazily, defaultKpi)

    val revenue: StateFlow<List<RevenuePoint>> = dashboardRepository.observeRevenueLast12Months()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val debtAging: StateFlow<List<DebtSummary>> = dashboardRepository.observeDebtByAging()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val paymentMethods: StateFlow<List<PaymentMethodSummary>> = dashboardRepository.observePaymentMethodsSummary()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val classRollCallStatuses: StateFlow<List<ClassRollCallStatus>> = dashboardRepository.observeClassRollCallStatus()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val operationalAlerts: StateFlow<List<DashboardOperationalAlert>> = dashboardRepository.observeOperationalAlerts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val notifications: StateFlow<List<AppNotification>> = notificationRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentPayments: StateFlow<List<Payment>> = paymentRepository.observe()
        .map { it.take(5) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // FIX (fabricated trend): previously 6 of the 7 days were hardcoded
    // (95.2 / 96.0 / 95.8 / 97.1 / 96.4 / 94.8) and "today" fell back to a
    // fake 96.5%. The trend now comes from the REAL per-day attendance
    // records via `observeAttendanceTrend()` — days without roll-call data
    // are simply not plotted.
    val attendanceTrend: StateFlow<List<ElLineChartPoint>> = dashboardRepository.observeAttendanceTrend()
        .map { points -> points.map { ElLineChartPoint(it.label, it.rate.toFloat()) } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                when (val r = dashboardRepository.refreshKpis()) {
                    is Result.Ok -> Unit
                    is Result.Err -> _error.value = r.error.userMessage.ifBlank { r.error.message }
                }
            } catch (_: Throwable) {
                _error.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
}