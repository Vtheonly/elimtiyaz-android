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
 * ElGroupedBarChart — side-by-side grouped bars (PARITY-003).
 *
 * The native twin of the desktop's `yoy-comparison-card.tsx` Recharts
 * grouped `BarChart`: two bars per X slot (previous = slate, current =
 * primaryDeep — the desktop's N−1/N convention), radius on top corners.
 * Bars auto-scale to the global max across BOTH series.
 */
@Composable
fun ElGroupedBarChart(
    pairs: List<ElGroupedBarPair>,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    primaryColor: Color = ElChartPalette.primaryDeep,
    secondaryColor: Color = ElChartPalette.slate,
    primaryLabel: String = "N",
    secondaryLabel: String = "N−1",
) {
    val c = ElTheme.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pairs) {
        progress.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }
    val maxVal = pairs.maxOfOrNull { maxOf(it.primary, it.secondary) }?.takeIf { it > 0f } ?: 1f

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.height(height).padding(start = 4.dp, end = 4.dp, top = 4.dp)) {
            val chartHeight = size.height
            val n = pairs.size.coerceAtLeast(1)
            val slot = size.width / n
            val groupWidth = slot * 0.7f
            val barWidth = groupWidth / 2f

            for (i in 1..4) {
                val y = chartHeight * (1f - i / 4f)
                drawLine(
                    color = c.surfaceVariant.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }

            pairs.forEachIndexed { i, g ->
                val groupCenter = slot * i + slot / 2f
                val left = groupCenter - groupWidth / 2f
                if (g.secondary > 0f) {
                    val h = (g.secondary / maxVal) * chartHeight * 0.86f * progress.value
                    drawRoundRect(
                        color = secondaryColor.copy(alpha = 0.85f),
                        topLeft = Offset(left, chartHeight - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth * 0.16f, barWidth * 0.16f),
                    )
                }
                if (g.primary > 0f) {
                    val h = (g.primary / maxVal) * chartHeight * 0.86f * progress.value
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(left + barWidth, chartHeight - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth * 0.16f, barWidth * 0.16f),
                    )
                }
            }
        }

        Row(modifier = Modifier.padding(horizontal = 4.dp)) {
            pairs.forEachIndexed { i, g ->
                val show = pairs.size <= 6 || i == 0 || i == pairs.size / 2 || i == pairs.size - 1
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (show) g.label else "",
                        color = c.textMuted,
                        style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Canvas(modifier = Modifier.width(8.dp).height(8.dp)) { drawRect(color = secondaryColor) }
                Spacer(Modifier.width(3.dp))
                Text(secondaryLabel, color = c.textSecondary, style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp), maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Canvas(modifier = Modifier.width(8.dp).height(8.dp)) { drawRect(color = primaryColor) }
                Spacer(Modifier.width(3.dp))
                Text(primaryLabel, color = c.textSecondary, style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp), maxLines = 1)
            }
        }
    }
}

/**
 * ElGaugeArc — a semi-circle capacity gauge (PARITY-003).
 *
 * The native twin of the desktop `see-details-modal.tsx` SVG gauges:
 * 180° arc, fill percent clamped to 100, tone = danger ≥100 / gold ≥80 /
 * success otherwise; the percent centered in the arc, the caption below.
 * Arc math mirrors the desktop (viewBox 0 0 100 58, radius 40, stroke 8).
 */
@Composable
fun ElGaugeArc(
    percent: Int,
    modifier: Modifier = Modifier,
    caption: String? = null,
    dangerThreshold: Int = 100,
    warnThreshold: Int = 80,
) {
    val c = ElTheme.colors
    val fillPct = percent.coerceIn(0, 100).toFloat()
    val tone = when {
        percent >= dangerThreshold -> ElChartPalette.danger
        percent >= warnThreshold -> ElChartPalette.gold
        else -> ElChartPalette.success
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(percent) {
        progress.animateTo(fillPct / 100f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Canvas(modifier = Modifier.size(width = 100.dp, height = 58.dp)) {
            val stroke = 8f
            val radius = 40f
            val cx = 50f
            val cy = 50f

            // Track (M 10 50 A 40 40 0 0 1 90 50 — the desktop path)
            drawArc(
                color = c.surfaceVariant,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            // Fill
            drawArc(
                color = tone,
                startAngle = 180f,
                sweepAngle = 180f * progress.value,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
        }
        Text(
            text = "$percent%",
            color = c.textPrimary,
            style = ElTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        )
        if (caption != null) {
            Text(
                text = caption,
                color = c.textMuted,
                style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
