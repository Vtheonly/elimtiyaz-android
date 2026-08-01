package com.example.ui.designsystem.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/**
 * Standard dialog content layout — title / optional message / actions row.
 *
 * Designed to sit inside [ElDialogShell]. The shell handles scrim, shape,
 * motion, and elevation; this composable handles the inner content structure.
 */
@Composable
fun ElDialogContent(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = ElTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        if (icon != null) {
            DialogIcon(icon = icon)
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = title,
            color = c.textPrimary,
            style = ElTheme.typography.headlineSmall,
        )
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = c.textSecondary,
                style = ElTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            content = actions,
        )
    }
}

/** The tinted icon block at the top of a dialog. */
@Composable
private fun DialogIcon(icon: ImageVector) {
    val c = ElTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.primary.copy(alpha = 0.10f))
            .padding(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = c.primary,
            modifier = Modifier.size(28.dp),
        )
    }
}
