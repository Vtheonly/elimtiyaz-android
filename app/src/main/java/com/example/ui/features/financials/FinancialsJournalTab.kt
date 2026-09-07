package com.example.ui.features.financials

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.LedgerEntry
import com.example.core.PaymentCategory
import com.example.core.PaymentStatus
import com.example.core.Session
import com.example.core.formatDzd
import com.example.domain.model.DebtSummary
import com.example.domain.model.Expense
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.model.Payment
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElFab
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElProgressBar
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTextField
import com.example.ui.components.ModernSecondaryTabRow
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold
import com.example.ui.util.PhoneUtils

@Composable
internal fun JournalTab(
    entries: List<LedgerEntry>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    message = "Aucun mouvement financier enregistré.",
                )
            }
        } else {
            items(entries.sortedByDescending { it.at }) { entry ->
                val isCredit = entry.type.code == "payment" || (entry.type.code == "adjustment" && entry.amount < 0) || entry.type.code == "refund"
                val (typeBadgeColor, typeLabel) = when (entry.type.code) {
                    "charge" -> DangerRed to "Facturation"
                    "payment" -> SuccessGreen to "Encaissement"
                    "adjustment" -> if (entry.amount < 0) SuccessGreen to "Remise / Déduction" else WarmGold to "Régularisation"
                    "refund" -> DangerRed to "Remboursement"
                    "reversal" -> WarmGold to "Extourne"
                    else -> PrimaryBlue to entry.type.code.replaceFirstChar { it.uppercase() }
                }

                val displayDescription = remember(entry.description) {
                    cleanDescription(entry.description, entry.category.name)
                }

                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    accent = if (isCredit) SuccessGreen else DangerRed,
                    compact = true,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ElTag(text = typeLabel, color = typeBadgeColor)
                            Text(
                                text = entry.at.take(10),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Text(
                            text = displayDescription,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Service : ${categoryFr(entry.category)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val formattedAmount = (kotlin.math.abs(entry.amount) / 100).formatDzd()
                            Text(
                                text = "${if (entry.amount < 0) "− " else "+ "}$formattedAmount DZD",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isCredit) SuccessGreen else DangerRed,
                            )
                        }
                    }
                }
            }
        }
    }
}
