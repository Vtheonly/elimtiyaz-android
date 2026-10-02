package com.example.ui.features.financials

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.formatDzd
import com.example.domain.model.Payment
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.display.ElChipVariant
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.theme.ElTheme

@Composable
internal fun PaymentsTab(
    collectedToday: Long,
    monthlyRevenue: Long,
    payments: List<Payment>,
    onNavigateToPayment: (String) -> Unit,
    onNewPayment: () -> Unit,
) {
    val c = ElTheme.colors
    var methodFilter by remember { mutableStateOf<String?>(null) }
    val filtered = if (methodFilter == null) payments else payments.filter { it.method.code.equals(methodFilter, ignoreCase = true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ElTheme.spacing.lg, vertical = ElTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricMiniCard("Aujourd'hui", "${(collectedToday / 100).formatDzd()} DZD", c.success, Modifier.weight(1f))
                MetricMiniCard("Ce mois", "${(monthlyRevenue / 100).formatDzd()} DZD", c.primary, Modifier.weight(1f))
            }
        }

        item {
            ElButton(
                text = "Encaisser un paiement au guichet",
                onClick = onNewPayment,
                fullWidth = true,
                icon = Icons.Default.Payments,
                variant = ElButtonVariant.PRIMARY,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    null to "Tous",
                    "cash" to "Espèces",
                    "check" to "Chèques",
                    "transfer" to "Virements",
                ).forEach { (code, label) ->
                    // T-460: interactive filter pills are ElChips (the DS
                    // language — the legacy ElTag's onClick/selected twins).
                    ElChip(
                        text = label,
                        variant = ElChipVariant.FILTER,
                        selected = methodFilter == code,
                        onClick = { methodFilter = code },
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                ElEmptyState(
                    icon = Icons.Default.Payments,
                    title = "Aucun encaissement",
                    subtitle = "Aucun paiement enregistré pour ce filtre.",
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        } else {
            items(filtered) { payment ->
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = ElCardSize.STANDARD,
                    onClick = { onNavigateToPayment(payment.id) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(c.success.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, tint = c.success, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(payment.receiptNumber, style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                                Text(
                                    "${payment.category.name.replace("_", " ")} • ${payment.collectedAt.take(10)}",
                                    style = ElTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "+${(payment.amount / 100).formatDzd()} DZD",
                                style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = c.success,
                            )
                            ElTag(
                                text = payment.method.name,
                                tone = ElTagTone.INFO,
                                size = ElTagSize.MD,
                            )
                        }
                    }
                }
            }
        }
    }
}
