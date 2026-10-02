package com.example.ui.features.dashboard.analytics

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.formatDzd
import com.example.domain.model.ExecCallListEntryItem
import com.example.domain.model.ExecConcentrationSnapshot
import com.example.domain.model.ExecDynamicsSnapshot
import com.example.domain.model.ExecErosionItem
import com.example.domain.model.ExecNonWaveItem
import com.example.domain.model.ExecPooledWaveItem
import com.example.domain.model.ExecRiskProfileItem
import com.example.domain.model.ExecRiskSummaryItem
import com.example.domain.model.ExecServiceItem
import com.example.domain.model.ExecTriageSnapshot
import com.example.domain.model.ExecTransportSnapshot
import com.example.domain.model.ExecWaveItem
import com.example.domain.model.ExecutiveStatsSnapshot
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.overlays.ElInfoTip
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.theme.ElTheme

/**
 * ExecutiveCards — the Kotlin/Compose mirror of the desktop's
 * executive-cards.tsx (T-340 / 61st session — STATS-400, mirroring the
 * desktop T-339 "Pilotage Exécutif" view).
 *
 * MIRRORED FROM (ADR-002):
 *   `elimtiyaz-desktop/src/features/dashboard/components/analytics/executive-cards.tsx`
 *   (source commit 66e69b7, T-339 / 61st session)
 *
 * DISCIPLINE (§15.16): every card is a THIN renderer over the
 * [ExecutiveStatsSnapshot] computed by `core/ExecutiveStatistics.kt` at the
 * repository level — ZERO inline math, ZERO hardcoded reference numbers,
 * honest empty states. The owner's mandate: identical statistics on BOTH
 * platforms from ONE canonical derivation.
 *
 * VANITY-PURGE COMPANION: these cards REPLACE the removed payment-amount
 * histogram, weekday collection heatmap, smooth 12-month revenue spline,
 * and fake class-capacity gauges.
 */

// ============================================================================
// Wave velocity — the revenue hero (the staircase)
// ============================================================================

/**
 * T-454 (PARITY-007, 127th session): the wave titles follow the desktop's
 * T-447 WAVE_TITLES (§15.65a) — the subtitles state the ALL-CATEGORIES basis
 * and NEVER fold the registration fee into T1 (FI is tranche 0, a non-wave
 * row with its own « Hors Tranches » section). The old
 * "T1 · Inscription + 1er versement (Sept)" subtitle contradicted the
 * billing model (the desktop's own STATS-401 finding).
 */
private val WAVE_TITLES_FR: Map<Int, Pair<String, String>> = mapOf(
    1 to ("Tranche 1 (T1)" to "Toutes catégories — 1er versement (Sept)"),
    2 to ("Tranche 2 (T2)" to "Toutes catégories — mi-parcours (Déc)"),
    3 to ("Tranche 3 (T3)" to "Toutes catégories — clôture (Mars)"),
)

// T-454: phaseLabelFr + phaseTone retired with the old WaveMeter (the pooled
// card uses the T-427 Clôturée/En retard/En cours badge derivation instead).

/**
 * The desktop's `formatDueDateRange` mirror (core/format/date.ts —
 * T-435/T-439): "dd MMM yyyy", the "min → max" range when the rows drifted
 * off the official schedule, null when no row carries a parseable date.
 * UTC formatting so a UTC-negative machine never shows the day before.
 */
private fun formatDueDateRangeFr(dueDateMinIso: String?, dueDateMaxIso: String?): String? {
    if (dueDateMinIso == null) return null
    val min = try {
        java.time.Instant.parse(dueDateMinIso)
    } catch (_: Exception) {
        return null
    }
    val fmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.FRENCH)
        .withZone(java.time.ZoneOffset.UTC)
    val minLabel = fmt.format(min)
    if (dueDateMaxIso == null || dueDateMaxIso == dueDateMinIso) return minLabel
    val max = try {
        java.time.Instant.parse(dueDateMaxIso)
    } catch (_: Exception) {
        return minLabel
    }
    return "$minLabel → ${fmt.format(max)}"
}

/** The desktop's daysBetweenFloor over the pooled rows' ISO dates. */
private fun waveDaysLate(dueDateIso: String?, nowEpochMs: Long): Long =
    if (dueDateIso == null) 0L else com.example.core.execDaysBetweenFloor(dueDateIso, nowEpochMs)

/** The non-wave group labels (the desktop's label derivation). */
private fun nonWaveKindLabelFr(kind: com.example.domain.model.ExecNonWaveKindItem, categoryLabel: String): String = when (kind) {
    com.example.domain.model.ExecNonWaveKindItem.FI -> "Frais d'inscription (FI)"
    com.example.domain.model.ExecNonWaveKindItem.UNNUMBERED -> "$categoryLabel — hors tranche"
    com.example.domain.model.ExecNonWaveKindItem.OUT_OF_RANGE -> "$categoryLabel — hors bornes"
}

