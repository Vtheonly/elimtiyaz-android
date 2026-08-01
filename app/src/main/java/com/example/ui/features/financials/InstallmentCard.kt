package com.example.ui.features.financials

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.PaymentStatus
import com.example.core.formatDzd
import com.example.domain.model.Installment
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElTag
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold

@Composable
internal fun InstallmentCard(
    installment: Installment,
    canMarkPaid: Boolean,
    onMarkPaid: () -> Unit,
) {
    val (statusColor, statusText) = when (installment.status) {
        PaymentStatus.PAID -> SuccessGreen to "Payée"
        PaymentStatus.OVERDUE -> DangerRed to "En retard"
        PaymentStatus.PENDING -> PrimaryBlue to "En attente"
        PaymentStatus.PARTIAL -> WarmGold to "Partielle"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to installment.status.name
    }
    ElCard(
        modifier = Modifier.fillMaxWidth(),
        accent = statusColor,
        compact = true,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    installment.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                ElTag(text = statusText, color = statusColor, selected = true)
            }
            Spacer(Modifier.height(8.dp))
            ElInfoRow(label = "Échéance", value = installment.dueDate)
            ElInfoRow(label = "Montant", value = "${(installment.amountDue / 100).formatDzd()} DZD")
            ElInfoRow(label = "Payé", value = "${(installment.amountPaid / 100).formatDzd()} DZD", valueColor = SuccessGreen)
            ElInfoRow(label = "Restant", value = "${(installment.remaining / 100).formatDzd()} DZD", valueColor = if (installment.remaining > 0) DangerRed else SuccessGreen)

            if (canMarkPaid) {
                Spacer(Modifier.height(8.dp))
                ElButton(
                    text = "Marquer comme payée",
                    onClick = onMarkPaid,
                    style = ElButtonStyle.Secondary,
                    fullWidth = true,
                )
            }
        }
    }
}
