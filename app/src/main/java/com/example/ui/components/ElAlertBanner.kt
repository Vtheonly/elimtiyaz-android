package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElCardShapeSmall

@Composable
fun ElAlertBanner(
    message: String,
    severity: ElAlertSeverity,
    modifier: Modifier = Modifier,
    title: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val (bgColor, contentColor, icon) = when (severity) {
        ElAlertSeverity.Info -> Triple(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), MaterialTheme.colorScheme.primary, null)
        ElAlertSeverity.Success -> Triple(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), androidx.compose.ui.graphics.Color(0xFF2D9B6B), null)
        ElAlertSeverity.Warning -> Triple(androidx.compose.ui.graphics.Color(0xFFE0922F).copy(alpha = 0.12f), androidx.compose.ui.graphics.Color(0xFFE0922F), null)
        ElAlertSeverity.Danger -> Triple(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), MaterialTheme.colorScheme.error, null)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ElCardShapeSmall)
            .background(bgColor)
            .border(1.dp, contentColor.copy(alpha = 0.2f), ElCardShapeSmall)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = contentColor,
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
            if (onDismiss != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
