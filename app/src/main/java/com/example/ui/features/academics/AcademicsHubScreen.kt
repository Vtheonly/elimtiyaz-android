package com.example.ui.features.academics

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
fun AcademicsHubScreen(session: Session) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Présences", "Notes", "Devoirs", "Classes")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopStart) {
            when (selectedTab) {
                0 -> RollCallScreen(session)
                1 -> GradeEntryScreen(session)
                2 -> HomeworkPushScreen(session)
                3 -> ClassesDirectoryScreen(session)
            }
        }
    }
}

@Composable
fun RollCallScreen(session: Session) {
    Column {
        Text("Appel — 30 secondes", style = MaterialTheme.typography.titleMedium)
        Text("(Sélectionnez une classe pour commencer)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // TODO: implement roll call with class picker + student list + 4-button status row
    }
}

@Composable
fun GradeEntryScreen(session: Session) {
    Column {
        Text("Saisie des notes", style = MaterialTheme.typography.titleMedium)
        Text("(Moyenne = (D1 + D2 + 2×Examen) / 4)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // TODO: implement grade entry with subject/class/term pickers + live formula
    }
}

@Composable
fun HomeworkPushScreen(session: Session) {
    Column {
        Text("Devoirs", style = MaterialTheme.typography.titleMedium)
        // TODO: implement homework push with file picker
    }
}

@Composable
fun ClassesDirectoryScreen(session: Session) {
    Column {
        Text("Classes", style = MaterialTheme.typography.titleMedium)
        // TODO: implement classes directory with subjects + schedule editor
    }
}
