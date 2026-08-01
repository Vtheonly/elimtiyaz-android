package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import com.example.core.Role
import com.example.infrastructure.sync.OnlineDetector
import com.example.infrastructure.sync.SyncService
import com.example.session.SessionManager
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * El-Imtiyaz Staff Application — Hilt-annotated, WorkManager-configured.
 *
 * This is the entry point for the DI graph. Hilt generates the dependency
 * injection code at compile time; all `@Inject`-annotated constructors
 * across the codebase are wired here.
 *
 * On startup [onCreate] performs three things in addition to creating
 * the notification channels:
 *   1. Starts [OnlineDetector] — registers the ConnectivityManager callback
 *      and launches the 30-second periodic probe loop.
 *   2. Registers the 15-minute periodic [SyncService] via WorkManager so
 *      the offline queue drains when online.
 *   3. Subscribes the FCM topic for the user's role (e.g. `role_teacher`)
 *      so role-targeted push notifications reach this device. The
 *      subscription is reactive — when the session changes (sign-in /
 *      sign-out / role switch), the previous topic is unsubscribed and
 *      the new one is subscribed.
 *
 * Notification channels are created here so they exist before any FCM
 * message arrives. Channels map to the desktop's `AlertPriority` enum:
 *   - `urgent` → high importance (sound + heads-up)
 *   - `high`   → default importance
 *   - `medium` → low importance
 *   - `low`    → minimum importance
 */
@HiltAndroidApp
class ElImtiyazApplication : MultiDexApplication(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var onlineDetector: OnlineDetector
    @Inject lateinit var syncService: SyncService
    @Inject lateinit var sessionManager: SessionManager

    /** Long-running scope for the FCM topic subscription observer. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Currently-subscribed role topic — used to unsubscribe on role change. */
    private var subscribedRoleTopic: String? = null

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
        startOnlineDetector()
        schedulePeriodicSync()
        observeRoleForFcmTopic()
    }

    /** Create the four notification channels before any FCM message arrives. */
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

    /**
     * Start the [OnlineDetector] — registers the ConnectivityManager
     * callback and launches the 30-second periodic HEAD probe loop. Safe
     * to call multiple times (idempotent).
     */
    private fun startOnlineDetector() {
        runCatching { onlineDetector.start() }
    }

    /**
     * Register the 15-minute periodic [SyncService] via WorkManager. Uses
     * `ExistingPeriodicWorkPolicy.KEEP` so the schedule is not reset on
     * every app launch (the existing interval timer is preserved).
     */
    private fun schedulePeriodicSync() {
        runCatching { syncService.schedulePeriodicSync(this) }
    }

    /**
     * Observe [SessionManager.state] and subscribe/unsubscribe FCM topics
     * for the active role. When the session changes:
     *   - Unsubscribe from the previous role topic (if any).
     *   - Subscribe to `role_${role.code}` for the new role.
     *
     * Role-scoped topics let the backend push role-targeted notifications
     * (e.g. "all teachers: staff meeting at 15:00") without enumerating
     * device tokens.
     */
    private fun observeRoleForFcmTopic() {
        appScope.launch {
            sessionManager.state
                .map { it?.role }
                .distinctUntilChanged()
                .collect { role ->
                    handleRoleTopic(role)
                }
        }
    }

    /** Swap the FCM role topic subscription — unsubscribes the old, subscribes the new. */
    private fun handleRoleTopic(role: Role?) {
        val previous = subscribedRoleTopic
        if (previous != null && previous != role?.let { roleTopic(it) }) {
            runCatching { FirebaseMessaging.getInstance().unsubscribeFromTopic(previous) }
        }
        if (role != null && role != Role.STUDENT && role != Role.PARENT) {
            // Only staff roles receive push notifications on mobile (per plan §13.05).
            val topic = roleTopic(role)
            runCatching { FirebaseMessaging.getInstance().subscribeToTopic(topic) }
            subscribedRoleTopic = topic
        } else {
            subscribedRoleTopic = null
        }
    }

    /** Build the FCM topic string for a given [Role] (e.g. `role_teacher`). */
    private fun roleTopic(role: Role): String = "role_${role.code}"

    companion object {
        const val CHANNEL_URGENT = "el_imtiyaz_urgent"
        const val CHANNEL_HIGH   = "el_imtiyaz_high"
        const val CHANNEL_MEDIUM = "el_imtiyaz_medium"
        const val CHANNEL_LOW    = "el_imtiyaz_low"
    }
}
