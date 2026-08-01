package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.feedback.ElLoadingBlock
import com.example.ui.designsystem.components.nav.ElBottomBar
import com.example.ui.designsystem.components.nav.ElNavDestination
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.theme.ElTheme

// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dashboard hub screen — modern refactored version.
 *
 * Layout: `ElScaffold` with `ElTopBar` (title + subtitle + refresh action) and
 * `ElBottomBar` (5 hub destinations). The content is a vertically scrollable
 * column composed of section composables defined in sibling files:
 *   a) [DashboardAlertsSection] — error / overdue count banners
 *   b) [DashboardKpiCardsRow] — 4 KPI gradient stat cards
 *   c) [DashboardRevenueChart] — revenue trend bar chart (last 12 months)
 *   d) [DashboardCollectionAndDebtRow] — collection rate ring + debt aging donut
 *   e) [DashboardAttendanceChart] — attendance trend line chart (last 7 days)
 *   f) [DashboardNotificationsSection] — last 5 unread notifications
 *   g) [DashboardApprovalsRow] — pending expenses + overdue alerts
 *   h) [DashboardQuickActionsRow] — 5 outlined action buttons
 *
 * The loading indicator is kept inline because it is a single line of UI.
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
            // ── Loading indicator (kept inline — tiny) ──────────────────────
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ElLoadingBlock(message = "Rafraîchissement des indicateurs…")
                }
            }

            // ── (a) Alerts row ─────────────────────────────────────────────
            DashboardAlertsSection(
                error = error,
                overdueCount = currentKpi.overdueAlerts,
                onNavigateToDebtDashboard = onNavigateToDebtDashboard,
            )

            // ── (b) KPI cards row ──────────────────────────────────────────
            DashboardKpiCardsRow(currentKpi = currentKpi)

            // ── (c) Revenue trend bar chart ────────────────────────────────
            DashboardRevenueChart(revenue = revenue)

            // ── (d) Collection rate + Debt aging row ───────────────────────
            DashboardCollectionAndDebtRow(
                currentKpi = currentKpi,
                debtAging = debtAging,
            )

            // ── (e) Attendance trend line chart ────────────────────────────
            DashboardAttendanceChart(attendanceTrend = attendanceTrend)

            // ── (f) Operational alerts section ─────────────────────────────
            DashboardNotificationsSection(notifications = notifications)

            // ── (g) Pending approvals row ──────────────────────────────────
            DashboardApprovalsRow(
                currentKpi = currentKpi,
                onNavigateToFinancials = onNavigateToFinancials,
                onNavigateToDebtDashboard = onNavigateToDebtDashboard,
            )

            // ── (h) Quick actions row ──────────────────────────────────────
            DashboardQuickActionsRow(
                onNavigateToCounterPayment = onNavigateToCounterPayment,
                onNavigateToBatchRegistration = onNavigateToBatchRegistration,
                onNavigateToFinancials = onNavigateToFinancials,
                onNavigateToAcademics = onNavigateToAcademics,
                onNavigateToDebtDashboard = onNavigateToDebtDashboard,
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}
