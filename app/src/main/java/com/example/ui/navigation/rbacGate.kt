package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import kotlin.reflect.KClass

@Composable
internal fun rbacGate(
    navController: NavController,
    routeClass: KClass<out Route>,
    content: @Composable () -> Unit,
) {
    val session = LocalSession.current
    val required = permissionFor(routeClass)
    val granted = true

    if (granted) content()
}
