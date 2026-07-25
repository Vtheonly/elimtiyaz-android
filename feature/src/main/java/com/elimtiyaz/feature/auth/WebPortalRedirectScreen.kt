package com.elimtiyaz.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing

/**
 * "Use the Web Portal" screen — shown when a parent or student signs in
 * to the Staff app by mistake. The platform deliberately keeps parents and
 * students on the Web Portal (master plan §02.06 + §01.06 conflict resolution).
 */
@Composable
fun WebPortalRedirectScreen(nav: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(ElimtiyazSpacing.x6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.padding(ElimtiyazSpacing.x4))
        Text(
            "Cette application est réservée au personnel.",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.padding(ElimtiyazSpacing.x2))
        Text(
            "Parents et élèves utilisent le portail web à l'adresse portal.elimtiyaz.dz",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(ElimtiyazSpacing.x6))
        Button(onClick = { nav.popBackStack(Route.Login.route, inclusive = false) }) {
            Text("Se déconnecter")
        }
    }
}

fun NavGraphBuilder.webPortalRedirectScreen(nav: NavController) {
    composable(Route.WebPortalRedirect.route) { WebPortalRedirectScreen(nav) }
}
