package com.example.ui.features.financials

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.formatDzd
import com.example.domain.model.DebtSummary
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.display.ElChipVariant
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.util.PhoneUtils

@Composable
internal fun CreancesTab(
    outstandingDebt: Long,
    debtors: List<DebtSummary>,
    onNavigateToDebtor: () -> Unit,
    onNavigateToCounter: (parentId: String?, studentId: String?) -> Unit = { _, _ -> },
) {
    val c = ElTheme.colors
    val context = LocalContext.current
    var bucketFilter by remember { mutableStateOf<String?>(null) }
    val filtered = if (bucketFilter == null) debtors else debtors.filter { it.bucket == bucketFilter }
    val totalOverdue = debtors.sumOf { it.overdueAmount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ElTheme.spacing.lg, vertical = ElTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricMiniCard("Créances totales", "${(outstandingDebt / 100).formatDzd()} DZD", c.primary, Modifier.weight(1f))
                MetricMiniCard("En retard", "${(totalOverdue / 100).formatDzd()} DZD", c.danger, Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    null to "Toutes",
                    "0_30" to "0-30j",
                    "31_60" to "31-60j",
                    "61_90" to "61-90j",
                    "91_180" to "91-180j",
                    "180_plus" to "180j+",
                ).forEach { (b, label) ->
                    ElChip(
                        text = label,
                        variant = ElChipVariant.FILTER,
                        selected = bucketFilter == b,
                        onClick = { bucketFilter = b },
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                ElEmptyState(
                    icon = Icons.Default.CheckCircle,
                    title = "Aucune créance en retard",
                    subtitle = "Toutes les familles sont à jour dans leurs paiements.",
                )
            }
        } else {
            items(filtered) { debtor ->
                // T-457 (§15.1/§15.3): the canonical 4-tier status tone
                // (green success · yellow/orange warning · red danger) — the
                // tone mirrors the desktop's DEBT_AGING_STATUS_TONE.
                val statusTone = when (debtor.statusLevel) {
                    "green" -> ElTagTone.SUCCESS
                    "yellow", "orange" -> ElTagTone.WARNING
                    else -> ElTagTone.DANGER
                }
                val statusColor = when (statusTone) {
                    ElTagTone.SUCCESS -> c.success
                    ElTagTone.WARNING -> c.warning
                    else -> c.danger
                }
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = ElCardSize.STANDARD,
                    border = if (debtor.statusLevel == "red" || debtor.statusLevel == "yellow" || debtor.statusLevel == "orange") {
                        BorderStroke(ElTheme.borders.thin, statusColor.copy(alpha = 0.45f))
                    } else null,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(debtor.parentName, style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                                Text(
                                    "${debtor.studentCount} enfant(s) inscrit(s) • Tél : ${debtor.parentPhone}",
                                    style = ElTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ElIconButton(
                                    icon = Icons.Default.Payments,
                                    onClick = { onNavigateToCounter(debtor.parentId, null) },
                                    contentDescription = "Encaisser",
                                    tint = c.primary,
                                    background = Color.Transparent,
                                )
                                if (debtor.parentPhone.isNotBlank()) {
                                    ElIconButton(
                                        icon = Icons.Default.Call,
                                        onClick = { PhoneUtils.dial(context, debtor.parentPhone) },
                                        contentDescription = "Appeler",
                                        tint = c.success,
                                        background = Color.Transparent,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            // T-457: the §15.3 canonical label replaces the
                            // bare days count as the row's status voice (the
                            // days fact stays on the Debt Dashboard).
                            ElTag(text = debtor.statusLabel, tone = statusTone, size = ElTagSize.MD)
                            Text(
                                "${(debtor.outstandingAmount / 100).formatDzd()} DZD",
                                style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = c.danger,
                            )
                        }
                        // INV-16d: the explanation is part of the contract.
                        if (debtor.statusExplanation.isNotBlank()) {
                            Text(
                                debtor.statusExplanation,
                                style = ElTheme.typography.bodySmall,
                                color = statusColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
