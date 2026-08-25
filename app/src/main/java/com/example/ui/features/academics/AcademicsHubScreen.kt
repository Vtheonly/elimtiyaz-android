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

/**
 * Academics hub — restores navigation callbacks to drill into class detail + subjects directory.
 *
 * Tabs: Présences (RollCall) / Notes (GradeEntry) / Devoirs (HomeworkPush) / Classes (ClassesDirectory).
 *
 * Each sub-screen receives the navigation callbacks it needs to drill into detail screens.
 */
@Composable
fun AcademicsHubScreen(
    session: Session,
    onNavigateToClassDetail: (String) -> Unit = {},
    onNavigateToSubjectsDirectory: () -> Unit = {},
    onNavigateToRollCall: (String) -> Unit = {},
    onNavigateToGradeEntry: (String) -> Unit = {},
    onNavigateToHomeworkPush: (String) -> Unit = {},
    onNavigateToPromotionReview: (String) -> Unit = {},
) {
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
                0 -> RollCallScreen(session, onNavigateToRollCall = onNavigateToRollCall)
                1 -> GradeEntryScreen(session, onNavigateToGradeEntry = onNavigateToGradeEntry)
                2 -> HomeworkPushScreen(session, onNavigateToHomeworkPush = onNavigateToHomeworkPush)
                3 -> ClassesDirectoryScreen(
                    session,
                    onNavigateToClassDetail = onNavigateToClassDetail,
                    onNavigateToSubjectsDirectory = onNavigateToSubjectsDirectory,
                    onNavigateToPromotionReview = onNavigateToPromotionReview,
                )
            }
        }
    }
}
