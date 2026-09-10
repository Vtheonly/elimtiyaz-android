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
            ModernSecondaryTabRow(
                tabs = tabs,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
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
                        // PARITY-003 — the global wave meters from the KPI contract
                        globalWaves = kpis?.trancheWaves ?: emptyList(),
                        globalOverdueCount = kpis?.overdueFamiliesCount ?: 0,
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
