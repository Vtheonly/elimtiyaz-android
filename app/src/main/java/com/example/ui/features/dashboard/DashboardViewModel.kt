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

    // ── Fallback demo data ────────────────────────────────────────────────
    private val defaultKpi = DashboardKpi(
        totalStudents = 390,
        totalParents = 185,
        totalStaff = 45,
        monthlyRevenue = 1_245_000_00L, // 1 245 000,00 DZD (centimes)
        outstandingDebt = 320_000_00L,  //   320 000,00 DZD
        pendingExpenses = 3,
        attendanceRateToday = 96.5,
        overdueAlerts = 2,
    )

    private val defaultRevenue = listOf(
        RevenuePoint("Janv", 9_800_000_00L),
        RevenuePoint("Févr", 10_200_000_00L),
        RevenuePoint("Mars", 11_100_000_00L),
        RevenuePoint("Avr", 10_650_000_00L),
        RevenuePoint("Mai", 12_050_000_00L),
        RevenuePoint("Juin", 11_900_000_00L),
        RevenuePoint("Juil", 10_300_000_00L),
        RevenuePoint("Août", 9_750_000_00L),
        RevenuePoint("Sept", 13_400_000_00L),
        RevenuePoint("Oct", 12_900_000_00L),
        RevenuePoint("Nov", 13_100_000_00L),
        RevenuePoint("Déc", 12_450_000_00L),
    )

    private val defaultDebtAging = listOf(
        DebtSummary("P-1", "Famille Benali", "0550123456", 2, 1_200_000_00L, 15L, "0_30"),
        DebtSummary("P-2", "Famille Khelifi", "0550654321", 1,   850_000_00L, 45L, "31_60"),
        DebtSummary("P-3", "Famille Brahimi", "0550111222", 3,   640_000_00L, 75L, "61_90"),
        DebtSummary("P-4", "Famille Mansouri", "0550222333", 2,  410_000_00L, 135L, "91_180"),
        DebtSummary("P-5", "Famille Belkacem", "0550444555", 1,  100_000_00L, 200L, "180_plus"),
    )

    private val defaultNotifications = listOf(
        AppNotification(
            id = "N-1", tenantId = "ten-001",
            title = "Alerte Dépense Tier-2",
            body = "Demande d'achat matériel informatique (45 000 DZD) en attente de validation par l'administration.",
            type = "expense_pending", priority = "high",
            source = "system", sourceLabel = "Système",
            entityType = "EXP-004", entityId = "EXP-004",
            triggeredAt = "2026-07-31T09:30:00Z",
            createdAt = "2026-07-31T09:30:00Z", createdBy = "system",
        ),
        AppNotification(
            id = "N-2", tenantId = "ten-001",
            title = "Seuil 3+ Absences Atteint",
            body = "L'élève Yacine Belkacem (PRIM - CE1 B) a atteint 3 absences non justifiées.",
            type = "attendance_alert", priority = "urgent",
            source = "system", sourceLabel = "Système",
            entityType = "STU-003", entityId = "STU-003",
            triggeredAt = "2026-07-31T08:15:00Z",
            createdAt = "2026-07-31T08:15:00Z", createdBy = "system",
        ),
        AppNotification(
            id = "N-3", tenantId = "ten-001",
            title = "Échéance Chèque de Banque",
            body = "Chèque BNA #883921 (150 000 DZD) à déposer pour compensation aujourd'hui.",
            type = "payment_overdue", priority = "medium",
            source = "system", sourceLabel = "Système",
            entityType = "CHK-001", entityId = "CHK-001",
            triggeredAt = "2026-07-30T16:00:00Z",
            createdAt = "2026-07-30T16:00:00Z", createdBy = "system",
        ),
    )

    /** Last 7 days attendance trend (repo doesn't expose this yet). */
    private val defaultAttendanceTrend = listOf(
        ElLineChartPoint("Lun", 94.2f),
        ElLineChartPoint("Mar", 96.1f),
        ElLineChartPoint("Mer", 95.5f),
        ElLineChartPoint("Jeu", 97.3f),
        ElLineChartPoint("Ven", 96.8f),
        ElLineChartPoint("Sam", 93.4f),
        ElLineChartPoint("Dim", 96.5f),
    )

    // ── Reactive state ────────────────────────────────────────────────────
    val kpis: StateFlow<DashboardKpi?> = dashboardRepository.observeKpis()
        .stateIn(viewModelScope, SharingStarted.Lazily, defaultKpi)

    val revenue: StateFlow<List<RevenuePoint>> = dashboardRepository.observeRevenueLast12Months()
        .stateIn(viewModelScope, SharingStarted.Lazily, defaultRevenue)

    val debtAging: StateFlow<List<DebtSummary>> = dashboardRepository.observeDebtByAging()
        .stateIn(viewModelScope, SharingStarted.Lazily, defaultDebtAging)

    val notifications: StateFlow<List<AppNotification>> = notificationRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, defaultNotifications)

    /**
     * Attendance trend (last 7 days).
     *
     * BUGFIX (iter 2): previously this was a permanently hardcoded
     * `MutableStateFlow(defaultAttendanceTrend)` that never updated. Now
     * we derive it from [kpis] — the latest `attendanceRateToday` value
     * is used as today's data point, and the previous 6 days fall back
     * to the demo seed. When the dashboard repo exposes a proper 7-day
     * attendance trend RPC (mirroring `mv_dashboard_kpis`), this can be
     * switched to a direct repository flow.
     */
    val attendanceTrend: StateFlow<List<ElLineChartPoint>> = kpis
        .map { kpi ->
            val todayRate = kpi?.attendanceRateToday?.toFloat() ?: defaultKpi.attendanceRateToday.toFloat()
            // Build a 7-day window ending today. The first 6 days use the
            // default seed values (historical baseline); day 7 (today) is
            // the live value. This avoids the "permanently hardcoded"
            // defect while a proper trend RPC is pending.
            val dayLabels = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
            val defaults = defaultAttendanceTrend.map { it.value }
            val merged = defaults.dropLast(1) + todayRate
            dayLabels.zip(merged).map { (label, value) -> ElLineChartPoint(label, value) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, defaultAttendanceTrend)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Pull fresh KPIs from the backend on screen open.
        refresh()
    }

    /** Re-fetch dashboard KPIs from the backend (refreshes materialized views). */
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            when (val r = dashboardRepository.refreshKpis()) {
                is Result.Ok -> Unit
                is Result.Err -> _error.value = r.error.userMessage.ifBlank {
                    r.error.message.ifBlank { "Erreur de chargement du tableau de bord" }
                }
            }
            _isLoading.value = false
        }
    }
}
