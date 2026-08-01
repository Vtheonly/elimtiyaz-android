package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.session.SessionManager
import com.example.ui.features.auth.ChangePasswordModal
import com.example.ui.features.auth.LoginScreen
import com.example.ui.features.crm.BatchRegistrationScreen
import com.example.ui.features.crm.ParentDetailScreen
import com.example.ui.features.crm.StudentDetailScreen
import com.example.ui.features.dashboard.DashboardHubScreen
import com.example.ui.features.financials.CounterPaymentScreen
import com.example.ui.features.financials.DebtDashboardScreen
import com.example.ui.features.financials.ExpenseApprovalScreen
import com.example.ui.features.financials.InstallmentScheduleScreen
import com.example.ui.features.financials.ProofScannerScreen
import com.example.ui.features.main.MainScreen
import com.example.ui.features.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val viewModel: AppNavViewModel = hiltViewModel()
    val currentSession by viewModel.sessionState.collectAsState()

    // Restore session on first launch. The SessionManager will propagate
    // the restored session via setSession() (bugfix iter 2), which updates
    // currentSession. The LaunchedEffect below handles post-restore routing.
    LaunchedEffect(Unit) {
        viewModel.restoreSession()
    }

    // Always start at Login; if a session is restored asynchronously,
    // navigate to Main. This avoids the startDestination race that occurs
    // when currentSession is null at composition time but becomes non-null
    // shortly after.
    LaunchedEffect(currentSession) {
        if (currentSession != null) {
            val current = navController.currentDestination?.route
            if (current == null || current.contains("Login") || current.contains("Splash")) {
                navController.navigate(Routes.Main) {
                    popUpTo(Routes.Login) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Login,
    ) {
        composable<Routes.Login> {
            CompositionLocalProvider(LocalSession provides currentSession) {
                LoginScreen(
                    onSignedIn = {
                        navController.navigate(Routes.Main) {
                            popUpTo(Routes.Login) { inclusive = true }
                        }
                    },
                    onChangePassword = { navController.navigate(Routes.ChangePassword) },
                )
            }
        }

        composable<Routes.ChangePassword> {
            ChangePasswordModal(onDismiss = { navController.popBackStack() })
        }

        composable<Routes.Main> {
            CompositionLocalProvider(LocalSession provides currentSession) {
                MainScreen(
                    session = currentSession,
                    onNavigateToStudent = { id -> navController.navigate(Routes.StudentDetail(id)) },
                    onNavigateToParent = { id -> navController.navigate(Routes.ParentDetail(id)) },
                    onNavigateToBatchRegistration = { navController.navigate(Routes.BatchRegistration) },
                    onNavigateToCounterPayment = { navController.navigate(Routes.CounterPayment) },
                    onNavigateToProofScanner = { navController.navigate(Routes.ProofScanner) },
                    onNavigateToDebtDashboard = { navController.navigate(Routes.DebtDashboard) },
                    onNavigateToInstallmentSchedule = { navController.navigate(Routes.InstallmentSchedule) },
                    onNavigateToSettings = { navController.navigate(Routes.Settings) },
                    onNavigateToAuditLog = { navController.navigate(Routes.AuditLog) },
                    onSignOut = {
                        navController.navigate(Routes.Login) {
                            popUpTo(Routes.Main) { inclusive = true }
                        }
                    },
                )
            }
        }

        // ── CRM detail routes (RBAC: VIEW_ROSTER) ────────────────────────
        composable<Routes.StudentDetail> { backStackEntry ->
            rbacGate(navController, Routes.StudentDetail::class) {
                val route: Routes.StudentDetail = backStackEntry.toRoute()
                StudentDetailScreen(
                    studentId = route.studentId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable<Routes.ParentDetail> { backStackEntry ->
            rbacGate(navController, Routes.ParentDetail::class) {
                val route: Routes.ParentDetail = backStackEntry.toRoute()
                ParentDetailScreen(
                    parentId = route.parentId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable<Routes.BatchRegistration> {
            rbacGate(navController, Routes.BatchRegistration::class) {
                BatchRegistrationScreen(onSuccess = { navController.popBackStack() })
            }
        }

        // ── Financials detail routes ─────────────────────────────────────
        composable<Routes.CounterPayment> {
            rbacGate(navController, Routes.CounterPayment::class) {
                CounterPaymentScreen(onBack = { navController.popBackStack() })
            }
        }
        composable<Routes.ProofScanner> {
            rbacGate(navController, Routes.ProofScanner::class) {
                ProofScannerScreen(onBack = { navController.popBackStack() })
            }
        }
        composable<Routes.DebtDashboard> {
            rbacGate(navController, Routes.DebtDashboard::class) {
                DebtDashboardScreen(onBack = { navController.popBackStack() })
            }
        }
        composable<Routes.InstallmentSchedule> {
            rbacGate(navController, Routes.InstallmentSchedule::class) {
                InstallmentScheduleScreen(onBack = { navController.popBackStack() })
            }
        }
        composable<Routes.ExpenseDetail> { backStackEntry ->
            rbacGate(navController, Routes.ExpenseDetail::class) {
                val route: Routes.ExpenseDetail = backStackEntry.toRoute()
                ExpenseApprovalScreen(
                    expenseId = route.expenseId,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // ── Payment detail (RBAC: VIEW_FINANCIALS) ───────────────────────
        // Renders a read-only receipt view of a collected payment. Linked
        // from DashboardHubScreen ("Flux des Encaissements" feed) and from
        // ParentDetailScreen. The screen itself lives in PaymentDetailScreen.kt.
        composable<Routes.PaymentDetail> { backStackEntry ->
            rbacGate(navController, Routes.PaymentDetail::class) {
                val route: Routes.PaymentDetail = backStackEntry.toRoute()
                com.example.ui.features.financials.PaymentDetailScreen(
                    paymentId = route.paymentId,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // ── Settings ─────────────────────────────────────────────────────
        composable<Routes.Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAuditLog = { navController.navigate(Routes.AuditLog) },
                onSignOut = {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Main) { inclusive = true }
                    }
                },
            )
        }
        composable<Routes.AuditLog> {
            rbacGate(navController, Routes.AuditLog::class) {
                com.example.ui.features.settings.AuditLogScreen(onBack = { navController.popBackStack() })
            }
        }

        // ── Permission denied (destination for RBAC redirects) ───────────
        composable<Routes.PermissionDenied> {
            PermissionDeniedScreen(onBack = { navController.popBackStack() })
        }
    }
}
