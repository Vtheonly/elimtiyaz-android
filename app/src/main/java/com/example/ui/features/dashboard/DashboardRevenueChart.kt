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
import com.example.domain.model.ExecutiveStatsSnapshot
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.features.dashboard.analytics.WaveVelocityCard

/**
 * T-340 (STATS-400): the overview hero. The smooth 12-month revenue trend
 * (the misleading curve — school revenue is a 3-spike staircase) was
 * REMOVED per the owner's kill list, replaced by the WaveVelocityCard
 * hero: the three tranche waves vs their invoiced targets (the desktop
 * T-339 overview parity — same restructure, same data).
 */
@Composable
internal fun DashboardRevenueChart(
    currentKpi: DashboardKpi,
    executive: ExecutiveStatsSnapshot,
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
                value = currentKpi.bestMonthName ?: "—",
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

        // ── 2. The wave staircase hero (replaces the smooth monthly trend —
        // T-340 / STATS-400, the owner's kill list; desktop T-339 parity) ──
        WaveVelocityCard(waves = executive.waves)

        // ── 3. Répartition par Catégorie (REAL data, kept) ──
        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
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
                                text = "${(item.amount / 100).formatDzd()} DA • ${item.percentage}%",
                                style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = ElTheme.colors.primary,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        ElLinearProgress(progress = (item.percentage / 100.0).toFloat())
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