package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.WorkflowRun
import kotlinx.coroutines.flow.Flow

/**
 * Read-only workflow monitor repository.
 *
 * Per desktop plan §10.02: the DAG editor is desktop-only. The mobile app
 * surfaces a read-only list of recent workflow runs with the ability to
 * retry (gated to `Permission.MANAGE_WORKFLOWS`).
 */
interface WorkflowRepository {

    /** Observe recent workflow runs (last 50, sorted by `startedAt` DESC). */
    fun observeRuns(limit: Int = 50): Flow<Result<List<WorkflowRun>>>

    /** Observe a single run by id (for the detail drawer). */
    fun observeRunById(runId: String): Flow<Result<WorkflowRun?>>

    /**
     * Retry a failed/timed-out run. Creates a new run with the same workflow definition.
     *
     * @return The new run id.
     */
    suspend fun retryRun(
        runId: String,
        actorId: String,
        actorName: String,
    ): Result<String>
}
