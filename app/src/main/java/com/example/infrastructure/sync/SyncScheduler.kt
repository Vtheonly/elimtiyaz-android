package com.example.infrastructure.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager scheduler that registers the 15-minute periodic [SyncWorker]
 * draining the offline sync queue.
 *
 * Extracted from [SyncService] to isolate the WorkManager API surface
 * (constraints, unique-work policy, periodic interval) from the queue
 * state machine. The scheduler is stateless — repeated calls are
 * idempotent because [ExistingPeriodicWorkPolicy.KEEP] preserves the
 * existing interval timer.
 */
@Singleton
class SyncScheduler @Inject constructor() {

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
}
