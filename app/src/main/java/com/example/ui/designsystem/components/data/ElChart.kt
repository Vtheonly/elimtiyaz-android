package com.example.ui.designsystem.components.data

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.designsystem.theme.ElTheme
import kotlin.math.min

/**
 * El-Imtiyaz Design System — pure-Compose chart family.
 *
 * Five chart composables implemented with [Canvas] (no external chart
 * library). Each chart:
 *  - Auto-scales to the data's min/max.
 *  - Animates from 0 → target values on first composition using a
 *    [Animatable] driven by [LaunchedEffect].
 *  - Uses ElTheme tokens for gridlines (`outline`), axis text
 *    (`onSurfaceVariant`), and the primary line color (`primary`).
 *
 * All charts accept pre-built data classes from [ElChartTypes.kt]:
 * [ElBarChartItem], [ElLineChartPoint], [ElDonutSegment].
 */

// ── ElBarChart ───────────────────────────────────────────────────────────────

/**
 * Vertical bar chart — one bar per [ElBarChartItem]. Bars are auto-scaled
 * to the max value; the chart draws subtle horizontal gridlines and a
 * rotated (45°) X-axis label under each bar.
 *
 * @param data     Bar descriptors (label, value, optional color).
 * @param modifier Outer modifier.
 * @param height   Chart canvas height in dp.
 */
@Composable
fun ElBarChart(
    data: List<ElBarChartItem>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
) {
    val c = ElTheme.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        progress.animateTo(1f, tween(durationMillis = 600, easing = FastOutSlowInEasing))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(bottom = 18.dp),
        ) {
            val maxValue = data.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
            val barCount = data.size.coerceAtLeast(1)
            val slotWidth = size.width / barCount
            val barWidth = slotWidth * 0.6f
            val baseline = size.height
            val chartTop = 0f

            // Gridlines — 4 horizontal lines.
            val gridCount = 4
            val gridStep = (baseline - chartTop) / gridCount
            for (i in 0..gridCount) {
                val y = baseline - i * gridStep
                drawLine(
                    color = c.outline.copy(alpha = 0.3f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
                )
            }

            // Bars.
            data.forEachIndexed { index, item ->
                val slotCenter = slotWidth * (index + 0.5f)
                val barLeft = slotCenter - barWidth / 2f
                val barHeightFull = (item.value / maxValue) * (baseline - chartTop)
                val barHeight = barHeightFull * progress.value
                val barTop = baseline - barHeight
                val color = if (item.color != Color.Unspecified) item.color else c.primary
                drawRoundRect(
                    color = color,
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth * 0.18f),
                    style = Fill,
                )
            }
        }

        // X-axis labels (rotated -45° for compact layout).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            data.forEach { item ->
                Text(
                    text = item.label,
                    color = c.textMuted,
                    style = ElTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).rotate(-45f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── ElLineChart ──────────────────────────────────────────────────────────────

/**
 * Line chart with optional gradient area fill below the line.
 *
 * @param points       X/Y points; connected in order.
 * @param modifier     Outer modifier.
 * @param height       Chart canvas height in dp.
 * @param lineColor    Stroke color of the line.
 * @param gradientFill When true, fills the area below the line with a
 *                     vertical gradient from `lineColor` (top, 30 % alpha)
 *                     to transparent (bottom).
 */
@Composable
fun ElLineChart(
    points: List<ElLineChartPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    lineColor: Color = ElTheme.colors.primary,
    gradientFill: Boolean = true,
) {
    val c = ElTheme.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(bottom = 18.dp),
    ) {
        if (points.isEmpty()) return@Canvas

        val maxValue = points.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
        val minValue = points.minOfOrNull { it.value } ?: 0f
        val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f

        val baseline = size.height
        val chartTop = 0f

        // Gridlines.
        val gridCount = 4
        val gridStep = (baseline - chartTop) / gridCount
        for (i in 0..gridCount) {
            val y = baseline - i * gridStep
            drawLine(
                color = c.outline.copy(alpha = 0.3f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
            )
        }

        // Compute point positions.
        val pointCount = points.size
        val stepX = if (pointCount > 1) size.width / (pointCount - 1) else size.width
        val positions = points.mapIndexed { index, point ->
            val x = stepX * index
            val normalized = (point.value - minValue) / range
            val y = baseline - normalized * (baseline - chartTop)
            Offset(x, y)
        }

        // Animated reveal — only show the first N points based on progress.
        val revealCount = (pointCount * progress.value).toInt().coerceIn(1, pointCount)
        val revealed = positions.take(revealCount)
        if (revealed.isEmpty()) return@Canvas

        // Build path.
        val path = Path().apply {
            moveTo(revealed[0].x, revealed[0].y)
            for (i in 1 until revealed.size) {
                lineTo(revealed[i].x, revealed[i].y)
            }
        }

        // Gradient fill below the line.
        if (gradientFill && revealed.size > 1) {
            val fillPath = Path().apply {
                addPath(path)
                lineTo(revealed.last().x, baseline)
                lineTo(revealed.first().x, baseline)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.30f),
                        lineColor.copy(alpha = 0.00f),
                    ),
                    startY = chartTop,
                    endY = baseline,
                ),
                style = Fill,
            )
        }

        // Line stroke.
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3f),
        )

        // Points.
        revealed.forEach { pos ->
            drawCircle(
                color = lineColor,
                radius = 4f,
                center = pos,
            )
            drawCircle(
                color = c.surface,
                radius = 2f,
                center = pos,
            )
        }
    }
}

