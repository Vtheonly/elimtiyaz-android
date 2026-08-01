package com.example.infrastructure.sync

/**
 * Slim reactive sync state for the UI — exposed via
 * [SyncService.observeSyncState].
 *
 * @property isRunning   True when [SyncService.drainPending] is actively executing.
 * @property lastSyncAt  ISO timestamp of the last successful drain, or null.
 * @property pendingCount Number of rows waiting to be pushed.
 * @property lastError   Last push error message, or null.
 */
data class SyncState(
    val isRunning: Boolean,
    val lastSyncAt: String?,
    val pendingCount: Int,
    val lastError: String?,
)
