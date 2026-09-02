package com.example.infrastructure.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * PUSH-101 deep-link bus (T-127, 2026-09-02).
 *
 * A notification tap (background: the system honours the EF's
 * `android.notification.click_action` = NOTIFICATION_CLICK_ACTION via the
 * manifest intent-filter; foreground: the contentIntent built by
 * [ElImtiyazMessagingService]) opens [com.example.MainActivity] with
 * routing extras. MainActivity extracts them and PUBLISHES a
 * [Pending] here — surviving the Splash → (Login) → Main flow — so the
 * bottom-nav host can select the matching hub tab once the user is signed
 * in, then CONSUME it (one-shot semantics).
 *
 * Deliberately tiny: no persistence, no queue — a cold start replays the
 * launch intent (MainActivity reads it in onCreate), a warm start replays
 * onNewIntent. Anything more (per-route navigation with ids, e.g.
 * PaymentDetail) is a documented follow-up on the notification-type →
 * route mapping table (see the PUSH-101 registry entry).
 */
object NotificationDeepLink {

    /** The notification's `type` (EF category) + optional route hint (`data.url`). */
    data class Pending(
        val type: String,
        val route: String?,
    )

    private val _pending = MutableStateFlow<Pending?>(null)

    /** The unconsumed deep-link, if any. Null after [consume]. */
    val pending: StateFlow<Pending?> = _pending

    /** Called by MainActivity when a deep-link intent arrives. */
    fun publish(value: Pending) {
        _pending.value = value
    }

    /** Called by the bottom-nav host after acting on the pending value. */
    fun consume() {
        _pending.value = null
    }
}
