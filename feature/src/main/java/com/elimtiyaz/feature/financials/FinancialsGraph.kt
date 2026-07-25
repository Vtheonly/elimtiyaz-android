package com.elimtiyaz.feature.financials

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.elimtiyaz.app.navigation.Route

/**
 * Registers every Financials-feature destination on the root NavHost.
 *
 * Routes registered:
 *  - `Route.Financials`      → [FinancialsHubScreen] (hub tab — bottom nav)
 *  - `Route.CounterPayment`  → [CounterPaymentScreen] (full-screen form)
 *  - `Route.PaymentDetail`   → [PaymentDetailScreen]
 *  - `Route.Installments`    → [InstallmentScheduleScreen]
 *  - `Route.DebtDashboard`   → [DebtDashboardScreen]
 *  - `Route.ExpenseDetail`   → [ExpenseDetailScreen]
 *  - `Route.ExpenseSubmit`   → [ExpenseSubmitScreen]
 *
 * Optional query parameters on the CounterPayment route (`parentId`,
 * `studentId`, `installmentId`, `category`, `amount`) are declared nullable
 * so the Installments "Encaisser" action can pre-fill the form.
 */
fun NavGraphBuilder.financialsGraph(nav: NavController) {
    composable(Route.Financials.route) {
        FinancialsHubScreen(nav)
    }

    composable(
        route = Route.CounterPayment.route,
        arguments = listOf(
            navArgument("parentId")     { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("studentId")    { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("installmentId"){ type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("category")     { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("amount")       { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) {
        CounterPaymentScreen(nav)
    }

    composable(
        route = Route.PaymentDetail.route,
        arguments = listOf(navArgument("paymentId") { type = NavType.StringType }),
    ) {
        PaymentDetailScreen(nav)
    }

    composable(
        route = Route.Installments.route,
        arguments = listOf(navArgument("parentId") { type = NavType.StringType }),
    ) {
        InstallmentScheduleScreen(nav)
    }

    composable(Route.DebtDashboard.route) {
        DebtDashboardScreen(nav)
    }

    composable(
        route = Route.ExpenseDetail.route,
        arguments = listOf(navArgument("expenseId") { type = NavType.StringType }),
    ) {
        ExpenseDetailScreen(nav)
    }

    composable(Route.ExpenseSubmit.route) {
        ExpenseSubmitScreen(nav)
    }
}
