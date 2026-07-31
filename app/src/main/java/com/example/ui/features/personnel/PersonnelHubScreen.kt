package com.example.ui.features.personnel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.Session

@Composable
fun PersonnelHubScreen(
    session: Session,
    onNavigateToAuditLog: () -> Unit,
    onSignOut: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Employés", "Activité", "Audit", "Déconnexion")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopStart) {
            when (selectedTab) {
                0 -> EmployeeDirectoryScreen(session)
                1 -> ReleveScreen(session)
                2 -> AuditStreamScreen(session, onNavigateToAuditLog = onNavigateToAuditLog)
                3 -> SignOutScreen(session, onSignOut = onSignOut)
            }
        }
    }
}

@Composable
fun EmployeeDirectoryScreen(session: Session) {
    Column {
        Text("Annuaire du personnel", style = MaterialTheme.typography.titleMedium)
        Text("(Connecté en tant que: ${session.displayName} — ${session.role.code})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // TODO: implement employee directory with departments tree + role badges
    }
}

@Composable
fun ReleveScreen(session: Session) {
    Column {
        Text("Relevé d'activité", style = MaterialTheme.typography.titleMedium)
        // TODO: implement releve (teacher activity ledger) with 30-day aggregate
    }
}

@Composable
fun AuditStreamScreen(session: Session, onNavigateToAuditLog: () -> Unit) {
    Column {
        Text("Journal d'audit", style = MaterialTheme.typography.titleMedium)
        androidx.compose.material3.TextButton(onClick = onNavigateToAuditLog) {
            Text("Voir le journal complet")
        }
        // TODO: implement audit stream with realtime updates
    }
}

@Composable
fun SignOutScreen(session: Session, onSignOut: () -> Unit) {
    Column {
        Text("Session", style = MaterialTheme.typography.titleMedium)
        Text("Utilisateur: ${session.displayName}")
        Text("Email: ${session.email}")
        Text("Rôle: ${session.role.code}")
        Text("Permissions: ${session.permissions.size}")
        androidx.compose.material3.Button(onClick = onSignOut) { Text("Se déconnecter") }
    }
}
