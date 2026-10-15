package com.example.ui.features.financials

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.core.Permission
import com.example.core.Result
import com.example.core.Role
import com.example.domain.model.Expense
import com.example.domain.model.Personnel
import com.example.domain.repository.ExpenseRepository
import com.example.domain.repository.PersonnelRepository
import com.example.domain.repository.StorageBuckets
import com.example.session.SessionManager
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElInfoRow
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.feedback.ElLoadingBlock
import com.example.ui.designsystem.components.feedback.ElSpinner
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.foundation.elMoneyFormat
import com.example.ui.designsystem.foundation.elMoneyParse
import com.example.ui.designsystem.overlays.ElDialogShell
import com.example.ui.designsystem.overlays.LocalElToast
import com.example.ui.designsystem.theme.ElTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

/**
 * Expense approval + detail ViewModel.
 *
 * Two modes (driven by the `expenseId` route arg):
 *  - `expenseId == null` → approval queue list mode (all expenses, sortable by status).
 *  - `expenseId != null`  → single-expense detail mode with full timeline + actions.
 *
 * Restored behavior (commit a34333a):
 *  - Reject requires a mandatory reason (per desktop §08).
 *  - `settleProof` action exposed (calls `expenseRepository.settleProof`).
 *  - No-self-approval enforced client-side (defense in depth alongside the DB trigger).
 *
 * T-322 fixes: the detail collector is CANCELLED per loadDetail call (the old
 * code leaked one collector per call); the submitter display name resolves via
 * [PersonnelRepository.observeByUserId]; the action permission getters let the
 * UI hide actions the session cannot exercise (the server stays the source of
 * truth — this only avoids surfacing raw server errors to unauthorized roles).
 */
@HiltViewModel
class ExpenseApprovalViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val personnelRepository: PersonnelRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val expenses: StateFlow<List<Expense>> = expenseRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _detailExpense = MutableStateFlow<Expense?>(null)
    val detailExpense: StateFlow<Expense?> = _detailExpense.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info.asStateFlow()

    /** T-322: submitter display name (from personnel via user id). */
    private val _submitterName = MutableStateFlow<String?>(null)
    val submitterName: StateFlow<String?> = _submitterName.asStateFlow()

    private var detailJob: Job? = null

    /** Load a single expense by id (for detail mode). */
    fun loadDetail(expenseId: String) {
        // T-322 fix: replace the previous collector instead of stacking one per call.
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            expenseRepository.observeById(expenseId).collectLatest { exp ->
                _detailExpense.value = exp
                _submitterName.value = exp?.let { resolveUserName(it.submittedBy) }
            }
        }
    }

    private suspend fun resolveUserName(userId: String): String? =
        personnelRepository.observeByUserId(userId).firstOrNull()?.fullName

    // ── UI permission gates (server still enforces — defense in depth) ──
    // T-521 (issue #29 / WORKFORCE-519a — the T-492 class): these were PLAIN
    // GETTERS reading sessionManager.current() imperatively — the composable
    // never subscribed to the session flow, so whenever the detail composed
    // against a not-yet-restored session the buttons rendered DISABLED (the
    // "approve does not work" report). The gates are now COLLECTED STATE
    // derived from the session flow (combine().stateIn() — the T-492 fix
    // pattern): every session emission re-derives them and recomposition
    // picks the change up.
    val canApprove: StateFlow<Boolean> = sessionManager.state
        .map { s ->
            s?.can(Permission.APPROVE_EXPENSE) == true || s?.role == Role.SUPER_ADMIN
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    val canDisburse: StateFlow<Boolean> = sessionManager.state
        .map { s ->
            s?.can(Permission.DISBURSE_EXPENSE) == true || s?.role == Role.SUPER_ADMIN
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    val canSettleProof: StateFlow<Boolean> = sessionManager.state
        .map { s ->
            s?.can(Permission.SETTLE_EXPENSE_PROOF) == true || s?.role == Role.SUPER_ADMIN
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun approve(expense: Expense, note: String) {
        // No-self-approval (defense in depth — the DB trigger is the source of truth)
        val currentUserId = sessionManager.currentUserId()
        if (expense.submittedBy == currentUserId) {
            _error.value = "Auto-approbation interdite (plan §08)."
            return
        }
        viewModelScope.launch {
            val actorId = currentUserId ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = expenseRepository.approve(expense.id, note, actorId, actorName)) {
                is Result.Ok -> _info.value = "Dépense approuvée."
                is Result.Err -> _error.value = result.error.userMessage
            }
        }
    }

    fun reject(expense: Expense, reason: String) {
        if (reason.isBlank()) {
            _error.value = "Un motif de rejet est obligatoire."
            return
        }
        viewModelScope.launch {
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = expenseRepository.reject(expense.id, reason, actorId, actorName)) {
                is Result.Ok -> _info.value = "Dépense rejetée."
                is Result.Err -> _error.value = result.error.userMessage
            }
        }
    }

    fun disburse(expense: Expense) {
        viewModelScope.launch {
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = expenseRepository.disburse(expense.id, actorId, actorName)) {
                is Result.Ok -> _info.value = "Fonds décaissés."
                is Result.Err -> _error.value = result.error.userMessage
            }
        }
    }

    /** Upload the receipt proof + set the final spent amount, marking the expense settled. */
    fun settleProof(expense: Expense, proofPath: String, finalAmount: Long) {
        viewModelScope.launch {
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = expenseRepository.settleProof(expense.id, proofPath, finalAmount, actorId, actorName)) {
                is Result.Ok -> _info.value = "Justificatif téléversé, dépense clôturée."
                is Result.Err -> _error.value = result.error.userMessage
            }
        }
    }

    fun clearError() { _error.value = null }
    fun clearInfo() { _info.value = null }
}

