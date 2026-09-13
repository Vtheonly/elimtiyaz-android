package com.example.ui.features.dashboard.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.YoYSnapshot
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.components.data.ElGroupedBarChart
import com.example.ui.designsystem.components.data.ElGroupedBarPair
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.theme.ElTheme

/**
 * YoYComparisonCard — inventory #10 (PARITY-003).
 *
 * The native twin of the desktop's `yoy-comparison-card.tsx` (T-257):
 * grouped monthly bars (previous N−1 slate, current N primaryDeep) + the
 * global delta. Null deltas render "n/a" — a divide-by-zero is not a
 * −100% trend (§15.16). Values from the engine's deriveYearOverYear.
 */
@Composable
internal fun YoYComparisonCard(
    yoy: YoYSnapshot,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    if (yoy.points.isEmpty()) {
        AnalyticsEmptyCard(title = "Comparatif Annuel (N vs N−1)")
        return
    }
    val deltaText = yoy.deltaPercent?.let { d ->
        (if (d > 0) "+" else "") + "$d%"
    } ?: "n/a"
    val deltaColor = when {
        yoy.deltaPercent == null -> c.textMuted
        yoy.deltaPercent > 0 -> ElChartPalette.success
        yoy.deltaPercent < 0 -> ElChartPalette.danger
        else -> c.textSecondary
    }

    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ElSectionHeader(
                title = "Comparatif Annuel",
                subtitle = "Encaissé N vs N−1 (mêmes mois)",
                trailing = {
                    Text(
                        text = deltaText,
                        color = deltaColor,
                        style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                    )
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                YoYTotal("N", compactDzd(yoy.totalCurrent / 100) + " DA", ElChartPalette.primaryDeep)
                YoYTotal("N−1", compactDzd(yoy.totalPrevious / 100) + " DA", ElChartPalette.slate)
                YoYTotal("Écart", deltaText, deltaColor)
            }
            ElGroupedBarChart(
                pairs = yoy.points.map {
                    ElGroupedBarPair(
                        label = it.label,
                        primary = (it.current / 100).toFloat(),
                        secondary = (it.previous / 100).toFloat(),
                    )
                },
                height = 170.dp,
                primaryLabel = "N",
                secondaryLabel = "N−1",
            )
        }
    }
}

@Composable
private fun YoYTotal(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
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

// ============================================================================
// T-340 (STATS-400) — AmountHistogramCard REMOVED per the owner's kill list
// (the payment-amount histogram: passive e-commerce vanity). The desktop's
// amount-histogram-card.tsx was deleted the same way (T-339). Its slot in
// the Analytique tab is now held by the executive cards (ExecutiveCards.kt).
// ============================================================================
