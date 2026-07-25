package com.elimtiyaz.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.elimtiyaz.app.navigation.Route

/**
 * Wires the Settings feature destination into the root NavHost.
 *
 *  - [Route.Settings] → [SettingsScreen]
 *
 * Called once from `ElImtiyazNavHost` during app start. The Settings route is
 * a full-screen destination (no bottom-nav bar) carrying its own TopAppBar
 * with a back button.
 */
fun NavGraphBuilder.settingsGraph(nav: NavController) {
    composable(Route.Settings.route) { SettingsScreen(nav) }
}

/**
 * Wires the [ProfileScreen] as a top-level composable — used directly by
 * `ElImtiyazNavHost` rather than inside a nested settings graph, so that
 * Profile is reachable from any feature's top-bar avatar without crossing
 * through the Settings stack.
 *
 *  - [Route.Profile] → [ProfileScreen]
 */
fun NavGraphBuilder.profileScreen(nav: NavController) {
    composable(Route.Profile.route) { ProfileScreen(nav) }
}
