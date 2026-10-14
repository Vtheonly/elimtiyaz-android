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
import com.example.domain.model.DashboardKpi
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElLoadingBlock
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.components.tabs.ElTabRow
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.features.dashboard.analytics.AgingCompositionCard
import com.example.ui.features.dashboard.analytics.CategoryMixCard
import com.example.ui.features.dashboard.analytics.DemographicsCard
import com.example.ui.features.dashboard.analytics.ExecutiveDashboard
import com.example.ui.features.dashboard.analytics.MethodMixCard
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
    val debtAging by viewModel.debtAging.collectAsState()
    val classRollCallStatuses by viewModel.classRollCallStatuses.collectAsState()
    val operationalAlerts by viewModel.operationalAlerts.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val recentPayments by viewModel.recentPayments.collectAsState()
    val attendanceTrend by viewModel.attendanceTrend.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val unreadMessages by viewModel.unreadMessages.collectAsState()

    var selectedViewTab by rememberSaveable { mutableIntStateOf(0) }
    val viewTabs = listOf("Vue d'ensemble", "Analytique")

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
                .padding(horizontal = ElTheme.spacing.lg, vertical = ElTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.lg),
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
                        modifier = Modifier.padding(end = ElTheme.spacing.sm),
                    )
                    Text(
                        text = todayFormatted,
                        style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = ElTheme.colors.textSecondary,
                    )
                }
                ElTag(
                    text = remember {
                        val y = java.time.LocalDate.now().year
                        val start = if (java.time.LocalDate.now().monthValue >= 9) y else y - 1
                        "$start–${start + 1}"
                    },
                    tone = ElTagTone.INFO,
                )
            }

            // Tab Switcher
            ElTabRow(
                tabs = viewTabs,
                selectedIndex = selectedViewTab,
                onSelected = { selectedViewTab = it },
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

                // 1. Core KPIs
                DashboardKpiCardsRow(currentKpi = currentKpi)

                // 2. Operational Quick Actions
                DashboardQuickActionsRow(
                    onNavigateToCounterPayment = { onNavigateToCounterPayment(null, null) },
                    onNavigateToBatchRegistration = onNavigateToBatchRegistration,
                    onNavigateToFinancials = onNavigateToFinancials,
                    onNavigateToAcademics = onNavigateToAcademics,
                    onNavigateToDebtDashboard = onNavigateToDebtDashboard,
                    onNavigateToChat = onNavigateToChat,
                    unreadMessages = unreadMessages,
                )

                // 3. Compact collection summary
                DashboardCollectionSummaryCard(
                    executive = currentKpi.executive,
                    onOpenAnalytics = { selectedViewTab = 1 },
                )

                // 3b. Operating rhythm
                WeeklyOperatingRhythmCard(weeklyRhythm = currentKpi.weeklyRhythm)

                // 4. Attendance & Life Overview
                DashboardAttendanceChart(
                    classStatuses = classRollCallStatuses,
                    attendanceTrend = attendanceTrend,
                    attendanceRateToday = currentKpi.attendanceRateToday,
                    classesCompleted = currentKpi.classesCompletedRollCall,
                    totalClasses = currentKpi.totalClassesCount,
                    onNavigateToRollCall = onNavigateToRollCall,
                    onNavigateToAcademics = onNavigateToAcademics,
                )

                // 5. Approvals & Checks
                DashboardApprovalsRow(
                    currentKpi = currentKpi,
                    onNavigateToFinancials = onNavigateToFinancials,
                    onNavigateToDebtDashboard = onNavigateToDebtDashboard,
                )

                // 6. Recent activity feed
                DashboardNotificationsSection(
                    notifications = notifications,
                    recentPayments = recentPayments,
                    onNavigateToFinancials = onNavigateToFinancials,
                    onNavigateToAlerts = onNavigateToAlerts,
                )

                // 7. Operational Alerts
                DashboardAlertsSection(
                    error = error,
                    alerts = operationalAlerts,
                    onNavigateToParent = onNavigateToParent,
                    onNavigateToRollCall = onNavigateToRollCall,
                    onNavigateToExpenseDetail = onNavigateToExpenseDetail,
                    onNavigateToFinancials = onNavigateToFinancials,
                    onNavigateToDebtDashboard = onNavigateToDebtDashboard,
                    onNavigateToAlerts = onNavigateToAlerts,
                )
            } else {
                // ════════════════════════════════════════════════════════════
                // ANALYTIQUE — Progressive Disclosure Executive Suite
                // ════════════════════════════════════════════════════════════

                // 1. Executive command center (Triple risk, Waves, Triage, Erosion, Concentration, Dynamics, Transport, Services)
                ExecutiveDashboard(snapshot = currentKpi.executive)

                // 2. Methods and Categories Mix Cards
                MethodMixCard(methodMix = currentKpi.methodMix)
                CategoryMixCard(categoryBreakdown = currentKpi.categoryBreakdown)

                // 3. YoY Comparison (with simulated previous year visual data) & Aging Composition
                YoYComparisonCard(yoy = currentKpi.yoy)
                AgingCompositionCard(debtByAging = currentKpi.debtByAging)

                // 4. Funnel and Debt Pareto
                DashboardCollectionAndDebtRow(
                    currentKpi = currentKpi,
                    debtAging = debtAging,
                    onNavigateToParent = onNavigateToParent,
                    onNavigateToDebtDashboard = onNavigateToDebtDashboard,
                )

                // 5. Demographics
                DemographicsCard(demographics = currentKpi.demographics)
            }

            Spacer(Modifier.height(ElTheme.spacing.lg))
        }
    }
}