package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.model.DashboardKpi
import com.example.domain.model.DebtSummary
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElDonutChart
import com.example.ui.designsystem.components.data.ElDonutSegment
import com.example.ui.designsystem.components.data.ElProgressRing
import com.example.ui.designsystem.components.display.ElInfoRow
import com.example.ui.designsystem.foundation.elMoneyFormat
import com.example.ui.designsystem.theme.ElTheme

/** Section (d) — collection-rate ring (left) + debt-aging donut (right). */
@Composable
internal fun DashboardCollectionAndDebtRow(
    currentKpi: DashboardKpi,
    debtAging: List<DebtSummary>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElCard(modifier = Modifier.weight(1f)) {
            val collected = currentKpi.monthlyRevenue.toFloat()
            val pending = currentKpi.outstandingDebt.toFloat()
            val total = collected + pending
            val rate = if (total > 0f) (collected / total).coerceIn(0f, 1f) else 0f

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ElProgressRing(
                    progress = rate,
                    size = 120.dp,
                    color = ElTheme.colors.success,
                    label = "Taux de\ncollecte",
                )
                Spacer(Modifier.height(12.dp))
                ElInfoRow(
                    label = "Collecté",
                    value = elMoneyFormat(currentKpi.monthlyRevenue),
                    valueTint = ElTheme.colors.success,
                )
                ElInfoRow(
                    label = "En attente",
                    value = elMoneyFormat(currentKpi.outstandingDebt),
                    valueTint = ElTheme.colors.danger,
                )
            }
        }

        ElCard(modifier = Modifier.weight(1f)) {
            val agingBuckets = listOf("0_30", "31_60", "61_90", "91_180", "180_plus")
            val segments = agingBuckets.mapNotNull { bucket ->
                val amount = debtAging.filter { it.bucket == bucket }.sumOf { it.outstandingAmount }
                if (amount > 0L) {
                    ElDonutSegment(
                        label = bucketLabel(bucket),
                        value = amount.toFloat(),
                        color = bucketColor(bucket),
                    )
                } else null
            }
            val safeSegments = segments.ifEmpty {
                listOf( // Fallback: no per-bucket data → single "Encours" segment.
                    ElDonutSegment(
                        label = "Encours",
                        value = currentKpi.outstandingDebt.toFloat(),
                        color = ElTheme.colors.danger,
                    ),
                )
            }
            val totalDebt = debtAging.sumOf { it.outstandingAmount }.takeIf { it > 0L }
                ?: currentKpi.outstandingDebt

            ElDonutChart(
                segments = safeSegments,
                size = 140.dp,
                centerLabel = "Créances",
                centerValue = elMoneyFormat(totalDebt),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
