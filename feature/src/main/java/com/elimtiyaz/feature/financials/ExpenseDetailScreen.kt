package com.elimtiyaz.feature.financials

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.elimtiyaz.core.common.ExpenseStatus
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.ErrorState
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.domain.model.Expense
import java.io.File

/** Expense detail screen — card, timeline, anomaly badge, status-gated actions. */
@Composable
fun ExpenseDetailScreen(
    nav: NavController,
    vm: ExpenseViewModel = hiltViewModel(),
) {
    val state by vm.detailState.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val canApprove = session?.can(Permission.ApproveExpense) == true
    val canDisburse = session?.can(Permission.DisburseExpense) == true
    val canSettle = session?.can(Permission.SettleExpenseProof) == true

    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectNote by remember { mutableStateOf("") }
    var capturedProofUri by remember { mutableStateOf<String?>(null) }

    // Camera capture launcher for the proof settlement step.
    val proofFile = remember { File.createTempFile("expense_proof_${System.currentTimeMillis()}", ".jpg", context.cacheDir) }
    var proofUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && proofUri != null) {
            capturedProofUri = proofUri.toString()
            // Immediately call settleProof — the repository handles upload.
            state.expense?.let { e -> vm.settleProof(e.id, proofUri.toString()) { ok, _ -> if (ok) capturedProofUri = null } }
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", proofFile)
            proofUri = uri
            cameraLauncher.launch(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dépense", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { inner ->
        when {
            state.isLoading && state.expense == null -> LoadingState(Modifier.padding(inner))
            state.error != null && state.expense == null ->
                ErrorState(state.error!!, onRetry = { state.expense?.let { vm.loadDetail(it.id) } }, modifier = Modifier.padding(inner))
            else -> {
                val e = state.expense ?: return@Scaffold
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .verticalScroll(rememberScrollState())
                        .padding(ElimtiyazSpacing.x4),
                    verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
                ) {
                    // --- Header card ---
                    ElImtiyazCard {
                        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.requestCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                StatusChip(label = ExpenseStatus.from(e.status)?.displayFr ?: e.status, tone = expenseTone(e.status))
                            }
                            Spacer(Modifier.height(ElimtiyazSpacing.x2))
                            Text(e.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            if (e.description.isNotBlank()) {
                                Spacer(Modifier.height(ElimtiyazSpacing.x2))
                                Text(e.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(ElimtiyazSpacing.x3))
                            DetailRow("Montant", Formatters.currency(e.amount))
                            DetailRow("Catégorie", labelForExpenseCategory(e.category))
                            DetailRow("Bénéficiaire", e.payee)
                            DetailRow("Soumis par", e.submittedBy)
                            DetailRow("Soumis le", Formatters.dateTime(e.submittedAt))

                            // Anomaly badge
                            e.anomalyScore?.let { score ->
                                if (score > 0.7) {
                                    Spacer(Modifier.height(ElimtiyazSpacing.x2))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ElimtiyazColors.DangerRed.copy(alpha = 0.18f))
                                            .padding(ElimtiyazSpacing.x3),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Outlined.Warning, contentDescription = null, tint = ElimtiyazColors.DangerRed)
                                        Spacer(Modifier.width(ElimtiyazSpacing.x2))
                                        Column {
                                            Text("Anomalie détectée (score ${"%.2f".format(score)})", style = MaterialTheme.typography.labelLarge, color = ElimtiyazColors.DangerRed, fontWeight = FontWeight.SemiBold)
                                            e.anomalyNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- Timeline ---
                    SectionHeader("Suivi")
                    ElImtiyazCard {
                        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
                            TimelineRow("Soumise", e.submittedAt, e.submittedBy, done = true)
                            TimelineRow(
                                label = "Approuvée",
                                at = e.approvedAt,
                                actor = e.approvedBy,
                                done = e.approvedAt != null,
                                note = e.approvalNote,
                                rejected = e.status == "rejected",
                            )
                            TimelineRow(
                                label = "Décaissée",
                                at = e.disbursedAt,
                                actor = e.disbursedBy,
                                done = e.disbursedAt != null,
                            )
                            TimelineRow(
                                label = "Justifiée",
                                at = e.proofUploadedAt,
                                actor = e.proofUploadedBy,
                                done = e.proofUploadedAt != null,
                            )
                        }
                    }

                    // --- Proof image (if any) ---
                    e.proofUrl?.let { url ->
                        SectionHeader("Justificatif")
                        ElImtiyazCard {
                            AsyncImage(
                                model = url,
                                contentDescription = "Justificatif",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .padding(ElimtiyazSpacing.x2),
                            )
                        }
                    }

                    // --- Actions by status & permission ---
                    when (e.status) {
                        "submitted" -> if (canApprove) {
                            Row(horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2)) {
                                Button(
                                    onClick = { vm.approve(e.id, note = null) { _, _ -> } },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElimtiyazColors.SuccessGreen),
                                ) { Text("Approuver") }
                                OutlinedButton(
                                    onClick = { showRejectDialog = true; rejectNote = "" },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElimtiyazColors.DangerRed),
                                ) { Text("Rejeter") }
                            }
                        }
                        "approved" -> if (canDisburse) {
                            Button(
                                onClick = { vm.disburse(e.id) { _, _ -> } },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) { Text("Décaisser") }
                        }
                        "disbursed" -> if (canSettle) {
                            Button(
                                onClick = { cameraPermission.launch(android.Manifest.permission.CAMERA) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                leadingIcon = { Icon(Icons.Outlined.CameraAlt, contentDescription = null) },
                            ) { Text("Téléverser justificatif") }
                        }
                    }
                    Spacer(Modifier.height(ElimtiyazSpacing.x4))
                }
            }
        }
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Rejeter la dépense") },
            text = {
                Column {
                    Text("Indiquez le motif du rejet (obligatoire).")
                    Spacer(Modifier.height(ElimtiyazSpacing.x2))
                    OutlinedTextField(
                        value = rejectNote,
                        onValueChange = { rejectNote = it },
                        label = { Text("Motif") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = rejectNote.isNotBlank(),
                    onClick = {
                        val note = rejectNote
                        showRejectDialog = false
                        state.expense?.let { e -> vm.reject(e.id, note) { _, _ -> } }
                    },
                ) { Text("Confirmer", color = ElimtiyazColors.DangerRed) }
            },
            dismissButton = { TextButton(onClick = { showRejectDialog = false }) { Text("Annuler") } },
        )
    }
}

