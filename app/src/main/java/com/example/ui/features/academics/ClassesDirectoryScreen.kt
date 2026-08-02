package com.example.ui.features.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Class
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElListItem
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.WarmGold
import androidx.compose.runtime.getValue

@Composable
fun ClassesDirectoryScreen(
    session: Session,
    viewModel: ClassesDirectoryViewModel = hiltViewModel(),
) {
    val classes by viewModel.classes.collectAsState()

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElSectionHeader(title = "Annuaire des Classes (${classes.size})")
        if (classes.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Class,
                title = "Aucune classe",
                message = "Aucune classe n'a été créée. Utilisez les paramètres pour en ajouter.",
            )
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(classes) { klass ->
                val fillRate = if (klass.capacity > 0) (klass.enrolledCount.toFloat() / klass.capacity * 100).toInt() else 0
                ElListItem(
                    title = klass.name,
                    subtitle = "Professeur: ${klass.homeroomTeacherName ?: "Non assigné"} · Salle: ${klass.room ?: "—"}",
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Class, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        }
                    },
                    trailing = {
                        ElTag(text = "${klass.enrolledCount}/${klass.capacity} ($fillRate%)", color = if (fillRate >= 90) WarmGold else PrimaryBlue)
                    },
                )
            }
        }
    }
}
