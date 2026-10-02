package com.example.ui.features.dashboard.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.formatDzd
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.overlays.ElInfoTip
import com.example.ui.designsystem.theme.ElTheme

/**
 * AnalyticsStatStrip — the 6-card statistics row (PARITY-003).
 *
 * The native twin of the desktop's `stat-strip.tsx` (T-255, the Power BI
 * "card" row): Opérations · Total encaissé · Moyenne · Médiane · Écart-type
 * σ · Meilleur mois. Every value comes from the ENGINE's derivePaymentStats
 * over the (filtered) paid slice — the repository contract, never a
 * re-derivation, never a fallback number (§15.16).
 */
@Composable
internal fun AnalyticsStatStrip(
    count: Int,
    totalCentimes: Long,
    meanCentimes: Long,
    medianCentimes: Long,
    stdDevCentimes: Long,
    bestMonthLabel: String?,
    bestMonthAmount: Long,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(
            title = "OPÉRATIONS",
            value = "$count",
            subtext = "encaissements",
            color = ElChartPalette.primary,
            modifier = Modifier.weight(1f),
            tip = "statStrip.count",
        )
        StatCard(
            title = "TOTAL ENCAISSÉ",
            value = "${(totalCentimes / 100).formatDzd()} DA",
            subtext = null,
            color = ElChartPalette.success,
            modifier = Modifier.weight(1f),
            tip = "statStrip.total",
        )
        StatCard(
            title = "MOYENNE",
            value = compactDzd(meanCentimes / 100),
            subtext = "DZD / op.",
            color = ElChartPalette.cyan,
            modifier = Modifier.weight(1f),
            tip = "statStrip.mean",
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(
            title = "MÉDIANE",
            value = compactDzd(medianCentimes / 100),
            subtext = "DZD",
            color = ElChartPalette.violet,
            modifier = Modifier.weight(1f),
            tip = "statStrip.median",
        )
        StatCard(
            title = "ÉCART-TYPE (Σ)",
            value = compactDzd(stdDevCentimes / 100),
            subtext = "DZD",
            color = ElChartPalette.gold,
            modifier = Modifier.weight(1f),
            tip = "statStrip.stdDev",
        )
        StatCard(
            title = "MEILLEUR MOIS",
            value = bestMonthLabel ?: "—",
            subtext = if (bestMonthLabel != null) compactDzd(bestMonthAmount / 100) else null,
            color = ElChartPalette.info,
            modifier = Modifier.weight(1f),
            tip = "statStrip.bestMonth",
        )
    }
}

/** One stat card (the desktop StatStrip card — tone-colored value). */
@Composable
private fun StatCard(
    title: String,
    value: String,
    subtext: String?,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    /** T-458: the dotted StatsTips glossary key (null = no tip). */
    tip: String? = null,
) {
    val c = ElTheme.colors
    ElCard(
        modifier = modifier,
        variant = com.example.ui.designsystem.components.card.ElCardVariant.OUTLINED,
        elevation = null,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = c.textMuted,
                    style = ElTheme.textStyles.chartMicro.copy(letterSpacing = 0.5.sp),
                    maxLines = 1,
                )
                // T-458: the T-447 explainability glossary (presentation-only).
                if (tip != null) ElInfoTip(tip = tip, size = 10)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                color = color,
                style = ElTheme.textStyles.numericSmall,
                maxLines = 1,
            )
            if (subtext != null) {
                Text(
                    text = subtext,
                    color = c.textSecondary,
                    style = ElTheme.textStyles.chartMicro,
                    maxLines = 1,
                )
            }
        }
    }
}
