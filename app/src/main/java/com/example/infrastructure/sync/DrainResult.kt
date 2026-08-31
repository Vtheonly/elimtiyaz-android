package com.example.infrastructure.sync

/**
 * Summary returned by [SyncService.drainPending] — counts of pushed,
 * failed, and skipped-mock entries from a single drain pass.
 *
 * T-021 / SYNC-107: [remainingPending] counts entries left in `pending`
 * after the pass (transient failures that still owe a retry, or entries
 * whose backoff window hadn't elapsed). The SyncWorker maps it to
 * WorkManager's `Result.retry()` so a transient-failure drain surfaces in
 * the WorkManager result instead of always reporting success.
 */
data class DrainResult(
    val pushed: Int,
    val failed: Int,
    val skippedMock: Int,
    val remainingPending: Int = 0,
)