/**
 * WaveVelocityCard — THE revenue hero (the T-447 pooled form).
 *
 * The native twin of the desktop's `executive-cards.tsx` WaveVelocityCard:
 * the MAIN T1/T2/T3 analysis is the canonical POOLED all-categories grid
 * (every billing category per wave — the exact-dinar parity object the
 * Finance Tranches strip consumes; one derivation, two presentations),
 * each wave carrying the 2×3 metric grid (Facturé / Encaissé / **En cours**
 * / Reste dû / Familles / Catégories), the reconciliation identity
 * (Total dû = Encaissé + En cours + Reste dû [+ couverts au-delà]), the
 * per-category chips, the T-434/T-435 échéance range + days-late, and the
 * T-427 status badge (Clôturée / En retard / En cours).
 *
 * The FULL variant adds the « Hors Tranches » section (FI + unnumbered +
 * out-of-range — the commitments the wave model excludes BY DESIGN, visible,
 * never silently dropped) and the per-category detail grid; the HERO variant
 * (the dashboard overview) renders the pooled grid alone.
 *
 * T-454 (PARITY-007): values come EXCLUSIVELY from the snapshot's
 * `pooledWaves` / `nonWaveSummary` (computed once at the repository level by
 * `deriveExecPooledTrancheWaves` / `deriveExecNonWaveSummary`) — ZERO inline
 * math, ZERO re-derivation (§15.16). The previous tuition/others split is
 * retired (the desktop removed its own in T-447).
 */
