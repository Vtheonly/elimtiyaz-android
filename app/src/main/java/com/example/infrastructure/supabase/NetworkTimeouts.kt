package com.example.infrastructure.supabase

import com.example.BuildConfig
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Centralized network timeout + Supabase configuration guards.
 *
 * Problem this solves: the Supabase Kotlin SDK's default network calls have
 * NO timeout — if the backend is unreachable (placeholder URL, slow emulator
 * DNS, captive portal, etc.) the call hangs forever and the UI spinner spins
 * indefinitely. This is the root cause of "the app blocks at login".
 *
 * Every Supabase call must be wrapped in [guard]:
 *
 * ```kotlin
 * val result = NetworkTimeouts.guard("signIn") {
 *     auth.signInWith(Email) { ... }
 * }
 * ```
 *
 * If the call times out, `guard` returns `null` and the caller falls through
 * to its demo/offline fallback.
 *
 * The [isSupabaseConfigured] check catches the case where the build is still
 * using the placeholder URL (`https://your-project.supabase.co`) — in that
 * case there is NO point attempting a real call, so `guard` returns null
 * immediately without even launching the coroutine.
 */
object NetworkTimeouts {

    /** Hard cap for any single Supabase call (auth, postgrest, rpc, storage). */
    const val DEFAULT_TIMEOUT_MS: Long = 4_000L

    /** Shorter cap for fire-and-forget reads (KPI refresh, observe*). */
    const val SHORT_TIMEOUT_MS: Long = 2_500L

    /**
     * True only when BuildConfig.SUPABASE_URL points at a real Supabase project
     * (not the placeholder, not "demo.supabase.co", not empty).
     *
     * This is checked BEFORE any network call to avoid pointless DNS lookups
     * that hang on the emulator.
     */
    val isSupabaseConfigured: Boolean
        get() {
            val url = BuildConfig.SUPABASE_URL.trim().removeSurrounding("\"")
            val key = BuildConfig.SUPABASE_ANON_KEY.ifBlank { BuildConfig.SUPABASE_PUBLISHABLE_KEY }.trim().removeSurrounding("\"")
            val buildConfigConfigured = url.startsWith("https://") &&
                !url.contains("your-project", ignoreCase = true) &&
                !url.contains("demo.supabase.co", ignoreCase = true) &&
                !url.contains("placeholder", ignoreCase = true) &&
                key.isNotBlank() &&
                !key.equals("your-anon-key", ignoreCase = true) &&
                !key.equals("placeholder-anon-key", ignoreCase = true) &&
                !key.equals("placeholder-publishable-key", ignoreCase = true) &&
                !key.equals("demo-key", ignoreCase = true)
            // SECURITY FIX — no hardcoded production fallback: the only valid
            // sources are BuildConfig (`.env` via the secrets plugin) and the
            // runtime override stored by SupabaseClientProvider (SharedPreferences).
            return buildConfigConfigured
        }

    /**
     * Run [block] with a hard timeout. Returns `null` on timeout OR on any
     * exception OR when Supabase is not configured.
     *
     * Callers MUST handle the null case by falling back to demo/offline data.
     */
    suspend fun <T> guard(
        tag: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onlyIfConfigured: Boolean = true,
        block: suspend () -> T,
    ): T? {
        if (onlyIfConfigured && !isSupabaseConfigured) return null
        return try {
            withTimeout(timeoutMs) { block() }
        } catch (_: TimeoutCancellationException) {
            android.util.Log.w("NetworkTimeouts", "[$tag] timed out after ${timeoutMs}ms")
            null
        } catch (e: Throwable) {
            android.util.Log.w("NetworkTimeouts", "[$tag] failed: ${e.message}")
            null
        }
    }

    /**
     * Run a sync PUSH with a hard timeout — the [guard] counterpart that
     * does NOT swallow failures (T-019 / CROSS-200 fix).
     *
     * [guard] converts every Throwable into a `null` return. That is the
     * right contract for reads/UI (fall back to demo data), but when the
     * sync dispatcher pushes a queued mutation, a swallowed failure means
     * `pushEntry` returns normally and the SyncService marks the entry
     * "synced" even though the server rejected it — silent data loss (the
     * local Room cache and the server drift apart, no retry, no lastError).
     *
     * Behaviour:
     *  - Supabase not configured → `null` (caller skips; demo mode).
     *  - Block succeeds → its result.
     *  - Timeout → throws [SyncPushTimeoutException] (a plain RuntimeException
     *    so the sync layer records a retryable failure and coroutine
     *    cancellation semantics are not abused).
     *  - Any other exception thrown by [block] (the supabase-kt SDK throws
     *    `PostgrestRestException` on every 4xx/5xx response — verified
     *    against the pinned 3.1.1 `SupabaseApi.rawRequest` bytecode) →
     *    propagates unchanged to `SyncService.drainPending`, which keeps the
     *    entry pending with `lastError` and retries with backoff (the
     *    desktop `defaultPushHandler` contract).
     */
    suspend fun <T> guardSyncPush(
        tag: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onlyIfConfigured: Boolean = true,
        block: suspend () -> T,
    ): T? {
        if (onlyIfConfigured && !isSupabaseConfigured) return null
        return try {
            withTimeout(timeoutMs) { block() }
        } catch (_: TimeoutCancellationException) {
            android.util.Log.w("NetworkTimeouts", "[$tag] sync push timed out after ${timeoutMs}ms")
            throw SyncPushTimeoutException(tag, timeoutMs)
        }
    }
}

/**
 * Thrown by [NetworkTimeouts.guardSyncPush] when a sync push exceeds its
 * timeout. A plain [RuntimeException] (NOT a [TimeoutCancellationException])
 * so the SyncService can catch and record it without touching coroutine
 * cancellation machinery.
 */
class SyncPushTimeoutException(tag: String, timeoutMs: Long) :
    RuntimeException("[$tag] sync push timed out after ${timeoutMs}ms")
