package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.domain.model.DashboardOperationalAlert
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.overlays.LocalElToast
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.util.PhoneUtils

/**
 * T-460 pass G-a (issue #3 F-14): the hub's alert cards now render on the
 * SAME [NotificationRow] component as the Alerts inbox — one notification
 * language for both surfaces. The type mapping and the per-type navigation
 * actions are preserved verbatim; the call action and the "Consulter"
 * action live in the row's trailing slot.
 */
@Composable
internal fun DashboardAlertsSection(
    error: String?,
    alerts: List<DashboardOperationalAlert>,
    onNavigateToParent: (String) -> Unit,
    onNavigateToRollCall: (String) -> Unit,
    onNavigateToExpenseDetail: (String) -> Unit,
    onNavigateToFinancials: () -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
) {
    val context = LocalContext.current
    val toast = LocalElToast.current
    val c = ElTheme.colors

    error?.let { msg ->
        ElAlertBanner(
            title = "Notification système",
            message = msg,
            severity = ElAlertSeverity.INFO,
        )
    }

    if (alerts.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElSectionHeader(
            title = "Rappels & Notifications de gestion",
            subtitle = "${alerts.size} notification${if (alerts.size > 1) "s" else ""}",
        )

        alerts.take(3).forEach { alert ->
            val (icon, tint, tone) = when (alert.type) {
                "overdue_debt" -> Triple(Icons.Default.Notifications, c.primaryAccent, ElTagTone.WARNING)
                "pending_expense" -> Triple(Icons.Default.ReceiptLong, c.primary, ElTagTone.INFO)
                "pending_check" -> Triple(Icons.Default.Payment, c.info, ElTagTone.INFO)
                else -> Triple(Icons.Default.Info, c.primary, ElTagTone.NEUTRAL)
            }

            NotificationRow(
                title = alert.title,
                body = alert.description,
                icon = icon,
                iconTint = tint,
                metaLabel = "Rappel",
                metaTone = if (alert.severity == "urgent" || alert.severity == "high") {
                    notificationPriorityTone(alert.severity)
                } else {
                    tone
                },
                onClick = {
                    // The same per-type navigation contract as the standalone
                    // action button (preserved verbatim from the pre-F-14 card).
                    when (alert.type) {
                        "overdue_debt" -> alert.entityId?.let { onNavigateToParent(it) } ?: onNavigateToDebtDashboard()
                        "pending_expense" -> alert.entityId?.let { onNavigateToExpenseDetail(it) } ?: onNavigateToFinancials()
                        "pending_check" -> onNavigateToFinancials()
                        "missing_roll_call" -> alert.entityId?.let { onNavigateToRollCall(it) }
                        else -> onNavigateToDebtDashboard()
                    }
                },
                trailing = {
                    if (!alert.phone.isNullOrBlank()) {
                        ElButton(
                            text = "Appeler",
                            onClick = { PhoneUtils.dial(context, alert.phone, toast) },
                            variant = ElButtonVariant.GHOST,
                            size = ElButtonSize.SMALL,
                            icon = Icons.Default.Call,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    ElButton(
                        text = alert.actionLabel ?: "Consulter",
                        onClick = {
                            when (alert.type) {
                                "overdue_debt" -> alert.entityId?.let { onNavigateToParent(it) } ?: onNavigateToDebtDashboard()
                                "pending_expense" -> alert.entityId?.let { onNavigateToExpenseDetail(it) } ?: onNavigateToFinancials()
                                "pending_check" -> onNavigateToFinancials()
                                "missing_roll_call" -> alert.entityId?.let { onNavigateToRollCall(it) }
                                else -> onNavigateToDebtDashboard()
                            }
                        },
                        variant = ElButtonVariant.OUTLINED,
                        size = ElButtonSize.SMALL,
                    )
                },
            )
        }
    }
}
