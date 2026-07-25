package com.elimtiyaz.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import dagger.hilt.android.HiltAndroidApp
import com.elimtiyaz.data.sync.SyncQueueWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Application entry-point. Bootstraps Hilt, WorkManager and the periodic
 * sync worker that drains the offline queue when network returns.
 *
 * Per master plan §13.05 (Mobile Backup Prohibition) `android:allowBackup`
 * is `false` in the manifest — the platform's only legitimate backup path
 * is the desktop-driven AES-256 offsite cycle.
 */
@HiltAndroidApp
class ElImtiyazApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        Logger.setTag("ElImtiyaz")
        Logger.i { "Application started" }

        // Schedule periodic sync queue drain
        val syncRequest = PeriodicWorkRequestBuilder<SyncQueueWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                10, TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "elimtiyaz-sync-queue",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest,
        )
    }
}
