package com.elimtiyaz.feature.crm

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.elimtiyaz.app.navigation.Route

/**
 * CRM feature NavGraph. Wires up the four CRM destinations behind a single
 * extension function on [NavGraphBuilder]:
 *
 *  - Route.Roster            → RosterScreen (hub tab)
 *  - Route.ParentDetail      → ParentDetailScreen
 *  - Route.StudentDetail     → StudentDetailScreen
 *  - Route.BatchRegistration → BatchRegistrationScreen
 *
 * The two detail routes declare their `{parentId}` / `{studentId}` path
 * arguments as `NavType.StringType` so the screen ViewModels can read them
 * from their `SavedStateHandle`.
 */
fun NavGraphBuilder.crmGraph(nav: NavController) {
    composable(Route.Roster.route) {
        RosterScreen(nav)
    }
    composable(
        route = Route.ParentDetail.route,
        arguments = listOf(navArgument("parentId") { type = NavType.StringType }),
    ) {
        ParentDetailScreen(nav)
    }
    composable(
        route = Route.StudentDetail.route,
        arguments = listOf(navArgument("studentId") { type = NavType.StringType }),
    ) {
        StudentDetailScreen(nav)
    }
    composable(Route.BatchRegistration.route) {
        BatchRegistrationScreen(nav)
    }
}
