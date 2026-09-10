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
import com.example.domain.model.CollectionHeatmapSnapshot
import com.example.domain.model.DebtAgingBucketItem
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.components.data.ElHeatmapCell
import com.example.ui.designsystem.components.data.ElHeatmapGrid
import com.example.ui.designsystem.components.data.ElHeatmapRow
import com.example.ui.designsystem.components.data.ElRatioSegment
import com.example.ui.designsystem.components.data.ElStackedRatioBar
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.theme.ElTheme

/**
 * CollectionHeatmapCard — inventory #7 (PARITY-003).
 *
 * The native twin of the desktop's `collection-heatmap-card.tsx` (T-256):
 * the weekday × calendar-month intensity matrix (school week Dim→Jeu, the
 * desktop's 5-step alpha quantization over brand blue), with the month
 * totals row. Values from the engine's deriveCollectionHeatmap.
 */
@Composable
internal fun CollectionHeatmapCard(
    heatmap: CollectionHeatmapSnapshot,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    if (heatmap.rows.isEmpty() || heatmap.monthLabels.isEmpty() || heatmap.max <= 0L) {
        AnalyticsEmptyCard(title = "Cadran des Encaissements", subtitle = "Aucun encaissement sur la période sélectionnée.")
        return
    }

    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ElSectionHeader(
                title = "Cadran des Encaissements",
                subtitle = "Volume par jour d'école × mois (Dim–Jeu)",
            )
            ElHeatmapGrid(
                rows = heatmap.rows.map { r ->
                    ElHeatmapRow(
                        rowLabel = r.day,
                        cells = r.cells.map { cell -> ElHeatmapCell(cell.level, (cell.amount / 100).toFloat()) },
                    )
                },
                columnLabels = heatmap.monthLabels,
                cellHeight = 24.dp,
            )
            // Row totals (the desktop's row-sum column)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                heatmap.rows.forEach { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = r.day,
                            color = c.textSecondary,
                            style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        )
                        Text(
                            text = "${compactDzd(r.rowTotal / 100)} DA",
                            color = c.textPrimary,
                            style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Pic de cellule : ${(heatmap.max / 100).formatDzd()} DA",
                    color = c.textMuted,
                    style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp),
                )
            }
        }
    }
}

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
