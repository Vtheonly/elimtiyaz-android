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
import com.example.ui.features.academics.ClassDetailScreen
import com.example.ui.features.academics.GradeEntryScreen
import com.example.ui.features.academics.HomeworkPushScreen
// ARCH-007 fix (T-081): the import was missing — the
// `Routes.PromotionReview` composable below references
// PromotionReviewScreen, so the module did not compile.
import com.example.ui.features.academics.PromotionReviewScreen
import com.example.ui.features.academics.RollCallScreen
import com.example.ui.features.academics.SubjectsDirectoryScreen
import com.example.ui.features.auth.ChangePasswordModal
import com.example.ui.features.auth.LoginScreen
import com.example.ui.features.crm.BatchRegistrationScreen
import com.example.ui.features.crm.ParentDetailScreen
import com.example.ui.features.crm.StudentDetailScreen
import com.example.ui.features.dashboard.AlertsScreen
import com.example.ui.features.dashboard.DashboardHubScreen
import com.example.ui.features.dashboard.GlobalSearchScreen
import com.example.ui.features.dashboard.ReportsScreen
import com.example.ui.features.financials.CounterPaymentScreen
import com.example.ui.features.financials.DebtDashboardScreen
import com.example.ui.features.financials.ExpenseApprovalScreen
import com.example.ui.features.financials.ExpenseSubmitScreen
import com.example.ui.features.financials.InstallmentScheduleScreen
import com.example.ui.features.financials.PaymentDetailScreen
import com.example.ui.features.financials.ProofScannerScreen
import com.example.ui.features.main.MainScreen
import com.example.ui.features.personnel.PersonnelDetailScreen
import com.example.ui.features.personnel.ReleveScreen
import com.example.ui.features.personnel.WorkflowMonitorScreen
import com.example.ui.features.profile.ProfileScreen
import com.example.ui.features.routing.RoutingMapScreen
import com.example.ui.features.routing.RoutingScreen
import com.example.ui.features.routing.TripHistoryScreen
import com.example.ui.features.settings.SettingsScreen
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue

