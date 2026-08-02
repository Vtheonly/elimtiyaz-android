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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Workflow monitor ViewModel — read-only list of recent workflow runs.
 *
 * Restored behavior (commit a34333a):
 *  - Loads recent runs (last 50, sorted by `startedAt` DESC).
 *  - Detail drawer with per-node timeline.
 *  - Retry button gated to `Permission.MANAGE_WORKFLOWS`.
 *  - Falls back to a built-in mock seed when repository returns empty
 *    (so the screen is never blank — same approach as the pre-redesign
 *    `WorkflowMonitorViewModel` which shipped with 4 mock runs).
 */
@HiltViewModel
class WorkflowMonitorViewModel @Inject constructor(
    private val workflowRepository: WorkflowRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val runs: StateFlow<List<WorkflowRun>> = workflowRepository.observeRuns(50)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exécutions de workflow") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }

            if (runs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucune exécution. Les workflows sont déclenchés côté serveur.", style = MaterialTheme.typography.bodySmall)
                }
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
                        Text("Erreur: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    run.outputLog?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("Journal:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
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
