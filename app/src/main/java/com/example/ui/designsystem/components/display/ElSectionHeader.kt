package com.example.ui.designsystem.components.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/**
 * Section header — title, optional subtitle, optional leading icon, and an
 * optional trailing slot (typically a "See all" link or filter button).
 *
 * Use at the top of grouped content blocks to maintain a consistent
 * vertical rhythm: the header sits in 16dp of bottom padding before the
 * content begins.
 *
 * @param title     Section title — uses `headlineSmall` weight.
 * @param subtitle  Optional supporting line under the title.
 * @param modifier  Outer modifier.
 * @param trailing  Optional trailing composable, right-aligned.
 * @param icon      Optional leading icon composable, left-aligned.
 * @param divider   When true, draws a thin divider under the header.
 */
@Composable
fun ElSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    divider: Boolean = false,
) {
    val c = ElTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                if (icon != null) {
                    icon()
                    Spacer(Modifier.width(10.dp))
                }
                Column {
                    Text(
                        text = title,
                        color = c.textPrimary,
                        style = ElTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = c.textMuted,
                            style = ElTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }

        if (divider) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.HorizontalDivider(
                thickness = ElTheme.borders.thin,
                color = c.outlineVariant,
            )
        }
    }
}
