package com.example.ui.features.personnel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.School
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElGradientStatCard

/**
 * TeacherWorkspaceScreen — "Mon espace" (T-237 / RBAC-300, 35th session).
 *
 * The teacher's dedicated in-Personnel workspace: their homeroom classes
 * with direct Appel (roll-call) and Notes (grade-entry) actions that route
 * to the ALREADY-GATED Routes.RollCall / Routes.GradeEntry (ROLL_CALL /
 * ENTER_GRADES permissions — retained by the teacher role).
 *
 * Teachers lost the module-ENTRY permissions (VIEW_ROSTER → CRM hub,
 * VIEW_ACADEMICS → Pédagogie hub) in core/Rbac.kt, so their hub tabs
 * disappear and this workspace becomes the single entry point for their
 * pedagogical duties — the exact mirror of the desktop T-235 dashboard.
 */
@Composable
fun TeacherWorkspaceScreen(
    session: Session,
    onNavigateToRollCall: (String) -> Unit,
    onNavigateToGradeEntry: (String) -> Unit,
    viewModel: TeacherWorkspaceViewModel = hiltViewModel(),
) {
    val classes by viewModel.myClasses.collectAsState()
    val totalStudents = classes.sumOf { it.enrolledCount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElGradientStatCard(
            title = "Mon espace",
            value = session.displayName,
            subtitle = "${classes.size} classe(s) · $totalStudents élève(s)",
            modifier = Modifier.fillMaxWidth(),
        )

        if (classes.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.School,
                title = "Aucune classe affectée",
                message = "Aucune classe ne vous est affectée. Contactez le responsable pédagogique pour activer votre espace.",
            )
        } else {
            classes.forEach { cls ->
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Row {
                                    androidx.compose.material3.Icon(
                                        Icons.Default.School,
                                        contentDescription = null,
                                        tint = com.example.ui.theme.PrimaryBlue,
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                    androidx.compose.material3.Text(
                                        cls.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                    )
                                }
                                androidx.compose.material3.Text(
                                    "Salle : ${cls.room ?: "—"} · ${cls.enrolledCount} élèves · ${cls.academicYear}",
                                    fontSize = 12.sp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ElButton(
                                text = "Appel",
                                onClick = { onNavigateToRollCall(cls.id) },
                                style = ElButtonStyle.Secondary,
                                icon = Icons.Default.CheckCircle,
                                modifier = Modifier.weight(1f),
                            )
                            ElButton(
                                text = "Notes",
                                onClick = { onNavigateToGradeEntry(cls.id) },
                                style = ElButtonStyle.Secondary,
                                icon = Icons.Default.Grade,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
