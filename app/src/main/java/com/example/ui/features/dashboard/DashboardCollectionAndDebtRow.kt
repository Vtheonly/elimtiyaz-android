package com.example.ui.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.derivePareto
import com.example.core.ParetoDebtor
import com.example.core.formatDzd
import com.example.domain.model.DashboardKpi
import com.example.domain.model.DebtSummary
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElDonutChart
import com.example.ui.designsystem.components.data.ElDonutSegment
import com.example.ui.designsystem.components.data.ElProgressRing
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.util.PhoneUtils

/**
 * PARITY-002 (T-284): this row renders ONLY repository-computed statistics.
 * Every hard-coded desktop-reference fallback (0.49f, the 26.9M/31.4M donut,
 * 58_355_700_00L, "0%"/"100%" funnel strings, "6 fam. = 80%") was DELETED —
 * empty data renders honest empty states (§15.16), never fabricated numbers.
 * The collection rate, aging donut, and funnel come from the KPI contract
 * (computed by core/StatisticsEngine in LocalDashboardRepository); the
 * Pareto uses the shared engine derivation over the real top-debtors list
 * (mirroring the desktop's DebtorsParetoCard layering).
 */
@Composable
internal fun DashboardCollectionAndDebtRow(
    currentKpi: DashboardKpi,
    debtAging: List<DebtSummary>,
    onNavigateToParent: (String) -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ── Synthèse Décisionnelle IA ──
        ElCard(
            modifier = Modifier.fillMaxWidth(),
            background = ElTheme.colors.primaryContainer.copy(alpha = 0.25f),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = ElTheme.colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Synthèse Décisionnelle IA",
                        style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.primary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Suivi des relances : ${currentKpi.overdueFamiliesCount} familles en attente, dont ${currentKpi.recoveryFunnel.lastOrNull()?.count ?: 0} dossiers prioritaires.",
                    style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ElTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(10.dp))
                ElButton(
                    text = "Accéder aux relances",
                    onClick = onNavigateToDebtDashboard,
                    variant = ElButtonVariant.PRIMARY,
                    size = ElButtonSize.SMALL,
                )
            }
        }

        // ── Taux de Recouvrement Annuel + Structure Impayé ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Left: Annual Recovery Rate — computed by StatisticsEngine.collectionRatePct
            ElCard(modifier = Modifier.weight(1f)) {
                val revenueToDisplay = currentKpi.totalRevenue
                val debtToDisplay = currentKpi.outstandingDebt
                val rate = currentKpi.collectionRatePct / 100f

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "TAUX RECOUVREMENT",
                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))

                    if (revenueToDisplay > 0L || debtToDisplay > 0L) {
                        ElProgressRing(
                            progress = rate,
                            size = 90.dp,
                            color = ElTheme.colors.info,
                            label = "${currentKpi.collectionRatePct}%",
                        )
                    } else {
                        ElProgressRing(
                            progress = 0f,
                            size = 90.dp,
                            color = ElTheme.colors.info,
                            label = "—",
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Encaissé", style = ElTheme.typography.labelSmall, color = ElTheme.colors.textSecondary)
                            Text(
                                "${(revenueToDisplay / 100).formatDzd()} DA",
                                style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ElTheme.colors.success,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Créances", style = ElTheme.typography.labelSmall, color = ElTheme.colors.textSecondary)
                            Text(
                                "${(debtToDisplay / 100).formatDzd()} DA",
                                style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ElTheme.colors.primaryAccent,
                            )
                        }
                    }
                }
            }

            // Right: Debt Aging Breakdown — the engine census (per-installment
            // INV-4, distinct families per bucket), never a fabricated donut.
            ElCard(modifier = Modifier.weight(1f)) {
                val segments = currentKpi.debtByAging.mapNotNull { bucketItem ->
                    if (bucketItem.amount > 0L) {
                        ElDonutSegment(
                            label = bucketItem.label,
                            value = (bucketItem.amount / 100).toFloat(),
                            color = bucketColor(bucketItem.bucket),
                        )
                    } else null
                }
                val totalDebtAmount = currentKpi.outstandingDebt
                val debtFormatted = if (totalDebtAmount > 0L) {
                    "%.1f M DA".format(totalDebtAmount / 100_000_000.0)
                } else "0"

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "PAR ANCIENNETÉ",
                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(6.dp))

                    if (segments.isNotEmpty()) {
                        ElDonutChart(
                            segments = segments,
                            size = 90.dp,
                            centerLabel = "Encours",
                            centerValue = debtFormatted,
                        )
                    } else {
                        Text(
                            text = "Aucune créance ouverte.",
                            style = ElTheme.typography.bodySmall,
                            color = ElTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }

        // ── Entonnoir de Recouvrement (Recovery Funnel) — real census stages.
        if (currentKpi.recoveryFunnel.isNotEmpty()) {
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val criticalPct = currentKpi.recoveryFunnel.last().sharePct
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "ENTONNOIR DE RECOUVREMENT",
                            style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ElTheme.colors.textPrimary,
                        )
                        ElTag(text = "Critique : $criticalPct%", tone = ElTagTone.WARNING)
                    }
                    Text(
                        text = "Profondeur d'ancienneté des dossiers",
                        style = ElTheme.typography.bodySmall,
                        color = ElTheme.colors.textSecondary,
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        currentKpi.recoveryFunnel.forEach { stage ->
                            FunnelBox(
                                title = stage.name,
                                value = "${stage.count}",
                                pct = "${stage.sharePct}%",
                                color = when (stage.name) {
                                    "En retard" -> ElTheme.colors.primaryAccent
                                    "≤ 60 j" -> ElTheme.colors.info
                                    "61–90 j" -> ElTheme.colors.primary
                                    else -> ElTheme.colors.danger
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        // ── Top Debtor Families / Pareto (top 8, cumulative % of the
        // displayed total — the desktop derivation, shared engine).
        if (debtAging.isNotEmpty()) {
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val pareto = derivePareto(
                        debtAging.map { ParetoDebtor(it.parentName, it.outstandingAmount) },
                        topN = 8,
                    )
                    val paretoCut = pareto.indexOfFirst { it.cumPercent >= 80 } + 1

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "PARETO DES DÉBITEURS (Top familles)",
                            style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ElTheme.colors.textPrimary,
                        )
                        if (paretoCut > 0) {
                            Text(
                                text = "${paretoCut} fam. = 80% de l'encours",
                                style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ElTheme.colors.primaryAccent,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    debtAging.take(8).forEachIndexed { idx, debtor ->
                        val cum = pareto.getOrNull(idx)?.cumPercent
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ElTheme.shapes.small)
                                .clickable { onNavigateToParent(debtor.parentId) }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "#${idx + 1}",
                                    style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ElTheme.colors.textSecondary,
                                    modifier = Modifier.width(24.dp),
                                )
                                Column {
                                    Text(
                                        text = debtor.parentName,
                                        style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = ElTheme.colors.textPrimary,
                                    )
                                    Text(
                                        text = if (cum != null) {
                                            "${debtor.studentCount} enfant(s) • Retard : ${debtor.daysOverdue} j • cum. ${cum}%"
                                        } else {
                                            "${debtor.studentCount} enfant(s) • Retard : ${debtor.daysOverdue} j"
                                        },
                                        style = ElTheme.typography.labelSmall,
                                        color = ElTheme.colors.textSecondary,
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${(debtor.outstandingAmount / 100).formatDzd()} DA",
                                    style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ElTheme.colors.textPrimary,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                if (debtor.parentPhone.isNotBlank()) {
                                    IconButton(
                                        onClick = { PhoneUtils.dial(context, debtor.parentPhone) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = "Appeler",
                                            tint = ElTheme.colors.success,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FunnelBox(
    title: String,
    value: String,
    pct: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = ElTheme.typography.labelSmall, color = ElTheme.colors.textSecondary)
            Text(value, style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
            Text(pct, style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp), color = color)
        }
    }
}
