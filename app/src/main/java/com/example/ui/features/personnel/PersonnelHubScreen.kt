package com.example.ui.features.personnel

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.Permission
import com.example.core.Role
import com.example.core.Session
import com.example.ui.components.ModernSecondaryTabRow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

/**
 * Personnel hub — restored navigation callbacks for personnel detail + workflow monitor + routing.
 *
 * T-237 / RBAC-300 (35th session): teachers get a FIRST tab — "Mon espace" —
 * the dedicated in-Personnel workspace (TeacherWorkspaceScreen: my homeroom
 * classes → Appel/Notes). The teacher role lost VIEW_ROSTER/VIEW_ACADEMICS
 * in core/Rbac.kt, so the CRM and Pédagogie bottom-nav hub tabs are no
 * longer visible for teachers; this workspace is their single pedagogical
 * entry point (desktop T-235 mirror).
 *
 * Non-teacher staff see the original layout: Employés / Activité / Audit /
 * (+ Tournées for drivers) / Déconnexion.
 */
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
    var selectedTab by remember { mutableIntStateOf(0) }
    val isTeacher = session.role == Role.TEACHER

    // Tab layout: teachers get "Mon espace" as their first tab; everyone
    // keeps the shared directory/activity/audit tabs.
    val tabs = buildList {
        if (isTeacher) add("Mon espace")
        add("Employés")
        add("Activité")
        add("Audit")
        if (session.can(Permission.ACCESS_DRIVER_MODE)) add("Tournées")
        add("Déconnexion")
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
                // ── Teacher workspace (T-237) ─────────────────────────────
                0 -> if (isTeacher) {
                    TeacherWorkspaceScreen(
                        session = session,
                        onNavigateToRollCall = onNavigateToRollCall,
                        onNavigateToGradeEntry = onNavigateToGradeEntry,
                    )
                } else {
                    // Non-teacher: index 0 = Employés (original layout).
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
