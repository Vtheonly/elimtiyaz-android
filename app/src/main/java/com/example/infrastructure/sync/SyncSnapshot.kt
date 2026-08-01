package com.example.infrastructure.sync

/**
 * Full sync snapshot — used by advanced consumers that need the complete
 * queue state (synced count, failed count, skipped-mock count, etc.).
 *
 * For UI consumers, prefer the slim [SyncState].
 */
data class SyncSnapshot(
    val online: Boolean,
    val isRunning: Boolean,
    val pendingCount: Int,
    val syncedCount: Int,
    val failedCount: Int,
    val skippedMockCount: Int,
    val lastSyncAt: String?,
    val lastError: String?,
)
