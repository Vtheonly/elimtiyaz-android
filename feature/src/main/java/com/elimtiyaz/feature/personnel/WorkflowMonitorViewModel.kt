package com.elimtiyaz.feature.personnel

import androidx.lifecycle.ViewModel
import com.elimtiyaz.core.common.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * WorkflowMonitorViewModel — read-only monitor of Supabase Edge Functions /
 * DAG workflow runs (master plan §13).
 *
 * The visual DAG canvas editor is **desktop-only** per the master plan; the
 * Android app exposes only a list of recent runs with status, trigger,
 * duration, and output preview. Tap a row to see the full output log in a
 * dialog.
 *
 * The data is statically mocked in v1 — the routing of Edge Function
 * invocations through Supabase Functions is part of the data layer
 * (Task 4-data) but the surface is not yet wired. Replacing the mock list
 * with a live repository call is a drop-in change.
 */
@HiltViewModel
class WorkflowMonitorViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(WorkflowMonitorUiState(runs = mockRuns()))
    val state: StateFlow<WorkflowMonitorUiState> = _state.asStateFlow()

    /** Set the detail dialog target. Pass null to dismiss. */
    fun openDetail(runId: String?) {
        _state.value = _state.value.copy(detailRunId = runId)
    }

    /** Refresh the workflow runs list (no-op on mock data). */
    fun reload() {
        _state.value = _state.value.copy(runs = mockRuns())
    }
}

/** Workflow monitor screen state. */
data class WorkflowMonitorUiState(
    val runs: List<WorkflowRun> = emptyList(),
    val detailRunId: String? = null,
) {
    /** The run whose detail dialog is open (if any). */
    val detailRun: WorkflowRun? get() = runs.firstOrNull { it.id == detailRunId }
}

/** Mock workflow run model. */
data class WorkflowRun(
    val id: String,
    val name: String,
    val trigger: WorkflowTrigger,
    val status: WorkflowStatus,
    val startedAt: String,
    val durationMs: Long,
    val outputPreview: String,
    val outputLog: String,
)

/** What started the workflow. */
enum class WorkflowTrigger(val displayFr: String) {
    Manual("Manuel"),
    Scheduled("Planifié"),
    Event("Événement"),
}

/** Run lifecycle. */
enum class WorkflowStatus(val displayFr: String) {
    Running("En cours"),
    Success("Succès"),
    Failed("Échec"),
    Cancelled("Annulé"),
}

/** Static seed for v1 — replace with a live repository call later. */
private fun mockRuns(): List<WorkflowRun> {
    val now = Formatters.nowIso()
    return listOf(
        WorkflowRun(
            id = "wf-001",
            name = "daily-attendance-rollup",
            trigger = WorkflowTrigger.Scheduled,
            status = WorkflowStatus.Success,
            startedAt = now,
            durationMs = 3_240L,
            outputPreview = "12 classes agrégées, 0 anomalie.",
            outputLog = """
[INFO] Starting daily-attendance-rollup
[INFO] Loading attendance records for 12 classes
[INFO] Aggregated 324 records
[INFO] Anomalies: 0
[INFO] Done in 3.24s
            """.trimIndent(),
        ),
        WorkflowRun(
            id = "wf-002",
            name = "debt-reminder-push",
            trigger = WorkflowTrigger.Event,
            status = WorkflowStatus.Running,
            startedAt = now,
            durationMs = 1_120L,
            outputPreview = "Envoi des notifications FCM aux parents…",
            outputLog = """
[INFO] Triggered by payment.overdue event
[INFO] Loading 47 debtors
[INFO] Dispatching FCM notifications
            """.trimIndent(),
        ),
        WorkflowRun(
            id = "wf-003",
            name = "monthly-statement-pdf",
            trigger = WorkflowTrigger.Scheduled,
            status = WorkflowStatus.Failed,
            startedAt = now,
            durationMs = 8_512L,
            outputPreview = "Échec: Supabase Storage timeout (signed-URL).",
            outputLog = """
[INFO] Starting monthly-statement-pdf
[INFO] Querying 1 204 payments
[INFO] Generating PDF batch
[ERROR] Supabase Storage timeout (signed-URL)
[ERROR] Retry 1/3 — failed
[ERROR] Retry 2/3 — failed
[ERROR] Retry 3/3 — failed
[FATAL] Aborting run
            """.trimIndent(),
        ),
        WorkflowRun(
            id = "wf-004",
            name = "edge-sync-tenant",
            trigger = WorkflowTrigger.Manual,
            status = WorkflowStatus.Success,
            startedAt = now,
            durationMs = 540L,
            outputPreview = "Sync 4 entités, 0 conflit.",
            outputLog = """
[INFO] Manual trigger by admin
[INFO] Syncing parents: 312 rows
[INFO] Syncing students: 489 rows
[INFO] Syncing payments: 1 204 rows
[INFO] Syncing expenses: 87 rows
[INFO] Done in 540ms
            """.trimIndent(),
        ),
    )
}
