package com.example.ui.designsystem.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.elShadow
import com.example.ui.designsystem.theme.ElNotificationShape
import com.example.ui.designsystem.theme.ElTheme
import kotlinx.coroutines.delay

/**
 * Top-of-screen toast / snackbar. Slides in from the top, auto-dismisses after
 * [durationMs]. Tap the X to dismiss early.
 *
 * All overlays in the system share the same shadow, shape family, and motion
 * specs — toasts use [ElNotificationShape] (16dp) and ElTheme.elevation.high.
 */
@Composable
fun ElToast(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: ElToastTone = ElToastTone.NEUTRAL,
    durationMs: Long = 3000,
    onDismiss: () -> Unit = {},
) {
    val c = ElTheme.colors
    val accent = c.toastAccent(tone)
    var visible by remember(message) { mutableStateOf(true) }
    LaunchedEffect(message) {
        delay(durationMs)
        visible = false
        onDismiss()
    }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.statusBarsPadding(),
        ) {
            ToastBody(
                message = message,
                icon = icon,
                accent = accent,
            )
        }
    }
}

/** The visible toast surface — accent bar + optional icon + message. */
@Composable
private fun ToastBody(
    message: String,
    icon: ImageVector?,
    accent: androidx.compose.ui.graphics.Color,
) {
    val c = ElTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(ElNotificationShape)
            .background(c.surface)
            .elShadow(ElTheme.elevation.high, ElNotificationShape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 24.dp)
                .clip(ElNotificationShape)
                .background(accent),
        )
        if (icon != null) {
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = message,
            color = c.textPrimary,
            style = ElTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
