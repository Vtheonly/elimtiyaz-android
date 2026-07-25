package com.elimtiyaz.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.elimtiyaz.core.designsystem.ElImtiyazSpacing

/**
 * Standard surface card. Dark theme uses a 1dp outline instead of elevation
 * for a flatter, modern look (per design tokens).
 */
@Composable
fun ElImtiyazCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    val shape = RoundedCornerShape(16.dp)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    if (onClick == null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = container),
            elevation = elevation,
            border = border,
        ) {
            content()
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = container),
            elevation = elevation,
            border = border,
        ) {
            content()
        }
    }
}

@Composable
fun AvatarCircle(
    initial: String,
    modifier: Modifier = Modifier,
    size: Int = 40,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(size.dp)
            .padding(0.dp)
            .background(backgroundColor, RoundedCornerShape(999.dp)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = initial.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
        )
    }
}

@Composable
fun ListRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(ElImtiyazSpacing.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.size(ElImtiyazSpacing.x3))
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                androidx.compose.material3.Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}
