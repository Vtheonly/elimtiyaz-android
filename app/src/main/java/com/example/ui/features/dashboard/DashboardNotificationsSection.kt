package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppNotification
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.theme.ElTheme

/**
 * Section (f) — operational alerts derived from the last 5 unread
 * [AppNotification]s.
 *
 * Each notification is mapped to a severity by `type` and rendered as a small
 * card with an icon, title and body. When no unread notifications are present
 * an [ElEmptyState] placeholder is shown instead.
 */
@Composable
internal fun DashboardNotificationsSection(
    notifications: List<AppNotification>,
) {
    ElSectionHeader(title = "Alertes opérationnelles")
    val unreadNotifications = notifications.filter { it.readAt == null }.take(5)
    if (unreadNotifications.isEmpty()) {
        ElEmptyState(
            icon = Icons.Default.HowToReg,
            title = "Aucune alerte",
            subtitle = "Tout est sous contrôle",
        )
    } else {
        unreadNotifications.forEach { notif ->
            val severity = when (notif.type) {
                "attendance_alert"  -> ElAlertSeverity.DANGER
                "payment_overdue"   -> ElAlertSeverity.WARNING
                "expense_pending"   -> ElAlertSeverity.WARNING
                else                -> ElAlertSeverity.INFO
            }
            val (bg, fg) = when (severity) {
                ElAlertSeverity.DANGER  -> ElTheme.colors.dangerContainer  to ElTheme.colors.danger
                ElAlertSeverity.WARNING  -> ElTheme.colors.warningContainer to ElTheme.colors.warning
                ElAlertSeverity.SUCCESS  -> ElTheme.colors.successContainer to ElTheme.colors.success
                ElAlertSeverity.INFO     -> ElTheme.colors.infoContainer     to ElTheme.colors.info
            }
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .padding(end = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = when (severity) {
                                    ElAlertSeverity.DANGER  -> Icons.Default.Warning
                                    ElAlertSeverity.WARNING  -> Icons.Default.Warning
                                    ElAlertSeverity.SUCCESS  -> Icons.Default.HowToReg
                                    ElAlertSeverity.INFO     -> Icons.Default.Assessment
                                },
                                contentDescription = null,
                                tint = fg,
                            )
                        }
                        Text(
                            text = notif.title,
                            color = ElTheme.colors.textPrimary,
                            style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = notif.body,
                        color = ElTheme.colors.textSecondary,
                        style = ElTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
