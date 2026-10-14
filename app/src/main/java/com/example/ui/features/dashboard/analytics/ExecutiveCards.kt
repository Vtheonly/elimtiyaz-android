package com.example.ui.features.dashboard.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.QueryBuilder
import androidx.compose.material.icons.filled.TrendingUp
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
import com.example.domain.model.ExecNonWaveItem
import com.example.domain.model.ExecPooledWaveItem
import com.example.domain.model.ExecRiskProfileItem
import com.example.domain.model.ExecRiskSummaryItem
import com.example.domain.model.ExecServiceItem
import com.example.domain.model.ExecTriageSnapshot
import com.example.domain.model.ExecTransportSnapshot
import com.example.domain.model.ExecWaveItem
import com.example.domain.model.ExecutiveStatsSnapshot
import com.example.ui.designsystem.components.card.ElExpandableCard
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.overlays.ElInfoTip
import com.example.ui.designsystem.theme.ElTheme

internal val WAVE_TITLES_FR: Map<Int, Pair<String, String>> = mapOf(
    1 to ("Tranche 1 (T1)" to "Toutes catégories — 1er versement (Sept)"),
    2 to ("Tranche 2 (T2)" to "Toutes catégories — mi-parcours (Déc)"),
    3 to ("Tranche 3 (T3)" to "Toutes catégories — clôture (Mars)"),
)

private fun formatDueDateRangeFr(dueDateMinIso: String?, dueDateMaxIso: String?): String? {
    if (dueDateMinIso == null) return null
    val min = runCatching { java.time.Instant.parse(dueDateMinIso) }.getOrNull() ?: return null
    val fmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.FRENCH)
        .withZone(java.time.ZoneOffset.UTC)
    val minLabel = fmt.format(min)
    if (dueDateMaxIso == null || dueDateMaxIso == dueDateMinIso) return minLabel
    val max = runCatching { java.time.Instant.parse(dueDateMaxIso) }.getOrNull() ?: return minLabel
    return "$minLabel → ${fmt.format(max)}"
}

internal fun waveDaysLate(dueDateIso: String?, nowEpochMs: Long): Long =
    if (dueDateIso == null) 0L else com.example.core.execDaysBetweenFloor(dueDateIso, nowEpochMs)

private fun nonWaveKindLabelFr(kind: com.example.domain.model.ExecNonWaveKindItem, categoryLabel: String): String = when (kind) {
    com.example.domain.model.ExecNonWaveKindItem.FI -> "Frais d'inscription (FI)"
    com.example.domain.model.ExecNonWaveKindItem.UNNUMBERED -> "$categoryLabel — hors tranche"
    com.example.domain.model.ExecNonWaveKindItem.OUT_OF_RANGE -> "$categoryLabel — hors bornes"
}

// ============================================================================
// 1. Triple-risk Radar (Dropout Prevention)
// ============================================================================

