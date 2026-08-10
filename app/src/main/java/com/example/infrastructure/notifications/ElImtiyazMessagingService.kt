package com.example.infrastructure.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.ElImtiyazApplication
import com.example.R
import com.example.core.Result
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

/**
 * FCM messaging service — receives push notifications from Supabase Edge
 * Functions (overdue alerts, expense approvals, chat mentions, workflow
 * failures).
 *
 * Notification channels are created in [ElImtiyazApplication.onCreate].
 * The priority from the FCM message data maps to a channel:
 *   urgent → CHANNEL_URGENT (high importance, sound + heads-up)
 *   high   → CHANNEL_HIGH (default importance)
 *   medium → CHANNEL_MEDIUM (low importance)
 *   low    → CHANNEL_LOW (minimum importance)
 */
@AndroidEntryPoint
class ElImtiyazMessagingService : FirebaseMessagingService() {

    @Inject lateinit var tokenRegistrar: FcmTokenRegistrar

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "El-Imtiyaz"
        val body = data["body"] ?: message.notification?.body ?: ""
        val priority = data["priority"] ?: "medium"
        val type = data["type"] ?: "system"

        val channelId = when (priority) {
            "urgent" -> ElImtiyazApplication.CHANNEL_URGENT
            "high"   -> ElImtiyazApplication.CHANNEL_HIGH
            "low"    -> ElImtiyazApplication.CHANNEL_LOW
            else     -> ElImtiyazApplication.CHANNEL_MEDIUM
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(when (priority) {
                "urgent" -> NotificationCompat.PRIORITY_HIGH
                "high"   -> NotificationCompat.PRIORITY_DEFAULT
                "low"    -> NotificationCompat.PRIORITY_MIN
                else     -> NotificationCompat.PRIORITY_LOW
            })
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        nm.notify(notificationId, notification)
    }

    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            tokenRegistrar.register(token)
        }
    }
}

/**
 * Registers the FCM token with the backend so push notifications can be
 * targeted to this device. The token is associated with the current user's
 * profile via an RPC.
 */
@Singleton
class FcmTokenRegistrar @Inject constructor(
    private val sessionManager: com.example.session.SessionManager,
) {
    suspend fun register(token: String): Result<Unit> = try {
        val userId = sessionManager.currentUserId()
            ?: return Result.Err(com.example.core.Errors.unauthorized("No session"))
        // Local-only build: log the token. In production this would call
        // the `register_fcm_token` RPC via Supabase.
        Log.i("FcmTokenRegistrar", "FCM token registered for user $userId (local build)")
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(com.example.core.Errors.fromException(e))
    }
}

