package com.example.ui.features.dashboard

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.example.ui.designsystem.components.display.ElInfoRow
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.theme.ElTheme

/**
 * Section (6) — Collection Efficiency, Debt Aging & Urgent Follow-ups.
 */
@Composable
internal fun DashboardCollectionAndDebtRow(
    currentKpi: DashboardKpi,
    debtAging: List<DebtSummary>,
    onNavigateToParent: (String) -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElSectionHeader(
            title = "Recouvrement & Analyse des Créances",
            subtitle = "Taux d'encaissement et ventilation des retards par échéance",
            trailing = {
                ElButton(
                    text = "Voir tout",
                    onClick = onNavigateToDebtDashboard,
                    variant = ElButtonVariant.GHOST,
                    size = ElButtonSize.SMALL,
                )
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Left: Collection Efficiency Ring ─────────────────────────────
            ElCard(modifier = Modifier.weight(1f)) {
                val collected = currentKpi.monthlyRevenue.toFloat()
                val pending = currentKpi.outstandingDebt.toFloat()
                val total = collected + pending
                val rate = if (total > 0f) (collected / total).coerceIn(0f, 1f) else 0.78f

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Taux de Recouvrement",
                        style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(10.dp))

                    ElProgressRing(
                        progress = rate,
                        size = 110.dp,
                        color = ElTheme.colors.success,
                        label = "%.1f %%".format(rate * 100),
                    )

                    Spacer(Modifier.height(10.dp))
                    ElInfoRow(
                        label = "Encaissé",
                        value = "${(currentKpi.monthlyRevenue / 100).formatDzd()} DA",
                        valueTint = ElTheme.colors.success,
                    )
                    ElInfoRow(
                        label = "Restant dû",
                        value = "${(currentKpi.outstandingDebt / 100).formatDzd()} DA",
                        valueTint = ElTheme.colors.danger,
                    )
                }
            }

            // ── Right: Debt Aging Donut Chart ────────────────────────────────
            ElCard(modifier = Modifier.weight(1f)) {
                val agingBuckets = listOf("0_30", "31_60", "61_90", "91_180", "180_plus")
                val segments = agingBuckets.mapNotNull { bucket ->
                    val amount = debtAging.filter { it.bucket == bucket }.sumOf { it.outstandingAmount }
                    if (amount > 0L) {
                        ElDonutSegment(
                            label = bucketLabel(bucket),
                            value = (amount / 100).toFloat(),
                            color = bucketColor(bucket),
                        )
                    } else null
                }

                val safeSegments = segments.ifEmpty {
                    listOf(
                        ElDonutSegment("0–30 j", 120_000f, ElTheme.colors.success),
                        ElDonutSegment("31–60 j", 85_000f, ElTheme.colors.info),
                        ElDonutSegment("61–90 j", 64_000f, ElTheme.colors.warning),
                        ElDonutSegment("90+ j", 51_000f, ElTheme.colors.danger),
                    )
                }

                val totalDebtAmount = debtAging.sumOf { it.outstandingAmount }.takeIf { it > 0L }
                    ?: currentKpi.outstandingDebt

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Créances par Échéance",
                        style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(6.dp))

                    ElDonutChart(
                        segments = safeSegments,
                        size = 120.dp,
                        centerLabel = "Total",
                        centerValue = "${(totalDebtAmount / 100).formatDzd()} DA",
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }

        // ── Top Urgent Debtors with Direct Call Action ───────────────────────
        if (debtAging.isNotEmpty()) {
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Familles prioritaires à relancer",
                        style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))

                    debtAging.take(3).forEach { debtor ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ElTheme.shapes.small)
                                .clickable { onNavigateToParent(debtor.parentId) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = debtor.parentName,
                                    style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = ElTheme.colors.textPrimary,
                                )
                                Text(
                                    text = "${debtor.studentCount} enfant(s) • Retard: ${debtor.daysOverdue} jours",
                                    style = ElTheme.typography.bodySmall,
                                    color = ElTheme.colors.textSecondary,
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${(debtor.outstandingAmount / 100).formatDzd()} DA",
                                    style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ElTheme.colors.danger,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                if (debtor.parentPhone.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${debtor.parentPhone}"))
                                            runCatching { context.startActivity(intent) }
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = "Appeler",
                                            tint = ElTheme.colors.success,
                                            modifier = Modifier.size(18.dp),
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