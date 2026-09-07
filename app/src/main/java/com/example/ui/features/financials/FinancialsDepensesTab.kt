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
internal fun DepensesTab(
    expenses: List<Expense>,
    pendingCount: Int,
    onExpenseClick: (String) -> Unit,
    onNewExpense: () -> Unit,
) {
    var statusFilter by remember { mutableStateOf<String?>(null) }
    val filtered = if (statusFilter == null) expenses else expenses.filter { it.status.equals(statusFilter, ignoreCase = true) }
    val totalAmount = expenses.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricMiniCard("Total engagé", "${(totalAmount / 100).formatDzd()} DZD", PrimaryBlue, Modifier.weight(1f))
                MetricMiniCard("En attente", "$pendingCount demande(s)", if (pendingCount > 0) WarmGold else SuccessGreen, Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    null to "Toutes",
                    "submitted" to "En attente",
                    "approved" to "Approuvées",
                    "disbursed" to "Décaissées",
                    "settled" to "Clôturées",
                ).forEach { (st, label) ->
                    ElTag(
                        text = label,
                        selected = statusFilter == st,
                        color = PrimaryBlue,
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
                    message = "Aucun ticket de dépense n'a été créé.",
                    actionText = "Créer une dépense",
                    onAction = onNewExpense,
                )
            }
        } else {
            items(filtered) { exp ->
                val (badgeColor, statusFr) = when (exp.status.lowercase()) {
                    "submitted" -> WarmGold to "En attente"
                    "approved" -> PrimaryBlue to "Approuvée"
                    "disbursed" -> SuccessGreen to "Décaissée"
                    "settled" -> SuccessGreen to "Clôturée"
                    else -> MaterialTheme.colorScheme.outline to exp.status
                }

                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onExpenseClick(exp.id) },
                    compact = true,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(exp.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                            ElTag(text = statusFr, color = badgeColor)
                        }
                        Text(
                            "${exp.requestCode} • Catégorie : ${exp.category} • Bénéficiaire : ${exp.payee}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Date : ${exp.submittedAt.take(10)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${(exp.amount / 100).formatDzd()} DZD",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
