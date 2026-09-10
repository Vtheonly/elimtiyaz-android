package com.example.ui.designsystem.components.data

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.designsystem.theme.ElTheme

/**
 * ElParetoChart — the top-debtors 80/20 chart (PARITY-003).
 *
 * The native twin of the desktop's `debtors-pareto-card.tsx` Recharts
 * `ComposedChart`: danger bars (left scale = amount) + the gold
 * cumulative-share curve (right scale, fixed 0–100 domain). The Pareto
 * 80% guide line renders dashed (desktop reference convention).
 */
@Composable
fun ElParetoChart(
    points: List<ElParetoPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    barColor: Color = ElChartPalette.danger,
    cumColor: Color = ElChartPalette.gold,
) {
    val c = ElTheme.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    val maxAmount = points.maxOfOrNull { it.amount }?.takeIf { it > 0f } ?: 1f

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.height(height).padding(start = 4.dp, end = 4.dp, top = 4.dp)) {
            val chartHeight = size.height
            val n = points.size.coerceAtLeast(1)
            val slot = size.width / n
            val barWidth = slot * 0.55f

            // Gridlines
            for (i in 1..4) {
                val y = chartHeight * (1f - i / 4f)
                drawLine(
                    color = c.surfaceVariant.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }

            // Bars (left scale)
            points.forEachIndexed { i, p ->
                if (p.amount > 0f) {
                    val barH = (p.amount / maxAmount) * chartHeight * 0.86f * progress.value
                    drawRoundRect(
                        color = barColor.copy(alpha = 0.9f),
                        topLeft = Offset(slot * i + slot / 2f - barWidth / 2f, chartHeight - barH),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(barWidth * 0.18f, barWidth * 0.18f),
                    )
                }
            }

            // Cumulative-percent curve (right scale 0–100)
            val cumPath = Path()
            var started = false
            points.forEachIndexed { i, p ->
                val x = slot * i + slot / 2f
                val y = chartHeight - (p.cumPercent / 100f) * (chartHeight * 0.86f) * progress.value
                if (!started) {
                    cumPath.moveTo(x, y)
                    started = true
                } else {
                    cumPath.lineTo(x, y)
                }
            }
            if (started) {
                drawPath(path = cumPath, color = cumColor, style = Stroke(width = 2f))
            }

            // 80% guide (dashed, gold @ 40%)
            val y80 = chartHeight - 0.8f * chartHeight * 0.86f
            drawLine(
                color = cumColor.copy(alpha = 0.4f),
                start = Offset(0f, y80),
                end = Offset(size.width, y80),
                strokeWidth = 1f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }

        // X labels (top-N debtor short names)
        Row(modifier = Modifier.padding(horizontal = 4.dp)) {
            points.forEach { p ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = p.label,
                        color = c.textMuted,
                        style = ElTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 1.dp),
                    )
                }
            }
        }

        // Legend
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Canvas(modifier = Modifier.width(8.dp).height(8.dp)) { drawRect(color = barColor) }
                Spacer(Modifier.width(3.dp))
                Text("Encours", color = c.textSecondary, style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp), maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Canvas(modifier = Modifier.width(10.dp).height(3.dp)) { drawRect(color = cumColor) }
                Spacer(Modifier.width(3.dp))
                Text("Cumul %", color = c.textSecondary, style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp), maxLines = 1)
            }
        }
    }
}

/**
 * ElStackedRatioBar — a single 100% stacked horizontal bar (PARITY-003).
 *
 * The native twin of the desktop's `aging-composition-card.tsx` CSS
 * `h-8` 100% stacked bar: segment widths = value / Σvalues. Segment
 * labels render UNDER the bar (the desktop draws % inside segments with
 * share ≥ 12; on the narrow mobile bar the under-bar legend is the
 * faithful equivalent — same data, same colors).
 */
@Composable
fun ElStackedRatioBar(
    segments: List<ElRatioSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 26.dp,
) {
    val c = ElTheme.colors
    val total = segments.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .background(c.surfaceVariant.copy(alpha = 0.4f)),
        ) {
            segments.forEach { seg ->
                val fraction = seg.value / total
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(seg.color),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            segments.forEach { seg ->
                val fraction = seg.value / total
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(fraction).padding(horizontal = 2.dp),
                ) {
                    Canvas(modifier = Modifier.width(6.dp).height(6.dp)) { drawRect(color = seg.color) }
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "${(fraction * 100f).toInt()}%",
                        color = c.textMuted,
                        style = ElTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
