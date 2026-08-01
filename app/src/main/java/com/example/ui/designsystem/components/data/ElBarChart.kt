package com.example.ui.designsystem.components.data

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

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
