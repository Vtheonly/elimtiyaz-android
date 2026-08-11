package com.example.infrastructure.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.session.SessionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Sync worker — drains the pending sync queue AND pulls the latest
 * parents + students from Supabase into the local Room cache.
 *
 * The worker is a thin wrapper around:
 *   1. [SyncService.drainPending] — pushes pending offline mutations to Supabase.
 *   2. [PullSyncRepository.pullAll] — fetches the latest parents + students
 *      from Supabase and upserts them into Room (the FIX for the previous
 *      "push-only" sync architecture).
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
 * @param syncService The push-side drain executor.
 * @param pullSyncRepository The pull-side fetcher (NEW — migration 0028).
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionManager: SessionManager,
    private val onlineDetector: OnlineDetector,
    private val syncService: SyncService,
    private val pullSyncRepository: PullSyncRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!onlineDetector.isOnline()) return Result.success()
        if (sessionManager.current() == null) return Result.success()
        // PUSH: drain pending offline mutations to Supabase.
        runCatching { syncService.drainPending() }
        // PULL: fetch the latest parents + students from Supabase into Room.
        // This is the FIX that lets Android see what the Desktop imported.
        // We pull with `since = null` (full refresh) for now — incremental
        // sync via `p_since` can be layered on later by persisting the
        // last-pull timestamp.
        runCatching { pullSyncRepository.pullAll(sinceIso = null) }
        return Result.success()
    }

    companion object {
        /** Unique WorkManager work name — used by [SyncService.schedulePeriodicSync]. */
        const val WORK_NAME = "el_imtiyaz_sync_drain"
    }
}

