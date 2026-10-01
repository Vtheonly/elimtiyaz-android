package com.example.ui.features.personnel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.theme.ElTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.domain.model.AuditLog
import com.example.ui.features.settings.AuditDiffSheet
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.feedback.ElEmptyState

@Composable
fun AuditStreamScreen(
    session: Session,
    onNavigateToAuditLog: () -> Unit,
    viewModel: AuditStreamViewModel = hiltViewModel(),
) {
    val c = ElTheme.colors
    val logs by viewModel.logs.collectAsState()
    var selectedAuditLog by remember { mutableStateOf<AuditLog?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElSectionHeader(
            title = "Journal d'Audit (${logs.size})",
            // T-460: the legacy actionText/onAction pair → the DS trailing
            // slot (a GHOST button; same behaviour).
            trailing = {
                ElButton(
                    text = "Journal complet",
                    onClick = onNavigateToAuditLog,
                    variant = ElButtonVariant.GHOST,
                )
            },
        )

        if (logs.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Code,
                title = "Aucun événement",
                subtitle = "Aucune entrée d'audit récente. Les actions des utilisateurs apparaîtront ici.",
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(logs) { log ->
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = ElCardSize.STANDARD,
                    onClick = { selectedAuditLog = log },
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                log.action,
                                style = ElTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = c.primary,
                                    fontSize = 14.sp,
                                ),
                            )
                            Text(
                                log.occurredAt.take(19).replace("T", " "),
                                style = ElTheme.typography.labelSmall,
                                color = c.textSecondary,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                log.actorName,
                                style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = c.textPrimary,
                            )
                            log.actorRole?.let { role ->
                                ElTag(text = role, tone = ElTagTone.INFO, size = ElTagSize.MD)
                            }
                            Spacer(Modifier.weight(1f))
                        }
                        Text(
                            "${log.entityType}/${log.entityId}",
                            style = ElTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                        log.note?.let {
                            Text(
                                it,
                                style = ElTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = c.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Voir le diff par champ", style = ElTheme.typography.labelSmall, color = c.primary)
                        }
                    }
                }
            }
        }
    }

    // T-297: the REAL field-level diff sheet (red/green rows from
    // beforeJson/afterJson via core/FieldDiff.kt) replaces the fabricated
    // "Inspecteur JSON" payload dump.
    AuditDiffSheet(
        log = selectedAuditLog,
        onDismiss = { selectedAuditLog = null },
    )
}
