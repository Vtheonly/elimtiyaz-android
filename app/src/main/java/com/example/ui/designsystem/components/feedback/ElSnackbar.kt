package com.example.ui.designsystem.components.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.display.ElSnackbarSeverity
import com.example.ui.designsystem.foundation.elShadow
import com.example.ui.designsystem.foundation.noRippleClickable
import com.example.ui.designsystem.theme.ElNotificationShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Bottom-anchored snackbar host — wraps Material 3 [SnackbarHost] but
 * applies the El-Imtiyaz theme colors, the [ElNotificationShape] (16dp),
 * and a slide-in-from-below motion matching the design system's
 * `ElTheme.motion.normal` cadence.
 *
 * Use with [ElSnackbarHostState.showSnackbar] — or pass a custom
 * [ElSnackbar] composable inside [content] for full styling control.
 *
 * @param hostState Snackbar host state, hoisted by the caller.
 * @param modifier  Outer modifier.
 * @param content   Optional override for the rendered snackbar; defaults
 *                  to the themed [ElSnackbar] implementation.
 */
@Composable
fun ElSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable (SnackbarData) -> Unit = { data ->
        ElSnackbar(
            message = data.visuals.message,
            actionLabel = data.visuals.actionLabel,
            severity = (data.visuals as? ElSnackbarVisuals)?.severity ?: ElSnackbarSeverity.INFO,
            onAction = { data.performAction() },
            onDismiss = if (data.visuals.withDismissAction) ({ data.dismiss() }) else null,
        )
    },
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { data -> content(SnackbarData(data)) },
    )
}

/**
 * Convenience overload accepting the El-Imtiyaz [ElSnackbarHostState] wrapper.
 * Delegates to the M3 [SnackbarHostState] inside the wrapper.
 */
@Composable
fun ElSnackbarHost(
    hostState: ElSnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable (SnackbarData) -> Unit = { data ->
        ElSnackbar(
            message = data.visuals.message,
            actionLabel = data.visuals.actionLabel,
            severity = (data.visuals as? ElSnackbarVisuals)?.severity ?: ElSnackbarSeverity.INFO,
            onAction = { data.performAction() },
            onDismiss = if (data.visuals.withDismissAction) ({ data.dismiss() }) else null,
        )
    },
) {
    ElSnackbarHost(
        hostState = hostState.delegate,
        modifier = modifier,
        content = content,
    )
}

/**
 * Lightweight wrapper around the M3 [androidx.compose.material3.SnackbarData]
 * so consumers can pattern-match without importing the M3 type.
 */
@JvmInline
value class SnackbarData(private val delegate: androidx.compose.material3.SnackbarData) {
    val visuals: androidx.compose.material3.SnackbarVisuals get() = delegate.visuals
    fun performAction() { delegate.performAction() }
    fun dismiss() { delegate.dismiss() }
}

/**
 * Custom visuals carrying the [ElSnackbarSeverity]. Created by
 * [ElSnackbarHostState.showSnackbar] and consumed by [ElSnackbarHost].
 */
class ElSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    override val duration: SnackbarDuration,
    override val withDismissAction: Boolean,
    val severity: ElSnackbarSeverity,
) : androidx.compose.material3.SnackbarVisuals

/**
 * Hoisted state for [ElSnackbarHost]. Holds the queue of pending snackbars
 * and exposes [showSnackbar] with the El-Imtiyaz severity enum.
 */
class ElSnackbarHostState {
    internal val delegate = SnackbarHostState()

    /**
     * Enqueue a themed snackbar.
     *
     * @return `true` if the action button was tapped, `false` if dismissed.
     */
    suspend fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        severity: ElSnackbarSeverity = ElSnackbarSeverity.INFO,
        duration: SnackbarDuration = SnackbarDuration.Short,
        withDismissAction: Boolean = false,
    ): SnackbarResult = delegate.showSnackbar(
        ElSnackbarVisuals(message, actionLabel, duration, withDismissAction, severity),
    )
}

/**
 * A single rendered snackbar — colored by severity with a matching leading
 * icon and an optional action / dismiss button.
 *
 * @param message     Body text.
 * @param actionLabel Optional action button label.
 * @param severity    Tint / icon selector.
 * @param onAction    Action tap callback.
 * @param onDismiss   Dismiss-tap callback.
 * @param modifier    Outer modifier.
 */
@Composable
fun ElSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    severity: ElSnackbarSeverity = ElSnackbarSeverity.INFO,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val c = ElTheme.colors
    val accent = c.snackbarAccent(severity)
    val icon = severity.snackbarIcon()

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically { it } + fadeIn(tween(200)),
        exit = slideOutVertically { it } + fadeOut(tween(180)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(ElNotificationShape)
                .background(c.surface)
                .elShadow(ElTheme.elevation.high, ElNotificationShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = message,
                color = c.textPrimary,
                style = ElTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = accent)
                }
            }
            if (onDismiss != null) {
                Spacer(Modifier.size(4.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = c.textMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .noRippleClickable(onClick = onDismiss),
                )
            }
        }
    }
}

/** Resolve a [severity] to its accent color via the active theme colors. */
private fun com.example.ui.designsystem.theme.ElColors.snackbarAccent(
    severity: ElSnackbarSeverity,
): Color = when (severity) {
    ElSnackbarSeverity.INFO -> primary
    ElSnackbarSeverity.SUCCESS -> success
    ElSnackbarSeverity.WARNING -> warning
    ElSnackbarSeverity.DANGER -> danger
}

/** Resolve a [severity] to its leading icon. */
private fun ElSnackbarSeverity.snackbarIcon(): ImageVector = when (this) {
    ElSnackbarSeverity.INFO -> Icons.Default.Info
    ElSnackbarSeverity.SUCCESS -> Icons.Default.CheckCircle
    ElSnackbarSeverity.WARNING -> Icons.Default.Warning
    ElSnackbarSeverity.DANGER -> Icons.Default.Error
}
