package com.example.ui.features.financials

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.formatDzd
import com.example.domain.model.TrancheWaveItem
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.theme.ElTheme

/**
 * TrancheWaveCard — inventory #12 (PARITY-003).
 *
 * The native twin of the desktop's `installment-schedule-tab.tsx`
 * tranche-wave meters (T-248): the GLOBAL T1/T2/T3 collection-health
 * meters (pct = min(100, round(paid/due×100)); the first wave with a
 * remaining balance highlighted as the next target; success at ≥90) +
 * the totals row (Total dû / Payé / Reste / En retard). Values from the
 * engine's deriveTrancheWaves (repository contract).
 */
@Composable
internal fun TrancheWaveCard(
    waves: List<TrancheWaveItem>,
    overdueCount: Int,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    val allZero = waves.all { it.due == 0L }
    if (allZero) {
        ElCard(modifier = modifier.fillMaxWidth(), variant = com.example.ui.designsystem.components.card.ElCardVariant.OUTLINED) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Vagues de Tranches (T1 / T2 / T3)",
                    color = c.textPrimary,
                    style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "Aucune tranche T1 / T2 / T3 dans la sélection courante — sélectionnez une famille ou créez les échéances de cycle.",
                    color = c.textMuted,
                    style = ElTheme.typography.bodySmall,
                )
            }
        }
        return
    }

    val totalDue = waves.sumOf { it.due }
    val totalPaid = waves.sumOf { it.paid }
    val totalReste = (totalDue - totalPaid - waves.sumOf { it.pending }).coerceAtLeast(0L)

    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Vagues de Tranches (T1 / T2 / T3)",
                color = c.textPrimary,
                style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )

            waves.forEach { w ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = w.label,
                                color = if (w.isNextTarget) ElChartPalette.primary else c.textPrimary,
                                style = ElTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                ),
                            )
                            if (w.isNextTarget) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "cible",
                                    color = ElChartPalette.primary,
                                    style = ElTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    modifier = Modifier
                                        .background(
                                            ElChartPalette.primary.copy(alpha = 0.14f),
                                            RoundedCornerShape(50),
                                        )
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(
                            text = "${w.pct}%",
                            color = when {
                                w.isNextTarget -> ElChartPalette.primary
                                w.pct >= 90 -> ElChartPalette.success
                                else -> c.textSecondary
                            },
                            style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                        )
                    }
                    // The meter (progress bar; primary for the next target,
                    // success ≥90, muted otherwise — the desktop tones)
                    ElLinearProgress(
                        progress = w.pct / 100f,
                        gradient = when {
                            w.isNextTarget -> listOf(ElChartPalette.primary, ElChartPalette.primaryDeep)
                            w.pct >= 90 -> listOf(ElChartPalette.success, ElChartPalette.success)
                            else -> listOf(c.surfaceVariant, c.surfaceVariant)
                        },
                    )
                    Text(
                        text = w.hint + " — Encaissé : ${(w.paid / 100).formatDzd()} DA · Dû : ${(w.due / 100).formatDzd()} DA" +
                            if (w.pending > 0L) {
                                " · Dont en attente (chèque / virement) : ${(w.pending / 100).formatDzd()} DA"
                            } else "",
                        color = c.textMuted,
                        style = ElTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    )
                }
            }

            // Totals row (the desktop 4-cell row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrancheTotal("Total dû", (totalDue / 100).formatDzd() + " DA", c.textPrimary, Modifier.weight(1f))
                TrancheTotal("Payé", (totalPaid / 100).formatDzd() + " DA", ElChartPalette.success, Modifier.weight(1f))
                TrancheTotal("Reste", (totalReste / 100).formatDzd() + " DA", ElChartPalette.danger, Modifier.weight(1f))
                TrancheTotal("En retard", "$overdueCount", ElChartPalette.warning, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TrancheTotal(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = ElTheme.colors.textMuted,
            style = ElTheme.typography.labelSmall.copy(fontSize = 9.sp),
        )
        Text(
            text = value,
            color = color,
            style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
        )
    }
}
