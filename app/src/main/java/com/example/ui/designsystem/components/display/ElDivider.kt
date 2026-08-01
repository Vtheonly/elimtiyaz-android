package com.example.ui.designsystem.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/**
 * Unified horizontal divider — thin line in the theme's outline-variant color.
 */
@Composable
fun ElDivider(
    modifier: Modifier = Modifier,
    verticalPadding: Int = 0,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding.dp)
            .height(1.dp)
            .background(ElTheme.colors.outlineVariant),
    )
}

/** Section divider with optional label. */
@Composable
fun ElSectionDivider(
    label: String,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label.uppercase(),
            color = c.textMuted,
            style = ElTheme.textStyles.overline,
            modifier = Modifier.padding(end = 12.dp),
        )
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(c.outlineVariant),
        )
    }
}
