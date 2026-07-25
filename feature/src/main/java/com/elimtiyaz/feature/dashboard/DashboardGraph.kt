package com.elimtiyaz.feature.dashboard

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.elimtiyaz.app.navigation.Route

/**
 * Wires the four dashboard-feature destinations into the root NavHost:
 *
 *  - [Route.Dashboard]     → [DashboardScreen] (bottom-nav tab #1)
 *  - [Route.Alerts]        → [AlertsScreen]
 *  - [Route.GlobalSearch]  → [GlobalSearchScreen]
 *  - [Route.Reports]       → [ReportsScreen]
 *
 * Called once from `ElImtiyazNavHost` during app start.
 */
fun NavGraphBuilder.dashboardGraph(nav: NavController) {
    composable(Route.Dashboard.route)    { DashboardScreen(nav) }
    composable(Route.Alerts.route)       { AlertsScreen(nav) }
    composable(Route.GlobalSearch.route) { GlobalSearchScreen(nav) }
    composable(Route.Reports.route)      { ReportsScreen(nav) }
}
