package com.example.ui.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.formatDzd
import com.example.domain.model.AppNotification
import com.example.domain.model.Payment
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.theme.ElTheme

/**
 * Section (8) — Live Activity Feed & Operational Notifications.
 * Displays recent real counter payments and recent notifications.
 */
@Composable
internal fun DashboardNotificationsSection(
    notifications: List<AppNotification>,
    recentPayments: List<Payment>,
    onNavigateToFinancials: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElSectionHeader(
            title = "Dernières Opérations & Journal",
            subtitle = "Encaissements récents et alertes opérationnelles",
            trailing = {
                ElButton(
                    text = "Finances",
                    onClick = onNavigateToFinancials,
                    variant = ElButtonVariant.GHOST,
                    size = ElButtonSize.SMALL,
                )
            },
        )

        // ── Recent Cash-Desk Payments ────────────────────────────────────────
        if (recentPayments.isNotEmpty()) {
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Encaissements récents au guichet",
                        style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))

                    recentPayments.take(3).forEach { payment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(ElTheme.colors.success.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = ElTheme.colors.success,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(Modifier.size(8.dp))
                                Column {
                                    Text(
                                        text = payment.receiptNumber,
                                        style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = ElTheme.colors.textPrimary,
                                    )
                                    Text(
                                        text = "${payment.method.code.uppercase()} • ${payment.collectedAt.take(16).replace("T", " ")}",
                                        style = ElTheme.typography.labelSmall,
                                        color = ElTheme.colors.textSecondary,
                                    )
                                }
                            }

                            Text(
                                text = "+${(payment.amount / 100).formatDzd()} DA",
                                style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = ElTheme.colors.success,
                            )
                        }
                    }
                }
            }
        }

        // ── Notifications List ───────────────────────────────────────────────
        val unreadNotifications = notifications.filter { it.readAt == null }.take(3)
        if (unreadNotifications.isNotEmpty()) {
            unreadNotifications.forEach { notif ->
                val severity = when (notif.type) {
                    "attendance_alert" -> ElAlertSeverity.DANGER
                    "payment_overdue" -> ElAlertSeverity.WARNING
                    "expense_pending" -> ElAlertSeverity.WARNING
                    else -> ElAlertSeverity.INFO
                }
                val (bg, fg) = when (severity) {
                    ElAlertSeverity.DANGER -> ElTheme.colors.dangerContainer to ElTheme.colors.danger
                    ElAlertSeverity.WARNING -> ElTheme.colors.warningContainer to ElTheme.colors.warning
                    ElAlertSeverity.SUCCESS -> ElTheme.colors.successContainer to ElTheme.colors.success
                    ElAlertSeverity.INFO -> ElTheme.colors.infoContainer to ElTheme.colors.info
                }

                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(bg)
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = when (severity) {
                                        ElAlertSeverity.DANGER -> Icons.Default.Warning
                                        ElAlertSeverity.WARNING -> Icons.Default.Warning
                                        ElAlertSeverity.SUCCESS -> Icons.Default.CheckCircle
                                        ElAlertSeverity.INFO -> Icons.Default.Assessment
                                    },
                                    contentDescription = null,
                                    tint = fg,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = notif.title,
                                color = ElTheme.colors.textPrimary,
                                style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
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
}