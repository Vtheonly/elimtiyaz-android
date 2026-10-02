package com.example.ui.features.financials

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.formatDzd
import com.example.domain.model.TrancheStripTotalsItem
import com.example.domain.model.TrancheWaveItem
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardVariant
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.theme.ElTheme

/**
 * TrancheWaveCard — inventory #12 (PARITY-003), the T-454 (PARITY-007) form.
 *
 * The native twin of the desktop's `installment-schedule-tab.tsx` wave strip
 * (the T-447 state): the GLOBAL T1/T2/T3 collection-health meters over the
 * canonical POOLED all-categories rows (the SAME object the Statistics wave
 * hero consumes — one derivation, two presentations; the label-REGEX feed
 * is retired). Per wave: the POOLED due/paid/pending/remaining, the
 * canonical UNCLAMPED rate (over-covered waves render their honest >100
 * rate), the T-434/T-435 DERIVED échéance range + days-late, the T-432
 * tuition-isolated rate, the pending line, and the next-target highlight.
 * The totals row (Total dû / Payé / Reste / En retard) comes from the
 * canonical strip totals (the whole selection, non-wave/FI rows included —
 * wave sums alone would silently drop the FI pool).
 *
 * T-427 (DATA-048b): the pooling basis is EXPLICIT — the basis label states
 * that this strip pools every category while the Statistics card's
 * per-category breakdown isolates them, so the two surfaces' numbers
 * reconcile at a glance.
 */
@Composable
internal fun TrancheWaveCard(
    waves: List<TrancheWaveItem>,
    totals: TrancheStripTotalsItem?,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    val allZero = waves.all { it.due == 0L }
    if (allZero) {
        ElCard(modifier = modifier.fillMaxWidth(), variant = ElCardVariant.OUTLINED) {
            Column(modifier = Modifier.padding(ElTheme.spacing.lg)) {
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

    // T-435 (UI-317): the échéance is DERIVED from the rows the card sums —
    // the wave's due-date RANGE (the static schedule hint stays ONLY as the
    // fallback when no row carries a parseable date; a hardcoded hint can
    // silently lie after the data drifts, the derived range never can).
    val nowEpochMs = System.currentTimeMillis()

    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ElTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md)) {
            Text(
                text = "Vagues de Tranches (T1 / T2 / T3)",
                color = c.textPrimary,
                style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            // T-427 (DATA-048b): the pooling basis is EXPLICIT.
            Text(
                text = "Base : toutes catégories de la sélection (parité exacte avec la carte « Vélocité » des Statistiques)",
                color = c.textMuted,
                style = ElTheme.textStyles.chartMicro,
            )

            waves.forEach { w ->
                Column(verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = w.label,
                                color = if (w.isNextTarget) ElChartPalette.primary else c.textPrimary,
                                style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            if (w.isNextTarget) {
                                Spacer(Modifier.width(ElTheme.spacing.sm))
                                Text(
                                    text = "cible",
                                    color = ElChartPalette.primary,
                                    style = ElTheme.textStyles.chartMicro,
                                    modifier = Modifier
                                        .background(
                                            ElChartPalette.primary.copy(alpha = 0.14f),
                                            RoundedCornerShape(50),
                                        )
                                        .padding(horizontal = ElTheme.spacing.sm, vertical = 1.dp),
                                )
                            }
                        }
                        Text(
                            // T-447 (PARITY-001): the canonical UNCLAMPED rate —
                            // an over-covered wave renders its honest >100 rate.
                            text = "${w.pct}%",
                            color = when {
                                w.isNextTarget -> ElChartPalette.primary
                                // T-462 (UI-327): the semantic success (the
                                // dark-softened Emerald400), never the chart
                                // palette's saturated #10B981 as bare text.
                                w.pct >= 90 -> c.success
                                else -> c.textSecondary
                            },
                            style = ElTheme.textStyles.numericSmall,
                        )
                    }
                    // The meter (progress bar; primary for the next target,
                    // semantic success ≥90, muted otherwise — the desktop
                    // tones; the bar itself caps at 100 by construction).
                    // T-462 (UI-327): the ≥90 fill resolves through the
                    // theme's semantic layer, not the chart palette.
                    ElLinearProgress(
                        progress = w.pct.coerceIn(0, 100) / 100f,
                        gradient = when {
                            w.isNextTarget -> listOf(ElChartPalette.primary, ElChartPalette.primaryDeep)
                            w.pct >= 90 -> listOf(c.success, c.success)
                            else -> listOf(c.surfaceVariant, c.surfaceVariant)
                        },
                    )
                    // T-434 (UI-316): the échéance is VISIBLE on the strip card.
                    // T-435 (UI-317): now DERIVED from the rows — the due-date
                    // range, not a hardcoded hint.
                    StripDueLine(w = w, nowEpochMs = nowEpochMs)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Encaissé : ${(w.paid / 100).formatDzd()} DA",
                            color = c.textMuted,
                            style = ElTheme.textStyles.chartMicro,
                        )
                        Text(
                            text = "Dû : ${(w.due / 100).formatDzd()} DA",
                            color = c.textMuted,
                            style = ElTheme.textStyles.chartMicro,
                        )
                    }
                    // T-432 (DATA-049): the tuition-isolated rate — the SAME
                    // number the Statistics card shows, so the two surfaces'
                    // different bases reconcile at a glance.
                    if (w.tuitionPct != null) {
                        Text(
                            text = "dont scolarité : ${w.tuitionPct}%",
                            color = c.textMuted,
                            style = ElTheme.textStyles.chartMicro,
                        )
                    }
                    if (w.pending > 0L) {
                        Text(
                            text = "Dont en attente (chèque / virement) : ${(w.pending / 100).formatDzd()} DA",
                            // T-462 (UI-327): the semantic warning (the
                            // dark-softened Tangerine400), not the chart
                            // palette's saturated #F59E0B.
                            color = c.warning,
                            style = ElTheme.textStyles.chartMicro,
                        )
                    }
                }
            }

            // Totals row (the desktop 4-cell block — the canonical strip
            // totals over the WHOLE selection, non-wave/FI rows included).
            // T-462 (UI-327): the four cells render in the same soft tile
            // language as the pooled meters' metric grid (tint + hairline
            // border) and every status value resolves through the semantic
            // layer — the closing row reads as a calm ledger, not four
            // floating neon numbers.
            val t = totals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
            ) {
                TrancheTotal("Total dû", "${((t?.totalDue ?: waves.sumOf { it.due }) / 100).formatDzd()} DA", c.textPrimary, Modifier.weight(1f))
                TrancheTotal("Payé", "${((t?.totalPaid ?: waves.sumOf { it.paid }) / 100).formatDzd()} DA", c.success, Modifier.weight(1f))
                TrancheTotal("Reste", "${((t?.totalRemaining ?: ((t?.totalDue ?: waves.sumOf { it.due }) - (t?.totalPaid ?: waves.sumOf { it.paid }) - waves.sumOf { it.pending }).coerceAtLeast(0L)) / 100).formatDzd()} DA", c.danger, Modifier.weight(1f))
                TrancheTotal("En retard", "${t?.overdueCount ?: 0}", c.warning, Modifier.weight(1f))
            }
        }
    }
}

