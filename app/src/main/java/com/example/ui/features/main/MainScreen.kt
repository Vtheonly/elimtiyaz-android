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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
    onNavigateToCounterPayment: () -> Unit,
    onNavigateToProofScanner: () -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
    onNavigateToInstallmentSchedule: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAuditLog: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    if (session == null) return

    // Filter tabs by session permissions + roles
    val visibleTabs = HUB_TABS.filter { tab ->
        val permOk = tab.requiresPermission?.let { session.can(it) } ?: true
        val roleOk = tab.requiresRole?.let { session.role in it } ?: true
        permOk && roleOk
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val safeSelected = selectedTab.coerceAtMost(visibleTabs.lastIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(visibleTabs.getOrElse(safeSelected) { visibleTabs.first() }.label) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Person, contentDescription = "Paramètres")
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
                )
                "CRM" -> CrmHubScreen(
                    session = session,
                    onNavigateToStudent = onNavigateToStudent,
                    onNavigateToParent = onNavigateToParent,
                    onNavigateToBatchRegistration = onNavigateToBatchRegistration,
                )
                "Pédagogie" -> AcademicsHubScreen(session = session)
                "Finances" -> FinancialsHubScreen(
                    session = session,
                    onNavigateToCounterPayment = onNavigateToCounterPayment,
                    onNavigateToProofScanner = onNavigateToProofScanner,
                    onNavigateToDebtDashboard = onNavigateToDebtDashboard,
                    onNavigateToInstallmentSchedule = onNavigateToInstallmentSchedule,
                )
                "Personnel" -> PersonnelHubScreen(
                    session = session,
                    onNavigateToAuditLog = onNavigateToAuditLog,
                    onSignOut = { viewModel.signOut(onSignOut) },
                )
            }
        }
    }
}
