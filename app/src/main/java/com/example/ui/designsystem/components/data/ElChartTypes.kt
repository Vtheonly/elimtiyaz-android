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

// ============================================================================
// PARITY-003 (45th session) — the visual-parity chart data carriers.
// Mirrors of the desktop analytics derivations' output shapes.
// ============================================================================

/** One stacked column of an [ElStackedBarChart] (weekly operating rhythm). */
data class ElStackedBarGroup(
    val label: String,             // "Dim"… "Jeu"
    val segments: List<ElStackedBarSegment>,
) {
    val total: Float get() = segments.sumOf { it.value.toDouble() }.toFloat()
}

/** One method band inside a stacked column. */
data class ElStackedBarSegment(
    val value: Float,
    val color: Color,
    /** Legend entry label (the series name — e.g. "Espèces"). */
    val label: String = "",
)

/** One point of an [ElComposedRevenueChart] (revenue trend explorer). */
data class ElComposedRevenuePoint(
    val label: String,             // month label
    val amount: Float,             // the monthly bar
    val cumulative: Float,         // the right-scale overlay line
    val movingAvg3: Float? = null, // the gold MA line (null before the 3rd point)
    val filtered: Float? = null,   // the violet dashed slicer overlay (optional)
)

/** One row of an [ElHorizontalBarChart] (category mix ranking). */
data class ElHorizontalBarItem(
    val label: String,
    val value: Float,
    val color: Color = Color.Unspecified,
    /** Trailing annotation (e.g. "53 200 000 DZD" or "785"). */
    val trailingText: String? = null,
)

/** One cell of an [ElHeatmapGrid] (collection heatmap matrix). */
data class ElHeatmapCell(
    val level: Int,                // 0–4 intensity (0 = empty)
    val amount: Float = 0f,
)

/** One row of an [ElHeatmapGrid]. */
data class ElHeatmapRow(
    val rowLabel: String,          // "Dim"… "Jeu"
    val cells: List<ElHeatmapCell>,
)

/** One point of an [ElParetoChart] (top-debtors 80/20). */
data class ElParetoPoint(
    val label: String,             // debtor short name
    val amount: Float,             // bar height (left scale)
    val cumPercent: Float,         // cumulative-share curve (right scale 0–100)
)

/** One segment of an [ElStackedRatioBar] (100% stacked composition). */
data class ElRatioSegment(
    val label: String,
    val value: Float,              // magnitude; shares computed against the sum
    val color: Color,
)

/** One month group of an [ElGroupedBarChart] (YoY comparison). */
data class ElGroupedBarPair(
    val label: String,             // month label
    val primary: Float,            // the current-year bar
    val secondary: Float,          // the previous-year bar
)
