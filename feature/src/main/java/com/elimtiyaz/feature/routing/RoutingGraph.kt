package com.elimtiyaz.feature.routing

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.elimtiyaz.app.navigation.Route

/**
 * Routing feature navigation graph.
 *
 * Wired from the root [com.elimtiyaz.app.navigation.ElImtiyazNavHost]. The
 * hub route ([Route.Routing]) is full-screen (not a bottom-nav destination)
 * because driver mode is reachable only from the Personnel tab when the user
 * holds [com.elimtiyaz.core.common.Permission.AccessDriverMode].
 *
 * Routes registered:
 *  - `Route.Routing`     → [RoutingScreen] (hub: vehicles + shift filter + optimise).
 *  - `Route.RoutingMap`  → [RoutingMapScreen] (full-screen osmdroid live map).
 *  - `Route.TripHistory` → [TripHistoryScreen] (past trip logs).
 */
fun NavGraphBuilder.routingGraph(nav: NavController) {
    composable(Route.Routing.route) {
        RoutingScreen(nav)
    }

    composable(
        route = Route.RoutingMap.route,
        arguments = listOf(navArgument("vehicleId") { type = NavType.StringType }),
    ) { backStack ->
        val vehicleId = backStack.arguments?.getString("vehicleId").orEmpty()
        // When the user reaches the map from the hub "Voir sur carte" button we
        // load in preview mode (no live trip, no foreground service). When they
        // arrive from "Démarrer la tournée" they want live tracking. We can't
        // tell the difference from the route alone, so we default to live mode
        // — the screen's actions stay disabled if the user has no permission.
        RoutingMapScreen(vehicleId = vehicleId, nav = nav, preview = false)
    }

    composable(Route.TripHistory.route) {
        TripHistoryScreen(nav)
    }
}
