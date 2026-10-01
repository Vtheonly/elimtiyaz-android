package com.example.ui.features.financials

import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.ui.designsystem.components.button.ElFab
import com.example.ui.designsystem.components.tabs.ElScrollableTabRow
import com.example.ui.features.financials.FinancialsHubViewModel

@Composable
fun FinancialsHubScreen(
    session: Session,
    onNavigateToCounterPayment: (parentId: String?, studentId: String?) -> Unit = { _, _ -> },
    onNavigateToProofScanner: () -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
    onNavigateToInstallmentSchedule: () -> Unit,
    onNavigateToExpenseSubmit: () -> Unit = {},
    onNavigateToExpenseDetail: (String) -> Unit = {},
    onNavigateToPaymentDetail: (String) -> Unit = {},
    viewModel: FinancialsHubViewModel = hiltViewModel(),
    installmentViewModel: InstallmentScheduleViewModel = hiltViewModel(),
) {
    val kpis by viewModel.kpis.collectAsState()
    val recentPayments by viewModel.recentPayments.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val ledgerEntries by viewModel.ledgerEntries.collectAsState()
    val collectedToday by viewModel.collectedToday.collectAsState()
    val pendingExpensesCount by viewModel.pendingExpensesCount.collectAsState()
    val debtors by viewModel.debtors.collectAsState()

    val parents by installmentViewModel.parents.collectAsState()
    val selectedParentId by installmentViewModel.selectedParentId.collectAsState()
    val installments by installmentViewModel.installments.collectAsState()
    val parentSummary by installmentViewModel.parentSummary.collectAsState()
    val installmentBusy by installmentViewModel.busy.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Encaissements", "Tranches", "Créances", "Dépenses", "Journal", "Scanner")

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // T-460 pass C: the DS scrollable tab row (the T-044 pass-3a
            // prerequisite component — 6 tabs need the scrollable variant).
            ElScrollableTabRow(
                tabs = tabs,
                selectedIndex = selectedTab,
                onSelected = { selectedTab = it },
            )

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                when (selectedTab) {
                    0 -> PaymentsTab(
                        collectedToday = collectedToday,
                        monthlyRevenue = kpis?.monthlyRevenue ?: 0L,
                        payments = recentPayments,
                        onNavigateToPayment = onNavigateToPaymentDetail,
                        onNewPayment = { onNavigateToCounterPayment(null, null) },
                    )
                    1 -> TranchesTab(
                        parents = parents,
                        selectedParentId = selectedParentId,
                        installments = installments,
                        parentSummary = parentSummary,
                        busy = installmentBusy,
                        onSelectParent = { installmentViewModel.selectParent(it) },
                        onMarkPaid = { installmentViewModel.markPaid(it) },
                        onNavigateToCounter = { pId, sId -> onNavigateToCounterPayment(pId, sId) },
                        // PARITY-003 — the global wave meters from the KPI contract.
                        // T-454 (PARITY-007): the canonical POOLED rows + the
                        // canonical strip totals (the whole selection, FI rows
                        // included — the same derivation the desktop strip uses).
                        globalWaves = kpis?.trancheWaves ?: emptyList(),
                        globalStripTotals = kpis?.trancheStripTotals,
                    )
                    2 -> CreancesTab(
                        outstandingDebt = kpis?.outstandingDebt ?: 0L,
                        debtors = debtors,
                        onNavigateToDebtor = onNavigateToDebtDashboard,
                        onNavigateToCounter = { pId, sId -> onNavigateToCounterPayment(pId, sId) },
                    )
                    3 -> DepensesTab(
                        expenses = expenses,
                        pendingCount = pendingExpensesCount,
                        onExpenseClick = onNavigateToExpenseDetail,
                        onNewExpense = onNavigateToExpenseSubmit,
                    )
                    4 -> JournalTab(
                        entries = ledgerEntries,
                    )
                    5 -> ProofScannerScreen(
                        onBack = { selectedTab = 0 },
                    )
                }
            }
        }

        if (selectedTab == 3) {
            ElFab(
                icon = Icons.Default.Add,
                onClick = onNavigateToExpenseSubmit,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                contentDescription = "Nouvelle dépense",
            )
        }
    }
}
