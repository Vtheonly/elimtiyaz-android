package com.example.ui.features.financials

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.PaymentStatus
import com.example.core.formatDzd
import com.example.domain.model.Installment
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElInfoRow
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.theme.ElTheme

@Composable
internal fun InstallmentCard(
    installment: Installment,
    canMarkPaid: Boolean,
    onMarkPaid: () -> Unit,
) {
    val c = ElTheme.colors
    // T-460 pass C: the status pair migrated to the DS tone vocabulary
    // (was the legacy color constants + selected-ElTag).
    val (statusTone, statusText, statusColor) = when (installment.status) {
        PaymentStatus.PAID -> Triple(ElTagTone.SUCCESS, "Payée", c.success)
        PaymentStatus.OVERDUE -> Triple(ElTagTone.DANGER, "En retard", c.danger)
        PaymentStatus.PENDING -> Triple(ElTagTone.INFO, "En attente", c.primary)
        PaymentStatus.PARTIAL -> Triple(ElTagTone.WARNING, "Partielle", c.warning)
        else -> Triple(ElTagTone.NEUTRAL, installment.status.name, c.textSecondary)
    }
    ElCard(
        modifier = Modifier.fillMaxWidth(),
        size = ElCardSize.STANDARD,
        border = BorderStroke(ElTheme.borders.thin, statusColor.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    installment.label,
                    style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                ElTag(text = statusText, tone = statusTone, size = ElTagSize.MD)
            }
            Spacer(Modifier.height(ElTheme.spacing.sm))
            ElInfoRow(label = "Échéance", value = installment.dueDate, valueTint = c.textPrimary)
            ElInfoRow(label = "Montant", value = "${(installment.amountDue / 100).formatDzd()} DZD", valueTint = c.textPrimary)
            ElInfoRow(label = "Payé", value = "${(installment.amountPaid / 100).formatDzd()} DZD", valueTint = c.success)
            ElInfoRow(label = "Restant", value = "${(installment.remaining / 100).formatDzd()} DZD", valueTint = if (installment.remaining > 0) c.danger else c.success)

            if (canMarkPaid) {
                Spacer(Modifier.height(ElTheme.spacing.sm))
                ElButton(
                    text = "Marquer comme payée",
                    onClick = onMarkPaid,
                    variant = ElButtonVariant.SECONDARY,
                    fullWidth = true,
                )
            }
        }
    }
}
