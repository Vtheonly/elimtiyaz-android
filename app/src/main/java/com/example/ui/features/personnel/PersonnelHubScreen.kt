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
import com.example.core.Session
import com.example.ui.components.ModernSecondaryTabRow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

/**
 * Personnel hub — restored navigation callbacks for personnel detail + workflow monitor + routing.
 *
 * 4-tab layout: Employés / Activité / Audit / Déconnexion.
 *
 * The Driver role sees a 5th implicit action — a "Tournées" button in the
 * Employés tab header — gated by [Permission.ACCESS_DRIVER_MODE].
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
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = if (session.can(Permission.ACCESS_DRIVER_MODE)) {
        listOf("Employés", "Activité", "Audit", "Tournées", "Déconnexion")
    } else {
        listOf("Employés", "Activité", "Audit", "Déconnexion")
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
            // FIX (dead conditional): was `if (session.can(ACCESS_DRIVER_MODE))
            // selectedTab else selectedTab` — both branches identical. The tab
            // INDEX layout below already accounts for the conditional 5th
            // "Tournées" tab, so the selected index maps directly.
            when (selectedTab) {
                0 -> EmployeeDirectoryScreen(session, onNavigateToPersonnelDetail = onNavigateToPersonnelDetail)
                1 -> ReleveScreen(session, onNavigateToReleve = onNavigateToReleve)
                2 -> AuditStreamScreen(session, onNavigateToAuditLog = onNavigateToAuditLog)
                3 -> if (session.can(Permission.ACCESS_DRIVER_MODE)) {
                    DriverRoutingEntry(onNavigateToRouting = onNavigateToRouting, onNavigateToWorkflowMonitor = onNavigateToWorkflowMonitor)
                } else {
                    SignOutScreen(session, onSignOut = onSignOut)
                }
                4 -> SignOutScreen(session, onSignOut = onSignOut)
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
