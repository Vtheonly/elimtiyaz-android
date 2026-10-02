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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.card.ElGradientStatCard
import com.example.ui.designsystem.components.display.ElGradient
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.theme.ElTheme

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
    val c = ElTheme.colors
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
            gradient = ElGradient.BRAND,
            modifier = Modifier.fillMaxWidth(),
        )

        if (classes.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.School,
                title = "Aucune classe affectée",
                subtitle = "Aucune classe ne vous est affectée. Contactez le responsable pédagogique pour activer votre espace.",
            )
        } else {
            classes.forEach { cls ->
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = ElCardSize.STANDARD,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Row {
                                    Icon(
                                        Icons.Default.School,
                                        contentDescription = null,
                                        tint = c.primary,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                    Text(
                                        cls.name,
                                        style = ElTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        color = c.textPrimary,
                                    )
                                }
                                Text(
                                    "Salle : ${cls.room ?: "—"} · ${cls.enrolledCount} élèves · ${cls.academicYear}",
                                    style = ElTheme.typography.bodySmall,
                                    color = c.textSecondary,
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
                                variant = ElButtonVariant.SECONDARY,
                                icon = Icons.Default.CheckCircle,
                                modifier = Modifier.weight(1f),
                            )
                            ElButton(
                                text = "Notes",
                                onClick = { onNavigateToGradeEntry(cls.id) },
                                variant = ElButtonVariant.SECONDARY,
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
