package com.example.ui.designsystem.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.elShadow
import com.example.ui.designsystem.theme.ElTooltipShape
import com.example.ui.designsystem.theme.ElTheme
import kotlinx.coroutines.delay

/**
 * Tooltip — short-lived hint. Auto-dismisses after [durationMs].
 * Use sparingly — never for critical information.
 *
 * Caller is responsible for anchoring (wrap the target in a Box and place
 * the tooltip at the desired alignment).
 */
@Composable
fun ElTooltip(
    text: String,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    durationMs: Long = 2200,
    onDismiss: () -> Unit = {},
) {
    val c = ElTheme.colors
    var showing by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        showing = visible
        if (visible) {
            delay(durationMs)
            showing = false
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = showing,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(ElTooltipShape)
                .background(c.inverseSurface)
                .elShadow(ElTheme.elevation.low, ElTooltipShape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = text,
                color = c.inverseOnSurface,
                style = ElTheme.typography.labelMedium,
            )
        }
    }
}
