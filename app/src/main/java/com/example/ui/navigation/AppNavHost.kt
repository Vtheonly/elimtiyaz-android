package com.example.ui.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.Session
import com.example.session.SessionManager
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

/**
 * Root navigation host — drives the auth gate and routes to main features.
 *
 * Auth gate logic:
 *   - If session is null → show [LoginScreen]
 *   - If session is non-null → show [MainScreen] (bottom-nav host)
 *
 * The session state is observed from [SessionManager.state]; when it flips
 * to null (sign-out, expiry), the app navigates back to login automatically.
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

    fun restoreSession() {
        viewModelScopeRestore()
    }

    private fun viewModelScopeRestore() {
        // Trigger session restore on first composition
        kotlinx.coroutines.MainScope().launch {
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

        // CRM detail routes
        composable<Routes.StudentDetail> { backStackEntry ->
            val route: Routes.StudentDetail = backStackEntry.toRoute()
            StudentDetailScreen(
                studentId = route.studentId,
                onBack = { navController.popBackStack() },
            )
        }
        composable<Routes.ParentDetail> { backStackEntry ->
            val route: Routes.ParentDetail = backStackEntry.toRoute()
            ParentDetailScreen(
                parentId = route.parentId,
                onBack = { navController.popBackStack() },
            )
        }
        composable<Routes.BatchRegistration> {
            BatchRegistrationScreen(onSuccess = { navController.popBackStack() })
        }

        // Financials detail routes
        composable<Routes.CounterPayment> {
            CounterPaymentScreen(onBack = { navController.popBackStack() })
        }
        composable<Routes.ProofScanner> {
            ProofScannerScreen(onBack = { navController.popBackStack() })
        }
        composable<Routes.DebtDashboard> {
            DebtDashboardScreen(onBack = { navController.popBackStack() })
        }
        composable<Routes.InstallmentSchedule> {
            InstallmentScheduleScreen(onBack = { navController.popBackStack() })
        }
        composable<Routes.ExpenseDetail> { backStackEntry ->
            val route: Routes.ExpenseDetail = backStackEntry.toRoute()
            ExpenseApprovalScreen(
                expenseId = route.expenseId,
                onBack = { navController.popBackStack() },
            )
        }

        // Settings
        composable<Routes.Settings> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<Routes.AuditLog> {
            com.example.ui.features.settings.AuditLogScreen(onBack = { navController.popBackStack() })
        }
    }
}