@Composable
fun WaveVelocityCard(
    waves: List<ExecWaveItem>,
    pooledWaves: List<ExecPooledWaveItem>,
    nonWave: List<ExecNonWaveItem>,
    modifier: Modifier = Modifier,
    variant: String = "full",
) {
    val c = ElTheme.colors
    val nowEpochMs = System.currentTimeMillis()
    // The global badges — sums over the POOLED rows (the presentation-side
    // totals; the reconciliation identity is per-row canonical).
    val totalDue = pooledWaves.sumOf { it.dueTotal }
    val totalPaid = pooledWaves.sumOf { it.paidTotal }
    val totalPending = pooledWaves.sumOf { it.pendingTotal }
    val totalRemaining = pooledWaves.sumOf { it.remainingTotal }
    val globalPct = if (totalDue > 0L) com.example.core.execSharePct(totalPaid, totalDue) else 0
    // The fixed T1..T3 slots — waves with no rows render the honest zero state.
    val slots = listOf(1, 2, 3).map { wave -> pooledWaves.firstOrNull { it.wave == wave } }
    val hasAnyRow = pooledWaves.isNotEmpty() || nonWave.isNotEmpty()

    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ── Header ──
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Vélocité de Recouvrement par Vague Saisonnière",
                    style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // T-458: the T-447 explainability glossary (presentation-only).
                ElInfoTip(tip = "waveVelocity.card")
            }
            Text(
                text = "Analyse T1/T2/T3 toutes catégories (Scolarité, Transport, FI, services) — parité exacte avec l'onglet Finances → Tranches",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            if (hasAnyRow) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "$globalPct% collecté global",
                            color = ElChartPalette.primary,
                            style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        ElInfoTip(tip = "waveVelocity.collectedPct", size = 11)
                    }
                    if (totalPending > 0L) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${(totalPending / 100).formatDzd()} DA en cours",
                                color = ElChartPalette.info,
                                style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            )
                            ElInfoTip(tip = "waveVelocity.pending", size = 11)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${(totalRemaining / 100).formatDzd()} DA restant",
                            color = ElChartPalette.danger,
                            style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        ElInfoTip(tip = "waveVelocity.remaining", size = 11)
                    }
                }
            }

            if (!hasAnyRow) {
                // The honest empty state (§15.16).
                Text(
                    text = "Aucune tranche facturée sur la période sélectionnée.",
                    style = ElTheme.typography.bodySmall,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                // ── The MAIN pooled T1/T2/T3 grid ──
                slots.forEach { slot -> PooledWaveMeter(w = slot, nowEpochMs = nowEpochMs) }

                // ── T-447: the « Hors Tranches » section (FULL variant only) ──
                if (nonWave.isNotEmpty() && variant == "full") {
                    Spacer(Modifier.height(2.dp))
                    HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
                    Text(
                        text = "HORS TRANCHES — INSCRIPTION & ENGAGEMENTS NON-TRANCHES",
                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textSecondary,
                    )
                    nonWave.forEach { g ->
                        val range = formatDueDateRangeFr(g.dueDateMin, g.dueDateMax)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${nonWaveKindLabelFr(g.kind, g.categoryLabel)} · ${g.installmentCount} engagement${if (g.installmentCount > 1) "s" else ""}",
                                    style = ElTheme.typography.bodySmall,
                                    color = c.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${(g.remainingTotal / 100).formatDzd()} DA restants" +
                                        (range?.let { " · échéance $it" } ?: "") +
                                        (if (g.remainingTotal == 0L && g.installmentCount > 0) " · soldé" else ""),
                                    style = ElTheme.typography.labelSmall,
                                    color = if (g.remainingTotal > 0L) ElChartPalette.danger else ElChartPalette.success,
                                )
                            }
                            val pct = if (g.dueTotal > 0L) com.example.core.execSharePct(g.paidTotal, g.dueTotal) else 0
                            Text(
                                text = "$pct%",
                                style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = c.textPrimary,
                            )
                        }
                    }
                }

                // ── T-447: the per-category detail grid (FULL variant only) ──
                if (waves.isNotEmpty() && variant == "full") {
                    Spacer(Modifier.height(2.dp))
                    HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
                    Text(
                        text = "DÉTAIL PAR CATÉGORIE DE FACTURATION",
                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textSecondary,
                    )
                    waves.forEach { w ->
                        val auxDue = formatDueDateRangeFr(w.dueDate, w.dueDateMax)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${w.categoryLabel} · T${w.wave}",
                                    style = ElTheme.typography.bodySmall,
                                    color = c.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${(w.remainingTotal / 100).formatDzd()} DA restants" +
                                        (auxDue?.let { " · échéance $it" } ?: "") +
                                        (if (w.phase == "overdue" && w.remainingTotal > 0L) " · en retard" else ""),
                                    style = ElTheme.typography.labelSmall,
                                    color = if (w.phase == "overdue" && w.remainingTotal > 0L) ElChartPalette.danger else c.textSecondary,
                                )
                            }
                            Text(
                                text = "${w.collectedPct}%",
                                style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = c.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One pooled T1/T2/T3 meter — the desktop's pooled-card twin: the status
 * badge, the échéance range + days-late, the big rate + dossiers, the
 * meter, the 2×3 metric grid, the reconciliation identity, and the
 * per-category chips. Pure renderer over [ExecPooledWaveItem] (null = the
 * honest zero-row slot).
 */
@Composable
private fun PooledWaveMeter(w: ExecPooledWaveItem?, nowEpochMs: Long) {
    val c = ElTheme.colors
    val wave = w?.wave ?: return
    // The zero-row slot state (the desktop emptyPooledWave presentation).
    val dueTotal = w?.dueTotal ?: 0L
    val paidTotal = w?.paidTotal ?: 0L
    val pendingTotal = w?.pendingTotal ?: 0L
    val remainingTotal = w?.remainingTotal ?: 0L
    val installmentCount = w?.installmentCount ?: 0
    val settledCount = w?.settledCount ?: 0
    val familyCount = w?.familyCount ?: 0
    val debtorFamilyCount = w?.debtorFamilyCount ?: 0
    val overdueDebtorFamilyCount = w?.overdueDebtorFamilyCount ?: 0
    val overCoverageTotal = w?.overCoverageTotal ?: 0L
    val collectedPct = w?.collectedPct ?: 0
    val anyUnsettledOverdue = w?.anyUnsettledOverdue ?: false
    val anyUnsettledFuture = w?.anyUnsettledFuture ?: false
    val title = WAVE_TITLES_FR[wave] ?: ("Tranche $wave" to "Toutes catégories")

    // T-427 (DATA-048): a wave is "Clôturée" only when NOTHING remains to
    // collect (remainingTotal === 0, the canonical INV-4 basis).
    val isComplete = remainingTotal == 0L && installmentCount > 0
    val isOverdue = anyUnsettledOverdue
    val statusText = if (isComplete) "Clôturée" else if (isOverdue) "En retard" else "En cours"
    val statusTone = if (isComplete) ElTagTone.SUCCESS else if (isOverdue) ElTagTone.DANGER else ElTagTone.INFO

    // T-434/T-435: the wave's échéance is ON the card (the verdict's visible
    // cause) — the DERIVED range, the days-late/days-remaining suffix.
    val dueRangeLabel = formatDueDateRangeFr(w?.dueDateMin, w?.dueDateMax)
    val daysLate = waveDaysLate(w?.dueDateMin, nowEpochMs)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.surfaceVariant.copy(alpha = 0.35f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Header: title + status badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.first,
                    style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                )
                Text(
                    text = title.second,
                    style = ElTheme.textStyles.badge.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textSecondary,
                )
                if (dueRangeLabel != null) {
                    Text(
                        text = "Échéance : $dueRangeLabel" +
                            if (!isComplete) {
                                when {
                                    isOverdue -> " — $daysLate j de retard"
                                    daysLate < 0 -> " — dans ${-daysLate} j"
                                    else -> ""
                                }
                            } else "",
                        style = ElTheme.textStyles.badge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isOverdue && !isComplete) ElChartPalette.danger else c.textSecondary,
                    )
                }
            }
            ElTag(text = statusText, tone = statusTone)
        }

        // The big rate + the dossiers count + the meter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "$collectedPct%",
                style = ElTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Black),
                color = c.textPrimary,
            )
            Text(
                text = "$settledCount/$installmentCount dossiers",
                style = ElTheme.typography.labelSmall,
                color = c.textSecondary,
            )
        }
        val meterColor = when {
            isComplete -> ElChartPalette.success
            isOverdue -> ElChartPalette.danger
            else -> ElChartPalette.primary
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(c.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(collectedPct.coerceIn(0, 100) / 100f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(meterColor),
            )
        }

        // The 2×3 metric grid — the mandate's Total Due = Paid + Pending +
        // Remaining identity verifiable at a glance.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PooledMetric("FACTURÉ", "${(dueTotal / 100).formatDzd()} DA", c.textPrimary, Modifier.weight(1f))
            PooledMetric("ENCAISSÉ", "${(paidTotal / 100).formatDzd()} DA", ElChartPalette.success, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PooledMetric(
                "EN COURS",
                "${(pendingTotal / 100).formatDzd()} DA",
                if (pendingTotal > 0L) ElChartPalette.info else c.textSecondary,
                Modifier.weight(1f),
            )
            PooledMetric(
                "RESTE DÛ",
                "${(remainingTotal / 100).formatDzd()} DA",
                if (remainingTotal > 0L) ElChartPalette.danger else c.textSecondary,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // T-427 (DATA-048b): the sub-label is PHASE-DRIVEN — an overdue
            // wave counts its actually-late families; a future wave's owing
            // families are "à échoir" / "non soldées".
            val familiesLabel = if (isOverdue) "FAMILLES EN RETARD" else if (anyUnsettledFuture) "FAMILLES À ÉCHOIR" else "FAMILLES NON SOLDÉES"
            val familiesValue = if (isOverdue) overdueDebtorFamilyCount else debtorFamilyCount
            val familiesColor = if (familiesValue > 0) {
                if (isOverdue) ElChartPalette.danger else ElChartPalette.warning
            } else c.textSecondary
            PooledMetric(familiesLabel, "$familiesValue / $familyCount", familiesColor, Modifier.weight(1f))
            PooledMetric("CATÉGORIES", "${w?.perCategory?.size ?: 0}", c.textPrimary, Modifier.weight(1f))
        }

        // The reconciliation line (T-447): Total dû = Encaissé + En cours +
        // Reste dû (+ couverts au-delà when funds exceed the due).
        Text(
            text = "Total dû ${(dueTotal / 100).formatDzd()} DA = Encaissé ${(paidTotal / 100).formatDzd()} DA + " +
                "En cours ${(pendingTotal / 100).formatDzd()} DA + Reste dû ${(remainingTotal / 100).formatDzd()} DA" +
                if (overCoverageTotal > 0L) " (+ ${(overCoverageTotal / 100).formatDzd()} DA couverts au-delà)" else "",
            style = ElTheme.textStyles.chartMicro,
            color = c.textMuted,
        )

        // The per-category breakdown chips (the audit trail that no revenue
        // category is silently excluded from the main analysis).
        val perCategory = w?.perCategory ?: emptyList()
        if (perCategory.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                perCategory.forEach { cat ->
                    val catPct = if (cat.dueTotal > 0L) com.example.core.execSharePct(cat.paidTotal, cat.dueTotal) else 0
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = cat.categoryLabel,
                            style = ElTheme.textStyles.chartMicro,
                            color = c.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "$catPct%" + if (cat.remainingTotal > 0L) " · ${(cat.remainingTotal / 100).formatDzd()}" else "",
                            style = ElTheme.textStyles.chartMicro.copy(fontWeight = FontWeight.SemiBold),
                            color = if (cat.remainingTotal > 0L) ElChartPalette.danger else c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PooledMetric(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = ElTheme.textStyles.chartMicro,
            color = ElTheme.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ExecStatRow(label: String, value: String, valueColor: Color = ElTheme.colors.textSecondary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = ElTheme.typography.labelSmall, color = ElTheme.colors.textMuted)
        Text(text = value, style = ElTheme.typography.labelSmall, color = valueColor)
    }
}

// ============================================================================
// Discount erosion
// ============================================================================

/**
 * DiscountErosionCard — the margin the school gave away to fill seats:
 * raw negotiated remises vs the reconstructed sticker total, with the
 * reconciliation-honest netting (the imported devis is already net).
 */
@Composable
fun DiscountErosionCard(
    erosion: ExecErosionItem,
    modifier: Modifier = Modifier,
) {
    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = "Érosion des Remises",
                style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = ElTheme.colors.textPrimary,
            )
            Text(
                text = if (erosion.remiseCount > 0) {
                    "${erosion.remiseCount} remises · ${(erosion.netRemiseTotal / 100).formatDzd()} DA nets consentis"
                } else {
                    "Remises négociées vs tarif affiché — la marge offerte pour remplir les sièges"
                },
                style = ElTheme.typography.bodySmall,
                color = ElTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))

            if (erosion.remiseCount == 0) {
                Text(
                    text = "Aucune remise enregistrée — tarification affichée intégralement appliquée.",
                    style = ElTheme.typography.bodySmall,
                    color = ElTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${erosion.erosionPct}%",
                        style = ElTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                        color = ElTheme.colors.danger,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "du tarif affiché\n(brut reconstruit)",
                        style = ElTheme.typography.labelSmall,
                        color = ElTheme.colors.textSecondary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ExecStatRow("Remises brutes", "${(erosion.remiseTotal / 100).formatDzd()} DA")
                if (erosion.cancelCount > 0) {
                    ExecStatRow("Annulations (double remise)", "−${(erosion.cancelTotal / 100).formatDzd()} DA")
                }
                ExecStatRow("Remises nettes", "${(erosion.netRemiseTotal / 100).formatDzd()} DA")
                ExecStatRow("Charges brutes", "${(erosion.grossCharges / 100).formatDzd()} DA")
                ExecStatRow("Tarif affiché (reconstruit)", "${(erosion.stickerTotal / 100).formatDzd()} DA")
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = ElTheme.colors.outlineVariant, thickness = 1.dp)
                Spacer(Modifier.height(6.dp))
                ExecStatRow("Remise moyenne", "${(erosion.averageRemise / 100).formatDzd()} DA")
                ExecStatRow(
                    "Amplitude",
                    "${if (erosion.minRemise > 0) (erosion.minRemise / 100).formatDzd() else "—"} – ${(erosion.maxRemise / 100).formatDzd()} DA",
                )
                ExecStatRow("Familles concernées", "${erosion.remiseFamilyCount}")
            }
        }
    }
}

