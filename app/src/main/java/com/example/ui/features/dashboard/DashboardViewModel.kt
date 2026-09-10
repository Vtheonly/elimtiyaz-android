package com.example.ui.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.core.StatsPayment
import com.example.core.derivePaymentStats
import com.example.core.deriveAmountHistogram
import com.example.core.deriveMethodMix
import com.example.core.deriveCategoryMix
import com.example.core.deriveFilteredMonthly
import com.example.core.applyAnalyticsFilters
import com.example.core.presentCategories
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Analytics-tab filter state (desktop AnalyticsFilterState mirror —
 * empty sets = ALL methods/categories included; Power BI slicer semantics).
 */
data class AnalyticsFilterState(
    val methods: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
) {
    val hasActiveFilters: Boolean get() = methods.isNotEmpty() || categories.isNotEmpty()
}

/** The cross-filtered analytics state — EVERY value from the shared engine. */
data class AnalyticsSliceState(
    val sliceCount: Int = 0,
    val sliceTotalCentimes: Long = 0L,
    val presentCategories: List<String> = emptyList(),
    // The desktop stat-strip inputs (derivePaymentStats over the slice)
    val stats: com.example.core.PaymentStats = com.example.core.PaymentStats(0, 0, 0, 0, 0, 0, 0, null),
    // The donut + ranked bars (deriveMethodMix / deriveCategoryMix over the slice)
    val methodMix: List<com.example.core.MixSlice> = emptyList(),
    val categoryMix: List<com.example.core.MixSlice> = emptyList(),
    // The histogram (deriveAmountHistogram over the slice)
    val histogram: List<com.example.core.HistogramBin> = emptyList(),
    // The dashed filtered overlay (deriveFilteredMonthly over the slice)
    val filteredMonthly: List<Long>? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val paymentRepository: PaymentRepository,
    notificationRepository: NotificationRepository,
) : ViewModel() {

    // PARITY-002: the loading placeholder is HONEST — all-zero, no fabricated
    // best month, no 100% attendance, no funnel. Real values arrive from
    // LocalDashboardRepository (which computes via core/StatisticsEngine).
    private val defaultKpi = DashboardKpi()

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

    // ════════════════════════════════════════════════════════════════════
    // PARITY-003 — the Analytique tab's cross-filtering state.
    // The full canonical payments stream (the desktop page subscribes to
    // repos.payments.observe() the same way); the slice + every derived
    // value come from the SHARED engine (applyAnalyticsFilters + the same
    // derive* functions the repository uses — one engine, one truth).
    // ════════════════════════════════════════════════════════════════════
    val payments: StateFlow<List<Payment>> = paymentRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _analyticsFilters = MutableStateFlow(AnalyticsFilterState())
    val analyticsFilters: StateFlow<AnalyticsFilterState> = _analyticsFilters.asStateFlow()

    fun toggleAnalyticsMethod(method: String) {
        _analyticsFilters.value = _analyticsFilters.value.copy(
            methods = _analyticsFilters.value.methods.toggle(method),
        )
    }

    fun toggleAnalyticsCategory(category: String) {
        _analyticsFilters.value = _analyticsFilters.value.copy(
            categories = _analyticsFilters.value.categories.toggle(category),
        )
    }

    fun resetAnalyticsFilters() {
        _analyticsFilters.value = AnalyticsFilterState()
    }

    private fun <T> Set<T>.toggle(el: T): Set<T> = if (el in this) this - el else this + el

    /** The cross-filtered slice state — every value from the engine. */
    val analyticsSlice: StateFlow<AnalyticsSliceState> = combine(
        payments,
        analyticsFilters,
    ) { raw, filters ->
        val rows = raw.map { StatsPayment(it.id, it.amount, it.method.name, it.status.name, it.category.name, it.collectedAt) }
        // No range filter on mobile (the repository's own 12-month window
        // governs the KPI contract; the slice uses the full local stream —
        // the desktop passes its selected range; both derive from the same
        // engine semantics).
        val slice = applyAnalyticsFilters(rows, null, filters.methods, filters.categories)
        val monthLabels = kpis.value?.revenueTrend?.map { it.label } ?: emptyList()
        AnalyticsSliceState(
            sliceCount = slice.size,
            sliceTotalCentimes = slice.sumOf { it.amount },
            presentCategories = presentCategories(rows, null),
            stats = derivePaymentStats(slice),
            methodMix = deriveMethodMix(slice),
            categoryMix = deriveCategoryMix(slice, topN = 6),
            histogram = deriveAmountHistogram(slice),
            filteredMonthly = if (filters.hasActiveFilters) deriveFilteredMonthly(slice, monthLabels) else null,
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, AnalyticsSliceState())

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