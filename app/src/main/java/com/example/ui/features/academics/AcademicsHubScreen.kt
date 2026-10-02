package com.example.ui.features.academics

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
import com.example.ui.designsystem.components.tabs.ElTabRow
import com.example.ui.designsystem.theme.ElTheme

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
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Présences", "Notes", "Devoirs", "Classes")

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // T-460 pass D: the DS segmented tab row (4 tabs fit the fixed row —
        // the same tab language as the Dashboard hub and ClassDetailScreen).
        ElTabRow(
            tabs = tabs,
            selectedIndex = selectedTab,
            onSelected = { selectedTab = it },
        )
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = ElTheme.spacing.lg, vertical = ElTheme.spacing.sm),
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
