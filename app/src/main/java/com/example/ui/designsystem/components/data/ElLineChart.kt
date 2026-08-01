package com.example.ui.designsystem.components.data

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

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
