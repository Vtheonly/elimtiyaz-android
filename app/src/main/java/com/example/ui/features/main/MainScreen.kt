package com.example.ui.features.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Permission
import com.example.core.Role
import com.example.core.Session
import com.example.domain.repository.AuthRepository
import com.example.infrastructure.notifications.NotificationDeepLink
import com.example.session.SessionManager
import com.example.ui.components.ModernBottomNavBar
import com.example.ui.features.academics.AcademicsHubScreen
import com.example.ui.features.crm.CrmHubScreen
import com.example.ui.features.dashboard.DashboardHubScreen
import com.example.ui.features.financials.FinancialsHubScreen
import com.example.ui.features.personnel.PersonnelHubScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val session = sessionManager.state

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            sessionManager.setSession(null)
            onComplete()
        }
    }
}

data class HubTab(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val requiresPermission: Permission?,
    val requiresRole: Set<Role>? = null,
)

/**
 * PUSH-101 (T-127) — maps a notification deep-link type to the index of the
 * matching hub tab WITHIN the visible (permission-filtered) tab list.
 *
 * Mapping by the hub's REQUIRED PERMISSION (a stable identifier), never by
 * label text: financial notifications open the Finances hub
 * (VIEW_FINANCIALS), academic notifications open the Pédagogie hub
 * (VIEW_ACADEMICS), everything else opens the first visible tab. When the
 * matching hub is not visible for the current role (no permission), the
 * deep link degrades to the first tab — the RBAC matrix stays authoritative.
 *
 * Deeper routing (e.g. PaymentDetail with the payment id) is a documented
 * follow-up on the notification-type -> route mapping table; see the
 * PUSH-101 registry entry.
 */
fun deepLinkTargetTabIndex(type: String, visible: List<HubTab>): Int {
    val targetPermission = when (type) {
        "payment", "expense" -> Permission.VIEW_FINANCIALS
        "absence", "grade", "homework", "calendar" -> Permission.VIEW_ACADEMICS
        else -> null
    }
    val index = visible.indexOfFirst { it.requiresPermission == targetPermission }
    return if (index >= 0) index else 0
}

val HUB_TABS = listOf(
    HubTab("Tableau", Icons.Default.Dashboard, null, Role.DASHBOARD_ROLES),
    HubTab("CRM", Icons.Default.Group, Permission.VIEW_ROSTER, null),
    HubTab("Pédagogie", Icons.Default.MenuBook, Permission.VIEW_ACADEMICS, null),
    HubTab("Finances", Icons.Default.Payments, Permission.VIEW_FINANCIALS, null),
    HubTab("Personnel", Icons.Default.Person, Permission.VIEW_PERSONNEL, null),
)

