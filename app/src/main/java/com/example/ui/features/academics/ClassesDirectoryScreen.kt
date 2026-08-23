package com.example.ui.features.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Permission
import com.example.core.Session
import com.example.domain.model.AcademicClass
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElListItem
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.WarmGold

@Composable
fun ClassesDirectoryScreen(
    session: Session,
    onNavigateToClassDetail: (String) -> Unit = {},
    onNavigateToSubjectsDirectory: () -> Unit = {},
    viewModel: ClassesDirectoryViewModel = hiltViewModel(),
) {
    val classes by viewModel.classes.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()

    // Promotion confirm dialog state (RBAC: PROMOTE_STUDENT).
    var promotionTarget by remember { mutableStateOf<AcademicClass?>(null) }
    val canPromote = session.can(Permission.PROMOTE_STUDENT) || viewModel.canPromote

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElSectionHeader(title = "Annuaire des Classes (${classes.size})")

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

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
                    // FIX (dead directory): the class rows had NO onClick, so
                    // ClassDetailScreen was unreachable from the Academics hub
                    // — tapping a class did nothing.
                    onClick = { onNavigateToClassDetail(klass.id) },
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ElTag(text = "${klass.enrolledCount}/${klass.capacity} ($fillRate%)", color = if (fillRate >= 90) WarmGold else PrimaryBlue)
                            // Promotion entry point — promotes every ACTIVE
                            // student of the class up one level of the
                            // canonical ladder (core/AcademicProgression.kt).
                            if (canPromote) {
                                IconButton(
                                    onClick = { promotionTarget = klass },
                                    enabled = !busy,
                                ) {
                                    Icon(
                                        Icons.Default.TrendingUp,
                                        contentDescription = "Promouvoir les élèves de ${klass.name}",
                                        tint = PrimaryBlue,
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    // Promotion confirmation — explains the ladder move before mutating.
    promotionTarget?.let { klass ->
        AlertDialog(
            onDismissRequest = { promotionTarget = null },
            title = { Text("Promotion — ${klass.name}") },
            text = {
                Text(
                    "Tous les élèves ACTIFS de cette classe seront promus au niveau supérieur " +
                        "selon l'échelle officielle (primaire → CEM → lycée). Les élèves de " +
                        "3ème année seront marqués comme diplômés. " +
                        "Cette action est enregistrée dans le journal d'audit et propagée à la synchronisation.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.promoteClass(klass)
                        promotionTarget = null
                    },
                    enabled = !busy,
                ) { Text("Promouvoir") }
            },
            dismissButton = {
                TextButton(onClick = { promotionTarget = null }) { Text("Annuler") }
            },
        )
    }
}
