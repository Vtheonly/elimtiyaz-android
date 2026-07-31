package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * El-Imtiyaz Staff Application — Hilt-annotated, WorkManager-configured.
 *
 * This is the entry point for the DI graph. Hilt generates the dependency
 * injection code at compile time; all `@Inject`-annotated constructors
 * across the codebase are wired here.
 *
 * Notification channels are created here so they exist before any FCM
 * message arrives. Channels map to the desktop's `AlertPriority` enum:
 *   - `urgent` → high importance (sound + heads-up)
 *   - `high`   → default importance
 *   - `medium` → low importance
 *   - `low`    → minimum importance
 */
@HiltAndroidApp
class ElImtiyazApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() {
            val builder = Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.INFO)
            if (::workerFactory.isInitialized) {
                builder.setWorkerFactory(workerFactory)
            }
            return builder.build()
        }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_URGENT,
            "Alertes urgentes",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Retards de paiement, absences critiques, échecs de workflow"
            enableVibration(true)
        })

        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_HIGH,
            "Alertes prioritaires",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Approbations en attente, mentions de chat" })

        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_MEDIUM,
            "Notifications",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Mises à jour générales" })

        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_LOW,
            "Silencieuses",
            NotificationManager.IMPORTANCE_MIN,
        ).apply { description = "Notifications de fond (sync, etc.)" })
    }

    companion object {
        const val CHANNEL_URGENT = "el_imtiyaz_urgent"
        const val CHANNEL_HIGH   = "el_imtiyaz_high"
        const val CHANNEL_MEDIUM = "el_imtiyaz_medium"
        const val CHANNEL_LOW    = "el_imtiyaz_low"
    }
}
