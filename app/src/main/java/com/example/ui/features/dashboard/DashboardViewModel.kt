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

    private val defaultKpi = DashboardKpi(
        totalStudents = 0,
        totalParents = 0,
        totalStaff = 0,
        totalFamilies = 0,
        totalRevenue = 0L,
        monthlyRevenue = 0L,
        todayRevenue = 0L,
        todayPaymentsCount = 0,
        totalOperationsCount = 0,
        averageBasketAmount = 0L,
        medianBasketAmount = 0L,
        volatilityAmount = 0L,
        bestMonthName = "Août",
        bestMonthAmount = 0L,
        outstandingDebt = 0L,
        overdueDebt = 0L,
        overdueFamiliesCount = 0,
        pendingExpenses = 0,
        pendingExpensesAmount = 0L,
        attendanceRateToday = 100.0,
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