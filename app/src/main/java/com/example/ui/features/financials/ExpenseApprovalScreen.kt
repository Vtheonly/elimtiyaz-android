package com.example.ui.features.financials

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.core.formatDzd
import com.example.domain.model.Expense
import com.example.domain.repository.ExpenseRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 */
@HiltViewModel
class ExpenseApprovalViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
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

    /** Load a single expense by id (for detail mode). */
    fun loadDetail(expenseId: String) {
        viewModelScope.launch {
            expenseRepository.observeById(expenseId).collect { exp ->
                _detailExpense.value = exp
            }
        }
    }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseApprovalScreen(
    expenseId: String?,
    onBack: () -> Unit,
    onNavigateToProofScanner: (() -> Unit)? = null,
    viewModel: ExpenseApprovalViewModel = hiltViewModel(),
) {
    val expenses by viewModel.expenses.collectAsState()
    val detailExpense by viewModel.detailExpense.collectAsState()
    val error by viewModel.error.collectAsState()
    val info by viewModel.info.collectAsState()

    var rejectTarget by remember { mutableStateOf<Expense?>(null) }
    var settleTarget by remember { mutableStateOf<Expense?>(null) }

    LaunchedEffect(expenseId) {
        if (expenseId != null) viewModel.loadDetail(expenseId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (expenseId != null) "Détail dépense" else "Dépenses") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }
            info?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            }

            if (expenseId != null) {
                // ── Detail mode ─────────────────────────────────────────────
                val exp = detailExpense
                if (exp == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Chargement…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    ExpenseDetailView(
                        expense = exp,
                        onApprove = { viewModel.approve(exp, "Approuvé") },
                        onReject = { rejectTarget = exp },
                        onDisburse = { viewModel.disburse(exp) },
                        onSettleProof = { settleTarget = exp },
                        onNavigateToProofScanner = onNavigateToProofScanner,
                    )
                }
            } else {
                // ── List mode (approval queue) ──────────────────────────────
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

    // ── Reject dialog (mandatory reason) ─────────────────────────────────
    rejectTarget?.let { exp ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("Rejeter la dépense") },
            text = {
                Column {
                    Text("${exp.title} — ${exp.requestCode}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Motif du rejet *") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reject(exp, reason)
                    rejectTarget = null
                }) { Text("Rejeter") }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) { Text("Annuler") }
            },
        )
    }

    // ── Settle-proof dialog (final amount + proof path) ─────────────────
    settleTarget?.let { exp ->
        var finalAmount by remember { mutableStateOf("") }
        var proofPath by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { settleTarget = null },
            title = { Text("Téléverser le justificatif") },
            text = {
                Column {
                    Text("${exp.title} — ${exp.requestCode}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = finalAmount,
                        onValueChange = { finalAmount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        label = { Text("Montant final dépensé (DZD) *") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = proofPath,
                        onValueChange = { proofPath = it },
                        label = { Text("Chemin du justificatif *") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (onNavigateToProofScanner != null) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onNavigateToProofScanner) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(Modifier.height(4.dp))
                            Text("Scanner un justificatif")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = finalAmount.replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (amt > 0 && proofPath.isNotBlank()) {
                        viewModel.settleProof(exp, proofPath, (amt * 100).toLong())
                        settleTarget = null
                    }
                }) { Text("Clôturer") }
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
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDisburse: () -> Unit,
    onSettleProof: () -> Unit,
    onNavigateToProofScanner: (() -> Unit)?,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(expense.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        ExpenseStatusChip(status = expense.status)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${expense.requestCode} • ${expense.category}", style = MaterialTheme.typography.bodySmall)
                    if (expense.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(expense.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Montant: ${(expense.amount / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Bénéficiaire: ${expense.payee}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ── Anomaly banner ──────────────────────────────────────────────
        if (expense.anomalyScore != null && expense.anomalyScore > 0.5) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("⚠ Anomalie détectée (score: ${expense.anomalyScore})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        expense.anomalyNote?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("L'IA est un signal d'aide à la décision, pas un verdict — l'humain décide toujours.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        // ── 4-stage timeline (submitted → approved → disbursed → settled) ─
        item { ExpenseTimeline(expense) }

        // ── Actions (gated by status) ───────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (expense.status) {
                    "submitted" -> {
                        TextButton(onClick = onApprove) { Icon(Icons.Default.Check, contentDescription = null); Spacer(Modifier.height(2.dp)); Text(" Approuver") }
                        TextButton(onClick = onReject) { Icon(Icons.Default.Close, contentDescription = null); Spacer(Modifier.height(2.dp)); Text(" Rejeter") }
                    }
                    "approved" -> {
                        TextButton(onClick = onDisburse) { Text("Décaisser les fonds") }
                    }
                    "disbursed" -> {
                        TextButton(onClick = onSettleProof) { Icon(Icons.Default.UploadFile, contentDescription = null); Spacer(Modifier.height(2.dp)); Text(" Téléverser justificatif") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseTimeline(expense: Expense) {
    val stages = listOf(
        Triple("Soumise", expense.submittedAt, expense.submittedBy),
        Triple("Approuvée", expense.approvedAt, expense.approvedBy),
        Triple("Décaissée", expense.disbursedAt, expense.disbursedBy),
        Triple("Justificatif téléversé", expense.proofUploadedAt, expense.proofUploadedBy),
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Cycle de vie", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            stages.forEachIndexed { idx, (label, at, by) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    val active = at != null
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .height(12.dp)
                            .padding(0.dp),
                    ) {
                        Icon(
                            imageVector = if (active) Icons.Default.Check else Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(0.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                        if (at != null) Text("Le $at${if (by != null) " par $by" else ""}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (idx < stages.lastIndex) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            if (expense.status == "rejected") {
                Spacer(Modifier.height(8.dp))
                Text("Rejetée${expense.approvalNote?.let { " : $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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
    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(expense.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                ExpenseStatusChip(status = expense.status)
            }
            Spacer(Modifier.height(4.dp))
            Text("${expense.requestCode} • ${expense.category}", style = MaterialTheme.typography.bodySmall)
            if (expense.description.isNotBlank()) {
                Text(expense.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Text("Montant: ${(expense.amount / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("Bénéficiaire: ${expense.payee}", style = MaterialTheme.typography.bodySmall)

            if (expense.anomalyScore != null && expense.anomalyScore > 0.5) {
                Text(
                    "⚠ Anomalie détectée (score: ${expense.anomalyScore})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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

@Composable
private fun ExpenseStatusChip(status: String) {
    val color = when (status) {
        "approved" -> MaterialTheme.colorScheme.primary
        "rejected" -> MaterialTheme.colorScheme.error
        "settled" -> MaterialTheme.colorScheme.tertiary
        "disbursed" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = status.replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(color, shape = RoundedCornerShape(8.dp)),
    )
}
