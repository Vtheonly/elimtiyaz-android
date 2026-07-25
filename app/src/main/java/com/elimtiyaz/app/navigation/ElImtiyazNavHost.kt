package com.elimtiyaz.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elimtiyaz.core.R
import com.elimtiyaz.core.rbac.AccessRequirement
import com.elimtiyaz.core.rbac.FeatureRegistry
import com.elimtiyaz.core.rbac.GatedNavigationBarItem
import com.elimtiyaz.feature.academics.academicsGraph
import com.elimtiyaz.feature.auth.activationScreen
import com.elimtiyaz.feature.auth.forgotPasswordScreen
import com.elimtiyaz.feature.auth.loginScreen
import com.elimtiyaz.feature.auth.webPortalRedirectScreen
import com.elimtiyaz.feature.crm.crmGraph
import com.elimtiyaz.feature.dashboard.dashboardGraph
import com.elimtiyaz.feature.financials.financialsGraph
import com.elimtiyaz.feature.personnel.personnelGraph
import com.elimtiyaz.feature.routing.routingGraph
import com.elimtiyaz.feature.settings.profileScreen
import com.elimtiyaz.feature.settings.settingsGraph

private val HubRoutes = setOf(
    Route.Dashboard.route,
    Route.Roster.route,
    Route.Academics.route,
    Route.Financials.route,
    Route.Personnel.route,
)

/**
 * The application's root navigation host.
 *
 * Single NavHost with a [Scaffold] whose bottom bar is shown only on the 5 hub
 * routes. The bottom-nav items are rendered via [GatedNavigationBarItem] so
 * that any tab the current session cannot access appears greyed-out (with a
 * lock icon) instead of being hidden. This implements the user's instruction
 * that disabled features should be visible-but-locked rather than invisible.
 *
 * Auth, settings, routing, and detail screens render full-screen (no bottom bar).
 */
@Composable
fun ElImtiyazNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBottomBar = current in HubRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BottomNavItems.forEach { item ->
                        val (label, icon, requirement) = metaFor(item)
                        val selected = current == item.route
                        GatedNavigationBarItem(
                            requirement = requirement,
                            selected = selected,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = icon,
                            label = stringResource(label),
                        )
                    }
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Route.Login.route,
            modifier = Modifier.padding(inner),
        ) {
            // Auth
            loginScreen(nav)
            activationScreen(nav)
            forgotPasswordScreen(nav)
            webPortalRedirectScreen(nav)

            // 5 hub graphs (bottom nav destinations)
            dashboardGraph(nav)
            crmGraph(nav)
            academicsGraph(nav)
            financialsGraph(nav)
            personnelGraph(nav)

            // Full-screen feature graphs (no bottom nav)
            routingGraph(nav)
            settingsGraph(nav)
            profileScreen(nav)
        }
    }
}

/** Per-tab metadata: label resource + icon + access requirement. */
private data class TabMeta(val labelRes: Int, val icon: ImageVector, val requirement: AccessRequirement)

@Composable
private fun metaFor(route: Route): TabMeta = when (route) {
    Route.Dashboard  -> TabMeta(R.string.tab_home,       Icons.Outlined.Dashboard, FeatureRegistry.Dashboard.requirement)
    Route.Roster     -> TabMeta(R.string.tab_roster,     Icons.Outlined.Groups,    FeatureRegistry.Crm.requirement)
    Route.Academics  -> TabMeta(R.string.tab_academics,  Icons.Outlined.School,     FeatureRegistry.Academics.requirement)
    Route.Financials -> TabMeta(R.string.tab_financials, Icons.Outlined.Payments,   FeatureRegistry.Financials.requirement)
    Route.Personnel  -> TabMeta(R.string.tab_personnel,  Icons.Outlined.Badge,      FeatureRegistry.Personnel.requirement)
    else -> TabMeta(R.string.tab_home, Icons.Outlined.Dashboard, AccessRequirement.None)
}