// ── ElDonutChart ─────────────────────────────────────────────────────────────

/**
 * Donut chart with center label and legend below.
 *
 * Each [ElDonutSegment] maps to an arc proportional to its value over the
 * sum of all values. The center of the donut shows [centerLabel] /
 * [centerValue] when supplied.
 *
 * @param segments     Donut segments.
 * @param modifier     Outer modifier.
 * @param size         Outer square size of the donut (legend is below).
 * @param centerLabel  Optional small caption inside the donut hole.
 * @param centerValue  Optional large value inside the donut hole.
 */
@Composable
fun ElDonutChart(
    segments: List<ElDonutSegment>,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    centerLabel: String? = null,
    centerValue: String? = null,
) {
    val c = ElTheme.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(segments) {
        progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }

    val total = segments.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val diameter = min(this.size.width, this.size.height)
                val stroke = diameter * 0.18f
                val arcSize = Size(diameter - stroke, diameter - stroke)
                val arcTopLeft = Offset(stroke / 2f, stroke / 2f)
                val sweepTotal = 360f * progress.value

                var startAngle = -90f // start at top
                segments.forEach { segment ->
                    val sweep = (segment.value / total) * 360f
                    val cappedSweep = (startAngle + sweep).coerceAtMost(-90f + sweepTotal) - startAngle
                    if (cappedSweep > 0f) {
                        drawArc(
                            color = segment.color,
                            startAngle = startAngle,
                            sweepAngle = cappedSweep,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt),
                        )
                    }
                    startAngle += sweep
                }
            }
            if (centerValue != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = centerValue,
                        color = c.textPrimary,
                        style = ElTheme.textStyles.numeric.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                        ),
                    )
                    if (centerLabel != null) {
                        Text(
                            text = centerLabel,
                            color = c.textMuted,
                            style = ElTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Column {
            segments.forEach { segment ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawRect(
                            color = segment.color,
                            topLeft = Offset(0f, 0f),
                            size = Size(10f, 10f),
                            style = Fill,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = segment.label,
                        color = c.textSecondary,
                        style = ElTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

// ── ElSparkline ──────────────────────────────────────────────────────────────

/**
 * Tiny inline trend line — no axis, no labels. Used in table cells and
 * stat cards to give a 5-second glance at trend direction.
 *
 * @param points  Y-axis values; X is implied as evenly spaced.
 * @param modifier Outer modifier.
 * @param color    Stroke color.
 * @param height   Chart canvas height in dp (default 40dp).
 */
@Composable
fun ElSparkline(
    points: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = ElTheme.colors.primary,
    height: Dp = 40.dp,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        progress.animateTo(1f, tween(durationMillis = 500))
    }

    Canvas(modifier = modifier.height(height)) {
        if (points.isEmpty()) return@Canvas
        val maxV = points.max().takeIf { it > 0f } ?: 1f
        val minV = points.min()
        val range = (maxV - minV).takeIf { it > 0f } ?: 1f

        val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
        val positions = points.mapIndexed { index, value ->
            val x = stepX * index
            val y = size.height - ((value - minV) / range) * size.height
            Offset(x, y)
        }

        val revealCount = (points.size * progress.value).toInt().coerceIn(1, points.size)
        val revealed = positions.take(revealCount)
        if (revealed.isEmpty()) return@Canvas

        val path = Path().apply {
            moveTo(revealed[0].x, revealed[0].y)
            for (i in 1 until revealed.size) lineTo(revealed[i].x, revealed[i].y)
        }
        drawPath(path = path, color = color, style = Stroke(width = 2f))
    }
}

// ── ElProgressRing ───────────────────────────────────────────────────────────

/**
 * Circular progress ring — a 360° track with the first [progress] fraction
 * filled in [color]. Optionally renders [label] in the center.
 *
 * @param progress    0f..1f fraction filled.
 * @param modifier    Outer modifier.
 * @param size        Outer square size.
 * @param strokeWidth Track stroke width.
 * @param color       Filled arc color.
 * @param label       Optional center text.
 */
@Composable
fun ElProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    strokeWidth: Dp = 8.dp,
    color: Color = ElTheme.colors.primary,
    label: String? = null,
) {
    val c = ElTheme.colors
    val animated = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        animated.animateTo(progress.coerceIn(0f, 1f), tween(durationMillis = 600, easing = FastOutSlowInEasing))
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val arcTopLeft = Offset(stroke / 2f, stroke / 2f)

            // Track.
            drawArc(
                color = c.surfaceVariant,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Filled arc.
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated.value,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        if (label != null) {
            Text(
                text = label,
                color = c.textPrimary,
                style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}
