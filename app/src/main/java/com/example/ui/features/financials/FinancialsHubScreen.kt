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
import com.example.ui.components.ElGradientStatCard
import com.example.ui.components.ModernSecondaryTabRow

/**
 * Financials hub — restored to use [FinancialsHubViewModel] for aggregated state.
 *
 * KPI cards at the top (collected today, monthly revenue, outstanding debt, pending expenses).
 * 5-tab layout: Encaissement / Preuves / Tranches / Créances / Dépenses.
 *
 * FAB on the "Dépenses" tab → ExpenseSubmit (gated by SUBMIT_EXPENSE).
 */
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
    val debtors by viewModel.topDebtors.collectAsState()
    val collectedToday by viewModel.collectedToday.collectAsState()
    val pendingExpensesCount by viewModel.pendingExpensesCount.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Encaissement", "Preuves", "Tranches", "Créances", "Dépenses")

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
            // KPI cards row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KpiCard("Encaissé aujourdhui", "${collectedToday.formatDzd()} DZD", Modifier.weight(1f))
                KpiCard("Revenu mensuel", "${(kpis?.monthlyRevenue ?: 0L).formatDzd()} DZD", Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KpiCard("Créances", "${(kpis?.outstandingDebt ?: 0L).formatDzd()} DZD", Modifier.weight(1f))
                KpiCard("Dépenses en attente", pendingExpensesCount.toString(), Modifier.weight(1f))
            }

            ModernSecondaryTabRow(
                tabs = tabs,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
            )

            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                when (selectedTab) {
                    0 -> PaymentsList(recentPayments, onNavigateToPaymentDetail)
                    1 -> ProofScannerScreen(onBack = onNavigateToProofScanner)
                    2 -> InstallmentScheduleScreen(onBack = onNavigateToInstallmentSchedule)
                    3 -> DebtDashboardScreen(onBack = onNavigateToDebtDashboard)
                    4 -> ExpensesList(expenses, onNavigateToExpenseDetail)
                }
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PaymentsList(payments: List<com.example.domain.model.Payment>, onNavigateToPaymentDetail: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(payments.take(30)) { payment ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigateToPaymentDetail(payment.id) },
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(payment.receiptNumber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${payment.method.code} • ${payment.category.code}", style = MaterialTheme.typography.labelSmall)
                    Text("${(payment.amount / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(payment.collectedAt.take(10), style = MaterialTheme.typography.labelSmall)
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
                    Text(expense.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${expense.requestCode} • ${expense.status}", style = MaterialTheme.typography.labelSmall)
                    Text("${(expense.amount / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(expense.payee, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
