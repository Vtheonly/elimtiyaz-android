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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.domain.model.CategoryRevenueItem
import com.example.domain.model.DashboardKpi
import com.example.ui.components.ModernSecondaryTabRow
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElLoadingBlock
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.features.dashboard.analytics.AnalyticsSlicersBar
import com.example.ui.features.dashboard.analytics.AnalyticsStatStrip
import com.example.ui.features.dashboard.analytics.AmountHistogramCard
import com.example.ui.features.dashboard.analytics.AgingCompositionCard
import com.example.ui.features.dashboard.analytics.CollectionHeatmapCard
import com.example.ui.features.dashboard.analytics.CategoryMixCard
import com.example.ui.features.dashboard.analytics.DebtorsParetoCard
import com.example.ui.features.dashboard.analytics.DemographicsCard
import com.example.ui.features.dashboard.analytics.MethodMixCard
import com.example.ui.features.dashboard.analytics.RevenueTrendExplorerCard
import com.example.ui.features.dashboard.analytics.WeeklyOperatingRhythmCard
import com.example.ui.features.dashboard.analytics.YoYComparisonCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardHubScreen(
    session: Session,
    onNavigateToStudent: (String) -> Unit,
    onNavigateToParent: (String) -> Unit,
    onNavigateToCounterPayment: (parentId: String?, studentId: String?) -> Unit = { _, _ -> },
    onNavigateToDebtDashboard: () -> Unit,
    onNavigateToBatchRegistration: () -> Unit = {},
    onNavigateToAcademics: () -> Unit = {},
    onNavigateToCrm: () -> Unit = {},
    onNavigateToFinancials: () -> Unit = { onNavigateToCounterPayment(null, null) },
    onNavigateToPersonnel: () -> Unit = {},
    onNavigateToGlobalSearch: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToAlerts: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToRollCall: (String) -> Unit = {},
    onNavigateToExpenseDetail: (String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val kpis by viewModel.kpis.collectAsState()
    val revenue by viewModel.revenue.collectAsState()
    val debtAging by viewModel.debtAging.collectAsState()
    val paymentMethods by viewModel.paymentMethods.collectAsState()
    val classRollCallStatuses by viewModel.classRollCallStatuses.collectAsState()
    val operationalAlerts by viewModel.operationalAlerts.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val recentPayments by viewModel.recentPayments.collectAsState()
    val attendanceTrend by viewModel.attendanceTrend.collectAsState()
    val analyticsFilters by viewModel.analyticsFilters.collectAsState()
    val analyticsSlice by viewModel.analyticsSlice.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var selectedViewTab by rememberSaveable { mutableIntStateOf(0) }
    val viewTabs = listOf("Vue d'ensemble", "Analytique")

    // PARITY-002: honest all-zero loading placeholder (defaults) — the real
    // KPIs arrive from the repository via StatisticsEngine.
    val currentKpi = kpis ?: DashboardKpi()

    val todayFormatted = remember {
        val now = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)
        now.format(formatter).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
    }

    ElScaffold(
        topBar = {
            ElTopBar(
                title = "Tableau de bord",
                subtitle = "Établissement Privé El-Imtiyaz",
                actions = {
                    ElIconButton(
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.refresh() },
                        contentDescription = "Actualiser les indicateurs",
                        background = Color.Transparent,
                        tint = ElTheme.colors.textPrimary,
                        size = 40,
                        iconSize = 22,
                    )
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: Date & Session status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = ElTheme.colors.textSecondary,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        text = todayFormatted,
                        style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = ElTheme.colors.textSecondary,
                    )
                }
                ElTag(
                    text = "2025–2026",
                    tone = ElTagTone.INFO,
                )
            }

            // Tab Switcher
            ModernSecondaryTabRow(
                tabs = viewTabs,
                selectedTabIndex = selectedViewTab,
                onTabSelected = { selectedViewTab = it },
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ElLoadingBlock(message = "Synchronisation des indicateurs réels…")
                }
            }

            if (selectedViewTab == 0) {
                // ════════════════════════════════════════════════════════════
                // VUE D'ENSEMBLE — Professional, Clean Executive View
                // ════════════════════════════════════════════════════════════

                // 1. Core KPIs: Students, Revenue, Active Balances, Attendance
                DashboardKpiCardsRow(currentKpi = currentKpi)

                // 2. Standard Operational Quick Actions
                DashboardQuickActionsRow(
                    onNavigateToCounterPayment = { onNavigateToCounterPayment(null, null) },
                    onNavigateToBatchRegistration = onNavigateToBatchRegistration,
                    onNavigateToFinancials = onNavigateToFinancials,
                    onNavigateToAcademics = onNavigateToAcademics,
                    onNavigateToDebtDashboard = onNavigateToDebtDashboard,
                    onNavigateToChat = onNavigateToChat,
                )

                // 3. Financial Flux & Revenue Trends (Positive overview)
                DashboardRevenueChart(
                    currentKpi = currentKpi,
                    revenue = revenue,
                    paymentMethods = paymentMethods,
                )

                // 3b. PARITY-003 — the Algerian school-week operating rhythm
                // (desktop weekly-operating-rhythm.tsx twin; Overview parity).
                WeeklyOperatingRhythmCard(weeklyRhythm = currentKpi.weeklyRhythm)

                // 4. Attendance & Life Overview (Status only, without pushy buttons)
                DashboardAttendanceChart(
                    classStatuses = classRollCallStatuses,
                    attendanceTrend = attendanceTrend,
                    attendanceRateToday = currentKpi.attendanceRateToday,
                    classesCompleted = currentKpi.classesCompletedRollCall,
                    totalClasses = currentKpi.totalClassesCount,
                    onNavigateToRollCall = onNavigateToRollCall,
                    onNavigateToAcademics = onNavigateToAcademics,
                )

                // 5. Pending Approvals & Checks
                DashboardApprovalsRow(
                    currentKpi = currentKpi,
                    onNavigateToFinancials = onNavigateToFinancials,
                    onNavigateToDebtDashboard = onNavigateToDebtDashboard,
                )

                // 6. Activity & Notifications Feed
                DashboardNotificationsSection(
                    notifications = notifications,
                    recentPayments = recentPayments,
                    onNavigateToFinancials = onNavigateToFinancials,
                )

                // 7. System alerts placed politely at bottom of overview
                DashboardAlertsSection(
                    error = error,
                    alerts = operationalAlerts,
                    onNavigateToParent = onNavigateToParent,
                    onNavigateToRollCall = onNavigateToRollCall,
                    onNavigateToExpenseDetail = onNavigateToExpenseDetail,
                    onNavigateToFinancials = onNavigateToFinancials,
                    onNavigateToDebtDashboard = onNavigateToDebtDashboard,
                )
            } else {
                // ════════════════════════════════════════════════════════════
                // ANALYTIQUE — PARITY-003: the desktop Analytics-tab twin
                // (slicers → stat strip → trend explorer + mix cards →
                // YoY + histogram → heatmap + aging composition → Pareto
                // + funnel + demographics; every value from the engine).
                // ════════════════════════════════════════════════════════════

                // Row 0 — the cross-filtering slicer bar
                AnalyticsSlicersBar(
                    methodFilters = analyticsFilters.methods,
                    categoryFilters = analyticsFilters.categories,
                    presentCategories = analyticsSlice.presentCategories,
                    sliceCount = analyticsSlice.sliceCount,
                    sliceTotalDzd = analyticsSlice.sliceTotalCentimes / 100,
                    onToggleMethod = viewModel::toggleAnalyticsMethod,
                    onToggleCategory = viewModel::toggleAnalyticsCategory,
                    onReset = viewModel::resetAnalyticsFilters,
                )

                // Row 1 — the 6-card statistics strip (over the filtered slice)
                AnalyticsStatStrip(
                    count = analyticsSlice.stats.count,
                    totalCentimes = analyticsSlice.stats.total,
                    meanCentimes = analyticsSlice.stats.mean,
                    medianCentimes = analyticsSlice.stats.median,
                    stdDevCentimes = analyticsSlice.stats.stdDev,
                    bestMonthLabel = analyticsSlice.stats.bestMonth?.label,
                    bestMonthAmount = analyticsSlice.stats.bestMonth?.amount ?: 0L,
                )

                // Row 2 — the revenue trend explorer (cumulative + MM3 + the
                // dashed filtered overlay when slicers are active)
                RevenueTrendExplorerCard(
                    trend = currentKpi.revenueTrend,
                    filteredMonthly = analyticsSlice.filteredMonthly,
                )

                // Row 2b — the method donut + the category ranked bars
                MethodMixCard(methodMix = currentKpi.methodMix)
                CategoryMixCard(categoryBreakdown = currentKpi.categoryBreakdown)

                // Row 3 — YoY + the amount histogram
                YoYComparisonCard(yoy = currentKpi.yoy)
                AmountHistogramCard(bins = currentKpi.amountBins, totalOps = currentKpi.totalOperationsCount)

                // Row 4 — the collection heatmap + the aging composition
                CollectionHeatmapCard(heatmap = currentKpi.collectionHeatmap)
                AgingCompositionCard(debtByAging = currentKpi.debtByAging)

                // Row 5 — funnel + debt donut + Pareto (the PARITY-002
                // surface — the same engine values, kept intact)
                DashboardCollectionAndDebtRow(
                    currentKpi = currentKpi,
                    debtAging = debtAging,
                    onNavigateToParent = onNavigateToParent,
                    onNavigateToDebtDashboard = onNavigateToDebtDashboard,
                )

                // Row 6 — class demographics & capacity gauges
                DemographicsCard(demographics = currentKpi.demographics)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}