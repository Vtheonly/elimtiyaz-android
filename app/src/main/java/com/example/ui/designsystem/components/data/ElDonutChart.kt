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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.designsystem.theme.ElTheme
import kotlin.math.min

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
