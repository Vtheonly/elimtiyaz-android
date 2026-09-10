package com.example.ui.features.dashboard.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.formatDzd
import com.example.domain.model.RevenueTrendPointItem
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.components.data.ElComposedRevenuePoint
import com.example.ui.designsystem.components.data.ElComposedRevenueChart
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.theme.ElTheme

/**
 * RevenueTrendExplorerCard — inventory #1 (PARITY-003).
 *
 * The native twin of the desktop's `revenue-trend-explorer.tsx` (T-255):
 * 12-month bars + cumulative line + 3-month moving average + the optional
 * dashed FILTERED overlay (rendered only when slicers are active — the
 * desktop renders the same overlay from deriveFilteredMonthly). Values
 * all from the engine's deriveRevenueTrend / deriveFilteredMonthly.
 */
@Composable
internal fun RevenueTrendExplorerCard(
    trend: List<RevenueTrendPointItem>,
    filteredMonthly: List<Long>? = null,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    if (trend.isEmpty()) {
        AnalyticsEmptyCard(title = "Explorateur de Tendance des Revenus")
        return
    }
    val hasFilterOverlay = filteredMonthly != null &&
        filteredMonthly.any { it > 0L } &&
        filteredMonthly.size == trend.size

    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ElSectionHeader(
                title = "Tendance des Revenus",
                subtitle = "12 mois • cumul • moyenne mobile 3 mois",
            )
            val last = trend.lastOrNull()
            if (last != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TrendStat("Mensuel", "${(last.amount / 100).formatDzd()} DA", ElChartPalette.primary)
                    TrendStat("Cumul", compactDzd(last.cumulative / 100) + " DA", ElChartPalette.cyan)
                    last.movingAvg3?.let {
                        TrendStat("MM3", compactDzd(it / 100) + " DA", ElChartPalette.gold)
                    }
                }
            }
            ElComposedRevenueChart(
                points = trend.mapIndexed { i, p ->
                    ElComposedRevenuePoint(
                        label = p.label,
                        amount = (p.amount / 100).toFloat(),
                        cumulative = (p.cumulative / 100).toFloat(),
                        movingAvg3 = p.movingAvg3?.let { (it / 100).toFloat() },
                        filtered = if (hasFilterOverlay) (filteredMonthly!![i] / 100).toFloat() else null,
                    )
                },
                height = 190.dp,
            )
        }
    }
}

/** Small inline stat (label + DZD value). */
@Composable
private fun TrendStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    val c = ElTheme.colors
    Column {
        Text(
            text = label,
            color = c.textMuted,
            style = ElTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.4.sp),
        )
        Text(
            text = value,
            color = color,
            style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
        )
    }
}

/** The honest empty card (§15.16 — never fabricated trends). */
@Composable
internal fun AnalyticsEmptyCard(title: String, subtitle: String? = null) {
    val c = ElTheme.colors
    ElCard(modifier = Modifier.fillMaxWidth(), variant = com.example.ui.designsystem.components.card.ElCardVariant.OUTLINED) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                color = c.textPrimary,
                style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = subtitle ?: "Aucune donnée sur la période sélectionnée.",
                color = c.textMuted,
                style = ElTheme.typography.bodySmall,
            )
        }
    }
}
