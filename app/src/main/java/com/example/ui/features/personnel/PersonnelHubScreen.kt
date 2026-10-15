package com.example.ui.features.personnel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.Role
import com.example.core.Session
import com.example.ui.designsystem.components.tabs.ElScrollableTabRow
import com.example.ui.designsystem.theme.ElTheme

@Composable
fun PersonnelHubScreen(
    session: Session,
    onNavigateToPersonnelDetail: (String) -> Unit = {},
    onNavigateToReleve: (String) -> Unit = {},
    onNavigateToWorkflowMonitor: () -> Unit = {},
    onNavigateToAuditLog: () -> Unit,
    // T-541 / OPS-324 (180th session): onNavigateToRouting REMOVED with
    // the GPS Tournées feature (the "Tournées" tab + DriverRoutingEntry
    // are gone; the route destination no longer exists).
    onNavigateToProfile: () -> Unit = {},
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
        // T-541 / OPS-324: the permission-gated "Tournées" tab REMOVED with
        // the GPS Tournées feature (ACCESS_DRIVER_MODE no longer exists).
        add("Session")
    }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // T-460 pass F: the DS scrollable tab row (the role-dependent tab
        // list can reach 6 — the scrollable variant, as in the Financials hub).
        ElScrollableTabRow(
            tabs = tabs,
            selectedIndex = selectedTab,
            onSelected = { selectedTab = it },
        )
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = ElTheme.spacing.lg, vertical = ElTheme.spacing.sm),
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
                    // T-541 / OPS-324: the "Tournées" branch (DriverRoutingEntry)
                    // REMOVED with the GPS Tournées feature.
                    // T-460 pass J (F-11): the session tab is now a redirect
                    // to the single Profile surface (owner decision).
                    "Session" -> SignOutScreen(session, onNavigateToProfile = onNavigateToProfile)
                    else -> SignOutScreen(session, onNavigateToProfile = onNavigateToProfile)
                }
            }
        }
    }
}

// T-541 / OPS-324 (180th session): DriverRoutingEntry (the "Mode chauffeur
// — Tournées" launch button + the workflows monitor shortcut) REMOVED with
// the GPS Tournées feature — the routing screens it launched are deleted.
