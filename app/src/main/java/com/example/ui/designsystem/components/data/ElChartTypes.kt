package com.example.ui.designsystem.components.data

import androidx.compose.ui.graphics.Color

/**
 * El-Imtiyaz Design System — Chart Data Types.
 *
 * Tiny immutable data carriers used by the chart family
 * ([ElBarChart], [ElLineChart], [ElDonutChart]).
 *
 * Kept in a separate file so chart consumers can construct data sets
 * without pulling in the Canvas / animation machinery.
 */

/**
 * One bar in an [ElBarChart].
 *
 * @param label  X-axis label drawn under the bar.
 * @param value  Y-axis value (any range; the chart auto-scales).
 * @param color  Optional per-bar override. Pass [Color.Unspecified] to
 *               fall back to the chart's default color.
 */
data class ElBarChartItem(
    val label: String,
    val value: Float,
    val color: Color = Color.Unspecified,
)

/**
 * One point on an [ElLineChart] (and [ElSparkline]).
 *
 * @param label  X-axis label drawn under the point.
 * @param value  Y-axis value (any range; the chart auto-scales).
 */
data class ElLineChartPoint(
    val label: String,
    val value: Float,
)

/**
 * One segment of an [ElDonutChart].
 *
 * @param label  Legend entry label.
 * @param value  Segment magnitude. The chart computes the arc angle
 *               proportional to the sum of all segment magnitudes.
 * @param color  Segment fill color. Required (no sensible default).
 */
data class ElDonutSegment(
    val label: String,
    val value: Float,
    val color: Color,
)
