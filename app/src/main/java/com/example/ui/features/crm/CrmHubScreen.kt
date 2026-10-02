package com.example.ui.features.crm

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
import com.example.core.Session
import com.example.domain.model.ChatChannelScope
import com.example.ui.features.chat.ChatScreen
import com.example.ui.designsystem.components.tabs.ElTabRow
import com.example.ui.designsystem.theme.ElTheme

@Composable
fun CrmHubScreen(
    session: Session,
    onNavigateToStudent: (String) -> Unit,
    onNavigateToParent: (String) -> Unit,
    onNavigateToBatchRegistration: () -> Unit,
    // T-463 / CHAT-300: opens a parent's PORTAL conversation (the Portail
    // tab's channel rows → the ChatDetail destination).
    onNavigateToPortalChat: (com.example.domain.model.ChatChannel) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Parents", "Élèves", "Inscription", "Portail")

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // T-460 pass E: the DS segmented tab row — now 4 tabs (T-463 added
        // the Portail messenger tab; ElTabRow handles the wider set).
        ElTabRow(
            tabs = tabs,
            selectedIndex = selectedTab,
            onSelected = { selectedTab = it },
        )
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = ElTheme.spacing.lg, vertical = ElTheme.spacing.sm), contentAlignment = Alignment.TopStart) {
            when (selectedTab) {
                0 -> ParentsDirectoryScreen(session = session, onParentClick = onNavigateToParent)
                1 -> StudentRosterScreen(session = session, onStudentClick = onNavigateToStudent)
                2 -> BatchRegistrationScreen(
                    onSuccess = {
                        selectedTab = 0
                    },
                )
                // T-463 / CHAT-300: the PORTAL↔STAFF messenger — the
                // parent/student conversations (ADR-012), kept strictly
                // apart from the internal staff messenger.
                3 -> ChatScreen(
                    onBack = { selectedTab = 0 },
                    onOpenChannel = onNavigateToPortalChat,
                    scope = ChatChannelScope.PORTAL,
                    topBarTitle = null,
                )
            }
        }
    }
}