/**
 * Top-level navigation host.
 *
 * Wires every route declared in [Routes] to its composable destination.
 * Each guarded route is wrapped in [rbacGate] which redirects to
 * [Routes.PermissionDenied] when the current session lacks the required
 * [Permission] (defined in [RoutePermissions]).
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val viewModel: AppNavViewModel = hiltViewModel()
    val currentSession by viewModel.sessionState.collectAsState()

    // Restore any persisted Supabase JWT exactly once on startup.
    // The restored session will propagate through `currentSession` and the
    // LaunchedEffect below will route to Main.
    LaunchedEffect(Unit) { viewModel.restoreSession() }

    // Single navigation effect — routes to Main the moment a session appears,
    // and ONLY if we're still on Login. This replaces the previous two
    // competing LaunchedEffects (one observing `currentSession`, one in
    // LoginScreen observing `state.signedIn`) that caused duplicate
    // navigate() calls and froze the back stack.
    //
    // `restoreState = true` + `launchSingleTop = true` makes the navigation
    // idempotent: if Main is already at the top, this is a no-op.
    LaunchedEffect(currentSession) {
        if (currentSession != null) {
            val current = navController.currentDestination?.route
            // Type-safe routes use the fully-qualified class name as the route
            // string. Only navigate if we're on Login (or have no destination
            // yet — happens during the very first composition).
            val isOnLogin = current == null ||
                current.contains("Login") ||
                current.contains("Splash") ||
                current.contains("PermissionDenied")
            if (isOnLogin) {
                navController.navigate(Routes.Main) {
                    popUpTo(Routes.Login) { inclusive = true }
                    launchSingleTop = true
                    restoreState = true
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
                    onNavigateToExpenseSubmit = { navController.navigate(Routes.ExpenseSubmit) },
                    onNavigateToExpenseDetail = { id -> navController.navigate(Routes.ExpenseDetail(id)) },
                    onNavigateToPaymentDetail = { id -> navController.navigate(Routes.PaymentDetail(id)) },
                    onNavigateToPersonnelDetail = { id -> navController.navigate(Routes.PersonnelDetail(id)) },
                    onNavigateToReleve = { id -> navController.navigate(Routes.Releve(id)) },
                    onNavigateToWorkflowMonitor = { navController.navigate(Routes.WorkflowMonitor) },
                    onNavigateToClassDetail = { id -> navController.navigate(Routes.ClassDetail(id)) },
                    onNavigateToSubjectsDirectory = { navController.navigate(Routes.SubjectsDirectory) },
                    onNavigateToRollCall = { id -> navController.navigate(Routes.RollCall(id)) },
                    onNavigateToGradeEntry = { id -> navController.navigate(Routes.GradeEntry(id)) },
                    onNavigateToHomeworkPush = { id -> navController.navigate(Routes.HomeworkPush(id)) },
                    onNavigateToPromotionReview = { id -> navController.navigate(Routes.PromotionReview(id)) },
                    onNavigateToProfile = { navController.navigate(Routes.Profile) },
                    onNavigateToGlobalSearch = { navController.navigate(Routes.GlobalSearch) },
                    onNavigateToReports = { navController.navigate(Routes.Reports) },
                    onNavigateToAlerts = { navController.navigate(Routes.Alerts) },
                    onNavigateToRouting = { navController.navigate(Routes.Routing) },
                    onNavigateToRoutingMap = { id -> navController.navigate(Routes.RoutingMap(id)) },
                    onNavigateToTripHistory = { navController.navigate(Routes.TripHistory) },
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

        // ── CRM detail routes ────────────────────────────────────────────
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
                    // FIX (dead children list): tapping a child now opens its
                    // dossier (parity with global search + desktop drawer).
                    onOpenStudent = { id -> navController.navigate(Routes.StudentDetail(id)) },
                )
            }
        }
        composable<Routes.BatchRegistration> {
            rbacGate(navController, Routes.BatchRegistration::class) {
                BatchRegistrationScreen(
                    onSuccess = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable<Routes.Profile> {
            rbacGate(navController, Routes.Profile::class) {
                ProfileScreen(
                    onBack = { navController.popBackStack() },
                    onChangePassword = { navController.navigate(Routes.ChangePassword) },
                    onSignOut = {
                        navController.navigate(Routes.Login) {
                            popUpTo(Routes.Main) { inclusive = true }
                        }
                    },
                )
            }
        }
        composable<Routes.GlobalSearch> {
            rbacGate(navController, Routes.GlobalSearch::class) {
                GlobalSearchScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToParent = { id -> navController.navigate(Routes.ParentDetail(id)) },
                    onNavigateToStudent = { id -> navController.navigate(Routes.StudentDetail(id)) },
                )
            }
        }

        // ── Financials detail routes ─────────────────────────────────────
        composable<Routes.CounterPayment> {
            rbacGate(navController, Routes.CounterPayment::class) {
                CounterPaymentScreen(onBack = { navController.popBackStack() })
            }
        }
        composable<Routes.ExpenseSubmit> {
            rbacGate(navController, Routes.ExpenseSubmit::class) {
                ExpenseSubmitScreen(onBack = { navController.popBackStack() })
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
                    onNavigateToProofScanner = { navController.navigate(Routes.ProofScanner) },
                )
            }
        }
        composable<Routes.PaymentDetail> { backStackEntry ->
            rbacGate(navController, Routes.PaymentDetail::class) {
                val route: Routes.PaymentDetail = backStackEntry.toRoute()
                PaymentDetailScreen(
                    paymentId = route.paymentId,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // ── Personnel detail routes ──────────────────────────────────────
        composable<Routes.PersonnelDetail> { backStackEntry ->
            rbacGate(navController, Routes.PersonnelDetail::class) {
                val route: Routes.PersonnelDetail = backStackEntry.toRoute()
                PersonnelDetailScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToReleve = { id -> navController.navigate(Routes.Releve(id)) },
                )
            }
        }
        composable<Routes.WorkflowMonitor> {
            rbacGate(navController, Routes.WorkflowMonitor::class) {
                WorkflowMonitorScreen(onBack = { navController.popBackStack() })
            }
        }

        // FIX (P0 crash): Routes.Releve was navigated to (from Main and from
        // PersonnelDetail) but never registered — every tap crashed with
        // "navigation destination cannot be found".
        composable<Routes.Releve> {
            rbacGate(navController, Routes.Releve::class) {
                val session = LocalSession.current ?: return@rbacGate
                ReleveScreen(
                    session = session,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // ── Academics detail routes ──────────────────────────────────────
        composable<Routes.ClassDetail> { backStackEntry ->
            rbacGate(navController, Routes.ClassDetail::class) {
                val route: Routes.ClassDetail = backStackEntry.toRoute()
                ClassDetailScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToStudent = { id -> navController.navigate(Routes.StudentDetail(id)) },
                    onNavigateToRollCall = { id -> navController.navigate(Routes.RollCall(id)) },
                    onNavigateToGradeEntry = { id -> navController.navigate(Routes.GradeEntry(id)) },
                    onNavigateToHomeworkPush = { id -> navController.navigate(Routes.HomeworkPush(id)) },
                )
            }
        }
        composable<Routes.SubjectsDirectory> {
            rbacGate(navController, Routes.SubjectsDirectory::class) {
                SubjectsDirectoryScreen(onBack = { navController.popBackStack() })
            }
        }

        // FIX (P0 crash): RollCall / GradeEntry / HomeworkPush were navigated
        // to (from Main hub shortcuts and ClassDetail's action icons) but
        // never registered — every tap crashed with
        // "navigation destination cannot be found".
        composable<Routes.RollCall> { backStackEntry ->
            rbacGate(navController, Routes.RollCall::class) {
                val route: Routes.RollCall = backStackEntry.toRoute()
                val session = LocalSession.current ?: return@rbacGate
                RollCallScreen(
                    session = session,
                    initialClassId = route.classId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable<Routes.GradeEntry> { backStackEntry ->
            rbacGate(navController, Routes.GradeEntry::class) {
                val route: Routes.GradeEntry = backStackEntry.toRoute()
                val session = LocalSession.current ?: return@rbacGate
                GradeEntryScreen(
                    session = session,
                    initialClassId = route.classId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable<Routes.HomeworkPush> { backStackEntry ->
            rbacGate(navController, Routes.HomeworkPush::class) {
                val route: Routes.HomeworkPush = backStackEntry.toRoute()
                val session = LocalSession.current ?: return@rbacGate
                HomeworkPushScreen(
                    session = session,
                    initialClassId = route.classId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        // Vault §06.04 — GPA-driven promotion review queue (admin overrides
        // before the one-click batch execution).
        composable<Routes.PromotionReview> { backStackEntry ->
            rbacGate(navController, Routes.PromotionReview::class) {
                val route: Routes.PromotionReview = backStackEntry.toRoute()
                PromotionReviewScreen(
                    classId = route.classId,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // ── Dashboard detail routes ──────────────────────────────────────
        composable<Routes.Reports> {
            rbacGate(navController, Routes.Reports::class) {
                ReportsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToAuditLog = { navController.navigate(Routes.AuditLog) },
                )
            }
        }
        composable<Routes.Alerts> {
            rbacGate(navController, Routes.Alerts::class) {
                AlertsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToEntity = { type, id ->
                        // Route to the appropriate detail screen based on entityType
                        when (type) {
                            "parent" -> navController.navigate(Routes.ParentDetail(id))
                            "student" -> navController.navigate(Routes.StudentDetail(id))
                            "payment" -> navController.navigate(Routes.PaymentDetail(id))
                            "expense" -> navController.navigate(Routes.ExpenseDetail(id))
                        }
                    },
                )
            }
        }

        // ── Routing detail routes ────────────────────────────────────────
        composable<Routes.Routing> {
            rbacGate(navController, Routes.Routing::class) {
                RoutingScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToRoutingMap = { id -> navController.navigate(Routes.RoutingMap(id)) },
                    onNavigateToTripHistory = { navController.navigate(Routes.TripHistory) },
                )
            }
        }
        composable<Routes.RoutingMap> { backStackEntry ->
            rbacGate(navController, Routes.RoutingMap::class) {
                val route: Routes.RoutingMap = backStackEntry.toRoute()
                RoutingMapScreen(
                    onBack = { navController.popBackStack() },
                    onTripEnded = { navController.popBackStack() },
                )
            }
        }
        composable<Routes.TripHistory> {
            rbacGate(navController, Routes.TripHistory::class) {
                TripHistoryScreen(onBack = { navController.popBackStack() })
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

        // ── Permission denied ────────────────────────────────────────────
        composable<Routes.PermissionDenied> {
            PermissionDeniedScreen(onBack = { navController.popBackStack() })
        }
    }
}
