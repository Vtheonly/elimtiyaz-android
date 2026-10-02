package com.example.ui.features.financials

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.LedgerEntry
import com.example.core.formatDzd
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.theme.ElTheme

@Composable
internal fun JournalTab(
    entries: List<LedgerEntry>,
) {
    val c = ElTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ElTheme.spacing.lg, vertical = ElTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElSectionHeader(
                title = "Grand Livre — Mouvements",
                subtitle = "Historique transparent des facturations, encaissements et régularisations",
            )
        }

        if (entries.isEmpty()) {
            item {
                ElEmptyState(
                    icon = Icons.Default.Receipt,
                    title = "Grand livre vide",
                    subtitle = "Aucun mouvement financier enregistré.",
                )
            }
        } else {
            items(entries.sortedByDescending { it.at }) { entry ->
                val isCredit = entry.type.code == "payment" || (entry.type.code == "adjustment" && entry.amount < 0) || entry.type.code == "refund"
                val (typeTone, typeLabel) = when (entry.type.code) {
                    "charge" -> ElTagTone.DANGER to "Facturation"
                    "payment" -> ElTagTone.SUCCESS to "Encaissement"
                    "adjustment" -> if (entry.amount < 0) ElTagTone.SUCCESS to "Remise / Déduction" else ElTagTone.WARNING to "Régularisation"
                    "refund" -> ElTagTone.DANGER to "Remboursement"
                    "reversal" -> ElTagTone.WARNING to "Extourne"
                    else -> ElTagTone.INFO to entry.type.code.replaceFirstChar { it.uppercase() }
                }
                val accent = when (typeTone) {
                    ElTagTone.SUCCESS -> c.success
                    ElTagTone.WARNING -> c.warning
                    ElTagTone.DANGER -> c.danger
                    else -> c.primary
                }

                val displayDescription = remember(entry.description) {
                    cleanDescription(entry.description, entry.category.name)
                }

                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = ElCardSize.STANDARD,
                    border = BorderStroke(ElTheme.borders.thin, accent.copy(alpha = 0.45f)),
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ElTag(text = typeLabel, tone = typeTone, size = ElTagSize.MD)
                            Text(
                                text = entry.at.take(10),
                                style = ElTheme.typography.labelSmall,
                                color = c.textSecondary,
                            )
                        }

                        Text(
                            text = displayDescription,
                            style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = c.textPrimary,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Service : ${categoryFr(entry.category)}",
                                style = ElTheme.typography.labelSmall,
                                color = c.textSecondary,
                            )
                            val formattedAmount = (kotlin.math.abs(entry.amount) / 100).formatDzd()
                            Text(
                                text = "${if (entry.amount < 0) "− " else "+ "}$formattedAmount DZD",
                                style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isCredit) c.success else c.danger,
                            )
                        }
                    }
                }
            }
        }
    }
}
