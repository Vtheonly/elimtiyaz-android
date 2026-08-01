package com.example.ui.designsystem.components.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/**
 * Compact stat card — for KPIs, summary tiles.
 * Layout: [icon] [label] [value] [optional trend]
 */
@Composable
fun ElStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trend: String? = null,
    trendPositive: Boolean = true,
    accentColor: Color = ElTheme.colors.primary,
    onClick: (() -> Unit)? = null,
) {
    val colors = ElTheme.colors
    ElCard(
        modifier = modifier,
        variant = ElCardVariant.ELEVATED,
        size = ElCardSize.STANDARD,
        onClick = onClick,
    ) {
        StatIcon(icon = icon, accentColor = accentColor)
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            color = colors.textSecondary,
            style = ElTheme.typography.labelMedium,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = value,
            color = colors.textPrimary,
            style = ElTheme.textStyles.numeric,
        )
        if (trend != null) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = trend,
                color = if (trendPositive) colors.success else colors.danger,
                style = ElTheme.typography.labelSmall,
            )
        }
    }
}

/** Renders the tinted icon block at the top of a stat card. */
@Composable
private fun StatIcon(icon: ImageVector?, accentColor: Color) {
    if (icon == null) return
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .padding(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(20.dp),
        )
    }
}
