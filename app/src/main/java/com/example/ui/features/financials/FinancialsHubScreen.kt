package com.example.ui.features.financials

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.Session
import com.example.ui.components.ModernSecondaryTabRow

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
        ModernSecondaryTabRow(
            tabs = tabs,
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
        )
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.TopStart) {
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
