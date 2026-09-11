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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.ui.components.AuditDiffSheet
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.theme.PrimaryBlue

@Composable
fun AuditStreamScreen(
    session: Session,
    onNavigateToAuditLog: () -> Unit,
    viewModel: AuditStreamViewModel = hiltViewModel(),
) {
    val logs by viewModel.logs.collectAsState()
    var selectedAuditLog by remember { mutableStateOf<AuditLog?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElSectionHeader(
            title = "Journal d'Audit (${logs.size})",
            actionText = "Journal complet",
            onAction = onNavigateToAuditLog,
        )

        if (logs.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Code,
                title = "Aucun événement",
                message = "Aucune entrée d'audit récente. Les actions des utilisateurs apparaîtront ici.",
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(logs) { log ->
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { selectedAuditLog = log },
                    compact = true,
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                log.action,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryBlue,
                                    fontSize = 14.sp,
                                ),
                            )
                            Text(
                                log.occurredAt.take(19).replace("T", " "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                log.actorName,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            )
                            log.actorRole?.let { role ->
                                ElTag(text = role, color = PrimaryBlue)
                            }
                            Spacer(Modifier.weight(1f))
                        }
                        Text(
                            "${log.entityType}/${log.entityId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        log.note?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Voir le diff par champ", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
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
