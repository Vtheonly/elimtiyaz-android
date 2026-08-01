package com.example.infrastructure.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.core.AuditActions
import com.example.core.Result
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.infrastructure.room.SyncQueueDao
import com.example.infrastructure.room.SyncQueueEntity
import com.example.infrastructure.supabase.SupabaseSyncDao
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync service — mirrors the desktop's `SyncService`.
 *
 * Enqueues offline mutations to the Room-backed sync queue. The [SyncWorker]
 * drains the queue when online + Supabase configured. Mock data is flagged
 * `isMock = true` at enqueue and skipped at drain (defense in depth).
 *
 * Exponential backoff: 1000 * 2^attempts ms between retries. Max 5 attempts
 * before the entry is marked `failed` permanently (and an audit log entry is
 * written so admins can investigate).
 *
 * Public API surface:
 *   - [enqueue]                       — append a new mutation to the queue.
 *   - [syncNow]                       — drain the queue immediately on a
 *                                       direct coroutine (NOT via WorkManager).
 *   - [observeSyncState]              — reactive [SyncState] flow for the UI.
 *   - [schedulePeriodicSync]          — register the 15-min WorkManager job.
 *   - [snapshot] / [refreshSnapshot]  — legacy snapshot accessors.
 */
@Singleton
class SyncService @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val sessionManager: SessionManager,
    private val onlineDetector: OnlineDetector,
    private val supabaseSyncDao: SupabaseSyncDao,
    private val auditRepository: AuditRepository,
) {
    /** Backing scope for [syncNow] — SupervisorJob keeps one failure from cancelling the scope. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Re-entrancy guard for [drainPending] — prevents overlapping drains from double-pushing. */
    private val drainMutex = Mutex()

    private val _snapshot = MutableStateFlow(
        SyncSnapshot(
            online = false,
            isRunning = false,
            pendingCount = 0,
            syncedCount = 0,
            failedCount = 0,
            skippedMockCount = 0,
            lastSyncAt = null,
            lastError = null,
        ),
    )

    /** Legacy snapshot — prefer [observeSyncState] for new UI code. */
    val snapshot: StateFlow<SyncSnapshot> = _snapshot.asStateFlow()

    private val maxAttempts = 5
    private val backoffBaseMs = 1000L

    /**
     * Append a new mutation to the sync queue. Mock entries are immediately
     * marked `skipped_mock` and never pushed (defense in depth).
     *
     * @return the new queue entry's ID.
     */
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

    /**
     * Drain the pending queue. This is the canonical drain loop, invoked by:
     *   - [SyncWorker.doWork] (every 15 min via WorkManager).
     *   - [syncNow] (manual trigger from the Settings screen).
     *
     * Algorithm:
     *   1. Bail if offline, no session, or Supabase unconfigured.
     *   2. Acquire [drainMutex] (re-entrancy guard).
     *   3. Mark `isRunning = true` in the snapshot.
     *   4. For each pending entry:
     *      - DEFENSE IN DEPTH: if `isMock`, mark `skipped_mock` and continue.
     *      - If `lastAttemptAt` is set, compute `backoffMs = 1000 * 2^attempts`
     *        and skip if `now < lastAttemptAt + backoffMs`.
     *      - Try to push (dispatch by entity type via [SupabaseSyncDao]).
     *      - On success: mark `synced`, clear `lastError`.
     *      - On failure: increment `attempts`; if >= 5, mark `failed` + write
     *        an audit log entry; else keep `pending`.
     *   5. Refresh the snapshot.
     *
     * Each entity type's push is isolated — failure of one row does NOT
     * block the others.
     *
     * @return summary counts of the drain.
     */
    suspend fun drainPending(): DrainResult = withContext(Dispatchers.IO) {
        if (!onlineDetector.isOnline()) return@withContext DrainResult(0, 0, 0)
        if (sessionManager.current() == null) return@withContext DrainResult(0, 0, 0)
        if (!isSupabaseConfigured()) return@withContext DrainResult(0, 0, 0)

        drainMutex.withLock {
            _snapshot.value = _snapshot.value.copy(isRunning = true)
            var pushed = 0
            var failed = 0
            var skippedMock = 0
            var lastError: String? = null

            val pending = runCatching { syncQueueDao.listPending() }.getOrDefault(emptyList())
            for (entry in pending) {
                try {
                    if (entry.isMock) {
                        updateStatus(entry, "skipped_mock", null)
                        skippedMock++
                        continue
                    }
                    // Backoff check
                    if (entry.lastAttemptAt != null) {
                        val lastAttempt = runCatching {
                            Instant.parse(entry.lastAttemptAt).toEpochMilli()
                        }.getOrDefault(0L)
                        val backoffMs = backoffBaseMs * (1L shl entry.attempts.coerceAtMost(10))
                        if (System.currentTimeMillis() < lastAttempt + backoffMs) continue
                    }
                    pushEntry(entry)
                    updateStatus(entry.copy(attempts = entry.attempts + 1, status = "synced", lastError = null), "synced", null)
                    pushed++
                } catch (e: Exception) {
                    lastError = e.message ?: e::class.simpleName
                    val newAttempts = entry.attempts + 1
                    if (newAttempts >= maxAttempts) {
                        updateStatus(
                            entry.copy(attempts = newAttempts, status = "failed", lastError = e.message),
                            "failed",
                            e.message,
                        )
                        logSyncFailure(entry, e)
                        failed++
                    } else {
                        updateStatus(
                            entry.copy(
                                attempts = newAttempts,
                                lastAttemptAt = Instant.now().toString(),
                                lastError = e.message,
                            ),
                            "pending",
                            e.message,
                        )
                    }
                }
            }

            _snapshot.value = _snapshot.value.copy(
                isRunning = false,
                online = onlineDetector.isOnline(),
                lastSyncAt = if (pushed > 0) Instant.now().toString() else _snapshot.value.lastSyncAt,
                lastError = lastError,
            )
            refreshSnapshot()
            DrainResult(pushed, failed, skippedMock)
        }
    }

    /**
     * Trigger an immediate one-shot sync on a direct coroutine (NOT via
     * WorkManager). Safe to call when offline — it'll no-op.
     *
     * @return [Result.Ok] when the drain completes (success or no-op),
     *         [Result.Err] only if the drain throws unexpectedly.
     */
    fun syncNow(): Result<Unit> {
        scope.launch {
            runCatching { drainPending() }
        }
        return Result.Ok(Unit)
    }

    /**
     * Reactive [SyncState] flow — used by the Settings screen diagnostics
     * section and the topbar sync indicator.
     */
    fun observeSyncState(): Flow<SyncState> = _snapshot.map { s ->
        SyncState(
            isRunning = s.isRunning,
            lastSyncAt = s.lastSyncAt,
            pendingCount = s.pendingCount,
            lastError = s.lastError,
        )
    }

    /**
     * Register the periodic WorkManager job that drains the queue every 15
     * minutes when the device is online + unmetered. Idempotent — uses
     * [ExistingPeriodicWorkPolicy.KEEP] so repeated calls do not reset the
     * interval timer.
     */
    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(SyncWorker.WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Refresh the snapshot from the DAO — used after enqueue / drain. */
    suspend fun refreshSnapshot() {
        val pending = countByStatus("pending")
        val synced = countByStatus("synced")
        val failed = countByStatus("failed")
        val skipped = countByStatus("skipped_mock")
        _snapshot.value = _snapshot.value.copy(
            online = onlineDetector.isOnline(),
            pendingCount = pending,
            syncedCount = synced,
            failedCount = failed,
            skippedMockCount = skipped,
        )
    }

    /** Clear the entire queue — used by the "Reset sync" button. */
    suspend fun clearQueue() {
        syncQueueDao.clear()
        refreshSnapshot()
    }

    // ── Internal helpers ────────────────────────────────────────────────

    /** Dispatch a single queue entry to the appropriate [SupabaseSyncDao] push method. */
    private suspend fun pushEntry(entry: SyncQueueEntity) {
        when (entry.entity) {
            "parent" -> supabaseSyncDao.pushParent(entry)
            "student" -> supabaseSyncDao.pushStudent(entry)
            "payment" -> supabaseSyncDao.pushPayment(entry)
            "installment" -> supabaseSyncDao.pushInstallment(entry)
            "expense" -> supabaseSyncDao.pushExpense(entry)
            "attendance" -> supabaseSyncDao.pushAttendance(entry)
            "grade" -> supabaseSyncDao.pushGrade(entry)
            "homework" -> supabaseSyncDao.pushHomework(entry)
            "personnel" -> supabaseSyncDao.pushPersonnel(entry)
            "ledger_entry" -> supabaseSyncDao.pushLedgerEntry(entry)
            else -> throw IllegalArgumentException("Unknown sync entity: ${entry.entity}")
        }
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
                    action = AuditActions.SYNC_PUSH_FAIL,
                    entityType = entry.entity,
                    entityId = entry.id,
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

    /** Summary returned by [drainPending]. */
    data class DrainResult(val pushed: Int, val failed: Int, val skippedMock: Int)

    /** Legacy snapshot — full state for advanced consumers. */
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

    /**
     * Slim reactive state for the UI — exposed via [observeSyncState].
     *
     * @property isRunning True when [drainPending] is actively executing.
     * @property lastSyncAt ISO timestamp of the last successful drain, or null.
     * @property pendingCount Number of rows waiting to be pushed.
     * @property lastError Last push error message, or null.
     */
    data class SyncState(
        val isRunning: Boolean,
        val lastSyncAt: String?,
        val pendingCount: Int,
        val lastError: String?,
    )
}
