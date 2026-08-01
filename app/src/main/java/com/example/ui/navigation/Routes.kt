package com.example.ui.navigation

import com.example.core.Permission
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes — Navigation 2.8+ @Serializable route objects.
 *
 * Three top-level destinations:
 *   - [Splash]: shown briefly while the session is being restored
 *   - [Auth]: login + change-password
 *   - [Main]: bottom-nav host with 5 hubs
 *
 * Detail routes (student/parent/payment/expense/workflow) are pushed from
 * hub screens and carry their ID argument.
 *
 * RBAC: every guarded route is mapped to a [Permission] in [RoutePermissions].
 * The [com.example.ui.navigation.AppNavHost] wraps each guarded composable
 * in an RbacGate that redirects to [PermissionDenied] when the current
 * session lacks the permission.
 */
object Routes {
    @Serializable object Splash : Route
    @Serializable object Login : Route
    @Serializable object ChangePassword : Route
    @Serializable object Main : Route
    @Serializable object PermissionDenied : Route

    // Bottom-nav hubs (children of Main)
    @Serializable object DashboardHub : Route
    @Serializable object CrmHub : Route
    @Serializable object AcademicsHub : Route
    @Serializable object FinancialsHub : Route
    @Serializable object PersonnelHub : Route

    // CRM detail routes
    @Serializable data class StudentDetail(val studentId: String) : Route
    @Serializable data class ParentDetail(val parentId: String) : Route
    @Serializable object BatchRegistration : Route

    // Financials detail routes
    @Serializable data class PaymentDetail(val paymentId: String) : Route
    @Serializable data class ExpenseDetail(val expenseId: String) : Route
    @Serializable object CounterPayment : Route
    @Serializable object ProofScanner : Route
    @Serializable object DebtDashboard : Route
    @Serializable object InstallmentSchedule : Route

    // Settings
    @Serializable object Settings : Route
    @Serializable object AuditLog : Route
}

sealed interface Route

/**
 * Per-route RBAC matrix — maps each guarded route to the [Permission]
 * required to view it. Routes NOT in this map (e.g. [Routes.Login],
 * [Routes.Main], [Routes.Settings]) are accessible to any signed-in user.
 *
 * Used by [AppNavHost] to gate navigation: when a user lands on a guarded
 * route without the required permission, they are redirected to
 * [Routes.PermissionDenied]. The bottom-bar items are also filtered by
 * this matrix in `MainScreen.HUB_TABS` (so invisible tabs cannot be
 * tapped in the first place — defense in depth).
 *
 * Wire-protocol: keyed by the route's [KClass] so the lookup survives
 * obfuscation and is type-safe at compile time.
 */
val RoutePermissions: Map<KClass<out Route>, Permission> = mapOf(
    // Bottom-nav hubs
    Routes.DashboardHub::class to Permission.VIEW_AUDIT_LOG,
    Routes.CrmHub::class to Permission.VIEW_ROSTER,
    Routes.AcademicsHub::class to Permission.VIEW_ACADEMICS,
    Routes.FinancialsHub::class to Permission.VIEW_FINANCIALS,
    Routes.PersonnelHub::class to Permission.VIEW_PERSONNEL,

    // CRM detail routes
    Routes.StudentDetail::class to Permission.VIEW_ROSTER,
    Routes.ParentDetail::class to Permission.VIEW_ROSTER,
    Routes.BatchRegistration::class to Permission.CREATE_PARENT,

    // Financials detail routes
    Routes.PaymentDetail::class to Permission.VIEW_FINANCIALS,
    Routes.ExpenseDetail::class to Permission.VIEW_FINANCIALS,
    Routes.CounterPayment::class to Permission.COLLECT_PAYMENT,
    Routes.ProofScanner::class to Permission.SETTLE_EXPENSE_PROOF,
    Routes.DebtDashboard::class to Permission.VIEW_DEBT,
    Routes.InstallmentSchedule::class to Permission.VIEW_FINANCIALS,

    // Settings
    Routes.AuditLog::class to Permission.VIEW_AUDIT_LOG,
)

/**
 * Look up the permission required for a given route class.
 *
 * @return The required [Permission], or `null` if the route is unguarded
 *         (accessible to any signed-in user).
 */
fun permissionFor(route: KClass<out Route>): Permission? = RoutePermissions[route]