@Composable
fun TripleRiskSummaryCard(
    summary: ExecRiskSummaryItem,
    profiles: List<ExecRiskProfileItem>,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    val total = profiles.size
    val triple = summary.tripleCriticalCount
    val tripleStudents = profiles.filter { it.riskCategory == "triple_critical" }.take(8)

    ElExpandableCard(
        title = "Radar Triple Risque",
        subtitle = "Moyenne < 10 · Absences ≥ 3 · Dette ouverte",
        primaryFigure = "$triple",
        primaryFigureColor = if (triple > 0) c.danger else c.success,
        icon = Icons.Default.PriorityHigh,
        iconTint = if (triple > 0) c.danger else c.success,
        headerBadge = {
            ElTag(
                text = "${summary.tripleCriticalPct}% critique",
                tone = if (triple > 0) ElTagTone.DANGER else ElTagTone.SUCCESS,
            )
        },
        headerTrailing = { ElInfoTip(tip = "risk.card") },
        modifier = modifier,
    ) {
        Text(
            text = "Moyenne < 10 + absences non justifiées ≥ 3 + créance familiale — les élèves à risque de décrochage financier",
            style = ElTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(ElTheme.spacing.md))

        if (total == 0) {
            Text(
                text = "Aucun profil élève évaluable (données académiques/assiduité non saisies).",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = ElTheme.spacing.md),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$triple",
                    style = ElTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                    color = c.danger,
                )
                Spacer(Modifier.width(ElTheme.spacing.sm))
                Text(
                    text = "élèves en risque critique\n(${summary.tripleCriticalPct}% de $total évalués)",
                    style = ElTheme.typography.labelSmall,
                    color = c.textSecondary,
                )
            }
            Spacer(Modifier.height(ElTheme.spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
            ) {
                RiskCountTile(summary.academicAlertCount, "Moyenne < 10", Modifier.weight(1f))
                RiskCountTile(summary.attendanceAlertCount, "Absences ≥ 3", Modifier.weight(1f))
                RiskCountTile(summary.financialTensionCount, "Créance famille", Modifier.weight(1f))
                RiskCountTile(summary.healthyCount, "Profils réguliers", Modifier.weight(1f))
            }
            if (tripleStudents.isNotEmpty()) {
                Spacer(Modifier.height(ElTheme.spacing.md))
                Text(
                    text = "ÉLÈVES EN RISQUE CRITIQUE (TOP 8)",
                    style = ElTheme.textStyles.chartMicro.copy(fontWeight = FontWeight.Bold),
                    color = c.danger,
                )
                Spacer(Modifier.height(ElTheme.spacing.xs))
                tripleStudents.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = p.studentName,
                            style = ElTheme.typography.bodySmall,
                            color = c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = p.gpa?.let { String.format(java.util.Locale.FRENCH, "%.2f", it) } ?: "—",
                            style = ElTheme.typography.labelSmall,
                            color = c.textSecondary,
                        )
                        Spacer(Modifier.width(ElTheme.spacing.sm))
                        Text(
                            text = "${p.unexcusedAbsences}a",
                            style = ElTheme.typography.labelSmall,
                            color = c.textSecondary,
                        )
                        Spacer(Modifier.width(ElTheme.spacing.sm))
                        Text(
                            text = "${(p.debtAmount / 100).formatDzd()} DA",
                            style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = c.danger,
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. Wave Velocity (Revenue Staircase)
// ============================================================================

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
    val totalDue = pooledWaves.sumOf { it.dueTotal }
    val totalPaid = pooledWaves.sumOf { it.paidTotal }
    val totalPending = pooledWaves.sumOf { it.pendingTotal }
    val totalRemaining = pooledWaves.sumOf { it.remainingTotal }
    val globalPct = if (totalDue > 0L) com.example.core.execSharePct(totalPaid, totalDue) else 0
    val slots = listOf(1, 2, 3).map { wave -> pooledWaves.firstOrNull { it.wave == wave } }
    val hasAnyRow = pooledWaves.isNotEmpty() || nonWave.isNotEmpty()

    ElExpandableCard(
        title = "Recouvrement des Tranches",
        subtitle = "T1, T2, T3 toutes catégories",
        primaryFigure = "$globalPct%",
        primaryFigureColor = if (globalPct >= 80) c.success else c.primary,
        icon = Icons.Default.TrendingUp,
        headerBadge = {
            if (totalRemaining > 0L) {
                ElTag(
                    text = "${(totalRemaining / 100).formatDzd()} DA restant",
                    tone = ElTagTone.DANGER,
                )
            } else {
                ElTag(text = "Clôturée", tone = ElTagTone.SUCCESS)
            }
        },
        headerTrailing = { ElInfoTip(tip = "waveVelocity.card") },
        modifier = modifier,
    ) {
        Text(
            text = "Analyse T1/T2/T3 toutes catégories (Scolarité, Transport, FI, services) — parité exacte avec l'onglet Finances → Tranches",
            style = ElTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(ElTheme.spacing.xs))

        if (hasAnyRow) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "$globalPct% collecté global",
                        color = ElChartPalette.primary,
                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    ElInfoTip(tip = "waveVelocity.collectedPct", size = 11)
                }
                if (totalPending > 0L) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "${(totalPending / 100).formatDzd()} DA en cours",
                            color = c.info,
                            style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        ElInfoTip(tip = "waveVelocity.pending", size = 11)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "${(totalRemaining / 100).formatDzd()} DA restant",
                        color = c.danger,
                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    ElInfoTip(tip = "waveVelocity.remaining", size = 11)
                }
            }
        }

        Spacer(Modifier.height(ElTheme.spacing.md))

        if (!hasAnyRow) {
            Text(
                text = "Aucune tranche facturée sur la période sélectionnée.",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = ElTheme.spacing.xl),
            )
        } else {
            slots.forEach { slot ->
                PooledWaveMeter(w = slot, nowEpochMs = nowEpochMs)
                Spacer(Modifier.height(ElTheme.spacing.sm))
            }

            if (nonWave.isNotEmpty() && variant == "full") {
                Spacer(Modifier.height(ElTheme.spacing.sm))
                HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
                Spacer(Modifier.height(ElTheme.spacing.sm))
                Text(
                    text = "HORS TRANCHES — INSCRIPTION & ENGAGEMENTS NON-TRANCHES",
                    style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(ElTheme.spacing.xs))
                nonWave.forEach { g ->
                    val range = formatDueDateRangeFr(g.dueDateMin, g.dueDateMax)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
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
                                color = if (g.remainingTotal > 0L) c.danger else c.success,
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

            if (waves.isNotEmpty() && variant == "full") {
                Spacer(Modifier.height(ElTheme.spacing.sm))
                HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
                Spacer(Modifier.height(ElTheme.spacing.sm))
                Text(
                    text = "DÉTAIL PAR CATÉGORIE DE FACTURATION",
                    style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(ElTheme.spacing.xs))
                waves.forEach { w ->
                    val auxDue = formatDueDateRangeFr(w.dueDate, w.dueDateMax)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
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
                                color = if (w.phase == "overdue" && w.remainingTotal > 0L) c.danger else c.textSecondary,
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

// ============================================================================
// 3. Debt Triage
// ============================================================================

private val TRIAGE_COLORS = mapOf(
    "not_due" to ElChartPalette.slate,
    "current" to ElChartPalette.success,
    "reminder" to ElChartPalette.warning,
    "chronic" to ElChartPalette.danger,
)

@Composable
fun DebtTriageCard(
    triage: ExecTriageSnapshot,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    val chronic = triage.buckets.firstOrNull { it.bucket == "chronic" }
    val chronicAmount = chronic?.amount ?: 0L

    ElExpandableCard(
        title = "Triage des Créances",
        subtitle = "Ventilation de l'encours par ancienneté",
        primaryFigure = "${(triage.totalOutstanding / 100).formatDzd()} DA",
        primaryFigureColor = if (triage.totalOutstanding > 0) c.danger else c.success,
        icon = Icons.Default.QueryBuilder,
        headerBadge = {
            if (chronicAmount > 0L) {
                ElTag(
                    text = "${(chronicAmount / 100).formatDzd()} DA > ${triage.redDays}j",
                    tone = ElTagTone.DANGER,
                )
            } else {
                ElTag(text = "Régulier", tone = ElTagTone.SUCCESS)
            }
        },
        headerTrailing = { ElInfoTip(tip = "triage.card") },
        modifier = modifier,
    ) {
        Text(
            text = if (triage.totalOutstanding > 0) {
                val chronicPart = chronic?.let { " · ${(it.amount / 100).formatDzd()} DA critiques > ${triage.redDays} j" } ?: ""
                "${(triage.totalOutstanding / 100).formatDzd()} DA d'encours total$chronicPart"
            } else {
                "Encours ventilé par ancienneté réelle et liste d'action immédiate"
            },
            style = ElTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(ElTheme.spacing.md))

        if (triage.totalOutstanding == 0L) {
            Text(
                text = "Aucune créance ouverte — aucun rappel à émettre.",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = ElTheme.spacing.lg),
            )
        } else {
            triage.buckets.forEach { b ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(ElTheme.spacing.sm)
                            .clip(CircleShape)
                            .background(TRIAGE_COLORS[b.bucket] ?: c.info),
                    )
                    Spacer(Modifier.width(ElTheme.spacing.sm))
                    Text(
                        text = b.label,
                        style = ElTheme.typography.labelMedium,
                        color = c.textPrimary,
                        modifier = Modifier.width(152.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ElInfoTip(tip = "triage.${b.bucket}", size = 11)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(ElTheme.spacing.sm)
                            .clip(RoundedCornerShape(50))
                            .background(c.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(b.share.coerceIn(0, 100) / 100f)
                                .height(ElTheme.spacing.sm)
                                .clip(RoundedCornerShape(50))
                                .background(TRIAGE_COLORS[b.bucket] ?: c.info),
                        )
                    }
                    Spacer(Modifier.width(ElTheme.spacing.sm))
                    Text(
                        text = "${(b.amount / 100).formatDzd()} DA",
                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textPrimary,
                        textAlign = TextAlign.End,
                    )
                    Spacer(Modifier.width(ElTheme.spacing.sm))
                    Text(
                        text = "${b.familyCount} fam.",
                        style = ElTheme.typography.labelSmall,
                        color = c.textSecondary,
                    )
                }
            }

            if (triage.callList.isNotEmpty()) {
                Spacer(Modifier.height(ElTheme.spacing.md))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.dangerContainer.copy(alpha = 0.35f))
                        .padding(ElTheme.spacing.md),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "LISTE D'APPEL IMMÉDIATE (> ${triage.redDays} j — ${triage.callList.size} familles)",
                            style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = c.danger,
                            modifier = Modifier.weight(1f),
                        )
                        ElInfoTip(tip = "triage.callList", size = 11)
                    }
                    Spacer(Modifier.height(ElTheme.spacing.xs))
                    triage.callList.forEach { f ->
                        CallListRow(f)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. Discount Erosion
// ============================================================================

@Composable
fun DiscountErosionCard(
    erosion: ExecErosionItem,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors

    ElExpandableCard(
        title = "Érosion des Remises",
        subtitle = "Impact sur le potentiel brut",
        primaryFigure = "${erosion.erosionPct}%",
        primaryFigureColor = if (erosion.erosionPct > 10) c.danger else c.primary,
        icon = Icons.Default.LocalOffer,
        headerBadge = {
            ElTag(
                text = "${(erosion.netRemiseTotal / 100).formatDzd()} DA",
                tone = if (erosion.netRemiseTotal > 0) ElTagTone.WARNING else ElTagTone.NEUTRAL,
            )
        },
        headerTrailing = { ElInfoTip(tip = "erosion.card") },
        modifier = modifier,
    ) {
        Text(
            text = if (erosion.remiseCount > 0) {
                "${erosion.remiseCount} remises · ${(erosion.netRemiseTotal / 100).formatDzd()} DA nets consentis"
            } else {
                "Remises négociées vs tarif affiché — la marge offerte pour remplir les sièges"
            },
            style = ElTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(ElTheme.spacing.md))

        if (erosion.remiseCount == 0) {
            Text(
                text = "Aucune remise enregistrée — tarification affichée intégralement appliquée.",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = ElTheme.spacing.lg),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${erosion.erosionPct}%",
                    style = ElTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                    color = c.danger,
                )
                Spacer(Modifier.width(ElTheme.spacing.sm))
                Text(
                    text = "du tarif affiché\n(brut reconstruit)",
                    style = ElTheme.typography.labelSmall,
                    color = c.textSecondary,
                )
            }
            Spacer(Modifier.height(ElTheme.spacing.md))
            ExecStatRow("Remises brutes", "${(erosion.remiseTotal / 100).formatDzd()} DA")
            if (erosion.cancelCount > 0) {
                ExecStatRow("Annulations (double remise)", "−${(erosion.cancelTotal / 100).formatDzd()} DA")
            }
            ExecStatRow("Remises nettes", "${(erosion.netRemiseTotal / 100).formatDzd()} DA")
            ExecStatRow("Charges brutes", "${(erosion.grossCharges / 100).formatDzd()} DA")
            ExecStatRow("Tarif affiché (reconstruit)", "${(erosion.stickerTotal / 100).formatDzd()} DA")
            Spacer(Modifier.height(ElTheme.spacing.sm))
            HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
            Spacer(Modifier.height(ElTheme.spacing.sm))
            ExecStatRow("Remise moyenne", "${(erosion.averageRemise / 100).formatDzd()} DA")
            ExecStatRow(
                "Amplitude",
                "${if (erosion.minRemise > 0) (erosion.minRemise / 100).formatDzd() else "—"} – ${(erosion.maxRemise / 100).formatDzd()} DA",
            )
            ExecStatRow("Familles concernées", "${erosion.remiseFamilyCount}")
        }
    }
}

// ============================================================================
// 5. Family Concentration (Pareto Risk)
// ============================================================================

@Composable
fun FamilyConcentrationCard(
    concentration: ExecConcentrationSnapshot,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors

    ElExpandableCard(
        title = "Concentration des Créances",
        subtitle = "Exposition Top Familles (80/20)",
        primaryFigure = "${concentration.topConcentrationPct}%",
        primaryFigureColor = if (concentration.topConcentrationPct >= 70) c.danger else c.primary,
        icon = Icons.Default.Group,
        headerBadge = {
            ElTag(
                text = "${concentration.debtorFamilyCount} débitrices",
                tone = if (concentration.debtorFamilyCount > 0) ElTagTone.WARNING else ElTagTone.SUCCESS,
            )
        },
        headerTrailing = { ElInfoTip(tip = "concentration.card") },
        modifier = modifier,
    ) {
        Text(
            text = if (concentration.debtorFamilyCount > 0) {
                "Top ${concentration.topFamilies.size} = ${concentration.topConcentrationPct}% de ${(concentration.totalOutstanding / 100).formatDzd()} DA · ${concentration.debtorFamilyCount} fam. débitrices"
            } else {
                "Exposition par tuteur — la règle des 80/20"
            },
            style = ElTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(ElTheme.spacing.md))

        if (concentration.debtorFamilyCount == 0) {
            Text(
                text = "Aucune famille débitrice — encours nul.",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = ElTheme.spacing.lg),
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("Famille", style = ElTheme.typography.labelSmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                Text("Enf.", style = ElTheme.typography.labelSmall, color = c.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
                Text("Encours", style = ElTheme.typography.labelSmall, color = c.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(88.dp))
                Text("Part", style = ElTheme.typography.labelSmall, color = c.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                Text("Retard", style = ElTheme.typography.labelSmall, color = c.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(ElTheme.spacing.xxxl))
            }
            concentration.topFamilies.forEach { f ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = f.parentName,
                        style = ElTheme.typography.bodySmall,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (f.childCount > 0) "${f.childCount} ×" else "—",
                        style = ElTheme.typography.bodySmall,
                        color = c.textPrimary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(36.dp),
                    )
                    Text(
                        text = "${(f.outstanding / 100).formatDzd()} DA",
                        style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textPrimary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(88.dp),
                    )
                    Text(
                        text = "${f.shareOfTotalDebt}%",
                        style = ElTheme.typography.bodySmall,
                        color = c.textPrimary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(44.dp),
                    )
                    Text(
                        text = "${f.worstDaysOverdue} j",
                        style = ElTheme.typography.labelSmall,
                        color = c.textSecondary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(ElTheme.spacing.xxxl),
                    )
                }
            }
            Spacer(Modifier.height(ElTheme.spacing.sm))
            HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
            Spacer(Modifier.height(ElTheme.spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Total top ${concentration.topFamilies.size}",
                    style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                )
                Text(
                    text = "${(concentration.topTotal / 100).formatDzd()} DA · ${concentration.topConcentrationPct}%",
                    style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                )
            }
        }
    }
}

// ============================================================================
// 6. Transport Yield
// ============================================================================

@Composable
fun TransportYieldCard(
    transport: ExecTransportSnapshot,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors

    ElExpandableCard(
        title = "Rendement Transport",
        subtitle = "Lignes, destinations & recouvrement",
        primaryFigure = "${transport.collectedPct}%",
        primaryFigureColor = if (transport.collectedPct >= 80) c.success else c.primary,
        icon = Icons.Default.DirectionsBus,
        headerBadge = {
            ElTag(
                text = "${transport.riders} transportés",
                tone = if (transport.riders > 0) ElTagTone.INFO else ElTagTone.NEUTRAL,
            )
        },
        headerTrailing = { ElInfoTip(tip = "transport.card") },
        modifier = modifier,
    ) {
        Text(
            text = if (transport.riders > 0) {
                "${transport.riders} transportés · ${(transport.paidTotal / 100).formatDzd()} / ${(transport.dueTotal / 100).formatDzd()} DA encaissés (${transport.collectedPct}%)"
            } else {
                "Rentabilité par ligne — destinations normalisées, remplissage et recouvrement"
            },
            style = ElTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(ElTheme.spacing.md))

        if (transport.riders == 0) {
            Text(
                text = "Aucune ligne de transport active.",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = ElTheme.spacing.lg),
            )
        } else {
            transport.routes.forEach { r ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = ElTheme.spacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = r.destinationLabel,
                            style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${r.riders} él.",
                            style = ElTheme.typography.labelSmall,
                            color = c.textSecondary,
                        )
                        Spacer(Modifier.width(ElTheme.spacing.sm))
                        Text(
                            text = "${r.collectedPct}%",
                            style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = c.textPrimary,
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
                        color = if (r.remainingTotal > 0) c.danger else c.textSecondary,
                    )
                }
            }
            if (transport.unresolvedRawValues.isNotEmpty()) {
                Spacer(Modifier.height(ElTheme.spacing.sm))
                Text(
                    text = "Valeurs non résolues : ${transport.unresolvedRawValues.joinToString(", ")}",
                    style = ElTheme.typography.labelSmall,
                    color = c.warning,
                )
            }
        }
    }
}

