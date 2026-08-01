package com.example.ui.designsystem.components.data

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

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
