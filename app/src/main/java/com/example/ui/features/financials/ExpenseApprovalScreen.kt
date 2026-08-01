package com.example.ui.features.financials

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.example.core.formatDzd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
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

@HiltViewModel
class ExpenseApprovalViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val expenses: StateFlow<List<Expense>> = expenseRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun approve(expense: Expense, note: String) {
        viewModelScope.launch {
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = expenseRepository.approve(expense.id, note, actorId, actorName)) {
                is Result.Ok -> {}
                is Result.Err -> _error.value = result.error.userMessage
            }
        }
    }

    fun reject(expense: Expense, reason: String) {
        viewModelScope.launch {
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = expenseRepository.reject(expense.id, reason, actorId, actorName)) {
                is Result.Ok -> {}
                is Result.Err -> _error.value = result.error.userMessage
            }
        }
    }

    fun disburse(expense: Expense) {
        viewModelScope.launch {
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = expenseRepository.disburse(expense.id, actorId, actorName)) {
                is Result.Ok -> {}
                is Result.Err -> _error.value = result.error.userMessage
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseApprovalScreen(
    expenseId: String?,
    onBack: () -> Unit,
    viewModel: ExpenseApprovalViewModel = hiltViewModel(),
) {
    val expenses by viewModel.expenses.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dépenses") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(expenses) { expense ->
                    ExpenseCard(
                        expense = expense,
                        onApprove = { viewModel.approve(expense, "Approuvé") },
                        onReject = { viewModel.reject(expense, "Rejeté") },
                        onDisburse = { viewModel.disburse(expense) },
                    )
                }
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
    val statusColor = when (expense.status) {
        "approved" -> MaterialTheme.colorScheme.primary
        "rejected" -> MaterialTheme.colorScheme.error
        "settled" -> MaterialTheme.colorScheme.tertiary
        "disbursed" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(expense.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(expense.status, style = MaterialTheme.typography.labelSmall, color = statusColor)
            }
            Spacer(Modifier.height(4.dp))
            Text("${expense.requestCode} • ${expense.category}", style = MaterialTheme.typography.bodySmall)
            Text(expense.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Text("Montant: ${(expense.amount / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
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
                        androidx.compose.material3.TextButton(onClick = onApprove) { Text("Approuver") }
                        androidx.compose.material3.TextButton(onClick = onReject) { Text("Rejeter") }
                    }
                    "approved" -> {
                        androidx.compose.material3.TextButton(onClick = onDisburse) { Text("Débourser") }
                    }
                }
            }
        }
    }
}
