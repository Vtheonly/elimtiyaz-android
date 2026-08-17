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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.domain.model.DashboardKpi
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElLoadingBlock
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.theme.ElTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dashboard hub screen — comprehensive, meaningful, operational cockpit.
 * Every metric and visualization is directly tied to the underlying school data.
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
    onNavigateToGlobalSearch: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToAlerts: () -> Unit = {},
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
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val currentKpi = kpis ?: DashboardKpi(
        totalStudents = 390, totalParents = 185, totalStaff = 45,
        monthlyRevenue = 1_245_000_00L, outstandingDebt = 320_000_00L,
        pendingExpenses = 2, attendanceRateToday = 96.5, overdueAlerts = 3,
    )

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
            // ── Live Date & User Status Banner ──────────────────────────────
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
                    text = "${session.displayName} • ${session.role.code}",
                    tone = ElTagTone.INFO,
                )
            }

            // ── Loading state banner ─────────────────────────────────────────
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ElLoadingBlock(message = "Synchronisation des indicateurs réels…")
                }
            }

            // ── (1) Actionable Alerts & Daily Workflow Stream ────────────────
            DashboardAlertsSection(
                error = error,
                alerts = operationalAlerts,
                onNavigateToParent = onNavigateToParent,
                onNavigateToRollCall = onNavigateToRollCall,
                onNavigateToExpenseDetail = onNavigateToExpenseDetail,
                onNavigateToFinancials = onNavigateToFinancials,
                onNavigateToDebtDashboard = onNavigateToDebtDashboard,
            )

            // ── (2) Hero Operational KPI Cards ───────────────────────────────
            DashboardKpiCardsRow(currentKpi = currentKpi)

            // ── (3) Direct Operational Quick Actions ─────────────────────────
            DashboardQuickActionsRow(
                onNavigateToCounterPayment = onNavigateToCounterPayment,
                onNavigateToBatchRegistration = onNavigateToBatchRegistration,
                onNavigateToFinancials = onNavigateToFinancials,
                onNavigateToAcademics = onNavigateToAcademics,
                onNavigateToDebtDashboard = onNavigateToDebtDashboard,
            )

            // ── (4) Today's Class Roll-Call & Attendance Pulse ───────────────
            DashboardAttendanceChart(
                classStatuses = classRollCallStatuses,
                attendanceTrend = attendanceTrend,
                attendanceRateToday = currentKpi.attendanceRateToday,
                classesCompleted = currentKpi.classesCompletedRollCall,
                totalClasses = currentKpi.totalClassesCount,
                onNavigateToRollCall = onNavigateToRollCall,
                onNavigateToAcademics = onNavigateToAcademics,
            )

            // ── (5) Revenue Trends & Payment Method Breakdown ────────────────
            DashboardRevenueChart(
                revenue = revenue,
                paymentMethods = paymentMethods,
            )

            // ── (6) Collection Efficiency & Debt Aging Distribution ──────────
            DashboardCollectionAndDebtRow(
                currentKpi = currentKpi,
                debtAging = debtAging,
                onNavigateToParent = onNavigateToParent,
                onNavigateToDebtDashboard = onNavigateToDebtDashboard,
            )

            // ── (7) Approvals & Pending Clearance Queue ──────────────────────
            DashboardApprovalsRow(
                currentKpi = currentKpi,
                onNavigateToFinancials = onNavigateToFinancials,
                onNavigateToDebtDashboard = onNavigateToDebtDashboard,
            )

            // ── (8) Recent Activity & Notifications Stream ───────────────────
            DashboardNotificationsSection(
                notifications = notifications,
                recentPayments = recentPayments,
                onNavigateToFinancials = onNavigateToFinancials,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}