/**
 * Main screen — bottom-nav host with 5 hub tabs.
 *
 * All navigation callbacks are passed in from [com.example.ui.navigation.AppNavHost]
 * so hub screens can drill down to detail screens without holding a NavController.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    session: Session?,
    onNavigateToStudent: (String) -> Unit,
    onNavigateToParent: (String) -> Unit,
    onNavigateToBatchRegistration: () -> Unit,
    onNavigateToCounterPayment: () -> Unit,
    onNavigateToProofScanner: () -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
    onNavigateToInstallmentSchedule: () -> Unit,
    onNavigateToExpenseSubmit: () -> Unit,
    onNavigateToExpenseDetail: (String) -> Unit,
    onNavigateToPaymentDetail: (String) -> Unit,
    onNavigateToPersonnelDetail: (String) -> Unit,
    onNavigateToReleve: (String) -> Unit,
    onNavigateToWorkflowMonitor: () -> Unit,
    onNavigateToClassDetail: (String) -> Unit,
    onNavigateToSubjectsDirectory: () -> Unit,
    onNavigateToRollCall: (String) -> Unit,
    onNavigateToGradeEntry: (String) -> Unit,
    onNavigateToHomeworkPush: (String) -> Unit,
    onNavigateToPromotionReview: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToGlobalSearch: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToChat: () -> Unit = {},
    onNavigateToRouting: () -> Unit,
    onNavigateToRoutingMap: (String) -> Unit,
    onNavigateToTripHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAuditLog: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    if (session == null) return

    val visibleTabs = HUB_TABS.filter { tab ->
        val permOk = tab.requiresPermission?.let { session.can(it) } ?: true
        val roleOk = tab.requiresRole?.let { session.role in it } ?: true
        permOk && roleOk
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val safeSelected = selectedTab.coerceAtMost(visibleTabs.lastIndex)

    // PUSH-101 (T-127): act ONCE on a pending notification deep-link by
    // selecting the hub tab that matches the notification's type. The deep
    // link survives the Splash -> (Login) -> Main flow because MainActivity
    // published it before the nav host reached Main (see NotificationDeepLink).
    val pendingDeepLink by NotificationDeepLink.pending.collectAsState()
    LaunchedEffect(pendingDeepLink, visibleTabs.size) {
        val link = pendingDeepLink ?: return@LaunchedEffect
        selectedTab = deepLinkTargetTabIndex(link.type, visibleTabs).coerceIn(0, visibleTabs.lastIndex)
        NotificationDeepLink.consume()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(visibleTabs.getOrElse(safeSelected) { visibleTabs.first() }.label) },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profil")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Dashboard, contentDescription = "Paramètres")
                    }
                },
            )
        },
        bottomBar = {
            ModernBottomNavBar(
                tabs = visibleTabs,
                selectedTabIndex = safeSelected,
                onTabSelected = { selectedTab = it },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (visibleTabs.getOrNull(safeSelected)?.label) {
                "Tableau" -> DashboardHubScreen(
                    session = session,
                    onNavigateToStudent = onNavigateToStudent,
                    onNavigateToParent = onNavigateToParent,
                    onNavigateToCounterPayment = onNavigateToCounterPayment,
                    onNavigateToDebtDashboard = onNavigateToDebtDashboard,
                    onNavigateToGlobalSearch = onNavigateToGlobalSearch,
                    onNavigateToReports = onNavigateToReports,
                    onNavigateToAlerts = onNavigateToAlerts,
                    onNavigateToChat = onNavigateToChat,
                )
                "CRM" -> CrmHubScreen(
                    session = session,
                    onNavigateToStudent = onNavigateToStudent,
                    onNavigateToParent = onNavigateToParent,
                    onNavigateToBatchRegistration = onNavigateToBatchRegistration,
                )
                "Pédagogie" -> AcademicsHubScreen(
                    session = session,
                    onNavigateToClassDetail = onNavigateToClassDetail,
                    onNavigateToSubjectsDirectory = onNavigateToSubjectsDirectory,
                    onNavigateToRollCall = onNavigateToRollCall,
                    onNavigateToGradeEntry = onNavigateToGradeEntry,
                    onNavigateToHomeworkPush = onNavigateToHomeworkPush,
                    onNavigateToPromotionReview = onNavigateToPromotionReview,
                )
                "Finances" -> FinancialsHubScreen(
                    session = session,
                    onNavigateToCounterPayment = onNavigateToCounterPayment,
                    onNavigateToProofScanner = onNavigateToProofScanner,
                    onNavigateToDebtDashboard = onNavigateToDebtDashboard,
                    onNavigateToInstallmentSchedule = onNavigateToInstallmentSchedule,
                    onNavigateToExpenseSubmit = onNavigateToExpenseSubmit,
                    onNavigateToExpenseDetail = onNavigateToExpenseDetail,
                    onNavigateToPaymentDetail = onNavigateToPaymentDetail,
                )
                "Personnel" -> PersonnelHubScreen(
                    session = session,
                    onNavigateToPersonnelDetail = onNavigateToPersonnelDetail,
                    onNavigateToReleve = onNavigateToReleve,
                    onNavigateToWorkflowMonitor = onNavigateToWorkflowMonitor,
                    onNavigateToAuditLog = onNavigateToAuditLog,
                    onNavigateToRouting = onNavigateToRouting,
                    onSignOut = { viewModel.signOut(onSignOut) },
                )
            }
        }
    }
}
