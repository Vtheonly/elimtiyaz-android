package com.example.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.Session
import com.example.session.SessionManager
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElScaffold
import com.example.ui.components.ElTopBar
import com.example.ui.features.academics.AcademicsHubScreen
import com.example.ui.features.auth.ChangePasswordModal
import com.example.ui.features.auth.LoginScreen
import com.example.ui.features.crm.BatchRegistrationScreen
import com.example.ui.features.crm.CrmHubScreen
import com.example.ui.features.crm.ParentDetailScreen
import com.example.ui.features.crm.StudentDetailScreen
import com.example.ui.features.dashboard.DashboardHubScreen
import com.example.ui.features.financials.CounterPaymentScreen
import com.example.ui.features.financials.DebtDashboardScreen
import com.example.ui.features.financials.ExpenseApprovalScreen
import com.example.ui.features.financials.FinancialsHubScreen
import com.example.ui.features.financials.InstallmentScheduleScreen
import com.example.ui.features.financials.ProofScannerScreen
import com.example.ui.features.main.MainScreen
import com.example.ui.features.personnel.PersonnelHubScreen
import com.example.ui.features.settings.SettingsScreen
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * Root navigation host — drives the auth gate, per-route RBAC, and
 * routes to main features.
 *
 * Auth gate logic:
 *   - If session is null → show [LoginScreen]
 *   - If session is non-null → show [MainScreen] (bottom-nav host)
 *
 * RBAC logic:
 *   - Each guarded route is mapped to a [Permission] via [RoutePermissions].
 *   - The [rbacGate] wrapper reads [LocalSession] and, if the session lacks
 *     the permission, redirects to [Routes.PermissionDenied].
 *   - Bottom-bar items are ALSO filtered by `MainScreen.HUB_TABS`, so
 *     forbidden tabs are invisible — defense in depth.
 *
 * Hilt-injected [SessionManager] is provided via the MainActivity (which
 * is @AndroidEntryPoint). Here we use a hiltViewModel for the splash
 * restore logic.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class AppNavViewModel @Inject constructor(
    val sessionManager: SessionManager,
) : androidx.lifecycle.ViewModel() {

    val sessionState = sessionManager.state

    /** Restore the session at app start (called once from [AppNavHost]). */
    fun restoreSession() {
        viewModelScope.launch {
            sessionManager.restoreSession()
        }
    }
}

@Composable
fun AppNavHost(sessionState: Session?) {
    val navController = rememberNavController()
    val viewModel: AppNavViewModel = hiltViewModel()
    val currentSession by viewModel.sessionState.collectAsState()

    // Restore session on first launch
    LaunchedEffect(Unit) {
        viewModel.restoreSession()
    }

    // Route based on session state
    val startRoute = if (currentSession == null) Routes.Login else Routes.Main

    NavHost(
        navController = navController,
        startDestination = startRoute,
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

/**
 * RBAC gate — wraps a composable destination and checks whether the
 * current session has the permission required for [routeClass].
 *
 * If the route is unguarded (not in [RoutePermissions]) the content is
 * rendered directly. If the session lacks the permission, the user is
 * redirected to [Routes.PermissionDenied] (via a one-shot [LaunchedEffect]
 * keyed on the session + permission) and the content is NOT rendered —
 * preventing any business logic in the destination from running.
 *
 * @param navController The NavHost controller used for the redirect.
 * @param routeClass The route's [KClass] — looked up in [RoutePermissions].
 * @param content The destination composable to render when access is granted.
 */
@Composable
private fun rbacGate(
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
private fun PermissionDeniedScreen(onBack: () -> Unit) {
    ElScaffold(
        topBar = { ElTopBar(title = "Accès refusé", onBack = onBack) },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            ElEmptyState(
                icon = Icons.Default.Lock,
                title = "Permission insuffisante",
                message = "Votre rôle ne vous permet pas d'accéder à cet écran. Contactez un administrateur si vous pensez qu'il s'agit d'une erreur.",
                actionText = "Retour",
                onAction = onBack,
            )
        }
    }
}
