package com.example.infrastructure.sync

import com.example.infrastructure.supabase.SyncPushTimeoutException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException

/**
 * Transient-vs-permanent failure classification for the sync pipeline
 * (T-020 / SYNC-103).
 *
 * WHY THIS EXISTS: `SyncSupport.tryThenEnqueue` used to enqueue the mutation
 * for a later drain ONLY on network/offline/timeout failures. A 5xx from the
 * server (deploy restart, DB migration window, PostgREST overload) surfaced
 * as `RestException` → mapped to a non-network error code → NOT enqueued —
 * even though the local Room write had already happened. The mutation then
 * NEVER reached the server (data drift) until the user retried the same
 * edit by hand. Transient failures must be REQUEUED; permanent rejections
 * (4xx validation, 401/403) must fail fast so the user can fix the data.
 *
 * The supabase-kt SDK (3.1.1) wraps EVERY non-2xx response in a
 * [RestException] subclass (verified from the pinned artifact's bytecode —
 * T-002 technique), exposing [RestException.getStatusCode]. Transport-level
 * failures surface as JDK IO exceptions or the SDK's [HttpRequestException].
 */
object SyncErrorClassifier {

    /** True for the TRANSIENT HTTP status class (server-side, retryable). */
    fun isTransientStatus(statusCode: Int): Boolean = statusCode in 500..599

    /**
     * True when [e] is a TRANSIENT failure — safe to enqueue for a later
     * drain. [deviceOnline] reflects the caller's current connectivity view.
     */
    fun isTransient(e: Throwable, deviceOnline: Boolean): Boolean {
        if (!deviceOnline) return true
        // Transport-level failures (DNS, connection refused, timeouts).
        if (e is java.net.UnknownHostException) return true
        if (e is java.net.ConnectException) return true
        if (e is java.net.SocketTimeoutException) return true
        // Project-level push timeout (NetworkTimeouts.guardSyncPush).
        if (e is SyncPushTimeoutException) return true
        // SDK request-level network failure (transport below HTTP semantics).
        if (e is HttpRequestException) return true
        // HTTP-level: only the 5xx class is transient; 4xx is a permanent
        // rejection (validation, RLS denial, 401) — fail fast, never requeue.
        if (e is RestException) return isTransientStatus(e.statusCode)
        return false
    }
}
