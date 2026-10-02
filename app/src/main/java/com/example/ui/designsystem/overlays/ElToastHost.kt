package com.example.ui.designsystem.overlays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * App-scoped toast state — the Android mirror of the desktop's
 * `ToastProvider` (src/app/providers/toast-provider.tsx: a root-mounted
 * provider so transient feedback survives navigation).
 *
 * T-460 H2 (issue #3 F-18): the replacement surface for
 * `android.widget.Toast`. The raw platform toast is system-styled
 * (ignores the app theme) and — unlike every other feedback primitive —
 * survived outside the design system because an in-app snackbar on a
 * screen that navigates away would be destroyed by the pop
 * (the ExpenseSubmit case). Mounting [ElToastHostState.render] ONCE at
 * the app root (above the NavHost, inside the theme) gives the DS toast
 * the same app-scoped lifetime as the platform toast while staying
 * themed.
 *
 * Usage:
 * ```
 * // root (once, inside ElImtiyazTheme):
 * CompositionLocalProvider(LocalElToast provides rememberElToastState()) {
 *     AppNavHost()
 *     LocalElToast.current.render()   // draws above the nav host
 * }
 *
 * // any screen / callback:
 * LocalElToast.current.show("Code copié", ElToastTone.SUCCESS)
 * ```
 */
class ElToastHostState internal constructor() {
    internal data class Pending(
        val message: String,
        val tone: ElToastTone,
        val durationMs: Long,
        // Monotonic nonce: re-showing the SAME message restarts the timer
        // and re-renders (the ElToast component keys auto-dismiss on the
        // message string; the nonce forces a fresh composition).
        val nonce: Long,
    )

    internal var current by mutableStateOf<Pending?>(null)
    private var seq = 0L

    /**
     * Show a transient toast. Replaces any toast currently on screen
     * (single-slot semantics, matching the platform toast queue's visible
     * entry).
     */
    fun show(
        message: String,
        tone: ElToastTone = ElToastTone.NEUTRAL,
        durationMs: Long = 3000,
    ) {
        seq += 1
        current = Pending(message, tone, durationMs, seq)
    }

    /** Convenience wrappers mirroring the desktop's severity helpers. */
    fun showSuccess(message: String, durationMs: Long = 3000) = show(message, ElToastTone.SUCCESS, durationMs)
    fun showError(message: String, durationMs: Long = 4000) = show(message, ElToastTone.DANGER, durationMs)
    fun showWarning(message: String, durationMs: Long = 4000) = show(message, ElToastTone.WARNING, durationMs)
    fun showInfo(message: String, durationMs: Long = 3000) = show(message, ElToastTone.INFO, durationMs)
}

/** Creates an [ElToastHostState] scoped to the composition. */
@Composable
fun rememberElToastState(): ElToastHostState = remember { ElToastHostState() }

/**
 * The [ElToastHostState] for the current tree. Provided once at the app
 * root by [ElToastHost]; read by any screen to fire app-scoped toasts.
 */
val LocalElToast = compositionLocalOf<ElToastHostState> {
    // A functional fallback so previews / tests without the host still work
    // (calls are silently dropped, like an unmounted provider).
    ElToastHostState()
}

/**
 * Root mount for the app-scoped toast layer: provides [LocalElToast] to
 * [content] and renders the active toast ABOVE it (so the toast survives
 * any navigation inside content — the platform-toast lifetime property).
 */
@Composable
fun ElToastHost(
    modifier: Modifier = Modifier,
    state: ElToastHostState = rememberElToastState(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalElToast provides state) {
        androidx.compose.foundation.layout.Box(modifier = modifier) {
            content()
            state.render()
        }
    }
}

/** Draws the current toast (if any). Called by [ElToastHost] above the app content. */
@Composable
private fun ElToastHostState.render() {
    val pending = current ?: return
    ElToast(
        message = pending.message,
        tone = pending.tone,
        durationMs = pending.durationMs,
        onDismiss = { current = null },
    )
}
