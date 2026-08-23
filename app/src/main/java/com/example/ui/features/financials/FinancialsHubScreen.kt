package com.example.ui.features.financials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Permission
import com.example.core.Session
import com.example.core.formatDzd
import com.example.ui.components.ModernSecondaryTabRow

@Composable
fun FinancialsHubScreen(
    session: Session,
    onNavigateToCounterPayment: () -> Unit,
    onNavigateToProofScanner: () -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
    onNavigateToInstallmentSchedule: () -> Unit,
    onNavigateToExpenseSubmit: () -> Unit = {},
    onNavigateToExpenseDetail: (String) -> Unit = {},
    onNavigateToPaymentDetail: (String) -> Unit = {},
    viewModel: FinancialsHubViewModel = hiltViewModel(),
) {
    val kpis by viewModel.kpis.collectAsState()
    val recentPayments by viewModel.recentPayments.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val ledgerEntries by viewModel.ledgerEntries.collectAsState()
    val collectedToday by viewModel.collectedToday.collectAsState()
    val pendingExpensesCount by viewModel.pendingExpensesCount.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Encaissements", "Preuves", "Tranches", "Créances", "Dépenses", "Circulation")

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 4 && session.can(Permission.SUBMIT_EXPENSE)) {
                FloatingActionButton(onClick = onNavigateToExpenseSubmit) {
                    Icon(Icons.Default.Add, contentDescription = "Nouvelle dépense")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // KPI cards row (divided by 100 for DZD)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KpiCard("Encaissé aujourd'hui", "${(collectedToday / 100).formatDzd()} DZD", Modifier.weight(1f))
                KpiCard("Revenu mensuel", "${((kpis?.monthlyRevenue ?: 0L) / 100).formatDzd()} DZD", Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KpiCard("Créances", "${((kpis?.outstandingDebt ?: 0L) / 100).formatDzd()} DZD", Modifier.weight(1f))
                KpiCard("Dépenses en attente", pendingExpensesCount.toString(), Modifier.weight(1f))
            }

            ModernSecondaryTabRow(
                tabs = tabs,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
            )

            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                when (selectedTab) {
                    0 -> PaymentsList(recentPayments, onNavigateToPaymentDetail)
                    // FIX (back pushes duplicate): the embedded screens' back
                    // buttons were wired to `onNavigateTo*` — tapping back
                    // PUSHED a second standalone copy of the same screen onto
                    // the stack. Embedded tabs have no parent to return to, so
                    // back now simply returns to the Encaissements tab.
                    1 -> ProofScannerScreen(onBack = { selectedTab = 0 })
                    2 -> InstallmentScheduleScreen(onBack = { selectedTab = 0 })
                    3 -> DebtDashboardScreen(onBack = { selectedTab = 0 })
                    4 -> ExpensesList(expenses, onNavigateToExpenseDetail)
                    5 -> LedgerCirculationList(ledgerEntries)
                }
            }
        }
    }
}

@Composable
private fun LedgerCirculationList(entries: List<com.example.core.LedgerEntry>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entries.sortedByDescending { it.at }) { entry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = entry.description.ifBlank { entry.category.name },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${(entry.amount / 100).formatDzd()} DZD",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (entry.type.code == "charge") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Type: ${entry.type.code}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = entry.at.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun PaymentsList(payments: List<com.example.domain.model.Payment>, onNavigateToPaymentDetail: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(payments.take(50)) { payment ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigateToPaymentDetail(payment.id) },
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(payment.receiptNumber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${(payment.amount / 100).formatDzd()} DZD",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${payment.method.code.uppercase()} • ${payment.category.code}", style = MaterialTheme.typography.labelSmall)
                        Text(payment.collectedAt.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpensesList(expenses: List<com.example.domain.model.Expense>, onNavigateToExpenseDetail: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(expenses) { expense ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigateToExpenseDetail(expense.id) },
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(expense.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${(expense.amount / 100).formatDzd()} DZD",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text("${expense.requestCode} • ${expense.status}", style = MaterialTheme.typography.labelSmall)
                    Text("Bénéficiaire: ${expense.payee}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}