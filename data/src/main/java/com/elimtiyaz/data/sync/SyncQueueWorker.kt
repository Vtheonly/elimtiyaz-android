package com.elimtiyaz.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import com.elimtiyaz.data.local.dao.SyncQueueDao
import com.elimtiyaz.data.local.entity.SyncQueueEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Reads pending rows from the local [SyncQueueDao] and replays them against
 * Supabase. Per master plan §09:
 *
 * - Scheduled via WorkManager with `Constraints.RequiredNetworkType = UNMETERED`
 *   and a 15-min periodic interval (see [SyncScheduler]).
 * - Exponential backoff for transient failures.
 * - Per-row failures bump `attempts` and update `lastError`; rows succeed or
 *   stay forever (no destructive discard).
 */
@HiltWorker
class SyncQueueWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val supabase: SupabaseClient,
    private val syncQueueDao: SyncQueueDao,
) : CoroutineWorker(appContext, params) {

    private val log = Logger.withTag("Data.Sync.Worker")

    /** Pull pending rows and replay each one against Supabase. */
    override suspend fun doWork(): Result {
        val pending = syncQueueDao.pending()
        if (pending.isEmpty()) return Result.success()
        log.i { "Replaying ${pending.size} queued operation(s)" }
        var failures = 0
        for (row in pending) {
            val ok = runCatching { replay(row) }.getOrElse { false }
            if (ok) {
                syncQueueDao.deleteById(row.id)
                log.i { "Replayed ${row.operation} on ${row.tableName} (${row.id})" }
            } else {
                failures++
                syncQueueDao.upsert(row.copy(attempts = row.attempts + 1, lastError = "attempt ${row.attempts + 1} failed"))
                log.w { "Failed to replay ${row.operation} on ${row.tableName} (${row.id}) — attempt ${row.attempts + 1}" }
            }
        }
        // Use retry() so WorkManager applies the exponential backoff policy.
        return if (failures == 0) Result.success() else Result.retry()
    }

    /** Replays a single queued row. Returns true on success, false on failure. */
    private suspend fun replay(row: SyncQueueEntity): Boolean {
        val payload = Json.parseToJsonElement(row.payloadJson)
        when (row.operation) {
            "insert" -> {
                supabase.from(row.tableName).insert(payload)
            }
            "update" -> {
                val id = (payload as? JsonObject)?.get("id")?.toString()?.trim('"')
                    ?: return false
                supabase.from(row.tableName).update(payload) { filter { eq("id", id) } }
            }
            "delete" -> {
                val id = (payload as? JsonObject)?.get("id")?.toString()?.trim('"')
                    ?: return false
                supabase.from(row.tableName).delete { filter { eq("id", id) } }
            }
            else -> {
                log.w { "Unknown operation: ${row.operation}" }
                return false
            }
        }
        return true
    }

    private companion object {
        /** Hard cap on attempts before giving up on a single row. */
        const val MAX_ATTEMPTS = 5
    }
}

/** Helper to schedule the periodic [SyncQueueWorker]. */
object SyncScheduler {

    /** Unique work name — prevents duplicate enqueue. */
    const val WORK_NAME = "elimtiyaz-sync-queue"

    /** True when the worker has already been enqueued this process. */
    @Volatile private var scheduled = false

    /**
     * Enqueue a periodic worker that runs every ~15 minutes when on an
     * unmetered network, with exponential backoff for retries. Idempotent —
     * safe to call from `Application.onCreate`.
     */
    fun schedule(context: Context) {
        if (scheduled) return
        scheduled = true
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
            .build()
        val request = androidx.work.PeriodicWorkRequestBuilder<SyncQueueWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30, java.util.concurrent.TimeUnit.SECONDS,
            )
            .build()
        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        Logger.withTag("Data.Sync").i { "Scheduled periodic sync worker (15 min, UNMETERED)" }
    }

    /** Convenience for tests / reset. */
    fun reset() { scheduled = false }

    /** Internal: returns the current epoch millis, exposed for tests. */
    @Suppress("unused")
    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