// ============================================================================
// Debt triage — who to call today
// ============================================================================

// T-460 pass H (issue #3 F-20): the triage status colors now resolve from the
// canonical chart palette (the pinned T-289 rule — chart/data colors come from
// ElChartPalette, never ad-hoc hex maps).
private val TRIAGE_COLORS = mapOf(
    "not_due" to ElChartPalette.slate,
    "current" to ElChartPalette.success,
    "reminder" to ElChartPalette.warning,
    "chronic" to ElChartPalette.danger,
)

/**
 * DebtTriageCard — the 4-tier aging census with the immediate call list:
 * not-yet-due / <15j / 15–45j / >45j (chronic). Raw debt NEVER renders
 * without this aging context (the owner's kill-list rule).
 */
@Composable
fun DebtTriageCard(
    triage: ExecTriageSnapshot,
    modifier: Modifier = Modifier,
) {
    val chronic = triage.buckets.firstOrNull { it.bucket == "chronic" }
    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Triage des Créances — Qui Appeler Aujourd'hui",
                    style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ElTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // T-458: the T-447 explainability glossary (presentation-only).
                ElInfoTip(tip = "triage.card")
            }
            Text(
                text = if (triage.totalOutstanding > 0) {
                    // T-457: the critical part derives its day count from the
                    // thresholds actually applied (never the stale "> 45 j").
                    val chronicPart = chronic?.let { " · ${(it.amount / 100).formatDzd()} DA critiques > ${triage.redDays} j" } ?: ""
                    "${(triage.totalOutstanding / 100).formatDzd()} DA d'encours total$chronicPart"
                } else {
                    "Encours ventilé par ancienneté réelle et liste d'action immédiate"
                },
                style = ElTheme.typography.bodySmall,
                color = ElTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))

            if (triage.totalOutstanding == 0L) {
                Text(
                    text = "Aucune créance ouverte — aucun rappel à émettre.",
                    style = ElTheme.typography.bodySmall,
                    color = ElTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            } else {
                triage.buckets.forEach { b ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(TRIAGE_COLORS[b.bucket] ?: ElTheme.colors.info),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = b.label,
                            style = ElTheme.typography.labelMedium,
                            color = ElTheme.colors.textPrimary,
                            modifier = Modifier.width(150.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // T-458: the per-bucket glossary tip (the desktop's
                        // per-bucket mounting — triage.notDue/current/…).
                        ElInfoTip(tip = "triage.${b.bucket}", size = 11)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(ElTheme.colors.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(b.share.coerceIn(0, 100) / 100f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(TRIAGE_COLORS[b.bucket] ?: ElTheme.colors.info),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${(b.amount / 100).formatDzd()} DA",
                            style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ElTheme.colors.textPrimary,
                            textAlign = TextAlign.End,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${b.familyCount} fam.",
                            style = ElTheme.typography.labelSmall,
                            color = ElTheme.colors.textSecondary,
                        )
                    }
                }

                if (triage.callList.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElTheme.colors.dangerContainer.copy(alpha = 0.35f))
                            .padding(10.dp),
                    ) {
                        Text(
                            // T-457 (§15.1 INV-16f): the "beyond RED" number
                            // DERIVES from the thresholds the derivation
                            // actually applied (the snapshot's redDays) —
                            // never the stale hardcoded "> 45 j" (the
                            // pre-T-457 label contradicted the live 60-day
                            // red edge since T-443).
                            text = "LISTE D'APPEL IMMÉDIATE (> ${triage.redDays} j — ${triage.callList.size} familles)",
                            style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ElTheme.colors.danger,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        ElInfoTip(tip = "triage.callList", size = 11)
                        Spacer(Modifier.height(4.dp))
                        triage.callList.forEach { f ->
                            CallListRow(f)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallListRow(f: ExecCallListEntryItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = f.parentName,
            style = ElTheme.typography.bodySmall,
            color = ElTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(f.outstanding / 100).formatDzd()} DA",
            style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = ElTheme.colors.danger,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${f.worstDaysOverdue} j",
            style = ElTheme.typography.labelSmall,
            color = ElTheme.colors.textSecondary,
        )
    }
}

// ============================================================================
// Family concentration
// ============================================================================

/**
 * FamilyConcentrationCard — the 80/20 view: top-10 family exposure with
 * child counts (the multi-child VIPs whose departure multiplies the loss)
 * and the concentration percentage of the total school debt.
 */
@Composable
fun FamilyConcentrationCard(
    concentration: ExecConcentrationSnapshot,
    modifier: Modifier = Modifier,
) {
    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = "Concentration des Créances par Famille",
                style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = ElTheme.colors.textPrimary,
            )
            Text(
                text = if (concentration.debtorFamilyCount > 0) {
                    "Top ${concentration.topFamilies.size} = ${concentration.topConcentrationPct}% de ${(concentration.totalOutstanding / 100).formatDzd()} DA · ${concentration.debtorFamilyCount} fam. débitrices"
                } else {
                    "Exposition par tuteur — la règle des 80/20"
                },
                style = ElTheme.typography.bodySmall,
                color = ElTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))

            if (concentration.debtorFamilyCount == 0) {
                Text(
                    text = "Aucune famille débitrice — encours nul.",
                    style = ElTheme.typography.bodySmall,
                    color = ElTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            } else {
                // Header row
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("Famille", style = ElTheme.typography.labelSmall, color = ElTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                    Text("Enf.", style = ElTheme.typography.labelSmall, color = ElTheme.colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
                    Text("Encours", style = ElTheme.typography.labelSmall, color = ElTheme.colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(86.dp))
                    Text("Part", style = ElTheme.typography.labelSmall, color = ElTheme.colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                    Text("Retard", style = ElTheme.typography.labelSmall, color = ElTheme.colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(46.dp))
                }
                concentration.topFamilies.forEach { f ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = f.parentName,
                            style = ElTheme.typography.bodySmall,
                            color = ElTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (f.childCount > 0) "${f.childCount} ×" else "—",
                            style = ElTheme.typography.bodySmall,
                            color = ElTheme.colors.textPrimary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(36.dp),
                        )
                        Text(
                            text = "${(f.outstanding / 100).formatDzd()} DA",
                            style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ElTheme.colors.textPrimary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(86.dp),
                        )
                        Text(
                            text = "${f.shareOfTotalDebt}%",
                            style = ElTheme.typography.bodySmall,
                            color = ElTheme.colors.textPrimary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(44.dp),
                        )
                        Text(
                            text = "${f.worstDaysOverdue} j",
                            style = ElTheme.typography.labelSmall,
                            color = ElTheme.colors.textSecondary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(46.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = ElTheme.colors.outlineVariant, thickness = 1.dp)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Total top ${concentration.topFamilies.size}",
                        style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.textPrimary,
                    )
                    Text(
                        text = "${(concentration.topTotal / 100).formatDzd()} DA · ${concentration.topConcentrationPct}%",
                        style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.textPrimary,
                    )
                }
            }
        }
    }
}

