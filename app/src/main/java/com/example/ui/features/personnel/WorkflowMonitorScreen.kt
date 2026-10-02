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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
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
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.overlays.ElDialogShell
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
                ElAlertBanner(
                    title = it,
                    severity = ElAlertSeverity.DANGER,
                )
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
        // T-460 pass G-b (issue #3 F-08): the run-detail dialog on the DS
        // ElDialogShell (was a raw M3 AlertDialog) — the T-231 node_results
        // surface and the retry contract preserved verbatim.
        ElDialogShell(onDismissRequest = { viewModel.openDetail(null) }) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    run.workflowName,
                    style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ElTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Statut : ", style = ElTheme.typography.bodySmall, color = ElTheme.colors.textSecondary)
                    WorkflowStatusChip(status = run.status)
                }
                Text("Déclencheur : ${run.trigger.displayFr}", style = ElTheme.typography.bodySmall, color = ElTheme.colors.textSecondary)
                Text("Début : ${run.startedAt}", style = ElTheme.typography.bodySmall, color = ElTheme.colors.textSecondary)
                run.completedAt?.let { Text("Fin : $it", style = ElTheme.typography.bodySmall, color = ElTheme.colors.textSecondary) }
                run.durationMs?.let { Text("Durée : ${it}ms", style = ElTheme.typography.bodySmall, color = ElTheme.colors.textSecondary) }
                run.actorName?.let { Text("Acteur : $it", style = ElTheme.typography.bodySmall, color = ElTheme.colors.textSecondary) }
                run.errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("Erreur : $it", style = ElTheme.typography.bodySmall, color = ElTheme.colors.danger)
                }
                // T-324 (UI-314): the T-231 decode populates nodeResults —
                // surface the executed steps instead of leaving the data dead.
                // The old "Journal" section was dead UI (the mapper never
                // populated outputLog) and is removed.
                if (run.nodeResults.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Étapes exécutées (${run.nodeResults.size})",
                        style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.textPrimary,
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
                                style = ElTheme.typography.bodySmall,
                                color = ElTheme.colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            ElTag(text = label, tone = tone)
                        }
                        node.error?.let { nodeError ->
                            Text(
                                nodeError,
                                style = ElTheme.typography.labelSmall,
                                color = ElTheme.colors.danger,
                                maxLines = 2,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (viewModel.canRetry && run.status in setOf(WorkflowRunStatus.Failed, WorkflowRunStatus.Timeout)) {
                        ElButton(
                            text = "Réessayer",
                            onClick = {
                                viewModel.retry(run.id)
                                viewModel.openDetail(null)
                            },
                            variant = ElButtonVariant.PRIMARY,
                            icon = Icons.Default.Refresh,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ElButton(
                        text = "Fermer",
                        onClick = { viewModel.openDetail(null) },
                        variant = ElButtonVariant.GHOST,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkflowRunCard(run: WorkflowRun, onClick: () -> Unit) {
    // T-460 pass G-b (issue #3 F-08): the run card on the DS ElCard (was a
    // raw M3 Card).
    ElCard(
        modifier = Modifier.fillMaxWidth(),
        size = ElCardSize.STANDARD,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    run.workflowName,
                    style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = ElTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                WorkflowStatusChip(status = run.status)
            }
            Spacer(Modifier.height(4.dp))
            Text("Déclencheur : ${run.trigger.displayFr}", style = ElTheme.typography.bodySmall, color = ElTheme.colors.textSecondary)
            Text("Début : ${run.startedAt}", style = ElTheme.typography.labelSmall, color = ElTheme.colors.textSecondary)
            run.durationMs?.let { Text("Durée : ${it}ms", style = ElTheme.typography.labelSmall, color = ElTheme.colors.textSecondary) }
            run.outputPreview?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = ElTheme.typography.bodySmall, color = ElTheme.colors.textPrimary, maxLines = 2)
            }
        }
    }
}

@Composable
private fun WorkflowStatusChip(status: WorkflowRunStatus) {
    // T-460 pass G-b: the status chip on the DS ElTag (was a hand-rolled
    // rounded box with direct colorScheme reads + a hardcoded White).
    val (tone, tint) = when (status) {
        WorkflowRunStatus.Running -> ElTagTone.INFO to ElTheme.colors.info
        WorkflowRunStatus.Succeeded -> ElTagTone.SUCCESS to ElTheme.colors.success
        WorkflowRunStatus.Failed -> ElTagTone.DANGER to ElTheme.colors.danger
        WorkflowRunStatus.Timeout -> ElTagTone.WARNING to ElTheme.colors.warning
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(tint, shape = androidx.compose.foundation.shape.CircleShape),
        )
        ElTag(text = status.displayFr, tone = tone, size = ElTagSize.SM)
    }
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
