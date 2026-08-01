package com.example.infrastructure.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.session.SessionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Sync worker — drains the pending sync queue.
 *
 * The worker is a thin wrapper around [SyncService.drainPending] so that
 * WorkManager can schedule periodic drains every 15 minutes when the
 * device is online. All the actual drain logic, push dispatch, retry,
 * backoff, and audit-logging lives in [SyncService] (mirroring the
 * desktop `SyncService.drain` pattern).
 *
 * The worker is also enqueued on-demand by [SyncService.schedulePeriodicSync]
 * and re-runs whenever WorkManager fires it.
 *
 * @param appContext Injected by Hilt's WorkerFactory.
 * @param workerParams Injected by Hilt's WorkerFactory.
 * @param sessionManager Used to bail early when no session is active.
 * @param onlineDetector Used to bail early when offline.
 * @param syncService The actual drain executor.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionManager: SessionManager,
    private val onlineDetector: OnlineDetector,
    private val syncService: SyncService,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!onlineDetector.isOnline()) return Result.success()
        if (sessionManager.current() == null) return Result.success()
        runCatching { syncService.drainPending() }
        return Result.success()
    }

    companion object {
        /** Unique WorkManager work name — used by [SyncService.schedulePeriodicSync]. */
        const val WORK_NAME = "el_imtiyaz_sync_drain"
    }
}