// ============================================================================
// Transport yield
// ============================================================================

/**
 * TransportYieldCard — per-route ridership and collection: normalized
 * towns, riders per route, billed/collected/remaining per route, and the
 * honest list of unresolved source spellings.
 */
@Composable
fun TransportYieldCard(
    transport: ExecTransportSnapshot,
    modifier: Modifier = Modifier,
) {
    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = "Rendement du Transport Scolaire",
                style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = ElTheme.colors.textPrimary,
            )
            Text(
                text = if (transport.riders > 0) {
                    "${transport.riders} transportés · ${(transport.paidTotal / 100).formatDzd()} / ${(transport.dueTotal / 100).formatDzd()} DA encaissés (${transport.collectedPct}%)"
                } else {
                    "Rentabilité par ligne — destinations normalisées, remplissage et recouvrement"
                },
                style = ElTheme.typography.bodySmall,
                color = ElTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))

            if (transport.riders == 0) {
                Text(
                    text = "Aucun élève transporté — aucune ligne active.",
                    style = ElTheme.typography.bodySmall,
                    color = ElTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            } else {
                transport.routes.forEach { r ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = r.destinationLabel,
                                style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ElTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${r.riders} él.",
                                style = ElTheme.typography.labelSmall,
                                color = ElTheme.colors.textSecondary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${r.collectedPct}%",
                                style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = ElTheme.colors.textPrimary,
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        ElLinearProgress(
                            progress = r.collectedPct.coerceIn(0, 100) / 100f,
                            height = 6,
                        )
                        Text(
                            text = "Restant : ${(r.remainingTotal / 100).formatDzd()} DA",
                            style = ElTheme.typography.labelSmall,
                            color = if (r.remainingTotal > 0) ElTheme.colors.danger else ElTheme.colors.textSecondary,
                        )
                    }
                }
                if (transport.unresolvedRawValues.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Valeurs non résolues : ${transport.unresolvedRawValues.joinToString(", ")}",
                        style = ElTheme.typography.labelSmall,
                        color = ElTheme.colors.warning,
                    )
                }
            }
        }
    }
}

