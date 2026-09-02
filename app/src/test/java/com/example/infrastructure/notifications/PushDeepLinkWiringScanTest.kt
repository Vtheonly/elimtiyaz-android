package com.example.infrastructure.notifications

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-127 / PUSH-101 source-scan regression — pins the deep-link WIRING
 * (the parts a pure unit test cannot reach: the manifest intent-filter
 * and the intent plumbing through MainActivity).
 *
 * The wire contract (three sides, all pinned by tests):
 *   - hub EF (T-126): `androidClickAction = "com.aistudio.elimtiyazstaff.bxmzlx.NOTIFICATION_CLICK"`
 *     — pinned by the hub's t-126-push-ef-canonical.test.ts
 *   - AndroidManifest: `<action android:name="${applicationId}.NOTIFICATION_CLICK"/>`
 *     — pinned here
 *   - Android service/MainActivity: `NOTIFICATION_CLICK_ACTION = BuildConfig.APPLICATION_ID + ".NOTIFICATION_CLICK"`
 *     + contentIntent extras + onNewIntent/onCreate deep-link publication — pinned here
 */
class PushDeepLinkWiringScanTest {

    private fun repoFile(rel: String): File {
        // app/src/test/java/com/example/infrastructure/notifications/ -> repo root is 7 levels up
        val root = File(javaClass.protectionDomain.codeSource.location.toURI())
            .resolve("../../../..").canonicalFile // app/build/intermediates-ish -> not stable; use cwd fallback
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File("app/src/main/$rel"),          // cwd = repo root (gradle default)
            File("../app/src/main/$rel"),        // cwd = app/
            File("$root/../../../../src/main/$rel"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Cannot locate $rel from cwd=${cwd.absolutePath}")
    }

    @Test
    fun `manifest declares the NOTIFICATION_CLICK intent filter with DEFAULT category`() {
        val manifest = repoFile("AndroidManifest.xml").readText()
        assertTrue(
            "MainActivity must declare the \${applicationId}.NOTIFICATION_CLICK intent-filter (PUSH-101/T-127)",
            manifest.contains("\${applicationId}.NOTIFICATION_CLICK"),
        )
        assertTrue(
            "the NOTIFICATION_CLICK filter must carry CATEGORY_DEFAULT so notification taps resolve",
            manifest.contains("android.intent.category.DEFAULT"),
        )
    }

    @Test
    fun `the messaging service builds a contentIntent with the click action and routing extras`() {
        val src = repoFile(
            "java/com/example/infrastructure/notifications/ElImtiyazMessagingService.kt",
        ).readText()
        assertTrue(src.contains("NOTIFICATION_CLICK_ACTION"))
        assertTrue(src.contains("EXTRA_DEEPLINK_TYPE"))
        assertTrue(src.contains("EXTRA_DEEPLINK_ROUTE"))
        assertTrue(src.contains("PendingIntent.getActivity"))
        assertTrue(src.contains("setContentIntent"))
        // the action constant is derived from applicationId — same value the
        // manifest placeholder and the hub EF hardcode
        assertTrue(src.contains("BuildConfig.APPLICATION_ID + \".NOTIFICATION_CLICK\""))
    }

    @Test
    fun `MainActivity publishes deep-links on cold start AND warm start`() {
        val src = repoFile("java/com/example/MainActivity.kt").readText()
        assertTrue("cold start: onCreate must feed the launch intent", src.contains("handleNotificationDeepLink(intent)"))
        assertTrue("warm start: onNewIntent must be overridden", src.contains("override fun onNewIntent"))
        assertTrue(src.contains("NotificationDeepLink.publish"))
    }
}
