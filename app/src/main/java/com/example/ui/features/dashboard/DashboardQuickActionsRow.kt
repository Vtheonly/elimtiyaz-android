package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.display.ElSectionHeader

/**
 * Section (3) — Direct Operational Quick Actions.
 * Fast shortcuts for daily administrative and financial actions.
 */
@Composable
internal fun DashboardQuickActionsRow(
    onNavigateToCounterPayment: () -> Unit,
    onNavigateToBatchRegistration: () -> Unit,
    onNavigateToFinancials: () -> Unit,
    onNavigateToAcademics: () -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
    onNavigateToChat: () -> Unit = {},
) {
    ElSectionHeader(
        title = "Actions Rapides",
        subtitle = "Raccourcis pour les opérations quotidiennes",
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            ElButton(
                text = "Encaisser",
                onClick = onNavigateToCounterPayment,
                variant = ElButtonVariant.PRIMARY,
                size = ElButtonSize.MEDIUM,
                icon = Icons.Default.Payments,
            )
        }
        item {
            ElButton(
                text = "Inscrire Famille",
                onClick = onNavigateToBatchRegistration,
                variant = ElButtonVariant.OUTLINED,
                size = ElButtonSize.MEDIUM,
                icon = Icons.Default.PersonAdd,
            )
        }
        item {
            ElButton(
                text = "Faire l'Appel",
                onClick = onNavigateToAcademics,
                variant = ElButtonVariant.OUTLINED,
                size = ElButtonSize.MEDIUM,
                icon = Icons.Default.HowToReg,
            )
        }
        item {
            ElButton(
                text = "Nouvelle Dépense",
                onClick = onNavigateToFinancials,
                variant = ElButtonVariant.OUTLINED,
                size = ElButtonSize.MEDIUM,
                icon = Icons.Default.Receipt,
            )
        }
        item {
            ElButton(
                text = "Relance Créances",
                onClick = onNavigateToDebtDashboard,
                variant = ElButtonVariant.OUTLINED,
                size = ElButtonSize.MEDIUM,
                icon = Icons.Default.Assessment,
            )
        }
        item {
            ElButton(
                text = "Messagerie",
                onClick = onNavigateToChat,
                variant = ElButtonVariant.OUTLINED,
                size = ElButtonSize.MEDIUM,
                icon = Icons.Default.Forum,
            )
        }
    }
}