package com.elimtiyaz.feature.personnel

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.elimtiyaz.app.navigation.Route

/**
 * Personnel feature NavGraph. Wires the five Personnel destinations behind
 * a single extension function on [NavGraphBuilder]:
 *
 *  - [Route.Personnel]        → [PersonnelHubScreen] (bottom-nav tab #5)
 *  - [Route.PersonnelDetail]  → [PersonnelDetailScreen] (path arg `personnelId`)
 *  - [Route.Releve]           → [ReleveScreen] (path arg `personnelId`)
 *  - [Route.AuditLog]         → [AuditLogScreen]
 *  - [Route.WorkflowMonitor]  → [WorkflowMonitorScreen]
 *
 * The two detail routes declare their `{personnelId}` path argument as
 * `NavType.StringType` so the screen ViewModels can read them from their
 * `SavedStateHandle`.
 *
 * Called once from `ElImtiyazNavHost` during app start.
 */
fun NavGraphBuilder.personnelGraph(nav: NavController) {
    composable(Route.Personnel.route) {
        PersonnelHubScreen(nav)
    }

    composable(
        route = Route.PersonnelDetail.route,
        arguments = listOf(navArgument("personnelId") { type = NavType.StringType }),
    ) {
        PersonnelDetailScreen(nav)
    }

    composable(
        route = Route.Releve.route,
        arguments = listOf(navArgument("personnelId") { type = NavType.StringType }),
    ) {
        ReleveScreen(nav)
    }

    composable(Route.AuditLog.route) {
        AuditLogScreen(nav)
    }

    composable(Route.WorkflowMonitor.route) {
        WorkflowMonitorScreen(nav)
    }
}
