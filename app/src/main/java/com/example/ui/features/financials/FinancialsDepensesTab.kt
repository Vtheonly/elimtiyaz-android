package com.example.ui.features.financials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
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
import com.example.domain.model.Expense
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
internal fun DepensesTab(
    expenses: List<Expense>,
    pendingCount: Int,
    onExpenseClick: (String) -> Unit,
    onNewExpense: () -> Unit,
) {
    val c = ElTheme.colors
    var statusFilter by remember { mutableStateOf<String?>(null) }
    val filtered = if (statusFilter == null) expenses else expenses.filter { it.status.equals(statusFilter, ignoreCase = true) }
    val totalAmount = expenses.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ElTheme.spacing.lg, vertical = ElTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricMiniCard("Total engagé", "${(totalAmount / 100).formatDzd()} DZD", c.primary, Modifier.weight(1f))
                MetricMiniCard("En attente", "$pendingCount demande(s)", if (pendingCount > 0) c.warning else c.success, Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    null to "Toutes",
                    "submitted" to "En attente",
                    "approved" to "Approuvées",
                    "disbursed" to "Décaissées",
                    "settled" to "Clôturées",
                ).forEach { (st, label) ->
                    ElChip(
                        text = label,
                        variant = ElChipVariant.FILTER,
                        selected = statusFilter == st,
                        onClick = { statusFilter = st },
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                ElEmptyState(
                    icon = Icons.Default.Receipt,
                    title = "Aucune dépense",
                    subtitle = "Aucun ticket de dépense n'a été créé.",
                    actionLabel = "Créer une dépense",
                    onAction = onNewExpense,
                )
            }
        } else {
            items(filtered) { exp ->
                val (badgeTone, statusFr) = when (exp.status.lowercase()) {
                    "submitted" -> ElTagTone.WARNING to "En attente"
                    "approved" -> ElTagTone.INFO to "Approuvée"
                    "disbursed" -> ElTagTone.SUCCESS to "Décaissée"
                    "settled" -> ElTagTone.SUCCESS to "Clôturée"
                    else -> ElTagTone.NEUTRAL to exp.status
                }

                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = ElCardSize.STANDARD,
                    onClick = { onExpenseClick(exp.id) },
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(exp.title, style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.textPrimary, modifier = Modifier.weight(1f))
                            ElTag(text = statusFr, tone = badgeTone, size = ElTagSize.MD)
                        }
                        Text(
                            "${exp.requestCode} • Catégorie : ${exp.category} • Bénéficiaire : ${exp.payee}",
                            style = ElTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Date : ${exp.submittedAt.take(10)}", style = ElTheme.typography.labelSmall, color = c.textSecondary)
                            Text(
                                "${(exp.amount / 100).formatDzd()} DZD",
                                style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = c.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