// ============================================================================
// 7. Specialized Services Yield
// ============================================================================

@Composable
fun ServiceYieldCard(
    services: List<ExecServiceItem>,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    val totalRevenue = services.sumOf { it.revenue }

    ElExpandableCard(
        title = "Services Spécialisés",
        subtitle = "Orthophonie, Psychologie & Soutien",
        primaryFigure = "${(totalRevenue / 100).formatDzd()} DA",
        primaryFigureColor = c.primary,
        icon = Icons.Default.MedicalServices,
        headerBadge = {
            ElTag(
                text = "${services.size} services",
                tone = if (services.isNotEmpty()) ElTagTone.INFO else ElTagTone.NEUTRAL,
            )
        },
        headerTrailing = { ElInfoTip(tip = "services.card") },
        modifier = modifier,
    ) {
        Text(
            text = if (services.isNotEmpty()) {
                "CA spécialisé : ${(totalRevenue / 100).formatDzd()} DA"
            } else {
                "Thérapies et soutien — le flux payé (PSY · ORTHO · E-PLANT)"
            },
            style = ElTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(ElTheme.spacing.md))

        if (services.isEmpty()) {
            Text(
                text = "Aucun encaissement sur les services spécialisés.",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = ElTheme.spacing.lg),
            )
        } else {
            services.forEach { s ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ElTheme.spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = s.label,
                        style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${s.studentCount} él. · ${s.paymentCount} paiements",
                        style = ElTheme.typography.labelSmall,
                        color = c.textSecondary,
                    )
                    Spacer(Modifier.width(ElTheme.spacing.sm))
                    Text(
                        text = "${(s.revenue / 100).formatDzd()} DA",
                        style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = c.textPrimary,
                    )
                }
            }
        }
    }
}

