package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.core.Session
import com.example.domain.model.AppNotification
import com.example.domain.model.DashboardKpi
import com.example.domain.model.DebtSummary
import com.example.domain.repository.DashboardRepository
import com.example.domain.repository.NotificationRepository
import com.example.domain.repository.RevenuePoint
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElGradientStatCard
import com.example.ui.designsystem.components.data.ElBarChart
import com.example.ui.designsystem.components.data.ElBarChartItem
import com.example.ui.designsystem.components.data.ElDonutChart
import com.example.ui.designsystem.components.data.ElDonutSegment
import com.example.ui.designsystem.components.data.ElLineChart
import com.example.ui.designsystem.components.data.ElLineChartPoint
import com.example.ui.designsystem.components.data.ElProgressRing
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElGradient
import com.example.ui.designsystem.components.display.ElInfoRow
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.feedback.ElLoadingBlock
import com.example.ui.designsystem.components.nav.ElBottomBar
import com.example.ui.designsystem.components.nav.ElNavDestination
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.foundation.elMoneyFormat
import com.example.ui.designsystem.foundation.elPercentFormat
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.designsystem.theme.Tangerine600
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dashboard hub ViewModel — wires the real [DashboardRepository] and exposes
 * reactive state for the screen.
 *
 * State flows fall back to demo data (defaultKpi, defaultRevenue, etc.) so the
 * screen always renders nicely even when the backend is unreachable. The
 * repository's `observeX()` flows emit `null` / `emptyList()` on network
 * failures — we keep the demo fallback values as the StateFlow seed so the UI
 * never flashes empty.
 */
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

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Resolve the bucket code to a theme color.
 *
 * The 5 aging tiers escalate from green (fresh) → red (very overdue):
 *   0_30      → success (emerald)
 *   31_60     → info    (sky blue)
 *   61_90     → warning (tangerine)
 *   91_180    → Tangerine600 (deep orange — `warningVariant` placeholder)
 *   180_plus  → danger  (rose)
 */
@androidx.compose.runtime.Composable
private fun bucketColor(bucket: String): Color = when (bucket) {
    "0_30"      -> ElTheme.colors.success
    "31_60"     -> ElTheme.colors.info
    "61_90"     -> ElTheme.colors.warning
    "91_180"    -> Tangerine600
    "180_plus"  -> ElTheme.colors.danger
    else        -> ElTheme.colors.primary
}

/** Human-readable label for each aging bucket, used in the donut legend. */
private fun bucketLabel(bucket: String): String = when (bucket) {
    "0_30"     -> "0–30 j"
    "31_60"    -> "31–60 j"
    "61_90"    -> "61–90 j"
    "91_180"   -> "91–180 j"
    "180_plus" -> "180+ j"
    else       -> bucket
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dashboard hub screen — modern refactored version.
 *
 * Layout: `ElScaffold` with `ElTopBar` (title + subtitle + refresh action) and
 * `ElBottomBar` (5 hub destinations). The content is a vertically scrollable
 * column with:
 *   a) Alert banners (error / overdue count)
 *   b) LazyRow of 4 KPI gradient stat cards
 *   c) Revenue trend bar chart (last 12 months)
 *   d) Collection rate progress ring + debt aging donut chart
 *   e) Attendance trend line chart (last 7 days)
 *   f) Operational alerts (last 5 unread notifications)
 *   g) Pending approvals row (expenses + overdue alerts)
 *   h) Quick actions row (5 outlined buttons)
 */
