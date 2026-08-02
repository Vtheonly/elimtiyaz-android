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
    @Serializable object ExpenseSubmit : Route
    @Serializable object ProofScanner : Route
    @Serializable object DebtDashboard : Route
    @Serializable object InstallmentSchedule : Route

    // Personnel detail routes
    @Serializable data class PersonnelDetail(val personnelId: String) : Route
    @Serializable data class Releve(val personnelId: String) : Route
    @Serializable object WorkflowMonitor : Route

    // Academics detail routes
    @Serializable data class ClassDetail(val classId: String) : Route
    @Serializable object SubjectsDirectory : Route
    @Serializable data class RollCall(val classId: String) : Route
    @Serializable data class GradeEntry(val classId: String) : Route
    @Serializable data class HomeworkPush(val classId: String) : Route

    // CRM detail routes (additions)
    @Serializable object Profile : Route
    @Serializable object GlobalSearch : Route

    // Dashboard detail routes
    @Serializable object Reports : Route
    @Serializable object Alerts : Route

    // Routing detail routes
    @Serializable object Routing : Route
    @Serializable data class RoutingMap(val vehicleId: String) : Route
    @Serializable object TripHistory : Route

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
    Routes.Profile::class to Permission.VIEW_PERSONNEL,
    Routes.GlobalSearch::class to Permission.VIEW_ROSTER,

    // Financials detail routes
    Routes.PaymentDetail::class to Permission.VIEW_FINANCIALS,
    Routes.ExpenseDetail::class to Permission.VIEW_FINANCIALS,
    Routes.CounterPayment::class to Permission.COLLECT_PAYMENT,
    Routes.ExpenseSubmit::class to Permission.SUBMIT_EXPENSE,
    Routes.ProofScanner::class to Permission.SETTLE_EXPENSE_PROOF,
    Routes.DebtDashboard::class to Permission.VIEW_DEBT,
    Routes.InstallmentSchedule::class to Permission.VIEW_FINANCIALS,

    // Personnel detail routes
    Routes.PersonnelDetail::class to Permission.VIEW_PERSONNEL,
    Routes.Releve::class to Permission.VIEW_RELEVE,
    Routes.WorkflowMonitor::class to Permission.VIEW_WORKFLOW_RUNS,

    // Academics detail routes
    Routes.ClassDetail::class to Permission.VIEW_ACADEMICS,
    Routes.SubjectsDirectory::class to Permission.VIEW_ACADEMICS,
    Routes.RollCall::class to Permission.ROLL_CALL,
    Routes.GradeEntry::class to Permission.ENTER_GRADES,
    Routes.HomeworkPush::class to Permission.ASSIGN_HOMEWORK,

    // Dashboard detail routes
    Routes.Reports::class to Permission.VIEW_AUDIT_LOG,
    Routes.Alerts::class to Permission.VIEW_PERSONNEL,

    // Routing detail routes
    Routes.Routing::class to Permission.ACCESS_DRIVER_MODE,
    Routes.RoutingMap::class to Permission.ACCESS_DRIVER_MODE,
    Routes.TripHistory::class to Permission.ACCESS_DRIVER_MODE,

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