// ============================================================================
// 8. Enrollment Dynamics & Section Imbalance
// ============================================================================

@Composable
fun EnrollmentDynamicsCard(
    dynamics: ExecDynamicsSnapshot,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    val indexDisplay = dynamics.siblingIndex?.let { String.format(java.util.Locale.FRENCH, "%.2f", it) } ?: "—"

    ElExpandableCard(
        title = "Dynamique des Inscriptions",
        subtitle = "Fratries & Équilibre des Sections",
        primaryFigure = "$indexDisplay él/fam",
        primaryFigureColor = c.primary,
        icon = Icons.Default.Analytics,
        headerBadge = {
            if (dynamics.imbalances.isNotEmpty()) {
                ElTag(text = "${dynamics.imbalances.size} déséquilibres", tone = ElTagTone.WARNING)
            } else {
                ElTag(text = "Équilibré", tone = ElTagTone.SUCCESS)
            }
        },
        headerTrailing = { ElInfoTip(tip = "dynamics.card") },
        modifier = modifier,
    ) {
        Text(
            text = if (dynamics.totalStudents > 0) {
                "${dynamics.totalStudents} élèves / ${dynamics.totalFamilies} familles · indice fraternel $indexDisplay"
            } else {
                "Indice fraternel, taille des familles et déséquilibres de sections"
            },
            style = ElTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(ElTheme.spacing.md))

        if (dynamics.totalStudents == 0) {
            Text(
                text = "Aucun élève inscrit.",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = ElTheme.spacing.lg),
            )
        } else {
            dynamics.familySizes.forEach { fs ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = fs.label, style = ElTheme.typography.bodySmall, color = c.textPrimary)
                    Text(
                        text = "${fs.familyCount} fam. · ${fs.studentCount} él.",
                        style = ElTheme.typography.labelSmall,
                        color = c.textSecondary,
                    )
                }
            }
            if (dynamics.multiChildFamilyCount > 0) {
                Spacer(Modifier.height(ElTheme.spacing.xs))
                Text(
                    text = "${dynamics.multiChildFamilyCount} familles multi-enfants (${dynamics.multiChildFamilyPct}%)",
                    style = ElTheme.typography.labelSmall,
                    color = c.info,
                )
            }

            if (dynamics.imbalances.isNotEmpty()) {
                Spacer(Modifier.height(ElTheme.spacing.md))
                HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
                Spacer(Modifier.height(ElTheme.spacing.sm))
                Text(
                    text = "DÉSÉQUILIBRES DE SECTIONS",
                    style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = c.warning,
                )
                dynamics.imbalances.forEach { imb ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = ElTheme.spacing.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = imb.gradeLabel,
                                style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = c.textPrimary,
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
                            color = c.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// Helpers & Subcomponents
// ============================================================================

@Composable
private fun CallListRow(f: ExecCallListEntryItem) {
    val c = ElTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = f.parentName,
            style = ElTheme.typography.bodySmall,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(f.outstanding / 100).formatDzd()} DA",
            style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = c.danger,
        )
        Spacer(Modifier.width(ElTheme.spacing.sm))
        Text(
            text = "${f.worstDaysOverdue} j",
            style = ElTheme.typography.labelSmall,
            color = c.textSecondary,
        )
    }
}

