package com.example.ui.features.dashboard.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.formatDzd
import com.example.domain.model.CategoryRevenueItem
import com.example.domain.model.MethodMixItem
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.components.data.ElDonutChart
import com.example.ui.designsystem.components.data.ElDonutSegment
import com.example.ui.designsystem.components.data.ElHorizontalBarChart
import com.example.ui.designsystem.components.data.ElHorizontalBarItem
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.theme.ElTheme

/**
 * MethodMixCard — inventory #5 (PARITY-003).
 *
 * The native twin of the desktop's `mix-cards.tsx` MethodMixCard: the
 * payment-method DONUT (cash primary / check gold / transfer cyan,
 * fallback slate) with the total encaissé at the center. Values from the
 * engine's deriveMethodMix — count, amount, and the Math.round'd percent.
 */
@Composable
internal fun MethodMixCard(
    methodMix: List<MethodMixItem>,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    if (methodMix.isEmpty()) {
        AnalyticsEmptyCard(title = "Répartition par Mode de Paiement")
        return
    }
    val total = methodMix.sumOf { it.amount }
    fun colorFor(method: String) = when (method) {
        "cash" -> ElChartPalette.primary
        "check" -> ElChartPalette.gold
        "transfer" -> ElChartPalette.cyan
        else -> ElChartPalette.slate
    }

    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ElSectionHeader(title = "Mode de Paiement", subtitle = "Encaissé par méthode")
            Box(contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ElDonutChart(
                        segments = methodMix.map {
                            ElDonutSegment(
                                label = "${it.label} ${it.percent}%",
                                value = it.amount.toFloat(),
                                color = colorFor(it.method),
                            )
                        },
                        size = 140.dp,
                        centerValue = compactDzd(total / 100) + " DA",
                        centerLabel = "total encaissé",
                    )
                }
            }
            // Full per-method table (the desktop tooltip rows)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                methodMix.forEach { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = m.label,
                            color = c.textSecondary,
                            style = ElTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        )
                        Text(
                            text = "${(m.amount / 100).formatDzd()} DA · ${m.count} op. · ${m.percent}%",
                            color = c.textPrimary,
                            style = ElTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
        }
    }
}

/**
 * CategoryMixCard — inventory #6 (PARITY-003).
 *
 * The native twin of the desktop's `mix-cards.tsx` CategoryMixCard: the
 * RANKED horizontal bars (the categoryCycle colors) with the
 * Montant / Nb opérations metric toggle. Values from the engine's
 * deriveCategoryMix (desc by amount, "Autres (N)" tail merge).
 */
@Composable
internal fun CategoryMixCard(
    categoryBreakdown: List<CategoryRevenueItem>,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    if (categoryBreakdown.isEmpty()) {
        AnalyticsEmptyCard(title = "Répartition par Poste")
        return
    }
    var byAmount by remember { mutableStateOf(true) }

    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ElSectionHeader(
                title = "Postes d'Encaissement",
                subtitle = "Classement par montant",
                trailing = {
                    // The Montant / Nb op. metric toggle (desktop convention)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetricToggleLabel("Montant", byAmount) { byAmount = true }
                        Spacer(Modifier.width(6.dp))
                        MetricToggleLabel("Nb op.", !byAmount) { byAmount = false }
                    }
                },
            )
            val items = categoryBreakdown.mapIndexed { i, cat ->
                ElHorizontalBarItem(
                    label = cat.label,
                    value = (if (byAmount) cat.amount else cat.count.toLong()).toFloat(),
                    color = ElChartPalette.categoryCycle[i % ElChartPalette.categoryCycle.size],
                    trailingText = if (byAmount) {
                        "${compactDzd(cat.amount / 100)} DA · ${cat.percentage}%"
                    } else {
                        "${cat.count} op. · ${cat.percentage}%"
                    },
                )
            }
            ElHorizontalBarChart(items = items, rowHeight = 26.dp)
        }
    }
}

@Composable
private fun MetricToggleLabel(label: String, active: Boolean, onClick: () -> Unit) {
    val c = ElTheme.colors
    Text(
        text = label,
        color = if (active) ElChartPalette.primary else c.textMuted,
        style = ElTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        ),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
