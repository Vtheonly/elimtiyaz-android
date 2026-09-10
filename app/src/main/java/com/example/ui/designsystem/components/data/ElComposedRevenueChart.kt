package com.example.ui.designsystem.components.data

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.designsystem.theme.ElTheme

/**
 * ElComposedRevenueChart — the revenue trend explorer (PARITY-003).
 *
 * The native Jetpack Compose twin of the desktop's
 * `revenue-trend-explorer.tsx` Recharts `ComposedChart`: monthly BARS
 * (primary blue) + the CUMULATIVE line (cyan, right scale) + the 3-month
 * MOVING AVERAGE (gold; absent before the 3rd point — never fabricated) +
 * the optional FILTERED overlay (violet, dashed — the slicers' visible
 * effect on the trend).
 *
 * Dual scale: the bars use the monthly-max scale; the cumulative line
 * uses its own max scale (mirroring the desktop's two Y axes).
 *
 * Pure Canvas + Animatable reveal (house style).
 */
@Composable
fun ElComposedRevenueChart(
    points: List<ElComposedRevenuePoint>,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    barColor: Color = ElChartPalette.primary,
    cumulativeColor: Color = ElChartPalette.cyan,
    maColor: Color = ElChartPalette.gold,
    filteredColor: Color = ElChartPalette.violet,
) {
    val c = ElTheme.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }

    val maxAmount = points.maxOfOrNull { it.amount }?.takeIf { it > 0f } ?: 1f
    val maxCumulative = points.maxOfOrNull { it.cumulative }?.takeIf { it > 0f } ?: 1f
    val hasMa = points.any { it.movingAvg3 != null }
    val hasFiltered = points.any { it.filtered != null }

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.height(height).padding(start = 4.dp, end = 4.dp, top = 4.dp)) {
            val chartHeight = size.height
            val n = points.size.coerceAtLeast(1)
            val slot = size.width / n
            val barWidth = slot * 0.55f

            // 4 horizontal gridlines (house style)
            for (i in 1..4) {
                val y = chartHeight * (1f - i / 4f)
                drawLine(
                    color = c.surfaceVariant.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }

            fun yForAmount(v: Float): Float = chartHeight - (v / maxAmount) * (chartHeight * 0.86f)
            fun yForCumulative(v: Float): Float = chartHeight - (v / maxCumulative) * (chartHeight * 0.86f)

            // Monthly bars (left scale)
            points.forEachIndexed { i, p ->
                if (p.amount > 0f) {
                    val barH = (p.amount / maxAmount) * chartHeight * 0.86f * progress.value
                    drawRoundRect(
                        color = barColor.copy(alpha = 0.85f),
                        topLeft = Offset(slot * i + slot / 2f - barWidth / 2f, chartHeight - barH),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(barWidth * 0.18f, barWidth * 0.18f),
                    )
                }
            }

            fun lineOf(selector: (ElComposedRevenuePoint) -> Float?, scale: (Float) -> Float): List<Path> {
                val paths = mutableListOf<Path>()
                var current: Path? = null
                points.forEachIndexed { i, p ->
                    val v = selector(p)
                    val x = slot * i + slot / 2f
                    val y = v?.let { scale(it) }
                    if (v == null || y == null) {
                        current = null // connectNulls=false — the line BREAKS at nulls
                    } else {
                        val path = current ?: Path().apply { moveTo(x, y) }.also { current = it; paths.add(it) }
                        if (paths.isNotEmpty() && paths.last() === path) {
                            path.lineTo(x, y)
                        }
                    }
                }
                return paths
            }

            // Cumulative line (cyan, right scale)
            lineOf({ it.cumulative }, ::yForCumulative).forEach { path ->
                drawPath(path = path, color = cumulativeColor, style = Stroke(width = 2.5f))
            }

            // 3-month moving average (gold; connectNulls=false — breaks before
            // the 3rd point and wherever the desktop value is null)
            if (hasMa) {
                lineOf({ it.movingAvg3 }, ::yForAmount).forEach { path ->
                    drawPath(path = path, color = maColor, style = Stroke(width = 2f))
                }
            }

            // Filtered overlay (violet, dashed)
            if (hasFiltered) {
                lineOf({ it.filtered }, ::yForAmount).forEach { path ->
                    drawPath(
                        path = path,
                        color = filteredColor,
                        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    )
                }
            }
        }

        // X labels row (first / middle / last only — 12 labels would overlap)
        Row(modifier = Modifier.padding(horizontal = 4.dp)) {
            points.forEachIndexed { i, p ->
                val show = points.size <= 6 || i == 0 || i == points.size / 2 || i == points.size - 1
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (show) p.label else "",
                        color = c.textMuted,
                        style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Legend
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOfNotNull(
                "Mensuel" to barColor,
                "Cumulé" to cumulativeColor,
                if (hasMa) "MM3" to maColor else null,
                if (hasFiltered) "Filtré" to filteredColor else null,
            ).forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Canvas(modifier = Modifier.width(10.dp).height(3.dp)) {
                        drawRect(color = color)
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
