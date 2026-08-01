package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.model.DashboardKpi
import com.example.ui.designsystem.components.card.ElGradientStatCard
import com.example.ui.designsystem.components.display.ElGradient

/**
 * Section (g) — two clickable gradient stat cards side by side:
 *
 *  - Pending expenses (taps route to the financials hub)
 *  - Payment overdue alerts (taps route to the debt dashboard)
 */
@Composable
internal fun DashboardApprovalsRow(
    currentKpi: DashboardKpi,
    onNavigateToFinancials: () -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElGradientStatCard(
            title = "Dépenses en attente",
            value = currentKpi.pendingExpenses.toString(),
            gradient = ElGradient.WARNING,
            icon = Icons.Default.Receipt,
            subtitle = "Approbation requise",
            onClick = onNavigateToFinancials,
            modifier = Modifier.weight(1f),
        )
        ElGradientStatCard(
            title = "Retards de paiement",
            value = currentKpi.overdueAlerts.toString(),
            gradient = ElGradient.DANGER,
            icon = Icons.Default.Warning,
            subtitle = "À relancer",
            onClick = onNavigateToDebtDashboard,
            modifier = Modifier.weight(1f),
        )
    }
}
