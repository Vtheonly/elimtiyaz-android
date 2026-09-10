package com.example.ui.designsystem.components.data

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.designsystem.theme.ElTheme

/**
 * ElHorizontalBarChart — horizontal ranked bars (PARITY-003).
 *
 * The native twin of the desktop's `mix-cards.tsx` CategoryMixCard
 * (Recharts `BarChart layout="vertical"`: horizontal bars, radius on the
 * RIGHT end, the value annotated at the row's end). Bars auto-scale to the
 * max value; rows are supplied PRE-SORTED (desc) by the caller — the
 * engine's order, never re-sorted here.
 */
@Composable
fun ElHorizontalBarChart(
    items: List<ElHorizontalBarItem>,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 30.dp,
    labelWidthFraction: Float = 0.34f,
    valueText: (ElHorizontalBarItem) -> String? = { it.trailingText },
) {
    val c = ElTheme.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(items) {
        progress.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }
    val maxVal = items.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f

    Column(modifier = modifier.fillMaxWidth()) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Text(
                    text = item.label,
                    color = c.textSecondary,
                    style = ElTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(labelWidthFraction),
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth((1f - labelWidthFraction) * 0.62f)
                        .height(rowHeight),
                ) {
                    val w = (item.value / maxVal) * size.width * progress.value
                    drawRoundRect(
                        color = if (item.color.isUnspecified) ElChartPalette.primary else item.color,
                        topLeft = Offset(0f, size.height * 0.2f),
                        size = Size(w, size.height * 0.6f),
                        cornerRadius = CornerRadius(size.height * 0.3f, size.height * 0.3f),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = valueText(item) ?: "",
                    color = c.textMuted,
                    style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