@Composable
private fun PooledWaveMeter(w: ExecPooledWaveItem?, nowEpochMs: Long) {
    val c = ElTheme.colors
    val wave = w?.wave ?: return
    val dueTotal = w.dueTotal
    val paidTotal = w.paidTotal
    val pendingTotal = w.pendingTotal
    val remainingTotal = w.remainingTotal
    val installmentCount = w.installmentCount
    val settledCount = w.settledCount
    val familyCount = w.familyCount
    val debtorFamilyCount = w.debtorFamilyCount
    val overdueDebtorFamilyCount = w.overdueDebtorFamilyCount
    val overCoverageTotal = w.overCoverageTotal
    val collectedPct = w.collectedPct
    val anyUnsettledOverdue = w.anyUnsettledOverdue
    val anyUnsettledFuture = w.anyUnsettledFuture
    val title = WAVE_TITLES_FR[wave] ?: ("Tranche $wave" to "Toutes catégories")

    val isComplete = remainingTotal == 0L && installmentCount > 0
    val isOverdue = anyUnsettledOverdue
    val statusText = if (isComplete) "Clôturée" else if (isOverdue) "En retard" else "En cours"
    val statusTone = if (isComplete) ElTagTone.SUCCESS else if (isOverdue) ElTagTone.DANGER else ElTagTone.INFO

    val dueRangeLabel = formatDueDateRangeFr(w.dueDateMin, w.dueDateMax)
    val daysLate = waveDaysLate(w.dueDateMin, nowEpochMs)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.surfaceVariant.copy(alpha = 0.35f))
            .padding(ElTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
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
                        color = if (isOverdue && !isComplete) c.danger else c.textSecondary,
                    )
                }
            }
            ElTag(text = statusText, tone = statusTone)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "$collectedPct%",
                style = ElTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = c.textPrimary,
            )
            Text(
                text = "$settledCount/$installmentCount dossiers",
                style = ElTheme.typography.labelSmall,
                color = c.textSecondary,
            )
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
            height = 6,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm)) {
            PooledMetric("FACTURÉ", "${(dueTotal / 100).formatDzd()} DA", c.textPrimary, Modifier.weight(1f))
            PooledMetric("ENCAISSÉ", "${(paidTotal / 100).formatDzd()} DA", c.success, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm)) {
            PooledMetric(
                "EN COURS",
                "${(pendingTotal / 100).formatDzd()} DA",
                if (pendingTotal > 0L) c.info else c.textSecondary,
                Modifier.weight(1f),
            )
            PooledMetric(
                "RESTE DÛ",
                "${(remainingTotal / 100).formatDzd()} DA",
                if (remainingTotal > 0L) c.danger else c.textSecondary,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm)) {
            val familiesLabel = if (isOverdue) "FAMILLES EN RETARD" else if (anyUnsettledFuture) "FAMILLES À ÉCHOIR" else "FAMILLES NON SOLDÉES"
            val familiesValue = if (isOverdue) overdueDebtorFamilyCount else debtorFamilyCount
            val familiesColor = if (familiesValue > 0) {
                if (isOverdue) c.danger else c.warning
            } else c.textSecondary
            PooledMetric(familiesLabel, "$familiesValue / $familyCount", familiesColor, Modifier.weight(1f))
            PooledMetric("CATÉGORIES", "${w.perCategory.size}", c.textPrimary, Modifier.weight(1f))
        }

        Text(
            text = "Total dû ${(dueTotal / 100).formatDzd()} DA = Encaissé ${(paidTotal / 100).formatDzd()} DA + " +
                "En cours ${(pendingTotal / 100).formatDzd()} DA + Reste dû ${(remainingTotal / 100).formatDzd()} DA" +
                if (overCoverageTotal > 0L) " (+ ${(overCoverageTotal / 100).formatDzd()} DA couverts au-delà)" else "",
            style = ElTheme.textStyles.chartMicro,
            color = c.textMuted,
        )

        val perCategory = w.perCategory
        if (perCategory.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.xs),
            ) {
                perCategory.forEach { cat ->
                    val catPct = if (cat.dueTotal > 0L) com.example.core.execSharePct(cat.paidTotal, cat.dueTotal) else 0
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.surfaceVariant.copy(alpha = 0.4f))
                            .border(
                                width = 1.dp,
                                color = c.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = ElTheme.spacing.sm, vertical = ElTheme.spacing.xs),
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
                            color = if (cat.remainingTotal > 0L) c.danger else c.textPrimary,
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
internal fun PooledMetric(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.surfaceVariant.copy(alpha = 0.4f))
            .border(
                width = 1.dp,
                color = c.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(
                horizontal = ElTheme.spacing.sm,
                vertical = ElTheme.spacing.sm,
            ),
    ) {
        Text(
            text = label,
            style = ElTheme.textStyles.chartMicro.copy(letterSpacing = 0.8.sp),
            color = c.textMuted,
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

@Composable
private fun RiskCountTile(count: Int, label: String, modifier: Modifier = Modifier) {
    val c = ElTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(c.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = ElTheme.spacing.sm, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$count",
            style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = c.textPrimary,
        )
        Text(
            text = label,
            style = ElTheme.textStyles.chartMicro,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

// ============================================================================
// Composed Executive Dashboard
// ============================================================================

@Composable
fun ExecutiveDashboard(
    snapshot: ExecutiveStatsSnapshot,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md),
    ) {
        TripleRiskSummaryCard(summary = snapshot.riskSummary, profiles = snapshot.riskRadar)
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