package com.example.ui.features.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Permission
import com.example.core.Session
import com.example.domain.model.AcademicClass
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.theme.ElTheme

/**
 * T-323 (55th session, UI-313) — classes directory on the canonical design
 * system, plus the REAL BUG FIX: `onNavigateToSubjectsDirectory` was declared
 * but NEVER referenced in the body, so the Matières directory was unreachable
 * from the Academics hub. The header now carries a "Matières" button.
 *
 * Preserved: the promotion entry point (Vault §06.04 — opens the REVIEW
 * QUEUE, never blind batch promotion), its busy gating, the permission
 * double-check, and the class-detail navigation.
 */
@Composable
fun ClassesDirectoryScreen(
    session: Session,
    onNavigateToClassDetail: (String) -> Unit = {},
    onNavigateToSubjectsDirectory: () -> Unit = {},
    onNavigateToPromotionReview: (String) -> Unit = {},
    viewModel: ClassesDirectoryViewModel = hiltViewModel(),
) {
    val c = ElTheme.colors
    val classes by viewModel.classes.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()

    val canPromote = session.can(Permission.PROMOTE_STUDENT) || viewModel.canPromote

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElSectionHeader(
            title = "Annuaire des classes",
            subtitle = "${classes.size} division${if (classes.size > 1) "s" else ""} active${if (classes.size > 1) "s" else ""}",
            trailing = {
                ElButton(
                    text = "Matières",
                    onClick = onNavigateToSubjectsDirectory,
                    variant = ElButtonVariant.SECONDARY,
                    icon = Icons.Default.MenuBook,
                )
            },
        )

        message?.let {
            ElAlertBanner(
                title = "Succès",
                message = it,
                severity = ElAlertSeverity.SUCCESS,
            )
        }
        error?.let {
            ElAlertBanner(
                title = "Erreur",
                message = it,
                severity = ElAlertSeverity.DANGER,
            )
        }

        if (classes.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Class,
                title = "Aucune classe",
                subtitle = "Aucune classe n'est enregistrée pour cette année scolaire.",
            )
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(classes) { klass ->
                val fillRate = if (klass.capacity > 0) (klass.enrolledCount.toFloat() / klass.capacity * 100).toInt() else 0
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = ElCardSize.COMPACT,
                    onClick = { onNavigateToClassDetail(klass.id) },
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(c.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Class,
                                contentDescription = null,
                                tint = c.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                klass.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Professeur : ${klass.homeroomTeacherName ?: "Non assigné"} · Salle : ${klass.room ?: "—"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                        ElTag(
                            text = "${klass.enrolledCount}/${klass.capacity} ($fillRate%)",
                            tone = if (fillRate >= 90) ElTagTone.WARNING else ElTagTone.INFO,
                        )
                        // Vault §06.04 — promotion entry point. Opens the
                        // GPA-driven REVIEW QUEUE (auto-flag + admin
                        // overrides) instead of blindly promoting every
                        // ACTIVE student. The vault explicitly forbids
                        // running batch promotion without first reviewing
                        // the queue.
                        if (canPromote) {
                            IconButton(
                                onClick = { onNavigateToPromotionReview(klass.id) },
                                enabled = !busy,
                            ) {
                                Icon(
                                    Icons.Default.TrendingUp,
                                    contentDescription = "File de promotion — ${klass.name}",
                                    tint = c.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
