package com.elimtiyaz.app.navigation

/**
 * All navigation routes in a single sealed enum so the NavHost and feature
 * modules can reference them type-safely. The `route` field is the path
 * used in `navController.navigate(route)`.
 *
 * Bottom-nav destinations (the 5 hubs from the master plan §03.05) live
 * alongside the deep-linkable feature destinations. Each feature module
 * exposes its own nested NavGraph builder function (e.g. `DashboardGraph`)
 * which the root NavHost calls.
 */
sealed class Route(val route: String) {
    // Auth flow
    data object Splash     : Route("splash")
    data object Login      : Route("login")
    data object Activation : Route("activation/{email}") {
        fun build(email: String) = "activation/$email"
    }
    data object Forgot     : Route("forgot")
    data object WebPortalRedirect : Route("web-portal-redirect")

    // Root hub (5-tab bottom nav)
    data object Root       : Route("root")

    // Hub-level tabs (used by Scaffold bottom bar)
    data object Dashboard  : Route("dashboard")
    data object Roster     : Route("roster")
    data object Academics  : Route("academics")
    data object Financials : Route("financials")
    data object Personnel  : Route("personnel")

    // Dashboard sub-routes (alerts center, global search, reports catalog)
    data object Alerts       : Route("alerts")
    data object GlobalSearch : Route("global-search")
    data object Reports      : Route("reports")

    // CRM
    data object ParentDetail     : Route("parent/{parentId}") {
        fun build(parentId: String) = "parent/$parentId"
    }
    data object StudentDetail    : Route("student/{studentId}") {
        fun build(studentId: String) = "student/$studentId"
    }
    data object BatchRegistration: Route("batch-registration")

    // Academics
    data object ClassDetail      : Route("class/{classId}") {
        fun build(classId: String) = "class/$classId"
    }
    data object RollCall         : Route("roll-call/{classId}") {
        fun build(classId: String) = "roll-call/$classId"
    }
    data object GradeEntry       : Route("grade-entry/{classId}/{subjectId}") {
        fun build(classId: String, subjectId: String) = "grade-entry/$classId/$subjectId"
    }
    data object HomeworkPush     : Route("homework-push/{classId}") {
        fun build(classId: String) = "homework-push/$classId"
    }

    // Financials
    data object CounterPayment   : Route(
        "counter-payment?parentId={parentId}&studentId={studentId}&installmentId={installmentId}&category={category}&amount={amount}"
    ) {
        /** Build a counter-payment route, optionally pre-filled from the Installments screen. */
        fun build(
            parentId: String? = null,
            studentId: String? = null,
            installmentId: String? = null,
            category: String? = null,
            amount: String? = null,
        ): String {
            val params = listOfNotNull(
                parentId?.let { "parentId=$it" },
                studentId?.let { "studentId=$it" },
                installmentId?.let { "installmentId=$it" },
                category?.let { "category=$it" },
                amount?.let { "amount=$it" },
            ).joinToString("&")
            return if (params.isEmpty()) "counter-payment" else "counter-payment?$params"
        }
    }
    data object PaymentDetail    : Route("payment/{paymentId}") {
        fun build(paymentId: String) = "payment/$paymentId"
    }
    data object Installments     : Route("installments/{parentId}") {
        fun build(parentId: String) = "installments/$parentId"
    }
    data object DebtDashboard    : Route("debt-dashboard")
    data object ExpenseDetail    : Route("expense/{expenseId}") {
        fun build(expenseId: String) = "expense/$expenseId"
    }
    data object ExpenseSubmit    : Route("expense-submit")

    // Personnel
    data object PersonnelDetail  : Route("personnel/{personnelId}") {
        fun build(personnelId: String) = "personnel/$personnelId"
    }
    data object Releve           : Route("releve/{personnelId}") {
        fun build(personnelId: String) = "releve/$personnelId"
    }
    data object AuditLog         : Route("audit-log")
    data object WorkflowMonitor  : Route("workflow-monitor")

    // Routing
    data object Routing          : Route("routing")
    data object RoutingMap       : Route("routing-map/{vehicleId}") {
        fun build(vehicleId: String) = "routing-map/$vehicleId"
    }
    data object TripHistory      : Route("trip-history")

    // Profile / Settings
    data object Profile          : Route("profile")
    data object Settings         : Route("settings")
}

/** Bottom-nav items in canonical order. */
val BottomNavItems: List<Route> = listOf(
    Route.Dashboard,
    Route.Roster,
    Route.Academics,
    Route.Financials,
    Route.Personnel,
)
