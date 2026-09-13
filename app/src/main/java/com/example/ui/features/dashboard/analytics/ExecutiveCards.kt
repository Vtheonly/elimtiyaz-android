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
import androidx.compose.ui.unit.sp
import com.example.core.formatDzd
import com.example.domain.model.ExecCallListEntryItem
import com.example.domain.model.ExecConcentrationSnapshot
import com.example.domain.model.ExecDynamicsSnapshot
import com.example.domain.model.ExecErosionItem
import com.example.domain.model.ExecRiskProfileItem
import com.example.domain.model.ExecRiskSummaryItem
import com.example.domain.model.ExecServiceItem
import com.example.domain.model.ExecTriageSnapshot
import com.example.domain.model.ExecTransportSnapshot
import com.example.domain.model.ExecWaveItem
import com.example.domain.model.ExecutiveStatsSnapshot
import com.example.ui.designsystem.components.card.ElCard
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

private val WAVE_LABELS_FR = mapOf(
    1 to "T1 · Inscription + 1er versement (Sept)",
    2 to "T2 · 2ème versement (Déc)",
    3 to "T3 · 3ème versement (Mars)",
)

private fun phaseLabelFr(phase: String): String = when (phase) {
    "overdue" -> "échue"
    "not_due" -> "à venir"
    else -> "en cours"
}

private fun phaseTone(phase: String): ElTagTone = when (phase) {
    "overdue" -> ElTagTone.DANGER
    "not_due" -> ElTagTone.NEUTRAL
    else -> ElTagTone.SUCCESS
}

/**
 * WaveVelocityCard — the tranche-wave collection meters. THE revenue hero:
 * school revenue is a staircase of three waves, not a smooth curve. Each
 * meter shows billed vs collected vs remaining with the value-based
 * collection rate — the number that predicts whether payroll clears.
 */
@Composable
fun WaveVelocityCard(
    waves: List<ExecWaveItem>,
    modifier: Modifier = Modifier,
) {
    val tuition = waves.filter { it.category == "tuition" }
    val others = waves.filter { it.category != "tuition" }
    val totalDue = waves.sumOf { it.dueTotal }
    val totalPaid = waves.sumOf { it.paidTotal }
    val totalRemaining = waves.sumOf { it.remainingTotal }

    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = "Vélocité de Recouvrement par Vague",
                style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = ElTheme.colors.textPrimary,
            )
            Text(
                text = if (waves.isNotEmpty()) {
                    "${(totalPaid / 100).formatDzd()} DA encaissés / ${(totalDue / 100).formatDzd()} DA facturés · ${(totalRemaining / 100).formatDzd()} DA restants"
                } else {
                    "Taux de recouvrement par tranche saisonnière (Sept / Déc / Mars)"
                },
                style = ElTheme.typography.bodySmall,
                color = if (waves.isNotEmpty()) ElTheme.colors.danger else ElTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))

            if (waves.isEmpty()) {
                Text(
                    text = "Aucune tranche facturée sur la période.",
                    style = ElTheme.typography.bodySmall,
                    color = ElTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                tuition.forEach { w ->
                    WaveMeter(w)
                    if (w != tuition.last()) Spacer(Modifier.height(10.dp))
                }
                if (others.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = ElTheme.colors.outlineVariant, thickness = 1.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "AUTRES VAGUES",
                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = ElTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    others.forEach { w ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${w.categoryLabel} · T${w.wave}",
                                style = ElTheme.typography.bodySmall,
                                color = ElTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${w.collectedPct}%",
                                style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = ElTheme.colors.textPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "${(w.remainingTotal / 100).formatDzd()} DA restants",
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

@Composable
private fun WaveMeter(w: ExecWaveItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = WAVE_LABELS_FR[w.wave] ?: "Tranche ${w.wave}",
                style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ElTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ElTag(text = phaseLabelFr(w.phase), tone = phaseTone(w.phase))
        }
        Spacer(Modifier.height(6.dp))
        // The meter: collected share of the billed total. Red only when the
        // wave is overdue AND under 90% (the desktop operational tone rule).
        val meterColor = if (w.phase == "overdue" && w.collectedPct < 90) ElTheme.colors.danger else ElTheme.colors.success
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(ElTheme.colors.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(w.collectedPct.coerceIn(0, 100) / 100f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(meterColor),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${w.collectedPct}%",
                style = ElTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Black),
                color = ElTheme.colors.textPrimary,
            )
            Text(
                text = "${w.paidCount}/${w.installmentCount} tranches",
                style = ElTheme.typography.labelSmall,
                color = ElTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.height(2.dp))
        Column {
            ExecStatRow(label = "Facturé", value = "${(w.dueTotal / 100).formatDzd()} DA")
            ExecStatRow(label = "Encaissé", value = "${(w.paidTotal / 100).formatDzd()} DA")
            ExecStatRow(
                label = "Restant",
                value = "${(w.remainingTotal / 100).formatDzd()} DA",
                valueColor = if (w.remainingTotal > 0) ElTheme.colors.danger else ElTheme.colors.textSecondary,
            )
            ExecStatRow(label = "Familles", value = "${w.debtorFamilyCount} débitrices · ${w.familyCount} facturées")
        }
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

private val TRIAGE_COLORS = mapOf(
    "not_due" to Color(0xFF94A3B8),
    "current" to Color(0xFF10B981),
    "reminder" to Color(0xFFEAB308),
    "chronic" to Color(0xFFEF4444),
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
            Text(
                text = "Triage des Créances — Qui Appeler Aujourd'hui",
                style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = ElTheme.colors.textPrimary,
            )
            Text(
                text = if (triage.totalOutstanding > 0) {
                    val chronicPart = chronic?.let { " · ${(it.amount / 100).formatDzd()} DA critiques > 45 j" } ?: ""
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
                            text = "LISTE D'APPEL IMMÉDIATE (> 45 j — ${triage.callList.size} familles)",
                            style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ElTheme.colors.danger,
                        )
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
            style = ElTheme.typography.labelSmall.copy(fontSize = 9.sp),
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
        WaveVelocityCard(waves = snapshot.waves)
        DebtTriageCard(triage = snapshot.triage)
        DiscountErosionCard(erosion = snapshot.erosion)
        FamilyConcentrationCard(concentration = snapshot.concentration)
        EnrollmentDynamicsCard(dynamics = snapshot.dynamics)
        TransportYieldCard(transport = snapshot.transport)
        ServiceYieldCard(services = snapshot.services)
    }
}
