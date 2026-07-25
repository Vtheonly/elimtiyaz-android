package com.elimtiyaz.feature.personnel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.EmptyState
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone

/**
 * WorkflowMonitorScreen — read-only monitor of Supabase Edge Function /
 * DAG workflow runs (Route.WorkflowMonitor).
 *
 * Each card lists the workflow name, trigger type (Manual / Scheduled /
 * Event), status chip (Running / Success / Failed / Cancelled), started-at,
 * duration, and a one-line output preview. Tapping a row opens a detail
 * dialog with the full output log in a Mono font.
 *
 * The visual DAG canvas editor is desktop-only per master plan §13 and is
 * therefore NOT exposed here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowMonitorScreen(
    nav: NavController,
    vm: WorkflowMonitorViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var dialogRunId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moniteur de workflows", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { inner ->
        if (state.runs.isEmpty()) {
            EmptyState(
                title = "Aucune exécution",
                description = "Les exécutions de workflows Edge Functions apparaîtront ici.",
                icon = Icons.Outlined.PlayArrow,
                modifier = Modifier.padding(inner),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
            ) {
                items(state.runs, key = { it.id }) { run ->
                    WorkflowRunCard(
                        run = run,
                        onClick = { dialogRunId = run.id },
                    )
                }
            }
        }
    }

    // Detail dialog with full output log.
    val detailRun = state.runs.firstOrNull { it.id == dialogRunId }
    if (detailRun != null) {
        AlertDialog(
            onDismissRequest = { dialogRunId = null },
            title = {
                Text(
                    text = detailRun.name,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    WorkflowDetailHeader(run = detailRun)
                    Spacer(Modifier.height(ElimtiyazSpacing.x3))
                    HorizontalDivider()
                    Spacer(Modifier.height(ElimtiyazSpacing.x3))
                    Text(
                        text = "Journal de sortie",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(ElimtiyazSpacing.x2))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(ElimtiyazSpacing.x3),
                    ) {
                        Text(
                            text = detailRun.outputLog,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogRunId = null }) { Text("Fermer") }
            },
        )
    }
}

@Composable
private fun WorkflowRunCard(run: WorkflowRun, onClick: () -> Unit) {
    val tone = when (run.status) {
        WorkflowStatus.Running -> StatusTone.Info
        WorkflowStatus.Success -> StatusTone.Success
        WorkflowStatus.Failed -> StatusTone.Danger
        WorkflowStatus.Cancelled -> StatusTone.Neutral
    }
    ElImtiyazCard(onClick = onClick) {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text(
                    text = run.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(label = run.status.displayFr, tone = tone)
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Déclencheur",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = run.trigger.displayFr,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Démarré",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = Formatters.dateTime(run.startedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Durée",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${run.durationMs} ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(
                text = run.outputPreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun WorkflowDetailHeader(run: WorkflowRun) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DetailRow("Statut", run.status.displayFr)
        DetailRow("Déclencheur", run.trigger.displayFr)
        DetailRow("Démarré", Formatters.dateTime(run.startedAt))
        DetailRow("Durée", "${run.durationMs} ms")
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ElimtiyazSpacing.x1),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
