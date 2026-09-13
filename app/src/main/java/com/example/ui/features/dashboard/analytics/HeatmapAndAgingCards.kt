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
import com.example.domain.model.DebtAgingBucketItem
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.components.data.ElRatioSegment
import com.example.ui.designsystem.components.data.ElStackedRatioBar
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.theme.ElTheme

// ============================================================================
// T-340 (STATS-400) — CollectionHeatmapCard REMOVED per the owner's kill
// list (the weekday×month collection heatmap: passive e-commerce vanity).
// The desktop's collection-heatmap-card.tsx was deleted the same way
// (T-339). The collection CADENCE survives as the weekly operating rhythm
// (WeeklyOperatingRhythmCard — real data, not on the kill list); the
// collection URGENCY lives in DebtTriageCard (ExecutiveCards.kt).
// ============================================================================

/**
 * AgingCompositionCard — inventory #9 (PARITY-003).
 *
 * The native twin of the desktop's `aging-composition-card.tsx` (T-257):
 * the 100% stacked composition bar (AGING_COLORS: 0–30j success / 31–60j
 * info / 61–90j warning / 91–180j danger / 180+j brown) + the per-bucket
 * stat table (amount, families, share). Values from the engine's
 * deriveAgingComposition — the ONE canonical INV-4 census.
 */
@Composable
internal fun AgingCompositionCard(
    debtByAging: List<DebtAgingBucketItem>,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    val present = debtByAging.filter { it.amount > 0L || it.debtorCount > 0 }
    if (present.isEmpty()) {
        AnalyticsEmptyCard(title = "Composition de l'Encours", subtitle = "Aucune créance ouverte.")
        return
    }

    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ElSectionHeader(
                title = "Composition de l'Encours",
                subtitle = "Répartition 100% par profondeur de retard",
            )
            ElStackedRatioBar(
                segments = present.map {
                    ElRatioSegment(
                        label = it.label,
                        value = it.amount.toFloat(),
                        color = ElChartPalette.agingColors[it.bucket] ?: ElChartPalette.slate,
                    )
                },
                height = 24.dp,
            )
            Spacer(Modifier.height(2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                present.forEach { b ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .width(8.dp)
                                    .height(8.dp),
                            ) {
                                drawRect(color = ElChartPalette.agingColors[b.bucket] ?: ElChartPalette.slate)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = b.label,
                                color = c.textSecondary,
                                style = ElTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            )
                        }
                        Text(
                            text = "${(b.amount / 100).formatDzd()} DA · ${b.debtorCount} fam. · ${b.sharePct}%",
                            color = c.textPrimary,
                            style = ElTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
        }
    }
}
