package com.example.ui.features.personnel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.Permission
import com.example.core.Role
import com.example.core.Session
import com.example.ui.components.ModernSecondaryTabRow

@Composable
fun PersonnelHubScreen(
    session: Session,
    onNavigateToPersonnelDetail: (String) -> Unit = {},
    onNavigateToReleve: (String) -> Unit = {},
    onNavigateToWorkflowMonitor: () -> Unit = {},
    onNavigateToAuditLog: () -> Unit,
    onNavigateToRouting: () -> Unit = {},
    onSignOut: () -> Unit,
    onNavigateToRollCall: (String) -> Unit = {},
    onNavigateToGradeEntry: (String) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val isTeacher = session.role == Role.TEACHER

    val tabs = buildList {
        if (isTeacher) add("Mon espace")
        add("Employés")
        add("Activité")
        add("Audit")
        if (session.can(Permission.ACCESS_DRIVER_MODE)) add("Tournées")
        add("Déconnexion")
    }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModernSecondaryTabRow(
            tabs = tabs,
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
        )
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            when (selectedTab) {
                0 -> if (isTeacher) {
                    TeacherWorkspaceScreen(
                        session = session,
                        onNavigateToRollCall = onNavigateToRollCall,
                        onNavigateToGradeEntry = onNavigateToGradeEntry,
                    )
                } else {
                    EmployeeDirectoryScreen(session, onNavigateToPersonnelDetail = onNavigateToPersonnelDetail)
                }
                else -> when (tabs[selectedTab]) {
                    "Employés" -> EmployeeDirectoryScreen(session, onNavigateToPersonnelDetail = onNavigateToPersonnelDetail)
                    "Activité" -> ReleveScreen(session, onNavigateToReleve = onNavigateToReleve)
                    "Audit" -> AuditStreamScreen(session, onNavigateToAuditLog = onNavigateToAuditLog)
                    "Tournées" -> DriverRoutingEntry(
                        onNavigateToRouting = onNavigateToRouting,
                        onNavigateToWorkflowMonitor = onNavigateToWorkflowMonitor,
                    )
                    "Déconnexion" -> SignOutScreen(session, onSignOut = onSignOut)
                    else -> SignOutScreen(session, onSignOut = onSignOut)
                }
            }
        }
    }
}

@Composable
private fun DriverRoutingEntry(onNavigateToRouting: () -> Unit, onNavigateToWorkflowMonitor: () -> Unit) {
    Column {
        Button(onClick = onNavigateToRouting, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.LocalShipping, contentDescription = null)
            Text(" Mode chauffeur — Tournées")
        }
        Spacer(Modifier.padding(8.dp))
        TextButton(onClick = onNavigateToWorkflowMonitor, modifier = Modifier.fillMaxWidth()) {
            Text("Moniteur de workflows")
        }
    }
}
