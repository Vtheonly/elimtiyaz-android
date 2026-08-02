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
    val isSupabaseConfigured: Boolean by lazy {
        val url = BuildConfig.SUPABASE_URL.trim().removeSurrounding("\"")
        val key = BuildConfig.SUPABASE_ANON_KEY.trim().removeSurrounding("\"")
        url.startsWith("https://") &&
            !url.contains("your-project", ignoreCase = true) &&
            !url.contains("demo.supabase.co", ignoreCase = true) &&
            !url.contains("placeholder", ignoreCase = true) &&
            key.isNotBlank() &&
            !key.equals("your-anon-key", ignoreCase = true) &&
            !key.equals("demo-key", ignoreCase = true)
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
}
