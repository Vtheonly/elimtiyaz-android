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
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * El-Imtiyaz Staff Application — Hilt-annotated, WorkManager-configured.
 *
 * This is the entry point for the DI graph. Hilt generates the dependency
 * injection code at compile time; all `@Inject`-annotated constructors
 * across the codebase are wired here.
 *
 * On startup [onCreate] performs four things in addition to creating
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
 *   4. FETCHES the FCM token on startup and registers it with the backend
 *      via [FcmTokenRegistrar]. This is the FIX for the previous bug where
 *      the token was only registered on `onNewToken` (which fires ONLY when
 *      the token rotates, not on first install or app upgrade). The fetch
 *      is also re-triggered reactively when the user signs in, so the
 *      token is always registered against the active session's user id.
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
    @Inject lateinit var fcmTokenRegistrar: com.example.infrastructure.notifications.FcmTokenRegistrar

    /** Long-running scope for the FCM topic subscription observer. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Currently-subscribed role topic — used to unsubscribe on role change. */
    private var subscribedRoleTopic: String? = null

    /** Last user id we registered the FCM token against — avoids re-registering the same pair. */
    private var lastRegisteredUserId: String? = null

    /** Token fetched before the user signed in — registered once a session appears. */
    @Volatile private var pendingFcmToken: String? = null

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
        fetchAndRegisterFcmTokenOnStartup()
        observeSessionForFcmToken()
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
     * callback and launches the 30-second periodic probe loop. Safe
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

    /**
     * FETCH the FCM token on startup and register it with the backend.
     *
     * This is the FIX for the bug where the token was only registered on
     * `onNewToken` (which fires ONLY when the token rotates — not on first
     * install, not on app upgrade, not on cold start). Without this fetch,
     * first-install tokens were never registered with the backend, so push
     * notifications silently failed for new devices.
     *
     * The fetch is asynchronous and best-effort — if it fails (e.g. no
     * network, no Google Play services), the next sign-in or the next
     * `onNewToken` callback will retry.
     */
    private fun fetchAndRegisterFcmTokenOnStartup() {
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener(OnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        android.util.Log.w(
                            "ElImtiyazApp",
                            "FCM token fetch failed on startup — will retry on next sign-in/onNewToken",
                            task.exception,
                        )
                        return@OnCompleteListener
                    }
                    val token = task.result ?: return@OnCompleteListener
                    // Register on the IO dispatcher so we don't block the
                    // Firebase callback thread.
                    appScope.launch {
                        val userId = sessionManager.currentUserId()
                        if (userId != null && userId != lastRegisteredUserId) {
                            fcmTokenRegistrar.register(token)
                            lastRegisteredUserId = userId
                        } else if (userId == null) {
                            // No active session yet — stash the token so the
                            // session observer can register it once sign-in
                            // completes. Stored in a volatile field; if the
                            // process dies before sign-in, the next startup
                            // will re-fetch.
                            pendingFcmToken = token
                        }
                    }
                })
        }.onFailure { e ->
            android.util.Log.w("ElImtiyazApp", "FCM token fetch setup failed", e)
        }
    }

    /**
     * Observe the session and register the FCM token against the active
     * user id whenever the session appears. This handles the common flow:
     *   1. App cold-starts (no session).
     *   2. FCM token fetch completes → stashed in [pendingFcmToken].
     *   3. User signs in → session appears → token is registered.
     *
     * Also re-registers when the user id changes (e.g. account switch) so
     * the new user receives pushes on this device.
     */
    private fun observeSessionForFcmToken() {
        appScope.launch {
            sessionManager.state
                .filter { it != null }
                .map { it!!.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    val token = pendingFcmToken
                        ?: try { FirebaseMessaging.getInstance().token.result } catch (t: Throwable) { null }
                    if (token != null && userId != lastRegisteredUserId) {
                        runCatching { fcmTokenRegistrar.register(token) }
                        lastRegisteredUserId = userId
                        pendingFcmToken = null
                    }
                }
        }
    }

    companion object {
        const val CHANNEL_URGENT = "el_imtiyaz_urgent"
        const val CHANNEL_HIGH   = "el_imtiyaz_high"
        const val CHANNEL_MEDIUM = "el_imtiyaz_medium"
        const val CHANNEL_LOW    = "el_imtiyaz_low"
    }
}
