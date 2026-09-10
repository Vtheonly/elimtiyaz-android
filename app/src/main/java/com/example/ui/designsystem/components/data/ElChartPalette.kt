package com.example.ui.designsystem.components.data

import androidx.compose.ui.graphics.Color

/**
 * ElChartPalette — the Kotlin mirror of the DESKTOP's canonical chart
 * palette (`src/shared/ui/dashboard-theme.ts` chartPalette, hex fallbacks).
 *
 * PARITY-003 (45th session, 2026-09-11): the owner's 13-chart parity
 * mandate requires the mobile charts to render with the same calm,
 * executive styling as the desktop — same color per series, per bucket,
 * per chart. Every analytics card consumes THIS palette (never ad-hoc
 * colors), so a desktop series and its mobile twin are visually
 * identical.
 *
 * The desktop resolves tokens with hex fallbacks
 * (token("--brand-blue", "#349bd4")) — the hex values below are those
 * fallbacks, the effective production values.
 */
object ElChartPalette {
    /** --brand-blue — the primary series (hero revenue bars, cash, funnel ≤60j). */
    val primary: Color = Color(0xFF349BD4)

    /** --brand-blue-deep — secondary-depth bars (non-dominant histogram bins, YoY current). */
    val primaryDeep: Color = Color(0xFF216D9B)

    /** --brand-cyan — the cumulative overlay line (revenue trend explorer). */
    val cyan: Color = Color(0xFF3DD6D0)

    /** --brand-violet — the filtered overlay line (slicers' effect on the trend). */
    val violet: Color = Color(0xFF8B5CF6)

    /** --brand-gold — the MA3 line, Pareto cumulative curve, funnel stage 1, check bars. */
    val gold: Color = Color(0xFFEAB308)

    /** --brand-slate — YoY previous-year bars, gender "Non spécifié", fallback mix slice. */
    val slate: Color = Color(0xFF3B464C)

    /** --status-success — aging 0–30j, funnel-adjacent positive accents, high-capacity gauges. */
    val success: Color = Color(0xFF10B981)

    /** --status-danger — aging 91–180j, Pareto bars, >90j funnel stage, saturated gauges. */
    val danger: Color = Color(0xFFEF4444)

    /** --status-info — aging 31–60j, 61–90j funnel stage, age histogram bars. */
    val info: Color = Color(0xFF0EA5E9)

    /** --status-warning — aging 61–90j (desktop AGING_COLORS uses the amber warning token). */
    val warning: Color = Color(0xFFF59E0B)

    /** Desktop AGING_COLORS "180_plus" — the brand-brown deep-aging tone. */
    val agingBrown: Color = Color(0xFF836C68)

    /**
     * The desktop AGING_COLORS map (aging-composition-card) — bucket → color.
     * 0_30 success · 31_60 info · 61_90 warning · 91_180 danger · 180_plus brown.
     */
    val agingColors: Map<String, Color> = mapOf(
        "0_30" to success,
        "31_60" to info,
        "61_90" to warning,
        "91_180" to danger,
        "180_plus" to agingBrown,
    )

    /** The desktop category-mix bar color cycle (mix-cards CATEGORY_COLORS order). */
    val categoryCycle: List<Color> = listOf(
        primary, cyan, violet, gold, success, info, danger, slate,
    )

    /** The desktop gender-donut cell cycle (see-details-modal). */
    val genderCycle: List<Color> = listOf(primary, gold, slate)
}
