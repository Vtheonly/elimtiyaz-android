package com.example.infrastructure.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.BuildConfig
import com.example.ElImtiyazApplication
import com.example.MainActivity
import com.example.R
import com.example.core.Result
import com.example.infrastructure.supabase.NetworkTimeouts
import com.example.infrastructure.supabase.SupabaseClientProvider
import com.example.session.SessionManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
 *
 * PUSH-101 fix (T-127, 2026-09-02): the canonical EF (hub, T-126) now
 * propagates `priority` + `type` into the FCM `data` field, and
 * `android.notification.click_action` carries the intent action name
 * [NOTIFICATION_CLICK_ACTION] (matched by the manifest intent-filter).
 * The receiver resolves content with fallbacks in BOTH directions
 * (`data` first — the canonical sender's routing fields — then the
 * standard `notification` payload for title/body), and taps are delivered
 * to [MainActivity] as a deep-link intent carrying the notification type
 * + optional route, published to [NotificationDeepLink] so the bottom-nav
 * host can select the matching hub tab.
 */
@AndroidEntryPoint
class ElImtiyazMessagingService : FirebaseMessagingService() {

    @Inject lateinit var tokenRegistrar: FcmTokenRegistrar

    override fun onMessageReceived(message: RemoteMessage) {
        val content = resolveNotificationContent(
            data = message.data,
            notificationTitle = message.notification?.title,
            notificationBody = message.notification?.body,
        )

        // Deep-link tap intent: the SAME action the EF sets as
        // android.notification.click_action (so background taps — handled
        // by the system from the notification payload — and foreground
        // taps — handled by THIS contentIntent — open MainActivity
        // identically, with the routing extras attached).
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            action = NOTIFICATION_CLICK_ACTION
            putExtra(EXTRA_DEEPLINK_TYPE, content.type)
            message.data["url"]?.let { putExtra(EXTRA_DEEPLINK_ROUTE, it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            content.type.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelFor(content.priority))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setPriority(importanceFor(content.priority))
            .setContentIntent(contentIntent)
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
 * PUSH-101 (T-127) — pure notification-content resolution, extracted for
 * unit testing. FCM HTTP v1 delivers `title`/`body` in the standard
 * `notification` payload; the canonical EF additionally propagates the
 * routing fields (`priority`, `type`) into `data`. `data` wins for
 * title/body too (legacy/foreign senders may put them there), then the
 * `notification` payload, then defaults.
 *
 * The `priority` fallback is "high" — the canonical EF's own default
 * (T-126) — so a payload missing the field still routes to the same
 * channel the EF would have chosen.
 */
data class ResolvedNotification(
    val title: String,
    val body: String,
    val priority: String,
    val type: String,
)

fun resolveNotificationContent(
    data: Map<String, String>,
    notificationTitle: String?,
    notificationBody: String?,
): ResolvedNotification = ResolvedNotification(
    title = data["title"] ?: notificationTitle ?: "El-Imtiyaz",
    body = data["body"] ?: notificationBody ?: "",
    priority = data["priority"] ?: "high",
    type = data["type"] ?: "system",
)

/** Maps the resolved priority to the application's notification channel. */
fun channelFor(priority: String): String = when (priority) {
    "urgent" -> ElImtiyazApplication.CHANNEL_URGENT
    "high"   -> ElImtiyazApplication.CHANNEL_HIGH
    "low"    -> ElImtiyazApplication.CHANNEL_LOW
    else     -> ElImtiyazApplication.CHANNEL_MEDIUM
}

/** Maps the resolved priority to a NotificationCompat priority constant. */
fun importanceFor(priority: String): Int = when (priority) {
    "urgent" -> NotificationCompat.PRIORITY_HIGH
    "high"   -> NotificationCompat.PRIORITY_DEFAULT
    "low"    -> NotificationCompat.PRIORITY_MIN
    else     -> NotificationCompat.PRIORITY_LOW
}

/**
 * PUSH-101 deep-link constants (T-127). The action string MUST match the
 * manifest's `${applicationId}.NOTIFICATION_CLICK` intent-filter AND the
 * EF's `androidClickAction` (hub, T-126).
 */
const val NOTIFICATION_CLICK_ACTION: String = BuildConfig.APPLICATION_ID + ".NOTIFICATION_CLICK"
const val EXTRA_DEEPLINK_TYPE = "deeplink_notification_type"
const val EXTRA_DEEPLINK_ROUTE = "deeplink_route"

/**
 * Registers the FCM token with the backend so push notifications can be
 * targeted to this device.
 *
 * When Supabase is configured (real URL + anon key in `.env`), this calls
 * the `register_fcm_token` RPC. When Supabase is NOT configured (placeholder
 * credentials), it logs the token locally — push notifications won't be
 * delivered, but the app still functions normally.
 *
 * SYNC-104 fix (2026-08-30): [deactivate] is the sign-out counterpart.
 * Previously the app never unregistered tokens on sign-out — device_tokens
 * rows stayed is_active=true and kept receiving pushes for a signed-out
 * user. Both RPCs are caller-verified on the server (hub migration 0050).
 */
@Singleton
class FcmTokenRegistrar @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val sessionManager: SessionManager,
) {
    suspend fun register(token: String): Result<Unit> = try {
        val userId = sessionManager.currentUserId()
            ?: return Result.Err(com.example.core.Errors.unauthorized("No session"))

        if (NetworkTimeouts.isSupabaseConfigured) {
            NetworkTimeouts.guard<Unit>("fcm.registerToken", timeoutMs = 4_000L) {
                val params = buildJsonObject {
                    put("p_user_id", userId)
                    put("p_token", token)
                    put("p_platform", "android")
                }
                provider.postgrest.rpc("register_fcm_token", params)
            }
            Log.i("FcmTokenRegistrar", "FCM token registered with Supabase for user $userId")
        } else {
            Log.i("FcmTokenRegistrar", "Supabase not configured — FCM token logged locally for user $userId")
        }
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(com.example.core.Errors.fromException(e))
    }

    /**
     * Deactivate this user's Android device tokens on sign-out (canonical
     * `deactivate_fcm_tokens` RPC, hub migration 0050). Soft-delete only —
     * the next sign-in re-activates the token via [register].
     *
     * MUST be called while the auth session is still valid (the RPC verifies
     * the caller via auth.uid()); failures are non-fatal by design — sign-out
     * must proceed even when the backend is unreachable.
     */
    suspend fun deactivate(): Result<Unit> = try {
        val userId = sessionManager.currentUserId()
            ?: return Result.Err(com.example.core.Errors.unauthorized("No session"))

        if (NetworkTimeouts.isSupabaseConfigured) {
            NetworkTimeouts.guard<Unit>("fcm.deactivateTokens", timeoutMs = 2_000L) {
                val params = buildJsonObject {
                    put("p_user_id", userId)
                    put("p_platform", "android")
                }
                provider.postgrest.rpc("deactivate_fcm_tokens", params)
            }
            Log.i("FcmTokenRegistrar", "FCM tokens deactivated for user $userId")
        }
        Result.Ok(Unit)
    } catch (e: Exception) {
        // Non-fatal: stale tokens are cleaned up on the next sign-in refresh.
        Log.w("FcmTokenRegistrar", "FCM token deactivation failed (non-fatal): ${e.message}")
        Result.Err(com.example.core.Errors.fromException(e))
    }
}
