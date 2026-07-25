package com.elimtiyaz.feature.routing

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.domain.model.GeoPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps tracking driver location while the
 * [RoutingMapScreen] is in the background (screen off, app recents, etc.).
 *
 * Publishes a sticky notification ("Tournée en cours — Arrêt X/Y") and exposes
 * the live location via the companion-object [liveLocation] StateFlow so the
 * [RoutingMapViewModel] can render the bus marker and compute ETA without
 * holding its own location callback.
 *
 * The service is started from [RoutingMapScreen] via [startTracking] and
 * stopped via [stopTracking]. The `FOREGROUND_SERVICE_LOCATION` permission and
 * the service declaration live in the feature module's `AndroidManifest.xml`.
 */
class RoutingForegroundService : Service() {

    private val log = Logger.withTag("RoutingFgSvc")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var fused: FusedLocationProviderClient

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            _liveLocation.value = GeoPoint(loc.latitude, loc.longitude)
            _lastSpeedKmh.value = if (loc.hasSpeed()) loc.speed * 3.6 else 0.0
            log.d { "Location update: ${loc.latitude},${loc.longitude}" }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        ensureNotificationChannel()
        log.i { "RoutingForegroundService created" }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> {
                val tripLabel = intent?.getStringExtra(EXTRA_TRIP_LABEL) ?: "Tournée en cours"
                val stopIndex = intent?.getIntExtra(EXTRA_STOP_INDEX, 0) ?: 0
                val stopTotal = intent?.getIntExtra(EXTRA_STOP_TOTAL, 0) ?: 0
                startForegroundWithNotification(tripLabel, stopIndex, stopTotal)
                startLocationUpdates()
                log.i { "Started tracking: $tripLabel — Arrêt ${stopIndex + 1}/$stopTotal" }
            }
            ACTION_UPDATE -> {
                val tripLabel = intent?.getStringExtra(EXTRA_TRIP_LABEL) ?: "Tournée en cours"
                val stopIndex = intent?.getIntExtra(EXTRA_STOP_INDEX, 0) ?: 0
                val stopTotal = intent?.getIntExtra(EXTRA_STOP_TOTAL, 0) ?: 0
                updateNotification(tripLabel, stopIndex, stopTotal)
            }
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                log.i { "Stopped tracking" }
            }
        }
        return START_STICKY
    }

    /** Start receiving high-accuracy location updates (~5s interval). */
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // Permission is checked by RoutingMapScreen before startService() is called.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setMaxUpdateDelayMillis(10_000L)
            .build()
        try {
            fused.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            log.w { "Missing location permission: ${e.message}" }
        }
    }

    /** Stop location updates — paired with [startLocationUpdates]. */
    private fun stopLocationUpdates() {
        runCatching { fused.removeLocationUpdates(locationCallback) }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tournées en cours",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Suivi de position pendant la tournée de ramassage."
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification(tripLabel: String, stopIndex: Int, stopTotal: Int) {
        val notification = buildNotification(tripLabel, stopIndex, stopTotal)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(tripLabel: String, stopIndex: Int, stopTotal: Int) {
        val nm = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(tripLabel, stopIndex, stopTotal))
    }

    private fun buildNotification(tripLabel: String, stopIndex: Int, stopTotal: Int): Notification {
        val text = if (stopTotal > 0) "Arrêt ${stopIndex + 1}/$stopTotal" else "Suivi en cours"
        // Tapping the notification does nothing special in v1 — we just keep
        // the app's launcher intent so the user lands back in the app.
        val pending = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(tripLabel)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopLocationUpdates()
        scope.cancel()
        _liveLocation.value = null
        _lastSpeedKmh.value = 0.0
        log.i { "RoutingForegroundService destroyed" }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "routing-fg"
        private const val NOTIFICATION_ID = 5_001

        const val ACTION_START = "com.elimtiyaz.routing.START"
        const val ACTION_STOP = "com.elimtiyaz.routing.STOP"
        const val ACTION_UPDATE = "com.elimtiyaz.routing.UPDATE"
        const val EXTRA_TRIP_LABEL = "trip_label"
        const val EXTRA_STOP_INDEX = "stop_index"
        const val EXTRA_STOP_TOTAL = "stop_total"

        private val _liveLocation = MutableStateFlow<GeoPoint?>(null)
        /** Latest device location reported by the foreground service. */
        val liveLocation: StateFlow<GeoPoint?> = _liveLocation.asStateFlow()

        private val _lastSpeedKmh = MutableStateFlow(0.0)
        /** Latest reported speed in km/h (0 if device doesn't report speed). */
        val lastSpeedKmh: StateFlow<Double> = _lastSpeedKmh.asStateFlow()

        /**
         * Start tracking driver location. Called from [RoutingMapScreen] after
         * the ACCESS_FINE_LOCATION permission has been granted.
         */
        fun startTracking(
            context: Context,
            tripLabel: String,
            stopIndex: Int = 0,
            stopTotal: Int = 0,
        ) {
            val intent = Intent(context, RoutingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRIP_LABEL, tripLabel)
                putExtra(EXTRA_STOP_INDEX, stopIndex)
                putExtra(EXTRA_STOP_TOTAL, stopTotal)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Update the sticky notification's progress text. Called when the
         * driver advances to the next stop.
         */
        fun updateProgress(
            context: Context,
            tripLabel: String,
            stopIndex: Int,
            stopTotal: Int,
        ) {
            val intent = Intent(context, RoutingForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TRIP_LABEL, tripLabel)
                putExtra(EXTRA_STOP_INDEX, stopIndex)
                putExtra(EXTRA_STOP_TOTAL, stopTotal)
            }
            // update-only: use startService so the service isn't restarted
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Stop tracking and tear the foreground service down. Called from
         * [RoutingMapScreen] on disposal or "Terminer la tournée".
         */
        fun stopTracking(context: Context) {
            val intent = Intent(context, RoutingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Human-readable timestamp helper exposed for service notifications. */
        fun nowLabel(): String = Formatters.dateTime(Formatters.nowIso())
    }
}
