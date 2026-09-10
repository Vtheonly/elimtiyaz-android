package com.example.ui.designsystem.components.data

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.designsystem.theme.ElTheme

/**
 * ElStackedBarChart — the stacked-column chart (PARITY-003).
 *
 * The native Jetpack Compose twin of the desktop's
 * `weekly-operating-rhythm.tsx` Recharts `BarChart` (stackId bars, 3
 * method series, corner radius on the TOP segment only) and the YoY
 * grouped-card when used with side-by-side grouping is not needed.
 *
 * Pure Canvas + Animatable reveal (house style — see ElBarChart).
 *
 * @param groups one column per X label; segments stack bottom→top.
 * @param height chart body height.
 */
@Composable
fun ElStackedBarChart(
    groups: List<ElStackedBarGroup>,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
) {
    val c = ElTheme.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(groups) {
        progress.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }

    val maxTotal = groups.maxOfOrNull { it.total }?.takeIf { it > 0f } ?: 1f
    val seriesLabels = groups.firstOrNull()?.segments?.mapNotNull { s ->
        s.label.takeIf { it.isNotBlank() }
    } ?: emptyList()

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        Canvas(modifier = Modifier.height(height).padding(start = 4.dp, end = 4.dp, top = 4.dp)) {
            val chartHeight = size.height
            val n = groups.size.coerceAtLeast(1)
            val slot = size.width / n
            val barWidth = slot * 0.6f
            val xLabelsReserve = 0f // labels render outside the Canvas (Row below)

            // 4 horizontal gridlines (house style)
            val gridColor = c.surfaceVariant
            for (i in 1..4) {
                val y = chartHeight * (1f - i / 4f) - xLabelsReserve
                drawLine(
                    color = gridColor.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }

            groups.forEachIndexed { i, g ->
                val slotCenter = slot * i + slot / 2f
                val left = slotCenter - barWidth / 2f
                var accHeight = 0f
                g.segments.forEachIndexed { si, seg ->
                    if (seg.value > 0f) {
                        val segH = (seg.value / maxTotal) * chartHeight * progress.value
                        val top = chartHeight - accHeight - segH
                        val isTopSegment = g.segments.drop(si + 1).none { it.value > 0f }
                        drawRoundRect(
                            color = seg.color,
                            topLeft = Offset(left, top),
                            size = Size(barWidth, segH),
                            cornerRadius = if (isTopSegment) CornerRadius(barWidth * 0.18f, barWidth * 0.18f) else CornerRadius.Zero,
                        )
                        accHeight += segH
                    }
                }
            }
        }

        // X labels row
        Row(modifier = Modifier.padding(horizontal = 4.dp)) {
            groups.forEach { g ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = g.label,
                        color = c.textMuted,
                        style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Legend (the series names — Espèces / Chèque / Virement)
        if (seriesLabels.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val seriesColors = groups.firstOrNull()?.segments?.map { it.color } ?: emptyList<Color>()
                seriesLabels.forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Canvas(modifier = Modifier.width(8.dp).height(8.dp)) {
                            drawRect(color = seriesColors.getOrElse(i) { c.primary })
                        }
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = label,
                            color = c.textSecondary,
                            style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
