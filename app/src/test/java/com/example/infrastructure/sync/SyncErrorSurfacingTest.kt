package com.example.infrastructure.sync

import com.example.infrastructure.supabase.NetworkTimeouts
import com.example.infrastructure.supabase.SyncPushTimeoutException
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * T-019 / CROSS-200 regression suite — sync pushes must surface server
 * rejections instead of silently marking the queue entry "synced".
 *
 * The defect had ONE swallowing layer, not two: the supabase-kt SDK
 * (3.1.1) already throws `PostgrestRestException` on every 4xx/5xx
 * response (verified against the pinned artifact: `SupabaseApi.rawRequest`
 * checks `!status.isSuccess() && parseErrorResponse != null` and throws;
 * Postgrest wires `parseErrorResponse` = `PostgrestRestException`). The
 * dispatcher, however, fed every push through `NetworkTimeouts.guard`,
 * whose `catch (e: Throwable) -> null` swallowed the SDK's exception — so
 * `pushEntry` returned normally and the SyncService marked the rejected
 * write "synced" (silent Room-server drift, no retry, no lastError).
 *
 * Fix under test: [NetworkTimeouts.guardSyncPush] — a push-oriented guard
 * that propagates block exceptions and converts timeouts into a plain
 * [SyncPushTimeoutException] — and the dispatcher's 8 push paths now use
 * it. The source-scan tests at the bottom pin that wiring against
 * regression.
 *
 * Recorded gap (why this is TESTED, not VERIFIED): an actual live 400/500
 * round-trip against a deployed `upsert_*_from_import` RPC needs a real
 * Supabase project — the SDK-side throw-on-4xx behaviour itself is pinned
 * by the bytecode verification documented in the hub problem registry
 * (ARCH-007 session notes) rather than by a test in this suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncErrorSurfacingTest {

    // ── guardSyncPush contract ────────────────────────────────────────────

    @Test
    fun `guardSyncPush returns the block result on success`() {
        val result = kotlinx.coroutines.runBlocking {
            NetworkTimeouts.guardSyncPush("test.success", onlyIfConfigured = false) { "pushed" }
        }
        assertEquals("pushed", result)
    }

    @Test
    fun `guardSyncPush propagates the block exception instead of swallowing it`() {
        val thrown = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                NetworkTimeouts.guardSyncPush(
                    "test.propagate",
                    onlyIfConfigured = false,
                ) { throw IllegalStateException("FK violation: parent row missing") }
            }
        }
        // The exception that reaches SyncService.drainPending must carry the
        // server-side context — drainPending stores e.message as lastError.
        assertTrue(
            "expected the original error message, got: ${thrown.message}",
            thrown.message?.contains("FK violation") == true,
        )
    }

    // NOTE (T-019 session): the "returns null when unconfigured" branch is
    // deliberately NOT pinned against the unit-test BuildConfig — a NEW
    // discovery recorded in the hub registry shows the `.env.example`
    // placeholder values (https://YOUR_PROJECT.supabase.co — underscore
    // defeats the "your-project" hyphen check — and "your-anon-key-here")
    // PASS NetworkTimeouts.isSupabaseConfigured, so the check's outcome in
    // a unit-test build depends on environment values that are outside the
    // test's control. The branch shares the long-established
    // onlyIfConfigured expression with guard(); SEC-005/T-064 tracks the
    // placeholder-detection weakness itself.

    @Test
    fun `guardSyncPush converts a timeout into a plain SyncPushTimeoutException`() {
        val thrown = assertThrows(SyncPushTimeoutException::class.java) {
            kotlinx.coroutines.runBlocking {
                NetworkTimeouts.guardSyncPush(
                    "test.timeout",
                    timeoutMs = 10L,
                    onlyIfConfigured = false,
                ) { delay(5_000L) }
            }
        }
        assertTrue(
            "timeout message should name the tag and duration, got: ${thrown.message}",
            thrown.message!!.contains("test.timeout") && thrown.message!!.contains("10ms"),
        )
        // Must NOT be a CancellationException — SyncService catches plain
        // Exceptions; a cancellation leaking into the drain loop would kill
        // the whole batch instead of failing one entry.
        assertTrue(thrown !is kotlinx.coroutines.CancellationException)
    }


    // ── source-scan regression pins ──────────────────────────────────────

    @Test
    fun `dispatcher no longer uses the swallowing guard for any push path`() {
        val dispatcherSrc = readMainSource("infrastructure/sync/SyncQueueDispatcher.kt")
        assertTrue(
            "SyncQueueDispatcher must not wrap pushes in the swallowing NetworkTimeouts.guard",
            !Regex("""NetworkTimeouts\.guard\b""").containsMatchIn(dispatcherSrc),
        )
        val pushCount = Regex("""NetworkTimeouts\.guardSyncPush\(""").findAll(dispatcherSrc).count()
        assertEquals(
            "expected all 8 push paths (homework, parent, student, payment, " +
                "ledger_entry, installment, grade, attendance) on guardSyncPush",
            8, pushCount,
        )
    }

    @Test
    fun `guardSyncPush has no catch-all Throwable clause (the CROSS-200 mechanism)`() {
        val timeoutsSrc = readMainSource("infrastructure/supabase/NetworkTimeouts.kt")
        val guardSyncPushBlock = timeoutsSrc
            .substringAfter("suspend fun <T> guardSyncPush(")
            .substringBefore("\n    }")
        assertTrue(
            "guardSyncPush body must not contain a catch (Throwable) clause",
            !guardSyncPushBlock.contains("Throwable"),
        )
        assertTrue(
            "guardSyncPush must convert timeouts into SyncPushTimeoutException",
            guardSyncPushBlock.contains("SyncPushTimeoutException"),
        )
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** Resolve a file under `src/main/java`, probing the module dir then the repo root (Gradle worker cwd = `app/`). */
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
