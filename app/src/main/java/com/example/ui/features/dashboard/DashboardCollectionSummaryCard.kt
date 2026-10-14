package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.execSharePct
import com.example.core.formatDzd
import com.example.domain.model.ExecPooledWaveItem
import com.example.domain.model.ExecutiveStatsSnapshot
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElExpandableCard
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.overlays.ElInfoTip
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.features.dashboard.analytics.PooledMetric
import com.example.ui.features.dashboard.analytics.WAVE_TITLES_FR
import com.example.ui.features.dashboard.analytics.waveDaysLate

@Composable
internal fun DashboardCollectionSummaryCard(
    executive: ExecutiveStatsSnapshot,
    onOpenAnalytics: () -> Unit,
) {
    val c = ElTheme.colors
    val nowEpochMs = System.currentTimeMillis()
    val pooledWaves = executive.pooledWaves

    val totalDue = pooledWaves.sumOf { it.dueTotal }
    val totalPaid = pooledWaves.sumOf { it.paidTotal }
    val totalPending = pooledWaves.sumOf { it.pendingTotal }
    val totalRemaining = pooledWaves.sumOf { it.remainingTotal }
    val globalPct = if (totalDue > 0L) execSharePct(totalPaid, totalDue) else 0
    val slots = listOf(1, 2, 3).map { wave -> pooledWaves.firstOrNull { it.wave == wave } }
    val hasAnyRow = pooledWaves.isNotEmpty()

    ElExpandableCard(
        title = "Recouvrement des Tranches",
        subtitle = "Taux global collecté",
        primaryFigure = "$globalPct%",
        primaryFigureColor = if (globalPct >= 80) c.success else c.primary,
        icon = Icons.Default.Assessment,
        headerBadge = {
            if (totalRemaining > 0L) {
                ElTag(
                    text = "${(totalRemaining / 100).formatDzd()} DA dû",
                    tone = ElTagTone.DANGER,
                )
            } else {
                ElTag(text = "Clôturé", tone = ElTagTone.SUCCESS)
            }
        },
        headerTrailing = { ElInfoTip(tip = "waveVelocity.card") },
    ) {
        if (!hasAnyRow) {
            Text(
                text = "Aucune tranche facturée sur la période sélectionnée.",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = ElTheme.spacing.lg),
            )
        } else {
            // Three primary indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
            ) {
                PooledMetric(
                    label = "ENCAISSÉ",
                    value = "${(totalPaid / 100).formatDzd()} DA",
                    valueColor = c.success,
                    modifier = Modifier.weight(1f),
                )
                PooledMetric(
                    label = "EN COURS",
                    value = "${(totalPending / 100).formatDzd()} DA",
                    valueColor = if (totalPending > 0L) c.info else c.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                PooledMetric(
                    label = "RESTE DÛ",
                    value = "${(totalRemaining / 100).formatDzd()} DA",
                    valueColor = if (totalRemaining > 0L) c.danger else c.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(ElTheme.spacing.md))

            // Thin progress bars per wave
            Column(verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm)) {
                slots.forEach { w -> CompactWaveLine(w, nowEpochMs) }
            }

            Spacer(Modifier.height(ElTheme.spacing.md))

            ElButton(
                text = "Analyse détaillée (T1 / T2 / T3, catégories, funnel)",
                onClick = onOpenAnalytics,
                variant = ElButtonVariant.OUTLINED,
                size = ElButtonSize.SMALL,
                icon = Icons.Default.Assessment,
                fullWidth = true,
            )
        }
    }
}

@Composable
private fun CompactWaveLine(w: ExecPooledWaveItem?, nowEpochMs: Long) {
    val c = ElTheme.colors
    val wave = w?.wave ?: return
    val installmentCount = w.installmentCount
    val settledCount = w.settledCount
    val remainingTotal = w.remainingTotal
    val collectedPct = w.collectedPct
    val anyUnsettledOverdue = w.anyUnsettledOverdue
    val title = WAVE_TITLES_FR[wave]?.first ?: ("Tranche $wave" to "Toutes catégories").first

    val isComplete = remainingTotal == 0L && installmentCount > 0
    val isOverdue = anyUnsettledOverdue
    val statusText = if (isComplete) "Clôturée" else if (isOverdue) "En retard" else "En cours"
    val statusTone = if (isComplete) ElTagTone.SUCCESS else if (isOverdue) ElTagTone.DANGER else ElTagTone.INFO
    val daysLate = waveDaysLate(w.dueDateMin, nowEpochMs)

    Column(verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isOverdue && !isComplete && daysLate > 0) {
                    Text(
                        text = "$daysLate j",
                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = c.danger,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = "$settledCount/$installmentCount",
                style = ElTheme.typography.labelSmall,
                color = c.textSecondary,
                maxLines = 1,
            )
            Spacer(Modifier.width(ElTheme.spacing.sm))
            Text(
                text = "$collectedPct%",
                style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = c.textPrimary,
            )
            Spacer(Modifier.width(ElTheme.spacing.xs))
            ElTag(text = statusText, tone = statusTone)
        }

        val meterColor = when {
            isComplete -> c.success
            isOverdue -> c.danger
            else -> ElChartPalette.primary
        }
        ElLinearProgress(
            progress = collectedPct.coerceIn(0, 100) / 100f,
            gradient = listOf(meterColor.copy(alpha = 0.65f), meterColor),
            trackColor = c.surfaceVariant,
            height = 4,
        )
    }
}