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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
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
import com.example.core.formatDzd
import com.example.domain.model.PaymentMethodSummary
import com.example.domain.repository.RevenuePoint
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElBarChart
import com.example.ui.designsystem.components.data.ElBarChartItem
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.theme.ElTheme

/**
 * Section (5) — Revenue Trends & Payment Method Breakdown.
 * Shows both the 12-month revenue curve and the real distribution of payment methods.
 */
@Composable
internal fun DashboardRevenueChart(
    revenue: List<RevenuePoint>,
    paymentMethods: List<PaymentMethodSummary>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElSectionHeader(
            title = "Flux Financier & Encaissements",
            subtitle = "Évolution des recettes et répartition des modes de paiement",
        )

        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Recettes mensuelles (DZD)",
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
                        height = 160.dp,
                    )
                }

                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.HorizontalDivider(
                    color = ElTheme.colors.outlineVariant,
                    thickness = 1.dp,
                )
                Spacer(Modifier.height(12.dp))

                // ── Breakdown by Payment Method (Cash vs Checks vs Transfers) ──
                Text(
                    text = "Répartition par mode de règlement",
                    style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ElTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))

                // FIX (fabricated breakdown): when no real payments existed the
                // UI rendered an invented distribution (18 espèces / 7 chèques /
                // 3 virements, 62/28/10%). It now renders ONLY the real data —
                // with an explicit empty state when nothing has been collected.
                val hasRealMethodData = paymentMethods.isNotEmpty() && paymentMethods.any { it.count > 0 }
                if (!hasRealMethodData) {
                    Text(
                        text = "Aucun règlement encaissé pour le moment — la répartition apparaîtra dès le premier paiement.",
                        style = ElTheme.typography.bodySmall,
                        color = ElTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        paymentMethods.forEach { methodSummary ->
                        val (icon, color) = when (methodSummary.method.lowercase()) {
                            "cash" -> Icons.Default.Money to ElTheme.colors.success
                            "check" -> Icons.Default.Payments to ElTheme.colors.info
                            "transfer" -> Icons.Default.AccountBalanceWallet to ElTheme.colors.primaryAccent
                            else -> Icons.Default.CreditCard to ElTheme.colors.primary
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(ElTheme.shapes.small)
                                .background(color.copy(alpha = 0.08f))
                                .padding(10.dp),
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        text = methodSummary.label,
                                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = color,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${(methodSummary.totalAmount / 100).formatDzd()} DA",
                                    style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ElTheme.colors.textPrimary,
                                )
                                Text(
                                    text = "${methodSummary.count} trans. • %.0f%%".format(methodSummary.percentage),
                                    style = ElTheme.typography.labelSmall,
                                    color = ElTheme.colors.textSecondary,
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