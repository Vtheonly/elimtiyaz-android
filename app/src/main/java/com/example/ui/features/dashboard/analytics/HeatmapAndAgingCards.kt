package com.example.ui.features.dashboard.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.formatDzd
import com.example.domain.model.DebtAgingBucketItem
import com.example.ui.designsystem.components.card.ElExpandableCard
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.components.data.ElRatioSegment
import com.example.ui.designsystem.components.data.ElStackedRatioBar
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.overlays.ElInfoTip
import com.example.ui.designsystem.theme.ElTheme

/**
 * AgingCompositionCard — Aging breakdown of outstanding debt.
 *
 * Minimalist by default: shows total debt and debtor count.
 * Expands to reveal the 100% stacked ratio bar and individual bucket metrics.
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

    val totalAmount = present.sumOf { it.amount }
    val totalDebtors = present.sumOf { it.debtorCount }
    val dominantBucket = present.maxByOrNull { it.amount }

    ElExpandableCard(
        title = "Composition de l'Encours",
        subtitle = "Répartition par profondeur de retard",
        primaryFigure = "${(totalAmount / 100).formatDzd()} DA",
        primaryFigureColor = if (totalAmount > 0L) c.danger else c.success,
        icon = Icons.Default.Schedule,
        headerBadge = {
            ElTag(
                text = "$totalDebtors fam.",
                tone = if (totalDebtors > 0) ElTagTone.DANGER else ElTagTone.SUCCESS,
            )
        },
        headerTrailing = { ElInfoTip(tip = "aging.card") },
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md)) {
            Text(
                text = "Ventilation 100% de la dette par tranche d'échéance",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
            )

            ElStackedRatioBar(
                segments = present.map {
                    ElRatioSegment(
                        label = it.label,
                        value = it.amount.toFloat(),
                        color = ElChartPalette.agingColors[it.bucket] ?: ElChartPalette.slate,
                    )
                },
                height = 26.dp,
            )

            Spacer(Modifier.height(2.dp))

            Column(verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.xs)) {
                present.forEach { b ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .width(ElTheme.spacing.sm)
                                    .height(ElTheme.spacing.sm),
                            ) {
                                drawRect(color = ElChartPalette.agingColors[b.bucket] ?: ElChartPalette.slate)
                            }
                            Spacer(Modifier.width(ElTheme.spacing.sm))
                            Text(
                                text = b.label,
                                color = c.textSecondary,
                                style = ElTheme.typography.labelSmall,
                            )
                        }
                        Text(
                            text = "${(b.amount / 100).formatDzd()} DA · ${b.debtorCount} fam. · ${b.sharePct}%",
                            color = c.textPrimary,
                            style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
        }
    }
}