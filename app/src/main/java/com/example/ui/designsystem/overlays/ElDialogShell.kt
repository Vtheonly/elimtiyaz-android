package com.example.ui.designsystem.overlays

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.designsystem.foundation.elShadow
import com.example.ui.designsystem.theme.ElDialogShape
import com.example.ui.designsystem.theme.EmphasizedDecelerate
import com.example.ui.designsystem.theme.ElTheme

/**
 * The base El-Imtiyaz dialog shell — every modal, popup, and overlay uses
 * this. Provides:
 *  - Consistent scrim (theme scrim color)
 *  - Consistent shape: [ElDialogShape] (28dp rounded)
 *  - Consistent motion: scale-in from 0.92 + fade, 220ms emphasized-decelerate
 *  - Consistent elevation: tinted shadow (floating spec)
 *
 * Use [ElDialogContent] for the standard inner layout (title / body / actions).
 */
@Composable
fun ElDialogShell(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnScrimTap: Boolean = true,
    dismissOnBackPress: Boolean = true,
    content: @Composable () -> Unit,
) {
    val c = ElTheme.colors
    Dialog(
        onDismissRequest = {
            if (dismissOnScrimTap || dismissOnBackPress) onDismissRequest()
        },
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnScrimTap,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.DropdownList,
                ) { if (dismissOnScrimTap) onDismissRequest() },
            contentAlignment = Alignment.Center,
        ) {
            DialogSurface(
                modifier = modifier,
                content = content,
            )
        }
    }
}

/** The elevated, scaled, animated surface that hosts the dialog content. */
@Composable
private fun DialogSurface(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val c = ElTheme.colors
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(220, easing = EmphasizedDecelerate),
        label = "el-dialog-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(180),
        label = "el-dialog-alpha",
    )
    Box(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .wrapContentHeight()
            .scale(0.92f + (scale - 0.92f))
            .alpha(alpha)
            .elShadow(ElTheme.elevation.floating, ElDialogShape)
            .clip(ElDialogShape)
            .background(c.surface)
            .border(ElTheme.borders.thin, c.outlineVariant, ElDialogShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},  // swallow taps so they don't dismiss
            ),
    ) {
        content()
    }
}