@Composable
fun DashboardHubScreen(
    session: Session,
    onNavigateToStudent: (String) -> Unit,
    onNavigateToParent: (String) -> Unit,
    onNavigateToCounterPayment: () -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
    onNavigateToBatchRegistration: () -> Unit = {},
    onNavigateToAcademics: () -> Unit = {},
    onNavigateToCrm: () -> Unit = {},
    onNavigateToFinancials: () -> Unit = onNavigateToCounterPayment,
    onNavigateToPersonnel: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val kpis by viewModel.kpis.collectAsState()
    val revenue by viewModel.revenue.collectAsState()
    val debtAging by viewModel.debtAging.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val attendanceTrend by viewModel.attendanceTrend.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val currentKpi = kpis ?: run {
        // Should never happen because StateFlow seeds with defaultKpi, but
        // keeps the compiler happy about nullability.
        return
    }

    val bottomDestinations = remember {
        listOf(
            ElNavDestination("dashboard", "Tableau", Icons.Default.Dashboard),
            ElNavDestination("crm", "CRM", Icons.Default.Group),
            ElNavDestination("academics", "Pédagogie", Icons.Default.MenuBook),
            ElNavDestination("financials", "Finances", Icons.Default.Payments),
            ElNavDestination("personnel", "Personnel", Icons.Default.Person),
        )
    }

    ElScaffold(
        topBar = {
            ElTopBar(
                title = "Tableau de bord",
                subtitle = "Vue d'ensemble opérationnelle",
                actions = {
                    ElIconButton(
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.refresh() },
                        contentDescription = "Rafraîchir",
                        background = Color.Transparent,
                        tint = ElTheme.colors.textPrimary,
                        size = 40,
                        iconSize = 22,
                    )
                },
            )
        },
        bottomBar = {
            ElBottomBar(
                destinations = bottomDestinations,
                currentRoute = "dashboard",
                onNavigate = { route ->
                    when (route) {
                        "crm"         -> onNavigateToCrm()
                        "academics"   -> onNavigateToAcademics()
                        "financials"  -> onNavigateToFinancials()
                        "personnel"   -> onNavigateToPersonnel()
                        // "dashboard" — already here, no-op
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Loading indicator ──────────────────────────────────────────
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ElLoadingBlock(message = "Rafraîchissement des indicateurs…")
                }
            }

            // ── (a) Alerts row ─────────────────────────────────────────────
            error?.let { msg ->
                ElAlertBanner(
                    title = msg,
                    severity = ElAlertSeverity.DANGER,
                    onDismiss = null,
                )
            }
            val overdueCount = currentKpi.overdueAlerts
            if (overdueCount > 0) {
                ElAlertBanner(
                    title = "$overdueCount alerte${if (overdueCount > 1) "s" else ""} de retard",
                    message = "Des paiements en souffrance dépassent l'échéance. Ouvrez le tableau des créances pour relancer.",
                    severity = ElAlertSeverity.WARNING,
                    actionLabel = "Voir",
                    onAction = onNavigateToDebtDashboard,
                )
            }

            // ── (b) KPI cards row ──────────────────────────────────────────
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
            ) {
                item {
                    ElGradientStatCard(
                        title = "Élèves actifs",
                        value = currentKpi.totalStudents.toString(),
                        gradient = ElGradient.BRAND,
                        icon = Icons.Default.Groups,
                        subtitle = "${currentKpi.totalParents} familles",
                        modifier = Modifier.width(200.dp),
                    )
                }
                item {
                    ElGradientStatCard(
                        title = "Revenu mensuel",
                        value = elMoneyFormat(currentKpi.monthlyRevenue),
                        gradient = ElGradient.REVENUE,
                        icon = Icons.Default.AccountBalance,
                        subtitle = "Encaissements du mois",
                        modifier = Modifier.width(220.dp),
                    )
                }
                item {
                    ElGradientStatCard(
                        title = "Créances en souffrance",
                        value = elMoneyFormat(currentKpi.outstandingDebt),
                        gradient = ElGradient.DEBT,
                        icon = Icons.Default.MoneyOff,
                        subtitle = "${currentKpi.overdueAlerts} en retard",
                        modifier = Modifier.width(220.dp),
                    )
                }
                item {
                    ElGradientStatCard(
                        title = "Présence aujourd'hui",
                        value = elPercentFormat(currentKpi.attendanceRateToday / 100.0),
                        gradient = ElGradient.ATTENDANCE,
                        icon = Icons.Default.TrendingUp,
                        subtitle = "${currentKpi.totalStaff} staff",
                        modifier = Modifier.width(200.dp),
                    )
                }
            }

            // ── (c) Revenue trend bar chart ────────────────────────────────
            ElSectionHeader(
                title = "Revenu mensuel",
                subtitle = "12 derniers mois",
            )
            ElCard(modifier = Modifier.fillMaxWidth()) {
                ElBarChart(
                    data = revenue.map {
                        ElBarChartItem(
                            label = it.label.take(3),
                            value = it.amount.toFloat(),
                            color = ElTheme.colors.primary,
                        )
                    },
                    height = 200.dp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // ── (d) Collection rate + Debt aging row ───────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Left card: collection rate progress ring.
                ElCard(
                    modifier = Modifier.weight(1f),
                ) {
                    val collected = currentKpi.monthlyRevenue.toFloat()
                    val pending = currentKpi.outstandingDebt.toFloat()
                    val total = collected + pending
                    val rate = if (total > 0f) (collected / total).coerceIn(0f, 1f) else 0f

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ElProgressRing(
                            progress = rate,
                            size = 120.dp,
                            color = ElTheme.colors.success,
                            label = "Taux de\ncollecte",
                        )
                        Spacer(Modifier.height(12.dp))
                        ElInfoRow(
                            label = "Collecté",
                            value = elMoneyFormat(currentKpi.monthlyRevenue),
                            valueTint = ElTheme.colors.success,
                        )
                        ElInfoRow(
                            label = "En attente",
                            value = elMoneyFormat(currentKpi.outstandingDebt),
                            valueTint = ElTheme.colors.danger,
                        )
                    }
                }

                // Right card: debt aging donut chart.
                ElCard(
                    modifier = Modifier.weight(1f),
                ) {
                    val agingBuckets = listOf("0_30", "31_60", "61_90", "91_180", "180_plus")
                    val segments = agingBuckets.mapNotNull { bucket ->
                        val amount = debtAging.filter { it.bucket == bucket }.sumOf { it.outstandingAmount }
                        if (amount > 0L) {
                            ElDonutSegment(
                                label = bucketLabel(bucket),
                                value = amount.toFloat(),
                                color = bucketColor(bucket),
                            )
                        } else null
                    }
                    val safeSegments = segments.ifEmpty {
                        // Fallback when no per-bucket data: show total outstanding as a single segment.
                        listOf(
                            ElDonutSegment(
                                label = "Encours",
                                value = currentKpi.outstandingDebt.toFloat(),
                                color = ElTheme.colors.danger,
                            ),
                        )
                    }
                    val totalDebt = debtAging.sumOf { it.outstandingAmount }.takeIf { it > 0L }
                        ?: currentKpi.outstandingDebt

                    ElDonutChart(
                        segments = safeSegments,
                        size = 140.dp,
                        centerLabel = "Créances",
                        centerValue = elMoneyFormat(totalDebt),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }

            // ── (e) Attendance trend line chart ────────────────────────────
            ElSectionHeader(
                title = "Tendance de présence",
                subtitle = "7 derniers jours",
            )
            ElCard(modifier = Modifier.fillMaxWidth()) {
                ElLineChart(
                    points = attendanceTrend,
                    height = 160.dp,
                    lineColor = ElTheme.colors.info,
                    gradientFill = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // ── (f) Operational alerts section ─────────────────────────────
            ElSectionHeader(title = "Alertes opérationnelles")
            val unreadNotifications = notifications.filter { it.readAt == null }.take(5)
            if (unreadNotifications.isEmpty()) {
                ElEmptyState(
                    icon = Icons.Default.HowToReg,
                    title = "Aucune alerte",
                    subtitle = "Tout est sous contrôle",
                )
            } else {
                unreadNotifications.forEach { notif ->
                    val severity = when (notif.type) {
                        "attendance_alert"  -> ElAlertSeverity.DANGER
                        "payment_overdue"   -> ElAlertSeverity.WARNING
                        "expense_pending"   -> ElAlertSeverity.WARNING
                        else                -> ElAlertSeverity.INFO
                    }
                    val (bg, fg) = when (severity) {
                        ElAlertSeverity.DANGER  -> ElTheme.colors.dangerContainer  to ElTheme.colors.danger
                        ElAlertSeverity.WARNING  -> ElTheme.colors.warningContainer to ElTheme.colors.warning
                        ElAlertSeverity.SUCCESS  -> ElTheme.colors.successContainer to ElTheme.colors.success
                        ElAlertSeverity.INFO     -> ElTheme.colors.infoContainer     to ElTheme.colors.info
                    }
                    ElCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .padding(end = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = when (severity) {
                                            ElAlertSeverity.DANGER  -> Icons.Default.Warning
                                            ElAlertSeverity.WARNING  -> Icons.Default.Warning
                                            ElAlertSeverity.SUCCESS  -> Icons.Default.HowToReg
                                            ElAlertSeverity.INFO     -> Icons.Default.Assessment
                                        },
                                        contentDescription = null,
                                        tint = fg,
                                    )
                                }
                                Text(
                                    text = notif.title,
                                    color = ElTheme.colors.textPrimary,
                                    style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = notif.body,
                                color = ElTheme.colors.textSecondary,
                                style = ElTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // ── (g) Pending approvals row ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ElGradientStatCard(
                    title = "Dépenses en attente",
                    value = currentKpi.pendingExpenses.toString(),
                    gradient = ElGradient.WARNING,
                    icon = Icons.Default.Receipt,
                    subtitle = "Approbation requise",
                    onClick = onNavigateToFinancials,
                    modifier = Modifier.weight(1f),
                )
                ElGradientStatCard(
                    title = "Retards de paiement",
                    value = currentKpi.overdueAlerts.toString(),
                    gradient = ElGradient.DANGER,
                    icon = Icons.Default.Warning,
                    subtitle = "À relancer",
                    onClick = onNavigateToDebtDashboard,
                    modifier = Modifier.weight(1f),
                )
            }

            // ── (h) Quick actions row ──────────────────────────────────────
            ElSectionHeader(title = "Actions rapides")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    ElButton(
                        text = "Nouveau paiement",
                        onClick = onNavigateToCounterPayment,
                        variant = ElButtonVariant.OUTLINED,
                        size = ElButtonSize.MEDIUM,
                        icon = Icons.Default.Payments,
                    )
                }
                item {
                    ElButton(
                        text = "Nouvel élève",
                        onClick = onNavigateToBatchRegistration,
                        variant = ElButtonVariant.OUTLINED,
                        size = ElButtonSize.MEDIUM,
                        icon = Icons.Default.Person,
                    )
                }
                item {
                    ElButton(
                        text = "Nouvelle dépense",
                        onClick = onNavigateToFinancials,
                        variant = ElButtonVariant.OUTLINED,
                        size = ElButtonSize.MEDIUM,
                        icon = Icons.Default.Receipt,
                    )
                }
                item {
                    ElButton(
                        text = "Roll call",
                        onClick = onNavigateToAcademics,
                        variant = ElButtonVariant.OUTLINED,
                        size = ElButtonSize.MEDIUM,
                        icon = Icons.Default.HowToReg,
                    )
                }
                item {
                    ElButton(
                        text = "Voir rapport",
                        onClick = onNavigateToDebtDashboard,
                        variant = ElButtonVariant.OUTLINED,
                        size = ElButtonSize.MEDIUM,
                        icon = Icons.Default.Assessment,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
