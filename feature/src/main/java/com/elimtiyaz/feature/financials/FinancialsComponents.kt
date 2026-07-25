package com.elimtiyaz.feature.financials

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elimtiyaz.core.common.ExpenseStatus
import com.elimtiyaz.core.common.PaymentStatus
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone

/**
 * Maps a payment lifecycle status (master plan §20.04) to a [StatusTone] for
 * the [StatusChip]. Paid→Success, Partial/Pending→Warning, Overdue/Refunded/
 * Cancelled→Danger (or Neutral for Refunded/Cancelled — see below).
 */
fun paymentTone(status: String): StatusTone = when (PaymentStatus.from(status)) {
    PaymentStatus.Paid     -> StatusTone.Success
    PaymentStatus.Partial  -> StatusTone.Warning
    PaymentStatus.Pending  -> StatusTone.Warning
    PaymentStatus.Overdue  -> StatusTone.Danger
    PaymentStatus.Refunded -> StatusTone.Neutral
    PaymentStatus.Cancelled-> StatusTone.Neutral
    null                   -> StatusTone.Neutral
}

/** Installments use the same lifecycle vocabulary as payments (minus Refunded/Cancelled). */
fun installmentTone(status: String): StatusTone = when (status) {
    "paid"     -> StatusTone.Success
    "partial"  -> StatusTone.Warning
    "pending"  -> StatusTone.Info
    "overdue"  -> StatusTone.Danger
    else       -> StatusTone.Neutral
}

/**
 * Maps the expense two-tier workflow status (master plan §08.02) to a [StatusTone].
 * Submitted/Draft/Disbursed are pending states → Warning/Info; Approved is a
 * checkpoint → Info; Settled is the terminal success → Success; Rejected/Anomaly → Danger.
 */
fun expenseTone(status: String): StatusTone = when (ExpenseStatus.from(status)) {
    ExpenseStatus.Draft     -> StatusTone.Neutral
    ExpenseStatus.Submitted -> StatusTone.Warning
    ExpenseStatus.Approved  -> StatusTone.Info
    ExpenseStatus.Rejected  -> StatusTone.Danger
    ExpenseStatus.Disbursed -> StatusTone.Warning
    ExpenseStatus.Settled   -> StatusTone.Success
    ExpenseStatus.Anomaly   -> StatusTone.Danger
    null                    -> StatusTone.Neutral
}

/** French label for an installment status code. */
fun installmentLabel(status: String): String = when (status) {
    "paid"     -> "Payée"
    "partial"  -> "Partielle"
    "pending"  -> "À échéance"
    "overdue"  -> "En retard"
    else       -> status
}

/**
 * A KPI card for the Financials hub — large title, large value, optional
 * tinted accent. Used for the 4 hub metrics (today's collected, monthly
 * revenue, outstanding debt, pending expenses).
 */
@Composable
fun KpiCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: Color = ElimtiyazColors.PrimaryBlue,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(ElimtiyazSpacing.x4),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tone.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) {
                        androidx.compose.material3.Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tone,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** A small horizontal bar used by the debt-dashboard aging-buckets chart. */
@Composable
fun AgingBucketBar(
    label: String,
    amount: Double,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = com.elimtiyaz.core.common.Formatters.currency(amount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(ElimtiyazSpacing.x1))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
        }
    }
}

/** Section title — a small uppercase label between groups of content. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = ElimtiyazSpacing.x2),
    )
}
