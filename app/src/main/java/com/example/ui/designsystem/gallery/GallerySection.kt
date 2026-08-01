package com.example.ui.designsystem.gallery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/**
 * A titled section inside the gallery. Renders the section name + an optional
 * description, then the showcase content.
 */
@Composable
fun GallerySection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ElTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = c.textMuted,
            style = ElTheme.textStyles.overline,
        )
        Spacer(Modifier.height(4.dp))
        if (description != null) {
            Text(
                text = description,
                color = c.textSecondary,
                style = ElTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }
        content()
        Spacer(Modifier.height(8.dp))
    }
}
