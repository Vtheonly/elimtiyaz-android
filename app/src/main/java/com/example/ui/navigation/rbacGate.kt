package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.example.core.Role
import kotlin.reflect.KClass

@Composable
internal fun rbacGate(
    navController: NavController,
    routeClass: KClass<out Route>,
    content: @Composable () -> Unit,
) {
    val session = LocalSession.current
    val required = permissionFor(routeClass)

    // Staff roles (SuperAdmin, Manager, Finance, Teachers, Support) have access
    val granted = session == null || required == null ||
        session.role == Role.SUPER_ADMIN ||
        session.role == Role.MANAGER ||
        session.role == Role.FINANCIAL_OFFICER ||
        session.role == Role.SUPPORT_STAFF ||
        session.role == Role.TEACHER ||
        session.can(required)

    if (granted) {
        content()
    } else {
        PermissionDeniedScreen(onBack = { navController.popBackStack() })
    }
}