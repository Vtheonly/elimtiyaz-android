package com.example.ui.features.dashboard

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

/**
 * Actionable Alerts & Daily Workflow Stream.
 * Surfaces real urgent tasks from live data with 1-tap actions.
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

    error?.let { msg ->
        ElAlertBanner(
            title = "Alerte système",
            message = msg,
            severity = ElAlertSeverity.DANGER,
        )
    }

    if (alerts.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElSectionHeader(
            title = "Centre d'actions & Tâches immédiates",
            subtitle = "${alerts.size} action${if (alerts.size > 1) "s" else ""} requise${if (alerts.size > 1) "s" else ""}",
        )

        alerts.take(4).forEach { alert ->
            val (icon, tint, tone) = when (alert.type) {
                "overdue_debt" -> Triple(Icons.Default.Warning, ElTheme.colors.danger, ElTagTone.DANGER)
                "pending_expense" -> Triple(Icons.Default.ReceiptLong, ElTheme.colors.warning, ElTagTone.WARNING)
                "pending_check" -> Triple(Icons.Default.Payment, ElTheme.colors.info, ElTagTone.INFO)
                "missing_roll_call" -> Triple(Icons.Default.HowToReg, ElTheme.colors.warning, ElTagTone.WARNING)
                else -> Triple(Icons.Default.ErrorOutline, ElTheme.colors.primary, ElTagTone.NEUTRAL)
            }

            ElCard(
                modifier = Modifier.fillMaxWidth(),
                background = if (alert.severity == "urgent") ElTheme.colors.dangerContainer.copy(alpha = 0.35f) else null,
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
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = alert.title,
                                style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = ElTheme.colors.textPrimary,
                            )
                        }
                        if (alert.severity == "urgent") {
                            ElTag(text = "URGENT", tone = ElTagTone.DANGER)
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = alert.description,
                        style = ElTheme.typography.bodySmall,
                        color = ElTheme.colors.textSecondary,
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // If phone number is available, offer direct dial action
                        if (!alert.phone.isNullOrBlank()) {
                            ElButton(
                                text = "Appeler",
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${alert.phone}"))
                                    runCatching { context.startActivity(intent) }
                                },
                                variant = ElButtonVariant.OUTLINED,
                                size = ElButtonSize.SMALL,
                                icon = Icons.Default.Call,
                            )
                            Spacer(Modifier.size(8.dp))
                        }

                        // Primary workflow button
                        ElButton(
                            text = alert.actionLabel ?: "Traiter",
                            onClick = {
                                when (alert.type) {
                                    "overdue_debt" -> alert.entityId?.let { onNavigateToParent(it) } ?: onNavigateToDebtDashboard()
                                    "pending_expense" -> alert.entityId?.let { onNavigateToExpenseDetail(it) } ?: onNavigateToFinancials()
                                    "pending_check" -> onNavigateToFinancials()
                                    "missing_roll_call" -> alert.entityId?.let { onNavigateToRollCall(it) }
                                    else -> onNavigateToDebtDashboard()
                                }
                            },
                            variant = if (alert.severity == "urgent") ElButtonVariant.DANGER else ElButtonVariant.PRIMARY,
                            size = ElButtonSize.SMALL,
                        )
                    }
                }
            }
        }
    }
}