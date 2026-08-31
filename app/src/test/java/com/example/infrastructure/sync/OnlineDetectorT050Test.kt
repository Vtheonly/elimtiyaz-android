package com.example.infrastructure.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-050 regression suite — OnlineDetector fail-closed semantics (WEAK-009),
 * third-party probe leak removal (SEC-006), and pullAll dedup (WEAK-010).
 *
 * WHAT THE DEFECTS WERE (all confirmed by the original audit):
 *  - WEAK-009: initial state was optimistic "online"; `isOnline()` returned
 *    ONLY `connectivityActive`; `probe()`'s catch-all returned TRUE on every
 *    network failure; `updateState` computed `online = connectivityActive`
 *    and ignored `probeOk` entirely.
 *  - SEC-006: an unconfigured build probed `https://supabase.com` every 30
 *    seconds — leaking the user's IP + app fingerprint to a third party.
 *  - WEAK-010: pullAll fired from 6 call sites; SyncWorker ran it TWICE per
 *    tick (its own call + the one inside drainPending); syncNow also
 *    double-pulled.
 *
 * The behavioural units (URL resolution, HTTP verdict, combine rule) are pure
 * companion functions, tested directly. The wiring (isOnline reads the
 * combined state, catch returns false, no supabase.com fallback, single pull
 * per tick) is pinned with source-scan tests — the pattern established by
 * SyncErrorSurfacingTest (the class needs an Android Context + real network
 * for end-to-end exercise, which unit tests deliberately do not fake).
 */
class OnlineDetectorT050Test {

    // ── resolveProbeUrl: our backend or nothing ──────────────────────────

    @Test
    fun `real configured url maps to its own auth health endpoint`() {
        assertEquals(
            "https://acme-school.supabase.co/auth/v1/health",
            OnlineDetector.resolveProbeUrl("https://acme-school.supabase.co"),
        )
    }

    @Test
    fun `trailing slash is normalized and quotes stripped`() {
        assertEquals(
            "https://acme-school.supabase.co/auth/v1/health",
            OnlineDetector.resolveProbeUrl("\"https://acme-school.supabase.co/\""),
        )
    }

    @Test
    fun `blank url yields null - never a third party host`() {
        assertNull(OnlineDetector.resolveProbeUrl(""))
        assertNull(OnlineDetector.resolveProbeUrl("   "))
    }

    @Test
    fun `placeholder urls yield null - fresh checkouts do not leak`() {
        assertNull(OnlineDetector.resolveProbeUrl("https://your-project.supabase.co"))
        assertNull(OnlineDetector.resolveProbeUrl("https://YOUR_PROJECT.supabase.co"))
        assertNull(OnlineDetector.resolveProbeUrl("placeholder"))
        assertNull(OnlineDetector.resolveProbeUrl("not-a-url"))
    }

    // ── probeAccepts: captive-portal-proof verdict ───────────────────────

    @Test
    fun `http 200 healthy and 401 reachable both count as online`() {
        assertTrue(OnlineDetector.probeAccepts(200))
        assertTrue(OnlineDetector.probeAccepts(401))
    }

    @Test
    fun `redirects 5xx and other codes are offline`() {
        assertFalse(OnlineDetector.probeAccepts(302)) // captive portal login redirect
        assertFalse(OnlineDetector.probeAccepts(301))
        assertFalse(OnlineDetector.probeAccepts(200 + 1))
        assertFalse(OnlineDetector.probeAccepts(500))
        assertFalse(OnlineDetector.probeAccepts(503))
    }

    // ── combineOnline: the fail-closed gate ─────────────────────────────

    @Test
    fun `online requires connectivity AND probe`() {
        assertTrue(OnlineDetector.combineOnline(true, true))
        assertFalse(OnlineDetector.combineOnline(true, false)) // probe failed → offline
        assertFalse(OnlineDetector.combineOnline(false, true)) // no network → offline
        assertFalse(OnlineDetector.combineOnline(false, false))
    }

    // ── source-scan regression pins ──────────────────────────────────────

    @Test
    fun `isOnline returns the combined state not connectivity alone`() {
        val src = readMainSource("infrastructure/sync/OnlineDetector.kt")
        assertTrue(
            "isOnline() must read _state.value.online",
            Regex("fun isOnline\\(\\): Boolean = _state\\.value\\.online").containsMatchIn(src),
        )
    }

    @Test
    fun `the initial state is fail-closed offline`() {
        val src = readMainSource("infrastructure/sync/OnlineDetector.kt")
        assertTrue(
            "the constructor must NOT default to online = true",
            Regex("connectivityActive = false,\\s*probeOk = false,\\s*online = false").containsMatchIn(src),
        )
    }

    @Test
    fun `probe failures return false - the catch-all-true is gone`() {
        val src = readMainSource("infrastructure/sync/OnlineDetector.kt")
        assertTrue(
            "the catch block must return false (fail-closed)",
            Regex("Log\\.w\\(\"OnlineDetector\", \"Supabase probe failed[^)]*\\)\\s*false").containsMatchIn(src),
        )
    }

    @Test
    fun `no supabase dot com fallback host anywhere in the detector`() {
        val src = readMainSource("infrastructure/sync/OnlineDetector.kt")
        assertFalse(
            "SEC-006: the third-party fallback host must not appear",
            src.contains("supabase.com"),
        )
    }

    @Test
    fun `updateState combines connectivity and probe`() {
        val src = readMainSource("infrastructure/sync/OnlineDetector.kt")
        assertTrue(
            "the combined state must use combineOnline(connectivityActive, probeOk)",
            src.contains("combineOnline(next.connectivityActive, next.probeOk)"),
        )
    }

    @Test
    fun `pullAll has the dedup gate`() {
        val src = readMainSource("infrastructure/sync/PullSyncRepository.kt")
        assertTrue(
            "pullAll must check the in-flight guard and dedup window",
            src.contains("pullInFlight") && src.contains("PULL_DEDUP_WINDOW_MS"),
        )
    }

    @Test
    fun `SyncWorker no longer pulls directly - one pull per tick via drainPending`() {
        val src = readMainSource("infrastructure/sync/SyncWorker.kt")
        assertFalse(
            "the worker must not call pullAll itself anymore",
            src.contains("pullSyncRepository.pullAll"),
        )
    }

    @Test
    fun `syncNow no longer double-pulls`() {
        val src = readMainSource("infrastructure/sync/SyncService.kt")
        val syncNowBlock = Regex(
            "fun syncNow\\(\\): Result<Unit> \\{[\\s\\S]*?\\n    \\}",
        ).find(src)?.value ?: error("syncNow not found")
        assertFalse(
            "syncNow must rely on drainPending's trailing pull only",
            syncNowBlock.contains("pullSyncRepository.pullAll"),
        )
    }

    private fun readMainSource(relativeUnderSrcMainJava: String): String {
        val relative = "src/main/java/com/example/$relativeUnderSrcMainJava"
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val inModule = File(cwd, relative)
        if (inModule.isFile) return inModule.readText()
        val inRepoRoot = File(cwd.parentFile ?: cwd, relative)
        if (inRepoRoot.isFile) return inRepoRoot.readText()
        error("Source file not found from either ${cwd.absolutePath} or ${cwd.parentFile}: $relative")
    }
}
