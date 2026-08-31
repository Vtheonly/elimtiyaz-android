package com.example.infrastructure.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.session.SessionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Sync worker — drains the pending sync queue (push) and pulls the latest
 * data from Supabase into the local Room cache.
 *
 * The worker is a thin wrapper around [SyncService.drainPending], which
 * pushes pending offline mutations AND performs the trailing pull of the
 * latest parents/students (T-050/WEAK-010: the worker previously called
 * pullAll() itself right after drainPending() — every WorkManager tick ran
 * the full pull TWICE; drainPending's trailing pull is the single source).
 *
 * The worker is scheduled by WorkManager every 15 minutes when the device
 * is online. All the actual drain logic, push dispatch, retry, backoff,
 * and audit-logging lives in [SyncService] (mirroring the desktop
 * `SyncService.drain` pattern). All the pull logic lives in
 * [PullSyncRepository].
 *
 * @param appContext Injected by Hilt's WorkerFactory.
 * @param workerParams Injected by Hilt's WorkerFactory.
 * @param sessionManager Used to bail early when no session is active.
 * @param onlineDetector Used to bail early when offline.
 * @param syncService The push-side drain executor (also performs the pull).
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
        // PUSH + PULL: drainPending pushes the queue AND performs the
        // trailing pullAll (single pull per tick — T-050/WEAK-010 removed
        // the duplicate full pull that used to run here).
        //
        // T-021/SYNC-107 — HONEST completion semantics. The old worker ran
        // the drain inside runCatching and returned Result.success() in
        // EVERY case, so WorkManager could never distinguish a clean drain
        // from one that crashed mid-way or left failures outstanding:
        //   - drain threw (unexpected crash)        → Result.retry()
        //   - drain ok, permanent failures (>= 5 attempts, audit-logged) → Result.failure()
        //   - drain ok, transient failures still queued → Result.retry()
        //   - drain ok, nothing outstanding         → Result.success()
        // For PERIODIC work retry()/failure() both just end the current run
        // (the 15-min schedule re-runs regardless), but the result is now
        // TRUTHFUL for diagnostics, and retry() additionally honours the
        // backoff policy when the work is ever scheduled one-shot.
        val outcome = runCatching { syncService.drainPending() }
        return when {
            outcome.isFailure -> Result.retry()
            else -> {
                val drain = outcome.getOrThrow()
                when {
                    drain.failed > 0 -> Result.failure()
                    drain.remainingPending > 0 -> Result.retry()
                    else -> Result.success()
                }
            }
        }
    }

    companion object {
        /** Unique WorkManager work name — used by [SyncService.schedulePeriodicSync]. */
        const val WORK_NAME = "el_imtiyaz_sync_drain"
    }
}

