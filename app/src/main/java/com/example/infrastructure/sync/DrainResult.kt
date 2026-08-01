package com.example.infrastructure.sync

/**
 * Summary returned by [SyncService.drainPending] — counts of pushed,
 * failed, and skipped-mock entries from a single drain pass.
 */
data class DrainResult(
    val pushed: Int,
    val failed: Int,
    val skippedMock: Int,
)
