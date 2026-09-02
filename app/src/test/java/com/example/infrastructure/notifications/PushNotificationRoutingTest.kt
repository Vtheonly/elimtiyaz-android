package com.example.infrastructure.notifications

import com.example.core.Permission
import com.example.ui.features.main.HUB_TABS
import com.example.ui.features.main.deepLinkTargetTabIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T-127 / PUSH-101 regression tests — the Android half of the push
 * notification payload fix.
 *
 * The defect: the canonical EF (FCM HTTP v1) puts `title`/`body` in the
 * standard `notification` payload and (since T-126) propagates `priority`
 * + `type` into `data`; the OLD Android receiver read every routing field
 * from `data` with wrong defaults (priority "medium" — the EF's default is
 * "high") and taps had NO deep-link intent filter in the manifest.
 *
 * The fix (T-127): [resolveNotificationContent] resolves content with
 * fallbacks in both directions (data first, then the notification
 * payload); the priority default matches the EF's ("high"); taps open
 * MainActivity via the `${applicationId}.NOTIFICATION_CLICK` intent
 * filter carrying type + route extras, published to [NotificationDeepLink];
 * the bottom-nav host selects the matching hub tab via
 * [deepLinkTargetTabIndex] (permission-keyed, RBAC-respecting).
 */
class PushNotificationRoutingTest {

    // ---- resolveNotificationContent ----

    @Test
    fun `data fields win when the canonical EF propagates them`() {
        val resolved = resolveNotificationContent(
            data = mapOf("title" to "T", "body" to "B", "priority" to "urgent", "type" to "payment"),
            notificationTitle = "ignored",
            notificationBody = "ignored",
        )
        assertEquals("T", resolved.title)
        assertEquals("B", resolved.body)
        assertEquals("urgent", resolved.priority)
        assertEquals("payment", resolved.type)
    }

    @Test
    fun `notification payload is the fallback for title and body`() {
        val resolved = resolveNotificationContent(
            data = emptyMap(),
            notificationTitle = "Notification title",
            notificationBody = "Notification body",
        )
        assertEquals("Notification title", resolved.title)
        assertEquals("Notification body", resolved.body)
        // routing fields absent -> canonical defaults
        assertEquals("high", resolved.priority) // the EF's own default (T-126)
        assertEquals("system", resolved.type)
    }

    @Test
    fun `the priority default is the EF default high, not medium`() {
        // PUSH-101's exact complaint: the foreground channel was ALWAYS
        // CHANNEL_MEDIUM because data["priority"] was null and the old
        // fallback was "medium".
        val resolved = resolveNotificationContent(data = emptyMap(), notificationTitle = null, notificationBody = null)
        assertEquals("high", resolved.priority)
        assertEquals(channelFor("high"), channelFor(resolved.priority))
    }

    @Test
    fun `empty payload falls back to app defaults`() {
        val resolved = resolveNotificationContent(data = emptyMap(), notificationTitle = null, notificationBody = null)
        assertEquals("El-Imtiyaz", resolved.title)
        assertEquals("", resolved.body)
    }

    // ---- channel + importance mapping ----

    @Test
    fun `priority maps to the four channels`() {
        assertEquals("el_imtiyaz_urgent", channelFor("urgent"))
        assertEquals("el_imtiyaz_high", channelFor("high"))
        assertEquals("el_imtiyaz_medium", channelFor("medium"))
        assertEquals("el_imtiyaz_low", channelFor("low"))
        // unknown priority degrades to medium — never crashes
        assertEquals("el_imtiyaz_medium", channelFor("weird"))
    }

    // ---- deep-link hub mapping ----

    @Test
    fun `financial notification types open the Finances hub`() {
        val idx = deepLinkTargetTabIndex("payment", HUB_TABS)
        assertEquals(Permission.VIEW_FINANCIALS, HUB_TABS[idx].requiresPermission)
    }

    @Test
    fun `academic notification types open the Pedagogie hub`() {
        for (type in listOf("absence", "grade", "homework", "calendar")) {
            val idx = deepLinkTargetTabIndex(type, HUB_TABS)
            assertEquals(Permission.VIEW_ACADEMICS, HUB_TABS[idx].requiresPermission)
        }
    }

    @Test
    fun `unknown types degrade to the first tab`() {
        assertEquals(0, deepLinkTargetTabIndex("system", HUB_TABS))
        assertEquals(0, deepLinkTargetTabIndex("message", HUB_TABS))
        assertEquals(0, deepLinkTargetTabIndex("announcement", HUB_TABS))
    }

    @Test
    fun `when the target hub is not visible the deep link degrades to the first visible tab`() {
        // a role that can only see Tableau + Finances (no academics permission)
        val visible = HUB_TABS.filter { it.requiresPermission != Permission.VIEW_ACADEMICS }
        val idx = deepLinkTargetTabIndex("grade", visible)
        assertEquals(0, idx) // academics hub filtered out -> first tab
        // and financial types still find their hub in the filtered list
        val finIdx = deepLinkTargetTabIndex("payment", visible)
        assertEquals(Permission.VIEW_FINANCIALS, visible[finIdx].requiresPermission)
    }

    // ---- NotificationDeepLink bus ----

    @Test
    fun `deep link bus is one-shot publish-consume`() {
        assertNull(NotificationDeepLink.pending.value)
        NotificationDeepLink.publish(NotificationDeepLink.Pending(type = "payment", route = "/#/finance"))
        assertEquals("payment", NotificationDeepLink.pending.value?.type)
        assertEquals("/#/finance", NotificationDeepLink.pending.value?.route)
        NotificationDeepLink.consume()
        assertNull(NotificationDeepLink.pending.value)
    }
}