/**
 * The échéance line — the desktop's strip card due line (T-434/T-435):
 * the DERIVED range (never the static hint when a row carries a date), the
 * days-late suffix when the wave claims lateness (an unsettled overdue row),
 * the days-remaining suffix for future waves. One clock (the card's).
 */
@Composable
private fun StripDueLine(w: TrancheWaveItem, nowEpochMs: Long) {
    val c = ElTheme.colors
    val range = dueRangeLabel(w)
    val daysLate = if (w.dueDate != null) com.example.core.execDaysBetweenFloor(w.dueDate!!, nowEpochMs) else 0L
    val claimsLateness = w.isOverdue && w.remaining > 0L
    val text = if (range != null) {
        "Échéance : $range" + when {
            claimsLateness -> " — $daysLate j de retard"
            w.remaining > 0L && daysLate < 0L -> " — dans ${-daysLate} j"
            else -> ""
        }
    } else {
        w.hint
    }
    Text(
        text = text,
        // T-462 (UI-327): the semantic danger (Rose400) for the
        // days-late verdict — never the chart palette as bare text.
        color = if (claimsLateness) c.danger else c.textMuted,
        style = ElTheme.textStyles.chartMicro,
    )
}

/** "dd MMM yyyy" / "min → max" — the desktop formatDueDateRange mirror. */
private fun dueRangeLabel(w: TrancheWaveItem): String? {
    val minIso = w.dueDate ?: return null
    val min = try {
        java.time.Instant.parse(minIso)
    } catch (_: Exception) {
        return null
    }
    val fmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.FRENCH)
        .withZone(java.time.ZoneOffset.UTC)
    val minLabel = fmt.format(min)
    val maxIso = w.dueDateMax ?: return minLabel
    if (maxIso == minIso) return minLabel
    val max = try {
        java.time.Instant.parse(maxIso)
    } catch (_: Exception) {
        return minLabel
    }
    return "$minLabel → ${fmt.format(max)}"
}

@Composable
private fun TrancheTotal(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    // T-462 (UI-327): the desktop twin's 4-cell closing block gains the
    // metric-tile treatment (soft tint + hairline border) — the totals
    // stop floating and the key numbers lead by size and weight.
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.surfaceVariant.copy(alpha = 0.4f))
            .border(
                width = 1.dp,
                color = c.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = ElTheme.spacing.sm, vertical = ElTheme.spacing.sm),
    ) {
        Text(
            text = label,
            color = c.textMuted,
            style = ElTheme.textStyles.chartMicro,
        )
        Text(
            text = value,
            color = color,
            style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}
