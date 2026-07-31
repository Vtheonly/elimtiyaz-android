package com.example.ui.features.financials

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.core.Session

@Composable
fun FinancialsHubScreen(
    session: Session,
    onNavigateToCounterPayment: () -> Unit,
    onNavigateToProofScanner: () -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
    onNavigateToInstallmentSchedule: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Encaissement", "Preuves", "Tranches", "Créances", "Dépenses")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopStart) {
            when (selectedTab) {
                0 -> CounterPaymentScreen(onBack = onNavigateToCounterPayment)
                1 -> ProofScannerScreen(onBack = onNavigateToProofScanner)
                2 -> InstallmentScheduleScreen(onBack = onNavigateToInstallmentSchedule)
                3 -> DebtDashboardScreen(onBack = onNavigateToDebtDashboard)
                4 -> ExpenseApprovalScreen(expenseId = null, onBack = {})
            }
        }
    }
}
