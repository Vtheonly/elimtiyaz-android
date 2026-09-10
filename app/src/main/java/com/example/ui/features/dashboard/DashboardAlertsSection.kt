package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.DashboardOperationalAlert
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.util.PhoneUtils

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

    error?.let { msg ->
        ElAlertBanner(
            title = "Notification système",
            message = msg,
            severity = ElAlertSeverity.INFO,
        )
    }

    if (alerts.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElSectionHeader(
            title = "Rappels & Notifications de gestion",
            subtitle = "${alerts.size} notification${if (alerts.size > 1) "s" else ""}",
        )

        alerts.take(3).forEach { alert ->
            val (icon, tint, tone) = when (alert.type) {
                "overdue_debt" -> Triple(Icons.Default.Notifications, ElTheme.colors.primaryAccent, ElTagTone.WARNING)
                "pending_expense" -> Triple(Icons.Default.ReceiptLong, ElTheme.colors.primary, ElTagTone.INFO)
                "pending_check" -> Triple(Icons.Default.Payment, ElTheme.colors.info, ElTagTone.INFO)
                else -> Triple(Icons.Default.Info, ElTheme.colors.primary, ElTagTone.NEUTRAL)
            }

            ElCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = alert.title,
                                style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ElTheme.colors.textPrimary,
                            )
                        }
                        ElTag(text = "Rappel", tone = tone)
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = alert.description,
                        style = ElTheme.typography.bodySmall,
                        color = ElTheme.colors.textSecondary,
                    )

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!alert.phone.isNullOrBlank()) {
                            ElButton(
                                text = "Appeler",
                                onClick = { PhoneUtils.dial(context, alert.phone) },
                                variant = ElButtonVariant.GHOST,
                                size = ElButtonSize.SMALL,
                                icon = Icons.Default.Call,
                            )
                            Spacer(Modifier.size(8.dp))
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
                    }
                }
            }
        }
    }
}