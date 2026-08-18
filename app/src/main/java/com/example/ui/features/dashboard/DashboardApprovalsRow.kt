package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.formatDzd
import com.example.domain.model.DashboardKpi
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.theme.ElTheme

/**
 * Section (7) — Approvals & Pending Clearance Queue.
 * Summarizes operational items awaiting bank deposit or administrative sign-off.
 */
@Composable
internal fun DashboardApprovalsRow(
    currentKpi: DashboardKpi,
    onNavigateToFinancials: () -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElSectionHeader(
            title = "Files d'Attente & Approbations",
            subtitle = "Opérations administratives et compensations en attente",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Left: Pending Check Deposit / Clearance ──────────────────────
            ElCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Payment,
                                contentDescription = null,
                                tint = ElTheme.colors.info,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = "Chèques",
                                style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = ElTheme.colors.textPrimary,
                            )
                        }
                        ElTag(
                            text = "${currentKpi.pendingChecksCount} en attente",
                            tone = if (currentKpi.pendingChecksCount > 0) ElTagTone.WARNING else ElTagTone.SUCCESS,
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${(currentKpi.pendingChecksAmount / 100).formatDzd()} DA",
                        style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.textPrimary,
                    )
                    Text(
                        text = "À déposer pour compensation",
                        style = ElTheme.typography.labelSmall,
                        color = ElTheme.colors.textSecondary,
                    )

                    Spacer(Modifier.height(10.dp))
                    ElButton(
                        text = "Voir chèques",
                        onClick = onNavigateToFinancials,
                        variant = ElButtonVariant.OUTLINED,
                        size = ElButtonSize.SMALL,
                        fullWidth = true,
                    )
                }
            }

            // ── Right: Pending Expense Approval ───────────────────────────────
            ElCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = ElTheme.colors.warning,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = "Dépenses",
                                style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = ElTheme.colors.textPrimary,
                            )
                        }
                        ElTag(
                            text = "${currentKpi.pendingExpenses} à valider",
                            tone = if (currentKpi.pendingExpenses > 0) ElTagTone.DANGER else ElTagTone.SUCCESS,
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${(currentKpi.pendingExpensesAmount / 100).formatDzd()} DA",
                        style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.textPrimary,
                    )
                    Text(
                        text = "Demandes en attente d'approbation",
                        style = ElTheme.typography.labelSmall,
                        color = ElTheme.colors.textSecondary,
                    )

                    Spacer(Modifier.height(10.dp))
                    ElButton(
                        text = "Examiner",
                        onClick = onNavigateToFinancials,
                        variant = ElButtonVariant.OUTLINED,
                        size = ElButtonSize.SMALL,
                        fullWidth = true,
                    )
                }
            }
        }
    }
}
