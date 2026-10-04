package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.overlays.ElInfoTip
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.features.dashboard.analytics.PooledMetric
import com.example.ui.features.dashboard.analytics.WAVE_TITLES_FR
import com.example.ui.features.dashboard.analytics.waveDaysLate

/**
 * T-489 (UI-330) — the Overview tab's ONE financial block: the compact
 * collection summary.
 *
 * WHAT IT REPLACES on the first page (the ~10-block stack the owner called
 * "a long list of everything in the app"): the "Analytique des
 * Encaissements" mini-tile row, the full WaveVelocityCard hero (3 giant
 * PooledWaveMeters with 2×3 metric grids + reconciliation identity lines +
 * per-category chips) and the "Répartition par poste d'encaissement" list.
 *
 * WHAT IT RENDERS instead — the at-a-glance layer only:
 *  - the global collected % (the same presentation-side sums the hero
 *    computes over the SAME [ExecutiveStatsSnapshot.pooledWaves]);
 *  - the ENCAISSÉ / EN COURS / RESTE DÛ trio in the shared [PooledMetric]
 *    tile language (UI-327);
 *  - ONE thin meter line per wave T1/T2/T3 with its status tag and, when
 *    overdue, its days-late — every value the PooledWaveMeter shows, at
 *    1/5th of the height;
 *  - the "Analyse détaillée" action that switches to the Analytique tab,
 *    where the FULL hero (the "Hors Tranches" + "DÉTAIL PAR CATÉGORIE"
 *    sections included), the stat strip, the mixes, YoY, the aging
 *    composition, the funnel and the Pareto live unchanged.
 *
 * PARITY-002 discipline preserved: EVERY displayed number comes from the
 * repository-derived ExecutiveStatsSnapshot — no re-derivation, no
 * hard-coded fallback, the honest empty state included (§15.16).
 */
@Composable
internal fun DashboardCollectionSummaryCard(
    executive: ExecutiveStatsSnapshot,
    onOpenAnalytics: () -> Unit,
) {
    val c = ElTheme.colors
    val nowEpochMs = System.currentTimeMillis()
    val pooledWaves = executive.pooledWaves
    // The global badges — sums over the POOLED rows, byte-identical to the
    // WaveVelocityCard hero's own computation (the presentation-side
    // totals; the reconciliation identity stays per-row canonical).
    val totalDue = pooledWaves.sumOf { it.dueTotal }
    val totalPaid = pooledWaves.sumOf { it.paidTotal }
    val totalPending = pooledWaves.sumOf { it.pendingTotal }
    val totalRemaining = pooledWaves.sumOf { it.remainingTotal }
    val globalPct = if (totalDue > 0L) execSharePct(totalPaid, totalDue) else 0
    // The fixed T1..T3 slots — waves with no rows render the honest zero row.
    val slots = listOf(1, 2, 3).map { wave -> pooledWaves.firstOrNull { it.wave == wave } }
    val hasAnyRow = pooledWaves.isNotEmpty()

    ElCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md),
        ) {
            // ── Header: title + glossary tip + the analysis quick access ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.xs),
            ) {
                Text(
                    text = "Recouvrement des Tranches",
                    style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // T-458: the same T-447 explainability glossary the full
                // card mounts (presentation-only).
                ElInfoTip(tip = "waveVelocity.card")
            }

            if (!hasAnyRow) {
                // The honest empty state (§15.16) — byte-identical to the
                // hero's own empty wording.
                Text(
                    text = "Aucune tranche facturée sur la période sélectionnée.",
                    style = ElTheme.typography.bodySmall,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = ElTheme.spacing.lg),
                )
            } else {
                // ── The global verdict: big % + the trio in one glance ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.md),
                ) {
                    Column(modifier = Modifier.weight(1.1f)) {
                        Text(
                            text = "$globalPct%",
                            style = ElTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = c.textPrimary,
                        )
                        Text(
                            text = "collecté global",
                            style = ElTheme.typography.labelSmall,
                            color = c.textSecondary,
                        )
                    }
                    Column(modifier = Modifier.weight(2f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.xs)) {
                            Text(
                                text = "${(totalPaid / 100).formatDzd()} DA encaissés",
                                style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = c.success,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (totalPending > 0L) {
                                Text(
                                    text = "${(totalPending / 100).formatDzd()} en cours",
                                    style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = c.info,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            text = "${(totalRemaining / 100).formatDzd()} DA restant",
                            style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = c.danger,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // ── The trio in the shared PooledMetric tile language ──
                Row(horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm)) {
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

                // ── ONE thin meter line per wave — the compact staircase ──
                Column(verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm)) {
                    slots.forEach { w -> CompactWaveLine(w, nowEpochMs) }
                }
            }

            // ── The relocated-detail quick access (UI-330's "keep every
            // feature available" contract): the full analysis is ONE tap
            // away on the Analytique tab, which renders the same snapshot's
            // full hero + stat strip + mixes + funnel + Pareto unchanged. ──
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

/**
 * ONE wave's compact line: the title + status tag on the first row, the
 * big % + dossiers + days-late on the trailing, and the thin meter under
 * it — the SAME values the Analytique tab's PooledWaveMeter renders
 * (collectedPct / settledCount / installmentCount / anyUnsettledOverdue),
 * at compact height. A null slot renders the honest zero row (the desktop
 * emptyPooledWave presentation).
 */
@Composable
private fun CompactWaveLine(w: ExecPooledWaveItem?, nowEpochMs: Long) {
    val c = ElTheme.colors
    val wave = w?.wave ?: return
    val installmentCount = w?.installmentCount ?: 0
    val settledCount = w?.settledCount ?: 0
    val remainingTotal = w?.remainingTotal ?: 0L
    val collectedPct = w?.collectedPct ?: 0
    val anyUnsettledOverdue = w?.anyUnsettledOverdue ?: false
    val title = WAVE_TITLES_FR[wave]?.first ?: ("Tranche $wave" to "Toutes catégories").first

    // T-427 (DATA-048): a wave is "Clôturée" only when NOTHING remains to
    // collect (remainingTotal === 0, the canonical INV-4 basis) — the SAME
    // status derivation as the full PooledWaveMeter.
    val isComplete = remainingTotal == 0L && installmentCount > 0
    val isOverdue = anyUnsettledOverdue
    val statusText = if (isComplete) "Clôturée" else if (isOverdue) "En retard" else "En cours"
    val statusTone = if (isComplete) ElTagTone.SUCCESS else if (isOverdue) ElTagTone.DANGER else ElTagTone.INFO
    val daysLate = waveDaysLate(w?.dueDateMin, nowEpochMs)

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
        // The thin meter — the same DS ElLinearProgress + the same
        // status→color resolution as the full meter (UI-327/T-462), 4dp.
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
