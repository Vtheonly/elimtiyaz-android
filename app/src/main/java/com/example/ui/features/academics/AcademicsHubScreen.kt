package com.example.ui.features.academics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.Session
import com.example.ui.components.ModernSecondaryTabRow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
fun AcademicsHubScreen(session: Session) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Présences", "Notes", "Devoirs", "Classes")

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
                0 -> RollCallScreen(session)
                1 -> GradeEntryScreen(session)
                2 -> HomeworkPushScreen(session)
                3 -> ClassesDirectoryScreen(session)
            }
        }
    }
}
