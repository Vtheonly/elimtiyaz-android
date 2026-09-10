package com.example.ui.features.dashboard.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.WeeklyRhythmItem
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.components.data.ElStackedBarChart
import com.example.ui.designsystem.components.data.ElStackedBarGroup
import com.example.ui.designsystem.components.data.ElStackedBarSegment
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.theme.ElTheme

/**
 * WeeklyOperatingRhythmCard — inventory #2 (PARITY-003).
 *
 * The native twin of the desktop's `weekly-operating-rhythm.tsx` (T-243):
 * the Algerian school-week stacked bars (Dimanche à Jeudi — NOT Mon–Sun),
 * volumes stacked by payment method (cash primary / check gold / transfer
 * cyan). COUNTER-ACTIVITY convention: every payment recorded at the
 * counter except "refunded" (pending/partial included — deliberately
 * different from the paid-only encaissé slice). Values from the engine's
 * deriveWeeklyRhythm (repository contract).
 */
@Composable
internal fun WeeklyOperatingRhythmCard(
    weeklyRhythm: List<WeeklyRhythmItem>,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    val total = weeklyRhythm.sumOf { it.total }
    if (weeklyRhythm.isEmpty() || total <= 0L) {
        AnalyticsEmptyCard(
            title = "Rythme d'Encaissement Hebdomadaire",
            subtitle = "Aucun encaissement sur la période sélectionnée.",
        )
        return
    }

    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ElSectionHeader(
                title = "Rythme d'Encaissement Hebdomadaire",
                subtitle = "Volume journalier au guichet (Dimanche à Jeudi)",
            )
            ElStackedBarChart(
                groups = weeklyRhythm.map { r ->
                    ElStackedBarGroup(
                        label = r.day,
                        segments = listOf(
                            ElStackedBarSegment((r.cash / 100).toFloat(), ElChartPalette.primary, "Espèces"),
                            ElStackedBarSegment((r.check / 100).toFloat(), ElChartPalette.gold, "Chèque"),
                            ElStackedBarSegment((r.transfer / 100).toFloat(), ElChartPalette.cyan, "Virement"),
                        ),
                    )
                },
                height = 160.dp,
            )
            // Per-day totals (the desktop tooltip rows)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                weeklyRhythm.forEach { r ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = r.day,
                            color = c.textSecondary,
                            style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        )
                        Text(
                            text = "${compactDzd(r.total / 100)} DA",
                            color = c.textPrimary,
                            style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
        }
    }
}
