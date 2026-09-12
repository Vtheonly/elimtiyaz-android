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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Permission
import com.example.core.Result
import com.example.core.Role
import com.example.domain.model.Expense
import com.example.domain.model.Personnel
import com.example.domain.repository.ExpenseRepository
import com.example.domain.repository.PersonnelRepository
import com.example.session.SessionManager
import com.example.ui.designsystem.components.button.ElButton
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
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.foundation.elMoneyFormat
import com.example.ui.designsystem.foundation.elMoneyParse
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    // ── UI permission getters (server still enforces — defense in depth) ──
    val canApprove: Boolean
        get() = sessionManager.current()?.can(Permission.APPROVE_EXPENSE) == true ||
            sessionManager.current()?.role == Role.SUPER_ADMIN
    val canDisburse: Boolean
        get() = sessionManager.current()?.can(Permission.DISBURSE_EXPENSE) == true ||
            sessionManager.current()?.role == Role.SUPER_ADMIN
    val canSettleProof: Boolean
        get() = sessionManager.current()?.can(Permission.SETTLE_EXPENSE_PROOF) == true ||
            sessionManager.current()?.role == Role.SUPER_ADMIN

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
    onNavigateToProofScanner: (() -> Unit)? = null,
    viewModel: ExpenseApprovalViewModel = hiltViewModel(),
) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        canApprove = viewModel.canApprove,
                        canDisburse = viewModel.canDisburse,
                        canSettleProof = viewModel.canSettleProof,
                        onApprove = { viewModel.approve(exp, "Approuvé") },
                        onReject = { rejectTarget = exp },
                        onDisburse = { viewModel.disburse(exp) },
                        onSettleProof = { settleTarget = exp },
                        onNavigateToProofScanner = onNavigateToProofScanner,
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
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("Rejeter la dépense ${exp.requestCode}") },
            text = {
                Column {
                    Text("${exp.title} — ${elMoneyFormat(exp.amount)}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Motif du rejet *") },
                        supportingText = { Text("Minimum 3 caractères") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.reject(exp, reason)
                        rejectTarget = null
                    },
                    enabled = reason.trim().length >= 3,
                ) { Text("Rejeter") }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) { Text("Annuler") }
            },
        )
    }

    // ── Settle-proof dialog (final amount + proof path) ─────────────────
    settleTarget?.let { exp ->
        // T-322: prefill with the declared amount (exact DZD), parse with
        // elMoneyParse (EXACT centimes — never toDouble()*100).
        var finalAmount by remember { mutableStateOf((exp.amount / 100).toString()) }
        var proofPath by remember { mutableStateOf("") }
        var settleError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { settleTarget = null },
            title = { Text("Téléverser le justificatif") },
            text = {
                Column {
                    Text(
                        "${exp.title} — ${exp.requestCode} • Décaissée : ${elMoneyFormat(exp.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = finalAmount,
                        onValueChange = { finalAmount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' || ch == ' ' } },
                        label = { Text("Montant final dépensé (DZD) *") },
                        isError = settleError != null,
                        supportingText = { settleError?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = proofPath,
                        onValueChange = { proofPath = it; settleError = null },
                        label = { Text("Chemin du justificatif *") },
                        isError = settleError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (onNavigateToProofScanner != null) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onNavigateToProofScanner) {
                            androidx.compose.material3.Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(Modifier.height(4.dp))
                            Text("Scanner un justificatif")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
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
                ) { Text("Clôturer") }
            },
            dismissButton = {
                TextButton(onClick = { settleTarget = null }) { Text("Annuler") }
            },
        )
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
    onNavigateToProofScanner: (() -> Unit)?,
) {
    val c = ElTheme.colors
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            expense.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        val (label, tone) = expenseStatusLabel(expense.status)
                        ElTag(text = label, tone = tone)
                    }
                    Text(
                        "${expense.requestCode} • ${expenseCategoryLabel(expense.category)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                    if (expense.description.isNotBlank()) {
                        Text(expense.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(4.dp))
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (expense.status) {
                    "submitted" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                style = MaterialTheme.typography.labelSmall,
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
                                style = MaterialTheme.typography.labelSmall,
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
                                style = MaterialTheme.typography.labelSmall,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (at != null) {
                            Text(
                                "Le $at${if (by != null) " par $by" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElTheme.colors.textSecondary,
                            )
                        }
                    }
                }
                if (idx < stages.lastIndex) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            if (expense.status == "rejected") {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Rejetée${expense.approvalNote?.let { " : $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    expense.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                val (label, tone) = expenseStatusLabel(expense.status)
                ElTag(text = label, tone = tone)
            }
            Text(
                "${expense.requestCode} • ${expenseCategoryLabel(expense.category)}",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            if (expense.description.isNotBlank()) {
                Text(expense.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Text(
                "Montant : ${elMoneyFormat(expense.amount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = c.primary,
                fontWeight = FontWeight.Bold,
            )
            Text("Bénéficiaire : ${expense.payee}", style = MaterialTheme.typography.bodySmall)

            if (expense.anomalyScore != null && expense.anomalyScore > 0.5) {
                Text(
                    "⚠ Anomalie détectée (score : ${expense.anomalyScore})",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.danger,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (expense.status) {
                    "submitted" -> {
                        TextButton(onClick = onApprove) { Text("Approuver") }
                        TextButton(onClick = onReject) { Text("Rejeter") }
                    }
                    "approved" -> {
                        TextButton(onClick = onDisburse) { Text("Décaisser") }
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