/** Vertical timeline row with a state dot, label, actor and date. */
@Composable
private fun TimelineRow(
    label: String,
    at: String?,
    actor: String?,
    done: Boolean,
    note: String? = null,
    rejected: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = ElimtiyazSpacing.x1)) {
        val dotColor = when {
            rejected -> ElimtiyazColors.DangerRed
            done -> ElimtiyazColors.SuccessGreen
            else -> MaterialTheme.colorScheme.outlineVariant
        }
        Box(
            modifier = Modifier
                .padding(top = 4.dp, end = ElimtiyazSpacing.x3)
                .size(12.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (at != null) {
                Text(
                    "${Formatters.dateTime(at)} • ${actor ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("En attente", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            note?.let {
                Text("Note: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A small label/value row inside a card. */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ElimtiyazSpacing.x1),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** French label for an expense category. */
private fun labelForExpenseCategory(c: com.elimtiyaz.domain.model.ExpenseCategory): String = when (c) {
    com.elimtiyaz.domain.model.ExpenseCategory.Utilities    -> "Factures (eau/élec/gaz)"
    com.elimtiyaz.domain.model.ExpenseCategory.Supplies     -> "Fournitures"
    com.elimtiyaz.domain.model.ExpenseCategory.Maintenance  -> "Maintenance"
    com.elimtiyaz.domain.model.ExpenseCategory.Transport    -> "Transport"
    com.elimtiyaz.domain.model.ExpenseCategory.Event        -> "Événement"
    com.elimtiyaz.domain.model.ExpenseCategory.Salary       -> "Salaires"
    com.elimtiyaz.domain.model.ExpenseCategory.Tax          -> "Taxes / Impôts"
    com.elimtiyaz.domain.model.ExpenseCategory.Rent         -> "Loyer"
    com.elimtiyaz.domain.model.ExpenseCategory.Other        -> "Autre"
}
