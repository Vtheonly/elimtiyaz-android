package com.example.ui.designsystem.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.elShadow
import com.example.ui.designsystem.theme.ElSheetHandleShape
import com.example.ui.designsystem.theme.ElSheetShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Unified bottom sheet — slide-up panel with the signature shape (32dp top
 * radius), drag handle, scrim, and tap-outside-to-dismiss. Shares elevation,
 * motion, and surface treatment with [ElDialogShell].
 *
 * Use [ElSheetContent] for the standard inner layout (title / body / actions).
 */
@Composable
fun ElBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnScrimTap: Boolean = true,
    showHandle: Boolean = true,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    val c = ElTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.scrim)
            .then(
                if (dismissOnScrimTap) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.DropdownList,
                        onClick = onDismissRequest,
                    )
                } else Modifier
            ),
    ) {
        Column(
            modifier = modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .clip(ElSheetShape)
                .background(c.surface)
                .border(ElTheme.borders.thin, c.outlineVariant, ElSheetShape)
                .elShadow(ElTheme.elevation.floating, ElSheetShape)
                .navigationBarsPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},  // swallow taps
                )
                .padding(bottom = 8.dp),
        ) {
            if (showHandle) SheetHandle()
            if (title != null) {
                Text(
                    text = title,
                    color = c.textPrimary,
                    style = ElTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            content()
        }
    }
}

/** The drag handle bar at the top of a sheet. */
@Composable
private fun ColumnScope.SheetHandle() {
    val c = ElTheme.colors
    Box(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .align(Alignment.CenterHorizontally)
            .width(36.dp)
            .height(4.dp)
            .clip(ElSheetHandleShape)
            .background(c.outlineStrong),
    )
}
