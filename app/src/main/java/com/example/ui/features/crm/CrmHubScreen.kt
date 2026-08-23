package com.example.ui.features.crm

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

import com.example.ui.components.ModernSecondaryTabRow

@Composable
fun CrmHubScreen(
    session: Session,
    onNavigateToStudent: (String) -> Unit,
    onNavigateToParent: (String) -> Unit,
    onNavigateToBatchRegistration: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Parents", "Élèves", "Inscription")

    Column(modifier = Modifier.fillMaxSize()) {
        ModernSecondaryTabRow(
            tabs = tabs,
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
        )
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.TopStart) {
            when (selectedTab) {
                0 -> ParentsDirectoryScreen(session = session, onParentClick = onNavigateToParent)
                1 -> StudentRosterScreen(session = session, onStudentClick = onNavigateToStudent)
                // FIX (duplicate navigation): `onSuccess` was wired to
                // `onNavigateToBatchRegistration`, which PUSHED a second
                // standalone registration screen on top of the embedded one —
                // after a successful registration the user landed on a blank
                // duplicate form. Stay on the embedded screen (which shows
                // its own success message and resets) and switch to the
                // parents tab so the new family is visible.
                2 -> BatchRegistrationScreen(
                    onSuccess = {
                        // The registration form shows its own success banner;
                        // surface the fresh parent directory alongside it.
                        selectedTab = 0
                    },
                )
            }
        }
    }
}
