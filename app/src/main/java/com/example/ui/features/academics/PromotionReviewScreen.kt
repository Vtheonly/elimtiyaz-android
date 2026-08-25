package com.example.ui.features.academics

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElProgressBar
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElScaffold
import com.example.ui.components.ElTag
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold

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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── Summary header ──────────────────────────────────────────
            ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ElSectionHeader(title = "Étape 3 sur 4 — Revue avant exécution")
                    Text(
                        "La moyenne annuelle de chaque élève a été calculée (moteur canonique, " +
                            "clubs exclus). GPA ≥ 10 → promotion · GPA < 10 → redoublement. " +
                            "Ajustez les cas particuliers avant d'exécuter le lot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SummaryStat("Promus", approved, SuccessGreen, Modifier.weight(1f))
                        SummaryStat("Redoublants", retained, DangerRed, Modifier.weight(1f))
                        SummaryStat("Diplômés", graduated, PrimaryBlue, Modifier.weight(1f))
                    }
                    if (pendingReview > 0) {
                        Text(
                            "⚠ $pendingReview élève(s) sans notes — arbitrage manuel requis avant l'exécution.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarmGold,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            message?.let { Text(it, color = SuccessGreen, style = MaterialTheme.typography.bodySmall) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            if (isLoading) {
                Text("Calcul des moyennes annuelles…", style = MaterialTheme.typography.bodyMedium)
            } else if (candidates.isEmpty()) {
                ElEmptyState(
                    icon = Icons.Default.School,
                    title = "Aucun élève à évaluer",
                    message = "Cette classe ne compte aucun élève actif.",
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
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
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
        AlertDialog(
            onDismissRequest = { showExecuteConfirm = false },
            title = { Text("Exécuter la promotion — ${klass?.name ?: ""}") },
            text = {
                Text(
                    "$approved élève(s) seront promus au niveau suivant de l'échelle officielle, " +
                        "$retained redoubleront leur année (réinscrits au même niveau), " +
                        "$graduated seront marqués diplômés. " +
                        "L'opération est atomique, journalisée et propagée à la synchronisation.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExecuteConfirm = false
                        viewModel.execute()
                    },
                    enabled = !isExecuting,
                ) { Text("Exécuter") }
            },
            dismissButton = {
                TextButton(onClick = { showExecuteConfirm = false }) { Text("Annuler") }
            },
        )
    }
}

/** One queue row: identity, yearly GPA bar, recommendation + decision chips. */
@Composable
private fun PromotionCandidateRow(
    candidate: PromotionReviewViewModel.PromotionCandidate,
    onOverride: () -> Unit,
) {
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
        accent = when (candidate.decision) {
            PromotionDecisions.PROMOTED, PromotionDecisions.GRADUATED -> SuccessGreen
            else -> DangerRed
        },
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ElAvatar(initials = candidate.student.fullName, size = 40)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(candidate.student.fullName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        "${candidate.student.gradeLevel.uppercase()} · $nextLabel · ${candidate.gradedSubjectCount} matière(s) évaluée(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DecisionTag(candidate.decision)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                gpa == null -> WarmGold.copy(alpha = 0.15f)
                                gpa >= 10.0 -> SuccessGreen.copy(alpha = 0.15f)
                                else -> DangerRed.copy(alpha = 0.15f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        gpa?.let { "%.1f".format(it) } ?: "—",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            gpa == null -> WarmGold
                            gpa >= 10.0 -> SuccessGreen
                            else -> DangerRed
                        },
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (gpa != null) "Moyenne annuelle — %.2f / 20".format(gpa) else "Aucune note saisie",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    ElProgressBar(progress = ((gpa ?: 0.0) / 20.0).toFloat())
                }
            }

            if (candidate.needsReview) {
                Text(
                    "Sans notes — arbitrage manuel requis (l'élève ne peut pas être promu automatiquement).",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarmGold,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (candidate.isOverridden) {
                Text(
                    "Décision manuelle (recommandation système : ${decisionLabel(candidate.recommendation)})" +
                        (candidate.overrideNote?.let { " — $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryBlue,
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, count: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.10f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$count", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DecisionTag(decision: String) {
    when (decision) {
        PromotionDecisions.PROMOTED -> ElTag(text = "APPROVED_FOR_PROMOTION", color = SuccessGreen)
        PromotionDecisions.GRADUATED -> ElTag(text = "DIPLÔMÉ", color = PrimaryBlue)
        else -> ElTag(text = "RETAINED_SAME_YEAR", color = DangerRed)
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
    var note by remember { mutableStateOf(candidate.overrideNote ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Arbitrage — ${candidate.student.fullName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (candidate.yearlyGpa != null)
                        "Moyenne annuelle : %.2f / 20 · recommandation système : %s."
                            .format(candidate.yearlyGpa, decisionLabel(candidate.recommendation))
                    else
                        "Aucune note saisie · le système ne recommande pas de promotion automatique.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Nouvelle décision", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElTag(
                        text = "Promouvoir",
                        selected = true,
                        color = SuccessGreen,
                        onClick = { onConfirm(PromotionDecisions.PROMOTED, note) },
                    )
                    ElTag(
                        text = "Redoubler",
                        selected = true,
                        color = DangerRed,
                        onClick = { onConfirm(PromotionDecisions.REPEATED, note) },
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Motif de l'exception (audit)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Text(
                    "Exemples : exception médicale, déménagement, décision de la direction.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("Réinitialiser") }
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        },
    )
}
