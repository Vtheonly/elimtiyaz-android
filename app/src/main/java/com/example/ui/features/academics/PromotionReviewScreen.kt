package com.example.ui.features.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.PromotionDecisions
import com.example.core.getNextGradeProgression
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.display.ElChipVariant
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.overlays.ElDialogShell
import com.example.ui.designsystem.theme.ElTheme

/**
 * Vault §06.04 — Promotion Review Queue (Steps 3 + 4 of the One-Click Batch
 * Promotion Engine).
 *
 * Shows every ACTIVE student of the class with the canonical yearly GPA and
 * the system auto-flag (GPA ≥ 10 → APPROVED_FOR_PROMOTION, < 10 →
 * RETAINED_SAME_YEAR). The admin reviews the queue, applies manual exception
 * overrides with a note, then executes the whole batch in one click — the
 * execution path is the UNCHANGED canonical `promoteStudents` repository.
 */
@Composable
fun PromotionReviewScreen(
    classId: String,
    onBack: () -> Unit,
    viewModel: PromotionReviewViewModel = hiltViewModel(),
) {
    LaunchedEffect(classId) { viewModel.load(classId) }

    val klass by viewModel.klass.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    val canPromote = viewModel.canPromote
    val c = ElTheme.colors

    // Override dialog state (Step 3 — manual exception).
    var overrideTarget by remember { mutableStateOf<PromotionReviewViewModel.PromotionCandidate?>(null) }
    // Execution confirmation (Step 4).
    var showExecuteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearMessages()
        }
    }

    val approved = candidates.count { it.decision == PromotionDecisions.PROMOTED }
    val retained = candidates.count { it.decision == PromotionDecisions.REPEATED }
    val graduated = candidates.count { it.decision == PromotionDecisions.GRADUATED }
    val pendingReview = candidates.count { it.needsReview && !it.isOverridden }

    ElScaffold(
        topBar = {
            ElTopBar(
                title = "File de promotion — ${klass?.name ?: "…"}",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = ElTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Summary header ──────────────────────────────────────────
            ElCard(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(ElTheme.borders.thin, c.primary.copy(alpha = 0.45f)),
            ) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElSectionHeader(title = "Étape 3 sur 4 — Revue avant exécution")
                    Text(
                        "La moyenne annuelle de chaque élève a été calculée (moteur canonique, " +
                            "clubs exclus). GPA ≥ 10 → promotion · GPA < 10 → redoublement. " +
                            "Ajustez les cas particuliers avant d'exécuter le lot.",
                        style = ElTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SummaryStat("Promus", approved, c.success, Modifier.weight(1f))
                        SummaryStat("Redoublants", retained, c.danger, Modifier.weight(1f))
                        SummaryStat("Diplômés", graduated, c.primary, Modifier.weight(1f))
                    }
                    if (pendingReview > 0) {
                        Text(
                            "⚠ $pendingReview élève(s) sans notes — arbitrage manuel requis avant l'exécution.",
                            style = ElTheme.typography.bodySmall,
                            color = c.warning,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            message?.let { Text(it, color = c.success, style = ElTheme.typography.bodySmall) }
            error?.let { Text(it, color = c.danger, style = ElTheme.typography.bodySmall) }

            if (isLoading) {
                Text("Calcul des moyennes annuelles…", style = ElTheme.typography.bodyMedium)
            } else if (candidates.isEmpty()) {
                ElEmptyState(
                    icon = Icons.Default.School,
                    title = "Aucun élève à évaluer",
                    subtitle = "Cette classe ne compte aucun élève actif.",
                )
            } else {
                // ── Step 3: the review queue ────────────────────────────
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(candidates, key = { it.student.id }) { candidate ->
                        PromotionCandidateRow(
                            candidate = candidate,
                            onOverride = { overrideTarget = candidate },
                        )
                    }
                    item {
                        if (canPromote && candidates.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            ElButton(
                                text = if (isExecuting) "Exécution…" else "Exécuter la promotion ($approved promus / $retained redoublants)",
                                onClick = { showExecuteConfirm = true },
                                enabled = !isExecuting,
                                fullWidth = true,
                            )
                            Spacer(Modifier.height(12.dp))
                        } else if (!canPromote) {
                            Text(
                                "Permission manquante : PROMOTE_STUDENT.",
                                style = ElTheme.typography.bodySmall,
                                color = c.danger,
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Override dialog (manual exception with mandatory note) ─────────
    overrideTarget?.let { target ->
        OverrideDecisionDialog(
            candidate = target,
            onConfirm = { decision, note ->
                viewModel.overrideDecision(target.student.id, decision, note)
                overrideTarget = null
            },
            onReset = {
                viewModel.resetDecision(target.student.id)
                overrideTarget = null
            },
            onDismiss = { overrideTarget = null },
        )
    }

    // ── Execution confirmation (Step 4) ────────────────────────────────
    if (showExecuteConfirm) {
        ElDialogShell(onDismissRequest = { showExecuteConfirm = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Exécuter la promotion — ${klass?.name ?: ""}",
                    style = ElTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                )
                Text(
                    "$approved élève(s) seront promus au niveau suivant de l'échelle officielle, " +
                        "$retained redoubleront leur année (réinscrits au même niveau), " +
                        "$graduated seront marqués diplômés. " +
                        "L'opération est atomique, journalisée et propagée à la synchronisation.",
                    style = ElTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElButton(
                        text = "Exécuter",
                        onClick = {
                            showExecuteConfirm = false
                            viewModel.execute()
                        },
                        enabled = !isExecuting,
                        variant = ElButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f),
                    )
                    ElButton(
                        text = "Annuler",
                        onClick = { showExecuteConfirm = false },
                        variant = ElButtonVariant.GHOST,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** One queue row: identity, yearly GPA bar, recommendation + decision chips. */
@Composable
private fun PromotionCandidateRow(
    candidate: PromotionReviewViewModel.PromotionCandidate,
    onOverride: () -> Unit,
) {
    val c = ElTheme.colors
    val gpa = candidate.yearlyGpa
    val nextStep = getNextGradeProgression(candidate.student.gradeLevel)
    val nextLabel = when {
        nextStep.isGraduation -> "Diplômé (fin de scolarité)"
        nextStep.nextGradeCode != null -> "→ ${nextStep.nextGradeCode!!.uppercase()}"
        else -> "—"
    }
    ElCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOverride),
        size = ElCardSize.STANDARD,
        border = when (candidate.decision) {
            PromotionDecisions.PROMOTED, PromotionDecisions.GRADUATED ->
                BorderStroke(ElTheme.borders.thin, c.success.copy(alpha = 0.45f))
            else -> BorderStroke(ElTheme.borders.thin, c.danger.copy(alpha = 0.45f))
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ElAvatar(initials = candidate.student.fullName, size = ElAvatarSize.M)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(candidate.student.fullName, style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        "${candidate.student.gradeLevel.uppercase()} · $nextLabel · ${candidate.gradedSubjectCount} matière(s) évaluée(s)",
                        style = ElTheme.typography.labelSmall,
                        color = c.textSecondary,
                    )
                }
                DecisionTag(candidate.decision)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                gpa == null -> c.warning.copy(alpha = 0.15f)
                                gpa >= 10.0 -> c.success.copy(alpha = 0.15f)
                                else -> c.danger.copy(alpha = 0.15f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        gpa?.let { "%.1f".format(it) } ?: "—",
                        style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            gpa == null -> c.warning
                            gpa >= 10.0 -> c.success
                            else -> c.danger
                        },
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (gpa != null) "Moyenne annuelle — %.2f / 20".format(gpa) else "Aucune note saisie",
                        style = ElTheme.typography.labelSmall,
                        color = c.textSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    ElLinearProgress(progress = ((gpa ?: 0.0) / 20.0).toFloat())
                }
            }

            if (candidate.needsReview) {
                Text(
                    "Sans notes — arbitrage manuel requis (l'élève ne peut pas être promu automatiquement).",
                    style = ElTheme.typography.labelSmall,
                    color = c.warning,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (candidate.isOverridden) {
                Text(
                    "Décision manuelle (recommandation système : ${decisionLabel(candidate.recommendation)})" +
                        (candidate.overrideNote?.let { " — $it" } ?: ""),
                    style = ElTheme.typography.labelSmall,
                    color = c.primary,
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, count: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val c = ElTheme.colors
    Column(
        modifier = modifier
            .clip(ElTheme.shapes.small)
            .background(color.copy(alpha = 0.10f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$count", style = ElTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = color)
        Text(label, style = ElTheme.typography.labelSmall, color = c.textSecondary)
    }
}

@Composable
private fun DecisionTag(decision: String) {
    when (decision) {
        PromotionDecisions.PROMOTED -> ElTag(text = "APPROVED_FOR_PROMOTION", tone = ElTagTone.SUCCESS, size = ElTagSize.MD)
        PromotionDecisions.GRADUATED -> ElTag(text = "DIPLÔMÉ", tone = ElTagTone.INFO, size = ElTagSize.MD)
        else -> ElTag(text = "RETAINED_SAME_YEAR", tone = ElTagTone.DANGER, size = ElTagSize.MD)
    }
}

private fun decisionLabel(decision: String): String = when (decision) {
    PromotionDecisions.PROMOTED -> "promotion"
    PromotionDecisions.GRADUATED -> "diplôme"
    else -> "redoublement"
}

/** Step 3 dialog — manual exception override with an audit note. */
@Composable
private fun OverrideDecisionDialog(
    candidate: PromotionReviewViewModel.PromotionCandidate,
    onConfirm: (decision: String, note: String?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = ElTheme.colors
    var note by remember { mutableStateOf(candidate.overrideNote ?: "") }

    ElDialogShell(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                Text(
                    "Arbitrage — ${candidate.student.fullName}",
                    style = ElTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                )
                Text(
                    if (candidate.yearlyGpa != null)
                        "Moyenne annuelle : %.2f / 20 · recommandation système : %s."
                            .format(candidate.yearlyGpa, decisionLabel(candidate.recommendation))
                    else
                        "Aucune note saisie · le système ne recommande pas de promotion automatique.",
                    style = ElTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                Text("Nouvelle décision", style = ElTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElChip(
                        text = "Promouvoir",
                        variant = ElChipVariant.ASSIST,
                        onClick = { onConfirm(PromotionDecisions.PROMOTED, note) },
                        modifier = Modifier.weight(1f),
                    )
                    ElChip(
                        text = "Redoubler",
                        variant = ElChipVariant.ASSIST,
                        onClick = { onConfirm(PromotionDecisions.REPEATED, note) },
                        modifier = Modifier.weight(1f),
                    )
                }
                ElTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Motif de l'exception (audit)",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                )
                Text(
                    "Exemples : exception médicale, déménagement, décision de la direction.",
                    style = ElTheme.typography.labelSmall,
                    color = c.textSecondary,
                )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ElButton(
                    text = "Réinitialiser",
                    onClick = onReset,
                    variant = ElButtonVariant.GHOST,
                    modifier = Modifier.weight(1f),
                )
                ElButton(
                    text = "Fermer",
                    onClick = onDismiss,
                    variant = ElButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