/** French status label + tone with stage nuance (T-322). */
internal fun expenseStatusLabel(status: String): Pair<String, ElTagTone> = when (status) {
    "draft" -> "Brouillon" to ElTagTone.NEUTRAL
    "submitted" -> "En attente de validation" to ElTagTone.WARNING
    "approved" -> "Approuvée (en attente de décaissement)" to ElTagTone.INFO
    "rejected" -> "Rejetée" to ElTagTone.DANGER
    "disbursed" -> "Fonds décaissés" to ElTagTone.INFO
    "settled" -> "Dépense clôturée avec justificatif" to ElTagTone.SUCCESS
    else -> status to ElTagTone.NEUTRAL
}

@Composable
fun ExpenseApprovalScreen(
    expenseId: String?,
    onBack: () -> Unit,
    /** T-545 / WORKFORCE-522: the proof capture is now IN-CONTEXT — the
     *  T-521 navigation loop (the standalone scanner route + the nav-result
     *  path return) is retired; the ONE upload engine (ProofUploadViewModel)
     *  runs inside the settle dialog itself. */
    viewModel: ExpenseApprovalViewModel = hiltViewModel(),
    proofUploadViewModel: ProofUploadViewModel = hiltViewModel(),
) {
    val c = ElTheme.colors
    val expenses by viewModel.expenses.collectAsState()
    val detailExpense by viewModel.detailExpense.collectAsState()
    val submitterName by viewModel.submitterName.collectAsState()
    val error by viewModel.error.collectAsState()
    val info by viewModel.info.collectAsState()

    var rejectTarget by remember { mutableStateOf<Expense?>(null) }
    var settleTarget by remember { mutableStateOf<Expense?>(null) }

    LaunchedEffect(expenseId) {
        if (expenseId != null) viewModel.loadDetail(expenseId)
    }

    ElScaffold(
        topBar = {
            ElTopBar(
                title = if (expenseId != null) "Détail dépense" else "Dépenses",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(ElTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md),
        ) {
            error?.let {
                ElAlertBanner(
                    title = "Erreur",
                    message = it,
                    severity = ElAlertSeverity.DANGER,
                    onDismiss = viewModel::clearError,
                )
            }
            info?.let {
                ElAlertBanner(
                    title = "Succès",
                    message = it,
                    severity = ElAlertSeverity.SUCCESS,
                    onDismiss = viewModel::clearInfo,
                )
            }

            if (expenseId != null) {
                // ── Detail mode ─────────────────────────────────────────────
                val exp = detailExpense
                if (exp == null) {
                    ElLoadingBlock(modifier = Modifier.fillMaxWidth())
                } else {
                    ExpenseDetailView(
                        expense = exp,
                        submitterName = submitterName,
                        canApprove = viewModel.canApprove.collectAsState().value,
                        canDisburse = viewModel.canDisburse.collectAsState().value,
                        canSettleProof = viewModel.canSettleProof.collectAsState().value,
                        onApprove = { viewModel.approve(exp, "Approuvé") },
                        onReject = { rejectTarget = exp },
                        onDisburse = { viewModel.disburse(exp) },
                        onSettleProof = { settleTarget = exp },
                    )
                }
            } else {
                // ── List mode (approval queue) ──────────────────────────────
                if (expenses.isEmpty()) {
                    ElEmptyState(
                        title = "Aucune dépense",
                        subtitle = "Les demandes soumises apparaîtront ici.",
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm)) {
                        items(expenses) { expense ->
                            ExpenseCard(
                                expense = expense,
                                onApprove = { viewModel.approve(expense, "Approuvé") },
                                onReject = { rejectTarget = expense },
                                onDisburse = { viewModel.disburse(expense) },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Reject dialog (mandatory reason, ≥3 chars — refund-dialog parity) ──
    rejectTarget?.let { exp ->
        var reason by remember { mutableStateOf("") }
        // T-460 H2 (issue #3 F-10): the raw AlertDialog → the DS dialog shell
        // (ElDialogShell + ElTextField + ElButton — the GradeEntry/EmployeeDirectory
        // dialog language). Every user-facing string preserved.
        ElDialogShell(onDismissRequest = { rejectTarget = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ElTheme.spacing.xl, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md),
            ) {
                Text(
                    "Rejeter la dépense ${exp.requestCode}",
                    style = ElTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                )
                Text(
                    "${exp.title} — ${elMoneyFormat(exp.amount)}",
                    style = ElTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                ElTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = "Motif du rejet *",
                    helperText = "Minimum 3 caractères",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    ElButton(
                        text = "Annuler",
                        onClick = { rejectTarget = null },
                        variant = ElButtonVariant.GHOST,
                        modifier = Modifier.weight(1f),
                    )
                    ElButton(
                        text = "Rejeter",
                        onClick = {
                            viewModel.reject(exp, reason)
                            rejectTarget = null
                        },
                        enabled = reason.trim().length >= 3,
                        variant = ElButtonVariant.DANGER,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // ── Settle-proof dialog (final amount + proof path) ─────────────────
    settleTarget?.let { exp ->
        // T-322: prefill with the declared amount (exact DZD), parse with
        // elMoneyParse (EXACT centimes — never toDouble()*100).
        var finalAmount by remember { mutableStateOf((exp.amount / 100).toString()) }
        var proofPath by remember { mutableStateOf("") }
        var settleError by remember { mutableStateOf<String?>(null) }

        // T-545 / WORKFORCE-522 — the IN-CONTEXT capture (the owner's
        // clarified scope: "simply take a picture and attach it"). The
        // T-521 navigation loop is REPLACED by Take Photo / Galerie buttons
        // inside this dialog: capture → preview → the ONE upload engine
        // (ProofUploadViewModel, the expense-receipts bucket under the
        // expense id — the T-521 path canon) → the uploaded path auto-fills
        // the proof field below. The manual path field is preserved.
        val context = LocalContext.current
        val toast = LocalElToast.current
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val proofUploading by proofUploadViewModel.isLoading.collectAsState()
        val proofUploadedPath by proofUploadViewModel.uploadedPath.collectAsState()
        val proofUploadError by proofUploadViewModel.error.collectAsState()
        var capturePreview by remember { mutableStateOf<Uri?>(null) }

        // A fresh dialog for a (possibly different) expense starts clean.
        LaunchedEffect(exp.id) {
            proofUploadViewModel.reset()
            capturePreview = null
        }
        // T-521 contract preserved: the uploaded path auto-fills the proof
        // field (the manual re-typing loop stays dead).
        LaunchedEffect(proofUploadedPath) {
            proofUploadedPath?.let { proofPath = it }
        }

        fun launchCameraCapture(file: File): Uri? =
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.onFailure { e -> toast.showError("Erreur caméra : ${e.message}") }
                .getOrNull()

        fun uploadProofBitmap(bitmap: Bitmap?) {
            if (bitmap == null) {
                toast.showError("Impossible de lire l'image capturée")
                return
            }
            // The T-521 path canon: the expense-receipts bucket under the
            // expense id — the same path shape the desktop's attachments read.
            scope.launch {
                proofUploadViewModel.uploadProof(
                    bitmap = bitmap,
                    entityId = exp.id,
                    bucket = StorageBuckets.EXPENSE_RECEIPTS,
                )
            }
        }

        val cameraLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture(),
        ) { success: Boolean ->
            if (success) {
                val path = capturePreview?.path
                val bitmap = path?.let { BitmapFactory.decodeFile(it) }
                uploadProofBitmap(bitmap)
            } else {
                capturePreview = null
            }
        }

        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted: Boolean ->
            if (granted) {
                val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
                val file = File(cameraDir, "proof_${System.currentTimeMillis()}.jpg")
                launchCameraCapture(file)?.let { uri ->
                    capturePreview = uri
                    cameraLauncher.launch(uri)
                }
            } else {
                toast.showWarning("Permission caméra nécessaire pour prendre une photo")
            }
        }

        val galleryLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            if (uri != null) {
                capturePreview = uri
                val bitmap = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
                uploadProofBitmap(bitmap)
            }
        }

        // T-460 H2 (issue #3 F-10): the raw AlertDialog → the DS dialog shell.
        ElDialogShell(onDismissRequest = { settleTarget = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ElTheme.spacing.xl, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md),
            ) {
                Text(
                    "Téléverser le justificatif",
                    style = ElTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                )
                Text(
                    "${exp.title} — ${exp.requestCode} • Décaissée : ${elMoneyFormat(exp.amount)}",
                    style = ElTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                ElTextField(
                    value = finalAmount,
                    onValueChange = { finalAmount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' || ch == ' ' } },
                    label = "Montant final dépensé (DZD) *",
                    isError = settleError != null,
                    errorText = settleError,
                    modifier = Modifier.fillMaxWidth(),
                )
                ElTextField(
                    value = proofPath,
                    onValueChange = { proofPath = it; settleError = null },
                    label = "Chemin du justificatif *",
                    isError = settleError != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                // T-545 / WORKFORCE-522: the Take Photo / Galerie capture —
                // the attachment options INSIDE the existing settle dialog
                // (the standalone scanner screen is gone). Capture → preview
                // below → upload → the path field above auto-fills.
                Row(horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    ElButton(
                        text = "Prendre une photo",
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
                                val file = File(cameraDir, "proof_${System.currentTimeMillis()}.jpg")
                                launchCameraCapture(file)?.let { uri ->
                                    capturePreview = uri
                                    cameraLauncher.launch(uri)
                                }
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        variant = ElButtonVariant.SECONDARY,
                        icon = Icons.Default.CameraAlt,
                        enabled = !proofUploading,
                        modifier = Modifier.weight(1f),
                    )
                    ElButton(
                        text = "Galerie",
                        onClick = { galleryLauncher.launch("image/*") },
                        variant = ElButtonVariant.SECONDARY,
                        icon = Icons.Default.Image,
                        enabled = !proofUploading,
                        modifier = Modifier.weight(1f),
                    )
                }
                // The capture preview — visible until the dialog closes.
                capturePreview?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Aperçu du justificatif capturé",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                if (proofUploading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
                    ) {
                        ElSpinner(size = 18, strokeWidth = 2)
                        Text(
                            "Téléversement du justificatif…",
                            style = ElTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                }
                proofUploadError?.let { err ->
                    ElAlertBanner(title = "Erreur de téléversement", message = err, severity = ElAlertSeverity.DANGER)
                }
                proofUploadedPath?.let { path ->
                    if (!path.startsWith("file://")) {
                        Text(
                            "Justificatif envoyé : $path",
                            style = ElTheme.typography.labelSmall,
                            color = c.success,
                        )
                    } else {
                        Text(
                            "Mode hors-ligne : preuve conservée sur l'appareil, PAS encore sur le serveur.",
                            style = ElTheme.typography.labelSmall,
                            color = c.warning,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    ElButton(
                        text = "Annuler",
                        onClick = { settleTarget = null },
                        variant = ElButtonVariant.GHOST,
                        modifier = Modifier.weight(1f),
                    )
                    ElButton(
                        text = "Clôturer",
                        onClick = {
                            val cents = elMoneyParse(finalAmount)
                            when {
                                cents <= 0L -> settleError = "Le montant final doit être supérieur à 0"
                                proofPath.isBlank() -> settleError = "Le justificatif est obligatoire avant clôture"
                                else -> {
                                    viewModel.settleProof(exp, proofPath, cents)
                                    settleTarget = null
                                }
                            }
                        },
                        variant = ElButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Detail view: header card + 4-stage timeline + action buttons + anomaly banner.
 * Mirrors the desktop `expense-detail-drawer.tsx` layout.
 */
@Composable
private fun ExpenseDetailView(
    expense: Expense,
    submitterName: String?,
    canApprove: Boolean,
    canDisburse: Boolean,
    canSettleProof: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDisburse: () -> Unit,
    onSettleProof: () -> Unit,
) {
    val c = ElTheme.colors
    LazyColumn(verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md)) {
        item {
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ElTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            expense.title,
                            style = ElTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        val (label, tone) = expenseStatusLabel(expense.status)
                        ElTag(text = label, tone = tone)
                    }
                    Text(
                        "${expense.requestCode} • ${expenseCategoryLabel(expense.category)}",
                        style = ElTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                    if (expense.description.isNotBlank()) {
                        Text(expense.description, style = ElTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(ElTheme.spacing.xs))
                    ElInfoRow(label = "Montant", value = elMoneyFormat(expense.amount))
                    ElInfoRow(
                        label = "Demandeur",
                        value = submitterName ?: expense.submittedBy,
                        valueTint = c.textPrimary,
                    )
                    ElInfoRow(label = "Bénéficiaire", value = expense.payee, valueTint = c.textPrimary)
                }
            }
        }

        // ── Anomaly banner ──────────────────────────────────────────────
        if (expense.anomalyScore != null && expense.anomalyScore > 0.5) {
            item {
                ElAlertBanner(
                    title = "Anomalie détectée (score : ${expense.anomalyScore})",
                    message = (expense.anomalyNote?.plus(" ") ?: "") +
                        "L'IA est un signal d'aide à la décision, pas un verdict — l'humain décide toujours.",
                    severity = ElAlertSeverity.DANGER,
                )
            }
        }

        // ── 4-stage timeline (submitted → approved → disbursed → settled) ─
        item { ExpenseTimeline(expense) }

        // ── Actions (gated by status AND session permissions) ────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm)) {
                when (expense.status) {
                    "submitted" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm)) {
                            ElButton(
                                text = "Approuver",
                                onClick = onApprove,
                                variant = ElButtonVariant.PRIMARY,
                                icon = Icons.Default.Check,
                                enabled = canApprove,
                            )
                            ElButton(
                                text = "Rejeter",
                                onClick = onReject,
                                variant = ElButtonVariant.DANGER,
                                icon = Icons.Default.Close,
                                enabled = canApprove,
                            )
                        }
                        if (!canApprove) {
                            Text(
                                "Action réservée aux rôles avec la permission d'approbation.",
                                style = ElTheme.typography.labelSmall,
                                color = c.textSecondary,
                            )
                        }
                    }
                    "approved" -> {
                        ElButton(
                            text = "Décaisser les fonds",
                            onClick = onDisburse,
                            variant = ElButtonVariant.PRIMARY,
                            enabled = canDisburse,
                        )
                        if (!canDisburse) {
                            Text(
                                "Action réservée aux rôles avec la permission de décaissement.",
                                style = ElTheme.typography.labelSmall,
                                color = c.textSecondary,
                            )
                        }
                    }
                    "disbursed" -> {
                        ElButton(
                            text = "Téléverser justificatif",
                            onClick = onSettleProof,
                            variant = ElButtonVariant.SECONDARY,
                            icon = Icons.Default.UploadFile,
                            enabled = canSettleProof,
                        )
                        if (!canSettleProof) {
                            Text(
                                "Action réservée aux rôles avec la permission de clôture.",
                                style = ElTheme.typography.labelSmall,
                                color = c.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The detail view's action gating is parameter-driven (canApprove/canDisburse/canSettleProof). */

@Composable
private fun ExpenseTimeline(expense: Expense) {
    val stages = listOf(
        Triple("Soumise", expense.submittedAt as String?, expense.submittedBy as String?),
        Triple("Approuvée", expense.approvedAt, expense.approvedBy),
        Triple("Décaissée", expense.disbursedAt, expense.disbursedBy),
        Triple("Justificatif téléversé", expense.proofUploadedAt, expense.proofUploadedBy),
    )
    ElCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ElTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
        ) {
            ElSectionHeader(title = "Cycle de vie")
            stages.forEachIndexed { idx, (label, at, by) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    val active = at != null
                    androidx.compose.material3.Icon(
                        imageVector = if (active) Icons.Default.Check else Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (active) ElTheme.colors.success else ElTheme.colors.textSecondary,
                        modifier = Modifier.padding(end = ElTheme.spacing.sm),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            label,
                            style = ElTheme.typography.bodySmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (at != null) {
                            Text(
                                "Le $at${if (by != null) " par $by" else ""}",
                                style = ElTheme.typography.labelSmall,
                                color = ElTheme.colors.textSecondary,
                            )
                        }
                    }
                }
                if (idx < stages.lastIndex) {
                    Spacer(modifier = Modifier.height(ElTheme.spacing.xs))
                }
            }
            if (expense.status == "rejected") {
                Spacer(Modifier.height(ElTheme.spacing.sm))
                Text(
                    "Rejetée${expense.approvalNote?.let { " : $it" } ?: ""}",
                    style = ElTheme.typography.bodySmall,
                    color = ElTheme.colors.danger,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ExpenseCard(
    expense: Expense,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDisburse: () -> Unit,
) {
    val c = ElTheme.colors
    ElCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ElTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    expense.title,
                    style = ElTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                val (label, tone) = expenseStatusLabel(expense.status)
                ElTag(text = label, tone = tone)
            }
            Text(
                "${expense.requestCode} • ${expenseCategoryLabel(expense.category)}",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            if (expense.description.isNotBlank()) {
                Text(expense.description, style = ElTheme.typography.bodySmall, maxLines = 2)
            }
            Text(
                "Montant : ${elMoneyFormat(expense.amount)}",
                style = ElTheme.typography.bodyMedium,
                color = c.primary,
                fontWeight = FontWeight.Bold,
            )
            Text("Bénéficiaire : ${expense.payee}", style = ElTheme.typography.bodySmall)

            if (expense.anomalyScore != null && expense.anomalyScore > 0.5) {
                Text(
                    "⚠ Anomalie détectée (score : ${expense.anomalyScore})",
                    style = ElTheme.typography.bodySmall,
                    color = c.danger,
                )
            }

            Spacer(Modifier.height(ElTheme.spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm)) {
                when (expense.status) {
                    "submitted" -> {
                        ElButton(text = "Approuver", onClick = onApprove, variant = ElButtonVariant.TONAL, size = ElButtonSize.SMALL)
                        ElButton(text = "Rejeter", onClick = onReject, variant = ElButtonVariant.DANGER, size = ElButtonSize.SMALL)
                    }
                    "approved" -> {
                        ElButton(text = "Décaisser", onClick = onDisburse, variant = ElButtonVariant.PRIMARY, size = ElButtonSize.SMALL)
                    }
                }
            }
        }
    }
}

internal fun expenseCategoryLabel(code: String): String = when (code) {
    "utilities" -> "Utilities"
    "supplies" -> "Fournitures"
    "maintenance" -> "Maintenance"
    "transport" -> "Transport"
    "event" -> "Événement"
    "salary" -> "Salaires"
    "tax" -> "Taxes"
    "rent" -> "Loyer"
    "other" -> "Autre"
    else -> code
}
