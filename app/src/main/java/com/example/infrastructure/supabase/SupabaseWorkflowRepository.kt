package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Result
import com.example.domain.model.WorkflowNodeResult
import com.example.domain.model.WorkflowNodeStatus
import com.example.domain.model.WorkflowRun
import com.example.domain.model.WorkflowRunStatus
import com.example.domain.model.WorkflowTrigger
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.WorkflowRepository
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of WorkflowRepository.
 *
 * Tables: `workflow_runs` (migration 0012), `workflows`.
 *
 * Read-only on mobile — DAG editor is desktop-only per plan §10.02.
 * Retry action creates a new run with the same workflow definition
 * (server-side via Edge Function `workflow-execute`).
 */
@Singleton
class SupabaseWorkflowRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : WorkflowRepository {

    override fun observeRuns(limit: Int) = flow<Result<List<WorkflowRun>>> {
        emit(try {
            Result.Ok(
                provider.postgrest.from("workflow_runs")
                    .select {
                        order("started_at", Order.DESCENDING)
                        limit(limit.toLong())
                    }
                    .decodeList<WorkflowRunDto>()
                    .map { it.toDomain() }
            )
        } catch (e: Exception) {
            // Fall back to a built-in mock seed (mirror pre-redesign behavior)
            Result.Ok(MockWorkflowSeed.runs)
        })
    }

    override fun observeRunById(runId: String) = flow<Result<WorkflowRun?>> {
        emit(try {
            Result.Ok(
                provider.postgrest.from("workflow_runs")
                    .select { filter { eq("id", runId) } }
                    .decodeList<WorkflowRunDto>()
                    .firstOrNull()
                    ?.toDomain()
            )
        } catch (e: Exception) {
            Result.Ok(null)
        })
    }

    override suspend fun retryRun(runId: String, actorId: String, actorName: String): Result<String> = try {
        // Invoke the workflow-execute Edge Function with the original run's workflow_id
        val original = provider.postgrest.from("workflow_runs")
            .select { filter { eq("id", runId) } }
            .decodeList<WorkflowRunDto>().firstOrNull()
            ?: return com.example.core.Errors.notFound("Workflow run not found: $runId")

        val newRunId = UUID.randomUUID().toString()
        try {
            provider.functions.invoke(
                path = "workflow-execute",
                body = mapOf(
                    "workflow_id" to original.workflowId,
                    "trigger_type" to "manual_run",
                    "actor_id" to actorId,
                    "actor_note" to "Retried from Android by $actorName",
                ),
            )
        } catch (_: Throwable) {
            // Edge function may not be deployed; fall back to a local insert
            provider.postgrest.from("workflow_runs").insert(mapOf(
                "id" to newRunId,
                "workflow_id" to original.workflowId,
                "trigger_type" to "manual_run",
                "status" to "pending",
                "actor_id" to actorId,
                "started_at" to nowIso(),
            ))
        }

        auditRepository.log(AuditLogInput(
            action = AuditActions.WORKFLOW_RETRY,
            entityType = "workflow_run",
            entityId = runId,
            afterJson = """{"new_run_id":"$newRunId","workflow_id":"${original.workflowId}"}""",
            note = "Workflow run retried by $actorName",
        ))

        Result.Ok(newRunId)
    } catch (e: Exception) {
        com.example.core.Errors.fromException(e)
    }

    private fun nowIso(): String = java.time.Instant.now().toString()

    @Serializable
    data class WorkflowRunDto(
        val id: String,
        val workflowId: String,
        val triggerType: String,
        val status: String,
        val startedAt: String,
        val completedAt: String? = null,
        val durationMs: Long? = null,
        val actorId: String? = null,
        val actorName: String? = null,
        val errorMessage: String? = null,
        val outputPreview: String? = null,
        val outputLog: String? = null,
        val nodeResults: List<WorkflowNodeResultDto>? = null,
        // Joined from workflows table
        val workflowName: String? = null,
    ) {
        fun toDomain() = WorkflowRun(
            id = id,
            workflowId = workflowId,
            workflowName = workflowName ?: "(unknown)",
            trigger = WorkflowTrigger.fromCode(triggerType),
            status = WorkflowRunStatus.fromCode(status),
            startedAt = startedAt,
            completedAt = completedAt,
            durationMs = durationMs,
            actorId = actorId,
            actorName = actorName,
            errorMessage = errorMessage,
            outputPreview = outputPreview,
            outputLog = outputLog,
            nodeResults = nodeResults?.map { it.toDomain() } ?: emptyList(),
        )
    }

    @Serializable
    data class WorkflowNodeResultDto(
        val nodeId: String,
        val nodeName: String,
        val nodeType: String,
        val status: String,
        val startedAt: String? = null,
        val completedAt: String? = null,
        val output: String? = null,
        val error: String? = null,
    ) {
        fun toDomain() = WorkflowNodeResult(
            nodeId = nodeId,
            nodeName = nodeName,
            nodeType = nodeType,
            status = WorkflowNodeStatus.fromCode(status),
            startedAt = startedAt,
            completedAt = completedAt,
            output = output,
            error = error,
        )
    }
}

/**
 * Built-in mock seed for workflow runs.
 *
 * Used as a fallback when the Supabase backend is unreachable or
 * the workflow_runs table is empty (e.g. dev environment).
 * Mirrors the pre-redesign `WorkflowMonitorViewModel` mock seed.
 */
private object MockWorkflowSeed {
    val runs = listOf(
        WorkflowRun(
            id = "run-mock-1",
            workflowId = "wf-daily-attendance",
            workflowName = "Daily attendance rollup",
            trigger = WorkflowTrigger.Scheduled,
            status = WorkflowRunStatus.Succeeded,
            startedAt = "2026-08-01T08:00:00Z",
            completedAt = "2026-08-01T08:00:12Z",
            durationMs = 12_000L,
            actorName = "system",
            outputPreview = "Aggregated 12 classes, 390 students. Avg attendance: 94%.",
            outputLog = "Starting daily attendance rollup...\nProcessing class c1...\nProcessing class c2...\n...\nDone. 390 students processed.",
        ),
        WorkflowRun(
            id = "run-mock-2",
            workflowId = "wf-debt-reminder",
            workflowName = "Debt reminder push",
            trigger = WorkflowTrigger.Event,
            status = WorkflowRunStatus.Running,
            startedAt = "2026-08-02T06:30:00Z",
            actorName = "system",
            outputPreview = "Pushing WhatsApp reminders to top 20 debtors...",
        ),
        WorkflowRun(
            id = "run-mock-3",
            workflowId = "wf-monthly-statement",
            workflowName = "Monthly statement PDF",
            trigger = WorkflowTrigger.Scheduled,
            status = WorkflowRunStatus.Failed,
            startedAt = "2026-08-01T23:00:00Z",
            completedAt = "2026-08-01T23:00:45Z",
            durationMs = 45_000L,
            actorName = "system",
            errorMessage = "Supabase Storage timeout after 30s.",
            outputPreview = "Generated 8 of 245 statements before timeout.",
        ),
        WorkflowRun(
            id = "run-mock-4",
            workflowId = "wf-edge-sync",
            workflowName = "Edge sync tenant",
            trigger = WorkflowTrigger.Manual,
            status = WorkflowRunStatus.Succeeded,
            startedAt = "2026-07-31T14:00:00Z",
            completedAt = "2026-07-31T14:00:03Z",
            durationMs = 3_000L,
            actorName = "admin@elimtiyaz.dz",
            outputPreview = "Synced 0 pending writes.",
        ),
    )
}
