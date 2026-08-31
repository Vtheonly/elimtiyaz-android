package com.example.infrastructure.sync

import com.example.infrastructure.supabase.SyncPushTimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * T-020 / SYNC-103 regression suite — transient (5xx) sync failures must be
 * REQUEUED, not dropped.
 *
 * The defect: `SyncSupport.tryThenEnqueue` enqueued the mutation for a later
 * drain ONLY on network/offline/timeout failures. A 5xx from the server
 * (deploy restart, DB migration window, PostgREST overload) surfaced as a
 * supabase-kt `RestException` → mapped to a non-network error code → NOT
 * enqueued — even though the local Room write had ALREADY happened. The
 * mutation then never reached the server (silent Room↔server drift) until
 * the user re-typed the same edit by hand. Permanent rejections (4xx
 * validation, 401/403 RLS/auth) must keep failing fast so the user fixes
 * the data instead of the app retrying forever.
 *
 * Fix under test: [SyncErrorClassifier] — one classification point used by
 * `tryThenEnqueue`: offline → always requeue; transport errors
 * (DNS/connection/timeout/[SyncPushTimeoutException]) → requeue; SDK
 * HttpRequestException → requeue; RestException with 5xx → requeue;
 * RestException with 4xx → fail fast; anything else online → fail fast.
 *
 * SDK BEHAVIOUR NOTE (recorded gap, same as SyncErrorSurfacingTest): that
 * supabase-kt 3.1.1 throws RestException on every non-2xx is verified from
 * the pinned artifact's bytecode (T-019 session notes); the live 5xx
 * round-trip needs a deployed backend. Constructing a real RestException
 * in a unit test requires the whole ktor call graph (its constructor
 * renders request URL/method/headers into the message), so the status
 * mapping is unit-tested via [SyncErrorClassifier.isTransientStatus] and
 * the RestException wiring is pinned by a source-scan.
 */
class SyncRequeueT020Test {

    // ── Classification semantics (pure) ───────────────────────────────────

    @Test
    fun `5xx statuses are transient - 4xx are not`() {
        for (s in listOf(500, 502, 503, 504, 599)) assertTrue(SyncErrorClassifier.isTransientStatus(s))
        for (s in listOf(400, 401, 403, 404, 409, 422, 499)) assertFalse(SyncErrorClassifier.isTransientStatus(s))
    }

    @Test
    fun `transport-level failures are transient`() {
        val online = true
        assertTrue(SyncErrorClassifier.isTransient(UnknownHostException("dns"), online))
        assertTrue(SyncErrorClassifier.isTransient(ConnectException("refused"), online))
        assertTrue(SyncErrorClassifier.isTransient(SocketTimeoutException("timed out"), online))
        assertTrue(SyncErrorClassifier.isTransient(SyncPushTimeoutException("t", 5_000), online))
    }

    @Test
    fun `offline device makes ANY failure transient - requeue (old behaviour preserved)`() {
        assertTrue(SyncErrorClassifier.isTransient(RuntimeException("whatever"), deviceOnline = false))
        assertTrue(SyncErrorClassifier.isTransient(IllegalStateException("x"), deviceOnline = false))
    }

    @Test
    fun `unknown failure with the device online stays fail-fast (no enqueue)`() {
        // e.g. an NPE in the repository — never a reason to silently queue.
        assertFalse(SyncErrorClassifier.isTransient(RuntimeException("bug"), deviceOnline = true))
    }

    // ── RestException wiring (source-scan) ────────────────────────────────

    @Test
    fun `RestException branch - 5xx transient, 4xx fail-fast (source-scan on the classifier)`() {
        // The SDK wraps every non-2xx in a RestException whose statusCode is
        // read from the ktor response. The STATUS mapping itself is
        // unit-tested via isTransientStatus above; this scan pins the
        // wiring: the classifier must read statusCode from the
        // RestException and delegate to isTransientStatus.
        val src = readMainSource("infrastructure/sync/SyncErrorClassifier.kt")
        val wiring = Regex("if \\(e is RestException\\) return isTransientStatus\\(e.statusCode\\)")
            .find(src) ?: error("RestException -> isTransientStatus wiring not found")
        assertTrue(wiring.value.isNotEmpty())
    }

    // ── tryThenEnqueue wiring (source-scan, SyncErrorSurfacingTest pattern) ──

    @Test
    fun `tryThenEnqueue classifies via SyncErrorClassifier and enqueues in the transient branch`() {
        val src = readMainSource("infrastructure/sync/SyncSupport.kt")
        val block = Regex("suspend fun <T : Any> tryThenEnqueue\\([\\s\\S]*?\\n    \\}").find(src)?.value
            ?: error("tryThenEnqueue not found")
        assertTrue(
            "tryThenEnqueue must delegate transient classification to SyncErrorClassifier.isTransient",
            block.contains("SyncErrorClassifier.isTransient(e, onlineDetector.isOnline())"),
        )
        assertTrue(
            "the transient branch must still enqueue to SyncService",
            block.contains("syncService.enqueue("),
        )
        assertFalse(
            "the old error-code-only condition must not return",
            block.contains("error.code == Errors.CODE_NETWORK || error.code == Errors.CODE_OFFLINE"),
        )
    }

    private fun readMainSource(relative: String): String =
        File("src/main/java/com/example/$relative").readText()
}
