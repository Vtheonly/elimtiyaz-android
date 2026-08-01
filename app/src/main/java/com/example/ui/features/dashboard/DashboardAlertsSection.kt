package com.example.ui.features.dashboard

import androidx.compose.runtime.Composable
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity

/**
 * Section (a) — top-of-screen alert banners.
 *
 * Shows a DANGER banner when the dashboard view-model surfaced a load error,
 * and a WARNING banner with a "Voir" action when there are overdue payment
 * alerts. Both banners are independent and may render simultaneously.
 */
@Composable
internal fun DashboardAlertsSection(
    error: String?,
    overdueCount: Int,
    onNavigateToDebtDashboard: () -> Unit,
) {
    error?.let { msg ->
        ElAlertBanner(
            title = msg,
            severity = ElAlertSeverity.DANGER,
            onDismiss = null,
        )
    }
    if (overdueCount > 0) {
        ElAlertBanner(
            title = "$overdueCount alerte${if (overdueCount > 1) "s" else ""} de retard",
            message = "Des paiements en souffrance dépassent l'échéance. Ouvrez le tableau des créances pour relancer.",
            severity = ElAlertSeverity.WARNING,
            actionLabel = "Voir",
            onAction = onNavigateToDebtDashboard,
        )
    }
}
