package com.example.ui.designsystem.overlays

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/**
 * Standard sheet content layout — body + actions with consistent spacing.
 * Designed to sit inside [ElBottomSheet].
 */
@Composable
fun ElSheetContent(
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ElTheme.spacing.xl, vertical = ElTheme.spacing.sm),
    ) {
        body()
        Spacer(Modifier.height(ElTheme.spacing.lg))
        actions()
    }
}
