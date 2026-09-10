package com.example.ui.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.formatDzd
import com.example.domain.model.DashboardKpi
import com.example.domain.model.PaymentMethodSummary
import com.example.domain.repository.RevenuePoint
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElBarChart
import com.example.ui.designsystem.components.data.ElBarChartItem
import com.example.ui.designsystem.components.display.ElProgressBar
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.theme.ElTheme

@Composable
internal fun DashboardRevenueChart(
    currentKpi: DashboardKpi,
    revenue: List<RevenuePoint>,
    paymentMethods: List<PaymentMethodSummary>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── 1. Descriptive Financial Stats Row (Images 2 & 3) ──
        ElSectionHeader(
            title = "Analytique des Encaissements",
            subtitle = "${currentKpi.totalOperationsCount} opérations • ${(currentKpi.totalRevenue / 100).formatDzd()} DA encaissés",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnalyticsMiniTile(
                title = "PANIER MOYEN",
                value = "${(currentKpi.averageBasketAmount / 100_000).toInt()} k DZD",
                subtext = "Moyenne/op.",
                modifier = Modifier.weight(1f),
            )
            AnalyticsMiniTile(
                title = "MÉDIANE",
                value = "${(currentKpi.medianBasketAmount / 100_000).toInt()} k DZD",
                subtext = "Valeur centrale",
                modifier = Modifier.weight(1f),
            )
            AnalyticsMiniTile(
                title = "MEILLEUR MOIS",
                value = currentKpi.bestMonthName,
                subtext = "Pic annuel",
                modifier = Modifier.weight(1f),
            )
            AnalyticsMiniTile(
                title = "VOLATILITÉ (Σ)",
                value = "${(currentKpi.volatilityAmount / 100_000).toInt()} k DZD",
                subtext = "Écart-type",
                modifier = Modifier.weight(1f),
            )
        }

        // ── 2. Monthly Trend Chart ──
        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Tendance mensuelle des encaissements",
                    style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ElTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))

                if (revenue.isNotEmpty()) {
                    ElBarChart(
                        data = revenue.map {
                            ElBarChartItem(
                                label = it.label,
                                value = (it.amount / 100).toFloat(),
                                color = ElTheme.colors.primary,
                            )
                        },
                        height = 150.dp,
                    )
                } else {
                    Text(
                        text = "Aucun encaissement sur la période.",
                        style = ElTheme.typography.bodySmall,
                        color = ElTheme.colors.textSecondary,
                    )
                }

                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.HorizontalDivider(
                    color = ElTheme.colors.outlineVariant,
                    thickness = 1.dp,
                )
                Spacer(Modifier.height(12.dp))

                // ── 3. Distribution des montants (Amount Bins Histogram) ──
                Text(
                    text = "Distribution des montants",
                    style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ElTheme.colors.textPrimary,
                )
                Text(
                    text = "Tranche dominante : 50k+ (${currentKpi.amountBins.lastOrNull()?.percentage?.toInt() ?: 54}%)",
                    style = ElTheme.typography.labelSmall,
                    color = ElTheme.colors.primary,
                )
                Spacer(Modifier.height(8.dp))

                if (currentKpi.amountBins.isNotEmpty()) {
                    ElBarChart(
                        data = currentKpi.amountBins.map {
                            ElBarChartItem(
                                label = it.label,
                                value = it.count.toFloat(),
                                color = if (it.label == "50k+") ElTheme.colors.primary else ElTheme.colors.info,
                            )
                        },
                        height = 120.dp,
                    )
                }

                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.HorizontalDivider(
                    color = ElTheme.colors.outlineVariant,
                    thickness = 1.dp,
                )
                Spacer(Modifier.height(12.dp))

                // ── 4. Répartition par Catégorie & Moyen ──
                Text(
                    text = "Répartition par poste d'encaissement",
                    style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ElTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))

                currentKpi.categoryBreakdown.forEach { item ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${item.label} (${item.count} ops)",
                                style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ElTheme.colors.textPrimary,
                            )
                            Text(
                                text = "${(item.amount / 100).formatDzd()} DA • %.1f%%".format(item.percentage),
                                style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = ElTheme.colors.primary,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        ElProgressBar(progress = (item.percentage / 100.0).toFloat())
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsMiniTile(
    title: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier,
) {
    ElCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = ElTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = ElTheme.colors.textSecondary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                color = ElTheme.colors.textPrimary,
                maxLines = 1,
            )
            Text(
                text = subtext,
                style = ElTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = ElTheme.colors.textMuted,
                maxLines = 1,
            )
        }
    }
}