package com.example.ui.features.personnel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Permission
import com.example.core.Result
import com.example.domain.model.WorkflowRun
import com.example.domain.model.WorkflowRunStatus
import com.example.domain.model.WorkflowTrigger
import com.example.domain.repository.WorkflowRepository
import com.example.session.SessionManager
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.theme.ElTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * Workflow monitor ViewModel — read-only list of recent workflow runs.
 *
 * Behavior:
 *  - Loads recent runs (last 50, sorted by `startedAt` DESC) from Room.
 *    Runs are PULLED from Supabase (`workflow_runs` table) by the sync layer
 *    when a backend is configured — locally-created runs (retries) appear too.
 *  - Detail drawer with per-run metadata (status, trigger, duration, error).
 *  - Retry button gated to `Permission.MANAGE_WORKFLOWS` — the actual
 *    execution engine is server-side; offline retries fail honestly.
 *  - Empty state is TRUTHFUL (no mock seed): "Aucune exécution."
 */
@HiltViewModel
class WorkflowMonitorViewModel @Inject constructor(
    private val workflowRepository: WorkflowRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val runs: StateFlow<List<WorkflowRun>> = workflowRepository.observeRuns(50)
        .mapNotNull { result -> (result as? com.example.core.Result.Ok)?.value ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _detailRunId = MutableStateFlow<String?>(null)
    val detailRunId: StateFlow<String?> = _detailRunId.asStateFlow()

    val detailRun: StateFlow<WorkflowRun?> = kotlinx.coroutines.flow.combine(
        runs, _detailRunId,
    ) { all, id -> all.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val canRetry: Boolean
        get() = sessionManager.current()?.can(Permission.MANAGE_WORKFLOWS) == true

    fun openDetail(runId: String?) { _detailRunId.value = runId }
    fun clearError() { _error.value = null }

    fun retry(runId: String) {
        if (!canRetry) {
            _error.value = "Permission manquante : MANAGE_WORKFLOWS."
            return
        }
        viewModelScope.launch {
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val r = workflowRepository.retryRun(runId, actorId, actorName)) {
                is Result.Ok -> _error.value = "Nouvelle exécution lancée: ${r.value}"
                is Result.Err -> _error.value = r.error.userMessage
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowMonitorScreen(
    onBack: () -> Unit,
    viewModel: WorkflowMonitorViewModel = hiltViewModel(),
) {
    val runs by viewModel.runs.collectAsState()
    val detailRun by viewModel.detailRun.collectAsState()
    val error by viewModel.error.collectAsState()

    ElScaffold(
        topBar = {
            ElTopBar(
                title = "Moniteur de workflows",
                subtitle = "Surveillance des automatisations serveur",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            error?.let {
                Text(it, color = ElTheme.colors.danger, modifier = Modifier.padding(bottom = 8.dp))
            }

            if (runs.isEmpty()) {
                ElEmptyState(
                    title = "Aucune exécution",
                    subtitle = "Les workflows sont déclenchés côté serveur. Les exécutions apparaîtront ici.",
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(runs) { run ->
                        WorkflowRunCard(
                            run = run,
                            onClick = { viewModel.openDetail(run.id) },
                        )
                    }
                }
            }
        }
    }

    detailRun?.let { run ->
        AlertDialog(
            onDismissRequest = { viewModel.openDetail(null) },
            title = { Text(run.workflowName) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Statut: ", style = MaterialTheme.typography.bodySmall)
                        WorkflowStatusChip(status = run.status)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Déclencheur: ${run.trigger.displayFr}", style = MaterialTheme.typography.bodySmall)
                    Text("Début: ${run.startedAt}", style = MaterialTheme.typography.bodySmall)
                    run.completedAt?.let { Text("Fin: $it", style = MaterialTheme.typography.bodySmall) }
                    run.durationMs?.let { Text("Durée: ${it}ms", style = MaterialTheme.typography.bodySmall) }
                    run.actorName?.let { Text("Acteur: $it", style = MaterialTheme.typography.bodySmall) }
                    run.errorMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("Erreur : $it", style = MaterialTheme.typography.bodySmall, color = ElTheme.colors.danger)
                    }
                    // T-324 (UI-314): the T-231 decode populates nodeResults —
                    // surface the executed steps instead of leaving the data dead.
                    // The old "Journal" section was dead UI (the mapper never
                    // populated outputLog) and is removed.
                    if (run.nodeResults.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Étapes exécutées (${run.nodeResults.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        run.nodeResults.forEach { node ->
                            val (label, tone) = workflowNodeStatusLabel(node.status)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                Text(
                                    node.nodeName,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                ElTag(text = label, tone = tone)
                            }
                            node.error?.let { nodeError ->
                                Text(
                                    nodeError,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElTheme.colors.danger,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (viewModel.canRetry && run.status in setOf(WorkflowRunStatus.Failed, WorkflowRunStatus.Timeout)) {
                    TextButton(onClick = {
                        viewModel.retry(run.id)
                        viewModel.openDetail(null)
                    }) { Icon(Icons.Default.Refresh, contentDescription = null); Text(" Réessayer") }
                } else {
                    TextButton(onClick = { viewModel.openDetail(null) }) { Text("Fermer") }
                }
            },
            dismissButton = {
                if (viewModel.canRetry && run.status in setOf(WorkflowRunStatus.Failed, WorkflowRunStatus.Timeout)) {
                    TextButton(onClick = { viewModel.openDetail(null) }) { Text("Fermer") }
                }
            },
        )
    }
}

@Composable
private fun WorkflowRunCard(run: WorkflowRun, onClick: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(run.workflowName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                WorkflowStatusChip(status = run.status)
            }
            Spacer(Modifier.height(4.dp))
            Text("Déclencheur: ${run.trigger.displayFr}", style = MaterialTheme.typography.bodySmall)
            Text("Début: ${run.startedAt}", style = MaterialTheme.typography.labelSmall)
            run.durationMs?.let { Text("Durée: ${it}ms", style = MaterialTheme.typography.labelSmall) }
            run.outputPreview?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
    }
}

@Composable
private fun WorkflowStatusChip(status: WorkflowRunStatus) {
    val color = when (status) {
        WorkflowRunStatus.Running -> MaterialTheme.colorScheme.primary
        WorkflowRunStatus.Succeeded -> MaterialTheme.colorScheme.tertiary
        WorkflowRunStatus.Failed -> MaterialTheme.colorScheme.error
        WorkflowRunStatus.Timeout -> MaterialTheme.colorScheme.secondary
    }
    Text(
        text = status.displayFr,
        style = MaterialTheme.typography.labelSmall,
        color = androidx.compose.ui.graphics.Color.White,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(color, shape = RoundedCornerShape(8.dp)),
    )
}

/** Humanized node-status label + tone (T-324: raw enum names no longer leak). */
internal fun workflowNodeStatusLabel(status: com.example.domain.model.WorkflowNodeStatus): Pair<String, ElTagTone> =
    when (status) {
        com.example.domain.model.WorkflowNodeStatus.Succeeded -> "Réussie" to ElTagTone.SUCCESS
        com.example.domain.model.WorkflowNodeStatus.Running -> "En cours" to ElTagTone.INFO
        com.example.domain.model.WorkflowNodeStatus.Failed -> "Échec" to ElTagTone.DANGER
        com.example.domain.model.WorkflowNodeStatus.Timeout -> "Délai dépassé" to ElTagTone.WARNING
        com.example.domain.model.WorkflowNodeStatus.Skipped -> "Ignorée" to ElTagTone.NEUTRAL
    }
