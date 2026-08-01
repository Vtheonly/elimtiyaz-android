package com.example.ui.designsystem.components.data

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme
import kotlin.math.min

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
