package com.example.infrastructure.routing

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.domain.model.GeoPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Routing foreground service — restores the pre-redesign `RoutingForegroundService` (commit a34333a).
 *
 * Owns the [FusedLocationProviderClient] for live driver position updates.
 * Publishes `liveLocation` and `lastSpeedKmh` StateFlows via companion object
 * so the [RoutingMapScreen] can observe them without holding a reference to the service.
 *
 * Notification: sticky, channel `routing-fg` (importance LOW), id 5_001.
 *
 * On Android 14+ uses `startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)`.
 *
 * Three actions: [ACTION_START], [ACTION_UPDATE], [ACTION_STOP].
 */
class RoutingForegroundService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            _liveLocation.value = GeoPoint(loc.latitude, loc.longitude)
            if (loc.hasSpeed()) {
                _lastSpeedKmh.value = loc.speed * 3.6
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val tripLabel = intent.getStringExtra(EXTRA_TRIP_LABEL) ?: "Tournée active"
                val stopIndex = intent.getIntExtra(EXTRA_STOP_INDEX, 0)
                val stopTotal = intent.getIntExtra(EXTRA_STOP_TOTAL, 0)
                startTracking(tripLabel, stopIndex, stopTotal)
            }
            ACTION_UPDATE -> {
                val tripLabel = intent.getStringExtra(EXTRA_TRIP_LABEL) ?: "Tournée active"
                val stopIndex = intent.getIntExtra(EXTRA_STOP_INDEX, 0)
                val stopTotal = intent.getIntExtra(EXTRA_STOP_TOTAL, 0)
                updateNotification(tripLabel, stopIndex, stopTotal)
            }
            ACTION_STOP -> {
                stopTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startTracking(tripLabel: String, stopIndex: Int, stopTotal: Int) {
        val notification = buildNotification(tripLabel, stopIndex, stopTotal)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
                .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
                .setMaxUpdateDelayMillis(MAX_DELAY_MS)
                .build()
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        }
    }

    private fun updateNotification(tripLabel: String, stopIndex: Int, stopTotal: Int) {
        val notification = buildNotification(tripLabel, stopIndex, stopTotal)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun stopTracking() {
        fusedClient.removeLocationUpdates(locationCallback)
        _liveLocation.value = null
        _lastSpeedKmh.value = 0.0
    }

    private fun buildNotification(tripLabel: String, stopIndex: Int, stopTotal: Int): Notification {
        val stopIntent = Intent(this, RoutingForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(tripLabel)
            .setContentText("Arrêt $stopIndex / $stopTotal")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Terminer", stopPending)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Suivi de tournée",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notifications du service de suivi de position pour les chauffeurs."
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "routing-fg"
        const val NOTIFICATION_ID = 5_001
        const val ACTION_START = "com.example.routing.START"
        const val ACTION_UPDATE = "com.example.routing.UPDATE"
        const val ACTION_STOP = "com.example.routing.STOP"
        const val EXTRA_TRIP_LABEL = "trip_label"
        const val EXTRA_STOP_INDEX = "stop_index"
        const val EXTRA_STOP_TOTAL = "stop_total"

        private const val INTERVAL_MS = 5_000L
        private const val MIN_INTERVAL_MS = 2_000L
        private const val MAX_DELAY_MS = 10_000L

        private val _liveLocation = MutableStateFlow<GeoPoint?>(null)
        val liveLocation: StateFlow<GeoPoint?> = _liveLocation.asStateFlow()

        private val _lastSpeedKmh = MutableStateFlow(0.0)
        val lastSpeedKmh: StateFlow<Double> = _lastSpeedKmh.asStateFlow()

        fun startTracking(context: Context, tripLabel: String, stopIndex: Int, stopTotal: Int) {
            val intent = Intent(context, RoutingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRIP_LABEL, tripLabel)
                putExtra(EXTRA_STOP_INDEX, stopIndex)
                putExtra(EXTRA_STOP_TOTAL, stopTotal)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun updateProgress(context: Context, tripLabel: String, stopIndex: Int, stopTotal: Int) {
            val intent = Intent(context, RoutingForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TRIP_LABEL, tripLabel)
                putExtra(EXTRA_STOP_INDEX, stopIndex)
                putExtra(EXTRA_STOP_TOTAL, stopTotal)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopTracking(context: Context) {
            val intent = Intent(context, RoutingForegroundService::class.java).apply { action = ACTION_STOP }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
