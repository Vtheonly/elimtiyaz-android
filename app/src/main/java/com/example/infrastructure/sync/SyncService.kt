package com.example.infrastructure.sync

import android.content.Context
import com.example.core.AuditActions
import com.example.core.Result
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.infrastructure.room.SyncQueueDao
import com.example.infrastructure.room.SyncQueueEntity
import com.example.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync service — mirrors the desktop's `SyncService`. Enqueues offline mutations to
 * the Room-backed sync queue; [SyncWorker] drains the queue when online + Supabase
 * configured. Mock entries are flagged `isMock` at enqueue and skipped at drain
 * (defense in depth). Exponential backoff (1000 * 2^attempts ms); max 5 attempts
 * before permanent `failed`. Push dispatch is delegated to [SyncQueueDispatcher];
 * periodic WorkManager scheduling is delegated to [SyncScheduler].
 */
@Singleton
class SyncService @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val sessionManager: SessionManager,
    private val onlineDetector: OnlineDetector,
    private val auditRepository: AuditRepository,
    private val queueDispatcher: SyncQueueDispatcher,
    private val scheduler: SyncScheduler,
    private val pullSyncRepository: PullSyncRepository,
    private val supabaseProvider: com.example.infrastructure.supabase.SupabaseClientProvider,
) {
    /** Backing scope for [syncNow]; SupervisorJob isolates failures. Re-entrancy guard for [drainPending]. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val drainMutex = Mutex()

    private val _snapshot = MutableStateFlow(
        SyncSnapshot(
            online = false, isRunning = false, pendingCount = 0, syncedCount = 0,
            failedCount = 0, skippedMockCount = 0, lastSyncAt = null, lastError = null,
        ),
    )

    /** Legacy snapshot — prefer [observeSyncState] for new UI code. */
    val snapshot: StateFlow<SyncSnapshot> = _snapshot.asStateFlow()

    private val maxAttempts = 5
    private val backoffBaseMs = 1000L

    /** Append a new mutation to the queue. Mock entries are marked `skipped_mock` immediately. Returns the new ID. */
    suspend fun enqueue(
        entity: String, operation: String, payload: String, isMock: Boolean,
        sourceScreen: String? = null,
    ): String {
        val tenantId = sessionManager.currentTenantId() ?: "unknown"
        val actorId = sessionManager.currentUserId() ?: "system"
        val id = "sync_${System.currentTimeMillis().toString(36)}_${UUID.randomUUID().toString().take(8)}"
        val entry = SyncQueueEntity(
            id = id, queuedAt = Instant.now().toString(), lastAttemptAt = null,
            entity = entity, operation = operation, tenantId = tenantId, actorId = actorId,
            payload = payload, isMock = isMock, sourceScreen = sourceScreen,
            status = if (isMock) "skipped_mock" else "pending", attempts = 0, lastError = null,
        )
        syncQueueDao.upsert(entry)
        refreshSnapshot()
        return id
    }

    /**
     * Drain the pending queue — invoked by [SyncWorker.doWork] and [syncNow].
     * Bails if offline / no session / Supabase unconfigured; acquires [drainMutex];
     * for each pending entry: skip if `isMock`, apply exponential backoff, push via
     * [SyncQueueDispatcher]; on failure increment `attempts` and either mark `failed` +
     * audit-log (>= [maxAttempts]) or keep `pending`. Each push is isolated.
     */
    suspend fun drainPending(): DrainResult = withContext(Dispatchers.IO) {
        if (!onlineDetector.isOnline()) return@withContext DrainResult(0, 0, 0)
        if (!supabaseProvider.isConfigured()) return@withContext DrainResult(0, 0, 0)

        drainMutex.withLock {
            _snapshot.value = _snapshot.value.copy(isRunning = true)
            var pushed = 0; var failed = 0; var skippedMock = 0; var lastError: String? = null
            val pending = runCatching { syncQueueDao.listPending() }.getOrDefault(emptyList())
            for (entry in pending) {
                try {
                    if (entry.isMock) {
                        updateStatus(entry, "skipped_mock", null); skippedMock++; continue
                    }
                    if (entry.lastAttemptAt != null) {
                        val lastAttempt = runCatching {
                            Instant.parse(entry.lastAttemptAt).toEpochMilli()
                        }.getOrDefault(0L)
                        val backoffMs = backoffBaseMs * (1L shl entry.attempts.coerceAtMost(10))
                        if (System.currentTimeMillis() < lastAttempt + backoffMs) continue
                    }
                    queueDispatcher.pushEntry(entry)
                    updateStatus(entry.copy(attempts = entry.attempts + 1, status = "synced", lastError = null), "synced", null)
                    pushed++
                } catch (e: Exception) {
                    lastError = e.message ?: e::class.simpleName
                    val newAttempts = entry.attempts + 1
                    if (newAttempts >= maxAttempts) {
                        updateStatus(entry.copy(attempts = newAttempts, status = "failed", lastError = e.message), "failed", e.message)
                        logSyncFailure(entry, e)
                        failed++
                    } else {
                        updateStatus(
                            entry.copy(attempts = newAttempts, lastAttemptAt = Instant.now().toString(), lastError = e.message),
                            "pending", e.message,
                        )
                    }
                }
            }

            // Also pull latest data from Supabase into Room
            runCatching { pullSyncRepository.pullAll() }

            _snapshot.value = _snapshot.value.copy(
                isRunning = false,
                online = onlineDetector.isOnline(),
                lastSyncAt = Instant.now().toString(),
                lastError = lastError,
            )
            refreshSnapshot()
            DrainResult(pushed, failed, skippedMock)
        }
    }

    /** Immediate one-shot sync on a direct coroutine (NOT via WorkManager). */
    fun syncNow(): Result<Unit> {
        scope.launch {
            // T-050/WEAK-010: drainPending performs the trailing pull itself —
            // the extra pullAll() here double-pulled on every manual sync.
            runCatching { drainPending() }
        }
        return Result.Ok(Unit)
    }

    /** Reactive [SyncState] flow — Settings diagnostics + topbar indicator. */
    fun observeSyncState(): Flow<SyncState> = _snapshot.map { s ->
        SyncState(isRunning = s.isRunning, lastSyncAt = s.lastSyncAt, pendingCount = s.pendingCount, lastError = s.lastError)
    }

    /** Register the 15-min periodic WorkManager job — delegates to [SyncScheduler]. */
    fun schedulePeriodicSync(context: Context) {
        scheduler.schedulePeriodicSync(context)
    }

    /** Refresh the snapshot from the DAO — used after enqueue / drain. */
    suspend fun refreshSnapshot() {
        _snapshot.value = _snapshot.value.copy(
            online = onlineDetector.isOnline(),
            pendingCount = countByStatus("pending"),
            syncedCount = countByStatus("synced"),
            failedCount = countByStatus("failed"),
            skippedMockCount = countByStatus("skipped_mock"),
        )
    }

    /** Clear the entire queue — used by the "Reset sync" button. */
    suspend fun clearQueue() {
        syncQueueDao.clear()
        refreshSnapshot()
    }

    /** Update a queue entry's status + lastError atomically. */
    private suspend fun updateStatus(entry: SyncQueueEntity, status: String, lastError: String?) {
        syncQueueDao.upsert(entry.copy(status = status, lastError = lastError))
    }

    /** Write a `sync.push_failed` audit log entry for a permanently-failed queue row. */
    private suspend fun logSyncFailure(entry: SyncQueueEntity, e: Exception) {
        runCatching {
            auditRepository.log(
                AuditLogInput(
                    action = AuditActions.SYNC_PUSH_FAIL, entityType = entry.entity, entityId = entry.id,
                    afterJson = """{"operation":"${entry.operation}","attempts":${entry.attempts + 1},"error":"${e.message ?: e::class.simpleName}"}""",
                    note = "Sync entry permanently failed after $maxAttempts attempts",
                ),
            )
        }
    }

    /** Count queue rows by status — falls back to 0 on DAO error. */
    private suspend fun countByStatus(status: String): Int = withContext(Dispatchers.IO) {
        runCatching { syncQueueDao.countByStatus(status) }.getOrDefault(0)
    }

    /** True when the Supabase URL is not the placeholder value. */
    private fun isSupabaseConfigured(): Boolean {
        val url = com.example.BuildConfig.SUPABASE_URL.trim().removeSurrounding("\"")
        return url.startsWith("https://") && !url.contains("your-project") && !url.contains("demo.supabase.co")
    }
}
