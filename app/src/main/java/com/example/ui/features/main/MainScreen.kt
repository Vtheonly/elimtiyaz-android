package com.example.ui.features.main
import androidx.compose.runtime.saveable.listSaver
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
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

fun deepLinkTargetTabIndex(type: String, visible: List<HubTab>): Int {
    val targetPermission = when (type) {
        "payment", "expense" -> Permission.VIEW_FINANCIALS
        "absence", "grade", "homework", "calendar" -> Permission.VIEW_ACADEMICS
        "message", "chat" -> null
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    session: Session?,
    onNavigateToStudent: (String) -> Unit,
    onNavigateToParent: (String) -> Unit,
    onNavigateToBatchRegistration: () -> Unit,
    onNavigateToCounterPayment: (parentId: String?, studentId: String?) -> Unit,
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

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val safeSelected = selectedTab.coerceAtMost(visibleTabs.lastIndex)

    
    val tabHistory = rememberSaveable(
    saver = listSaver(
        save = { it.toList() },
        restore = { mutableStateListOf<Int>().apply { addAll(it) } }
    )
) { mutableStateListOf(0) }

    val selectTab: (Int) -> Unit = { index ->
        val validIndex = index.coerceIn(0, visibleTabs.lastIndex)
        if (selectedTab != validIndex) {
            tabHistory.remove(validIndex)
            tabHistory.add(validIndex)
            selectedTab = validIndex
        }
    }

    BackHandler(enabled = tabHistory.size > 1) {
        tabHistory.removeAt(tabHistory.lastIndex)
        selectedTab = tabHistory.last().coerceIn(0, visibleTabs.lastIndex)
    }

    val pendingDeepLink by NotificationDeepLink.pending.collectAsState()
    LaunchedEffect(pendingDeepLink, visibleTabs.size) {
        val link = pendingDeepLink ?: return@LaunchedEffect
        val target = deepLinkTargetTabIndex(link.type, visibleTabs).coerceIn(0, visibleTabs.lastIndex)
        if (selectedTab != target) {
            selectTab(target)
        }
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
                onTabSelected = selectTab,
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
                    onNavigateToBatchRegistration = onNavigateToBatchRegistration,
                    onNavigateToAcademics = {
                        val idx = visibleTabs.indexOfFirst { it.label == "Pédagogie" }
                        if (idx >= 0) selectTab(idx)
                    },
                    onNavigateToCrm = {
                        val idx = visibleTabs.indexOfFirst { it.label == "CRM" }
                        if (idx >= 0) selectTab(idx)
                    },
                    onNavigateToFinancials = {
                        val idx = visibleTabs.indexOfFirst { it.label == "Finances" }
                        if (idx >= 0) selectTab(idx)
                    },
                    onNavigateToPersonnel = {
                        val idx = visibleTabs.indexOfFirst { it.label == "Personnel" }
                        if (idx >= 0) selectTab(idx)
                    },
                    onNavigateToGlobalSearch = onNavigateToGlobalSearch,
                    onNavigateToReports = onNavigateToReports,
                    onNavigateToAlerts = onNavigateToAlerts,
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToRollCall = onNavigateToRollCall,
                    onNavigateToExpenseDetail = onNavigateToExpenseDetail,
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
                    onNavigateToRollCall = onNavigateToRollCall,
                    onNavigateToGradeEntry = onNavigateToGradeEntry,
                )
            }
        }
    }
}
