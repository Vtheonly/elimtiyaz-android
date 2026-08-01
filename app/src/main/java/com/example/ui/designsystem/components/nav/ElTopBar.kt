package com.example.ui.designsystem.components.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.theme.ElTheme

/**
 * Top app bar — title + optional subtitle, back button, and actions slot.
 * Transparent background for edge-to-edge.
 */
@Composable
fun ElTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
) {
    val c = ElTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            ElIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                contentDescription = "Back",
                background = Color.Transparent,
                tint = c.textPrimary,
            )
            Spacer(Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = c.textPrimary,
                style = ElTheme.typography.titleLarge,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = c.textSecondary,
                    style = ElTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
        if (actions != null) {
            actions()
        }
    }
}
