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
    val granted = required == null || session?.can(required) == true

    LaunchedEffect(granted, required, session?.userId) {
        if (!granted) {
            navController.navigate(Routes.PermissionDenied) {
                launchSingleTop = true
            }
        }
    }

    if (granted) content()
}

/**
 * Permission-denied destination — shown when a user navigates to a route
 * their role lacks permission for. Provides a single "back" action.
 */
@Composable
