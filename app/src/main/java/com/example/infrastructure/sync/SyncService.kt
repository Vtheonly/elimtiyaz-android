package com.example.infrastructure.sync

import com.example.infrastructure.room.SyncQueueDao
import com.example.infrastructure.room.SyncQueueEntity
import com.example.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync service — mirrors the desktop's `SyncService`.
 *
 * Enqueues offline mutations to the Room-backed sync queue. The SyncWorker
 * drains the queue when online + Supabase configured. Mock data is flagged
 * `isMock = true` at enqueue and skipped at drain (defense in depth).
 *
 * Exponential backoff: 1000 * 2^attempts ms between retries. Max 5 attempts
 * before the entry is marked `failed` permanently.
 */
@Singleton
class SyncService @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val sessionManager: SessionManager,
    private val onlineDetector: OnlineDetector,
) {
    private val _snapshot = MutableStateFlow(SyncSnapshot(
        online = false,
        pendingCount = 0,
        syncedCount = 0,
        failedCount = 0,
        skippedMockCount = 0,
        lastSyncAt = null,
        lastError = null,
    ))
    val snapshot: StateFlow<SyncSnapshot> = _snapshot.asStateFlow()

    suspend fun enqueue(
        entity: String,
        operation: String,
        payload: String,
        isMock: Boolean,
        sourceScreen: String? = null,
    ): String {
        val tenantId = sessionManager.currentTenantId() ?: "unknown"
        val actorId = sessionManager.currentUserId() ?: "system"
        val id = "sync_${System.currentTimeMillis().toString(36)}_${UUID.randomUUID().toString().take(8)}"
        val entry = SyncQueueEntity(
            id = id,
            queuedAt = Instant.now().toString(),
            lastAttemptAt = null,
            entity = entity,
            operation = operation,
            tenantId = tenantId,
            actorId = actorId,
            payload = payload,
            isMock = isMock,
            sourceScreen = sourceScreen,
            status = if (isMock) "skipped_mock" else "pending",
            attempts = 0,
            lastError = null,
        )
        syncQueueDao.upsert(entry)
        refreshSnapshot()
        return id
    }

    suspend fun refreshSnapshot() {
        val pending = observeCount("pending")
        val synced = observeCount("synced")
        val failed = observeCount("failed")
        val skipped = observeCount("skipped_mock")
        _snapshot.value = _snapshot.value.copy(
            online = onlineDetector.isOnline,
            pendingCount = pending,
            syncedCount = synced,
            failedCount = failed,
            skippedMockCount = skipped,
        )
    }

    private suspend fun observeCount(status: String): Int =
        syncQueueDao.listPending().let { /* placeholder — would use COUNT query */ it.size }

    suspend fun clearQueue() {
        syncQueueDao.clear()
        refreshSnapshot()
    }

    data class SyncSnapshot(
        val online: Boolean,
        val pendingCount: Int,
        val syncedCount: Int,
        val failedCount: Int,
        val skippedMockCount: Int,
        val lastSyncAt: String?,
        val lastError: String?,
    )
}
