package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Workflow run status — mirrors the desktop `WorkflowRunStatus` enum.
 *
 * DB also has `pending` and `cancelled`, but the mobile UI surfaces only the 4 below.
 */
@Serializable
enum class WorkflowRunStatus(val wireCode: String, val displayFr: String) {
    Running("running", "En cours"),
    Succeeded("succeeded", "Réussi"),
    Failed("failed", "Échoué"),
    Timeout("timeout", "Expiré");

    companion object {
        fun fromCode(code: String?): WorkflowRunStatus =
            entries.firstOrNull { it.wireCode.equals(code, ignoreCase = true) } ?: Running
    }
}

/**
 * What triggered a workflow run.
 *
 * T-231: the REAL workflow_runs.trigger_type enum (0012 + 0081) is
 * manual_run | schedule | payment_overdue | student_enrolled |
 * payment_recorded | absence_limit | debt_over_threshold |
 * grade_below_threshold | payment_cleared_or_bounced | document_expiration
 * | calendar_cron_event | stock_level_critical. The coarse display union
 * maps manual_run→Manuel, schedule→Programmé, everything else→Événement
 * (the previous default-to-Manual made every automatic run display
 * "Manuel" — the exact WEAK-008 defect family).
 */
@Serializable
enum class WorkflowTrigger(val wireCode: String, val displayFr: String) {
    Manual("manual", "Manuel"),
    Scheduled("scheduled", "Programmé"),
    Event("event", "Événement");

    companion object {
        fun fromCode(code: String?): WorkflowTrigger = when (code?.trim()?.lowercase()) {
            "manual", "manual_run" -> Manual
            "schedule", "scheduled" -> Scheduled
            null, "" -> Manual
            else -> Event
        }
    }
}

/**
 * Status of a single node within a workflow run.
 */
@Serializable
enum class WorkflowNodeStatus(val wireCode: String) {
    Skipped("skipped"),
    Running("running"),
    Succeeded("succeeded"),
    Failed("failed"),
    Timeout("timeout");

    companion object {
        fun fromCode(code: String?): WorkflowNodeStatus =
            entries.firstOrNull { it.wireCode.equals(code, ignoreCase = true) } ?: Skipped
    }
}

/**
 * Per-node result within a workflow run.
 *
 * Stored as a JSONB array on the `workflow_runs.node_results` column.
 */
@Serializable
data class WorkflowNodeResult(
    val nodeId: String,
    val nodeName: String,
    val nodeType: String, // trigger / condition / action / delay / transform
    val status: WorkflowNodeStatus,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val output: String? = null,
    val error: String? = null,
)

/**
 * A single execution of a workflow.
 *
 * Read-only on mobile — the DAG editor is desktop-only per plan §10.02.
 * Mobile users can view runs and (if they have MANAGE_WORKFLOWS) retry them.
 */
@Serializable
data class WorkflowRun(
    val id: String,
    val workflowId: String,
    val workflowName: String,
    val trigger: WorkflowTrigger,
    val status: WorkflowRunStatus,
    val startedAt: String,
    val completedAt: String? = null,
    val durationMs: Long? = null,
    val actorId: String? = null,
    val actorName: String? = null,
    val errorMessage: String? = null,
    val outputPreview: String? = null,
    val outputLog: String? = null,
    val nodeResults: List<WorkflowNodeResult> = emptyList(),
)