// ============================================================================
// Services yield
// ============================================================================

/**
 * ServiceYieldCard — therapy and support-service revenue from the PAID
 * stream: PSY / ORTHO / E-PLANT / orthophonie sessions actually sold.
 */
@Composable
fun ServiceYieldCard(
    services: List<ExecServiceItem>,
    modifier: Modifier = Modifier,
) {
    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = "Rendement des Services Spécialisés",
                style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = ElTheme.colors.textPrimary,
            )
            Text(
                text = if (services.isNotEmpty()) {
                    "CA spécialisé : ${(services.sumOf { it.revenue } / 100).formatDzd()} DA"
                } else {
                    "Thérapies et soutien — le flux payé (PSY · ORTHO · E-PLANT)"
                },
                style = ElTheme.typography.bodySmall,
                color = ElTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))

            if (services.isEmpty()) {
                Text(
                    text = "Aucun encaissement sur les services spécialisés.",
                    style = ElTheme.typography.bodySmall,
                    color = ElTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            } else {
                services.forEach { s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = s.label,
                            style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ElTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${s.studentCount} él. · ${s.paymentCount} paiements",
                            style = ElTheme.typography.labelSmall,
                            color = ElTheme.colors.textSecondary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${(s.revenue / 100).formatDzd()} DA",
                            style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = ElTheme.colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// Enrollment dynamics + section imbalance
// ============================================================================

/**
 * EnrollmentDynamicsCard — sibling index, family-size distribution, and
 * the SECTION IMBALANCE warnings (spread ≥ 10 OR max ≥ 1.5 × min — NO
 * capacity ceilings: real enrollments only).
 */
@Composable
fun EnrollmentDynamicsCard(
    dynamics: ExecDynamicsSnapshot,
    modifier: Modifier = Modifier,
) {
    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = "Dynamique des Inscriptions",
                style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = ElTheme.colors.textPrimary,
            )
            Text(
                text = if (dynamics.totalStudents > 0) {
                    "${dynamics.totalStudents} élèves / ${dynamics.totalFamilies} familles · indice fraternel ${
                        dynamics.siblingIndex?.let { String.format(java.util.Locale.FRENCH, "%.2f", it) } ?: "—"
                    }"
                } else {
                    "Indice fraternel, taille des familles et déséquilibres de sections"
                },
                style = ElTheme.typography.bodySmall,
                color = ElTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))

            if (dynamics.totalStudents == 0) {
                Text(
                    text = "Aucun élève inscrit.",
                    style = ElTheme.typography.bodySmall,
                    color = ElTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            } else {
                dynamics.familySizes.forEach { fs ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = fs.label, style = ElTheme.typography.bodySmall, color = ElTheme.colors.textPrimary)
                        Text(
                            text = "${fs.familyCount} fam. · ${fs.studentCount} él.",
                            style = ElTheme.typography.labelSmall,
                            color = ElTheme.colors.textSecondary,
                        )
                    }
                }
                if (dynamics.multiChildFamilyCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${dynamics.multiChildFamilyCount} familles multi-enfants (${dynamics.multiChildFamilyPct}%)",
                        style = ElTheme.typography.labelSmall,
                        color = ElTheme.colors.info,
                    )
                }

                if (dynamics.imbalances.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = ElTheme.colors.outlineVariant, thickness = 1.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "DÉSÉQUILIBRES DE SECTIONS",
                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.warning,
                    )
                    dynamics.imbalances.forEach { imb ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = imb.gradeLabel,
                                    style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = ElTheme.colors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                ElTag(
                                    text = "${imb.spread} d'écart",
                                    tone = ElTagTone.WARNING,
                                )
                            }
                            Text(
                                text = "${imb.sectionCount} sections : ${imb.sectionsLabel} (moy. ${imb.averageEnrolled})",
                                style = ElTheme.typography.labelSmall,
                                color = ElTheme.colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Triple-risk radar — the front-page alert
// ============================================================================

/**
 * TripleRiskSummaryCard — GPA < 10 + unexcused absences ≥ 3 + overdue
 * family balance: the dropout-risk students (the desktop operational-query
 * engine's radar, promoted front-and-center per the owner's directive).
 */
@Composable
fun TripleRiskSummaryCard(
    summary: ExecRiskSummaryItem,
    profiles: List<ExecRiskProfileItem>,
    modifier: Modifier = Modifier,
) {
    val total = profiles.size
    val triple = summary.tripleCriticalCount
    val tripleStudents = profiles.filter { it.riskCategory == "triple_critical" }.take(8)

    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = "Radar Triple Risque — Décrochage",
                style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = ElTheme.colors.danger,
            )
            Text(
                text = "Moyenne < 10 + absences non justifiées ≥ 3 + créance familiale — les élèves à risque de décrochage financier",
                style = ElTheme.typography.bodySmall,
                color = ElTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))

            if (total == 0) {
                Text(
                    text = "Aucun profil élève évaluable (données académiques/assiduité non saisies).",
                    style = ElTheme.typography.bodySmall,
                    color = ElTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$triple",
                        style = ElTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                        color = ElTheme.colors.danger,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "élèves en risque critique\n(${summary.tripleCriticalPct}% de $total évalués)",
                        style = ElTheme.typography.labelSmall,
                        color = ElTheme.colors.textSecondary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RiskCountTile(summary.academicAlertCount, "Moyenne < 10", Modifier.weight(1f))
                    RiskCountTile(summary.attendanceAlertCount, "Absences ≥ 3", Modifier.weight(1f))
                    RiskCountTile(summary.financialTensionCount, "Créance famille", Modifier.weight(1f))
                    RiskCountTile(summary.healthyCount, "Profils réguliers", Modifier.weight(1f))
                }
                if (tripleStudents.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    tripleStudents.forEach { p ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = p.studentName,
                                style = ElTheme.typography.bodySmall,
                                color = ElTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = p.gpa?.let { String.format(java.util.Locale.FRENCH, "%.2f", it) } ?: "—",
                                style = ElTheme.typography.labelSmall,
                                color = ElTheme.colors.textSecondary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${p.unexcusedAbsences}a",
                                style = ElTheme.typography.labelSmall,
                                color = ElTheme.colors.textSecondary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${(p.debtAmount / 100).formatDzd()} DA",
                                style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ElTheme.colors.danger,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskCountTile(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ElTheme.colors.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$count",
            style = ElTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = ElTheme.colors.textPrimary,
        )
        Text(
            text = label,
            style = ElTheme.textStyles.chartMicro,
            color = ElTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

// ============================================================================
// The composed ExecutiveDashboard view
// ============================================================================

/**
 * ExecutiveDashboard — the "Pilotage Exécutif" view composed from every
 * executive card (the desktop T-339 layout mirror, stacked for mobile).
 * The radar + the wave staircase lead — the two front-page alerts.
 */
@Composable
fun ExecutiveDashboard(
    snapshot: ExecutiveStatsSnapshot,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TripleRiskSummaryCard(summary = snapshot.riskSummary, profiles = snapshot.riskRadar)
        // T-454 (PARITY-007): the FULL T-447 pooled form — the pooled grid +
        // the « Hors Tranches » section + the per-category detail.
        WaveVelocityCard(
            waves = snapshot.waves,
            pooledWaves = snapshot.pooledWaves,
            nonWave = snapshot.nonWaveSummary,
        )
        DebtTriageCard(triage = snapshot.triage)
        DiscountErosionCard(erosion = snapshot.erosion)
        FamilyConcentrationCard(concentration = snapshot.concentration)
        EnrollmentDynamicsCard(dynamics = snapshot.dynamics)
        TransportYieldCard(transport = snapshot.transport)
        ServiceYieldCard(services = snapshot.services)
    }
}
