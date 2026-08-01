package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.display.ElSectionHeader

/**
 * Section (h) — horizontally scrollable row of 5 outlined quick-action
 * buttons: new payment, new student, new expense, roll call, view debt report.
 */
@Composable
internal fun DashboardQuickActionsRow(
    onNavigateToCounterPayment: () -> Unit,
    onNavigateToBatchRegistration: () -> Unit,
    onNavigateToFinancials: () -> Unit,
    onNavigateToAcademics: () -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
) {
    ElSectionHeader(title = "Actions rapides")
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            ElButton(
                text = "Nouveau paiement",
                onClick = onNavigateToCounterPayment,
                variant = ElButtonVariant.OUTLINED,
                size = ElButtonSize.MEDIUM,
                icon = Icons.Default.Payments,
            )
        }
        item {
            ElButton(
                text = "Nouvel élève",
                onClick = onNavigateToBatchRegistration,
                variant = ElButtonVariant.OUTLINED,
                size = ElButtonSize.MEDIUM,
                icon = Icons.Default.Person,
            )
        }
        item {
            ElButton(
                text = "Nouvelle dépense",
                onClick = onNavigateToFinancials,
                variant = ElButtonVariant.OUTLINED,
                size = ElButtonSize.MEDIUM,
                icon = Icons.Default.Receipt,
            )
        }
        item {
            ElButton(
                text = "Roll call",
                onClick = onNavigateToAcademics,
                variant = ElButtonVariant.OUTLINED,
                size = ElButtonSize.MEDIUM,
                icon = Icons.Default.HowToReg,
            )
        }
        item {
            ElButton(
                text = "Voir rapport",
                onClick = onNavigateToDebtDashboard,
                variant = ElButtonVariant.OUTLINED,
                size = ElButtonSize.MEDIUM,
                icon = Icons.Default.Assessment,
            )
        }
    }
}
