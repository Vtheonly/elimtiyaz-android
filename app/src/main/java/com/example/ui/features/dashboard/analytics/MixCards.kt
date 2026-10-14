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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Payment
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
import com.example.core.formatDzd
import com.example.domain.model.CategoryRevenueItem
import com.example.domain.model.MethodMixItem
import com.example.ui.designsystem.components.card.ElExpandableCard
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.components.data.ElDonutChart
import com.example.ui.designsystem.components.data.ElDonutSegment
import com.example.ui.designsystem.components.data.ElHorizontalBarChart
import com.example.ui.designsystem.components.data.ElHorizontalBarItem
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.overlays.ElInfoTip
import com.example.ui.designsystem.theme.ElTheme

/**
 * MethodMixCard — Payment Method Breakdown.
 *
 * Shows headline volume/method in collapsed state, expanding to the donut chart
 * and transaction table.
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
    val dominantMethod = methodMix.maxByOrNull { it.amount }

    fun colorFor(method: String) = when (method) {
        "cash" -> ElChartPalette.primary
        "check" -> ElChartPalette.gold
        "transfer" -> ElChartPalette.cyan
        else -> ElChartPalette.slate
    }

    ElExpandableCard(
        title = "Modes de Paiement",
        subtitle = "Encaissé par méthode",
        primaryFigure = compactDzd(total / 100) + " DA",
        primaryFigureColor = c.primary,
        icon = Icons.Default.Payment,
        headerBadge = {
            dominantMethod?.let {
                ElTag(text = "${it.label} ${it.percent}%", tone = ElTagTone.INFO)
            }
        },
        headerTrailing = { ElInfoTip(tip = "methodMix.card") },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
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

            Column(verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.xs)) {
                methodMix.forEach { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = m.label,
                            color = c.textSecondary,
                            style = ElTheme.typography.labelSmall,
                        )
                        Text(
                            text = "${(m.amount / 100).formatDzd()} DA · ${m.count} op. · ${m.percent}%",
                            color = c.textPrimary,
                            style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
        }
    }
}

/**
 * CategoryMixCard — Revenue by billing category.
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
    val topCategory = categoryBreakdown.firstOrNull()

    ElExpandableCard(
        title = "Postes d'Encaissement",
        subtitle = "Classement par catégorie de frais",
        primaryFigure = "${categoryBreakdown.size} postes",
        primaryFigureColor = c.primary,
        icon = Icons.Default.Category,
        headerBadge = {
            topCategory?.let {
                ElTag(text = "Top: ${it.label}", tone = ElTagTone.INFO)
            }
        },
        headerTrailing = { ElInfoTip(tip = "categoryMix.card") },
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Classement des flux",
                    style = ElTheme.typography.labelSmall,
                    color = c.textSecondary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricToggleLabel("Montant", byAmount) { byAmount = true }
                    Spacer(Modifier.width(ElTheme.spacing.sm))
                    MetricToggleLabel("Nb op.", !byAmount) { byAmount = false }
                }
            }

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
            ElHorizontalBarChart(items = items, rowHeight = 28.dp)
        }
    }
}

@Composable
private fun MetricToggleLabel(label: String, active: Boolean, onClick: () -> Unit) {
    val c = ElTheme.colors
    Text(
        text = label,
        color = if (active) ElChartPalette.primary else c.textMuted,
        style = ElTheme.textStyles.badge.copy(
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        ),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = ElTheme.spacing.xs, vertical = 2.dp),
    )
}