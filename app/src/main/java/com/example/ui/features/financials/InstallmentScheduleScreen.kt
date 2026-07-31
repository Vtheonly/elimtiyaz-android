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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.PaymentStatus
import com.example.domain.model.Installment
import com.example.domain.repository.InstallmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class InstallmentScheduleViewModel @Inject constructor(
    private val installmentRepository: InstallmentRepository,
) : ViewModel() {
    // Placeholder — would observe installments for a selected parent/student
    val installments: StateFlow<List<Installment>> = kotlinx.coroutines.flow.flowOf(emptyList())
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentScheduleScreen(
    onBack: () -> Unit,
    viewModel: InstallmentScheduleViewModel = hiltViewModel(),
) {
    val installments by viewModel.installments.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tranches") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (installments.isEmpty()) {
                Text("Sélectionnez un parent pour voir ses tranches.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val totalDue = installments.sumOf { it.amountDue }
                val totalPaid = installments.sumOf { it.amountPaid }
                val progress = if (totalDue > 0) totalPaid.toFloat() / totalDue.toFloat() else 0f

                Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Progression", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("${(totalPaid / 100).formatDzd()} / ${(totalDue / 100).formatDzd()} DZD")
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(installments) { inst ->
                        InstallmentCard(inst)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallmentCard(installment: Installment) {
    val statusColor = when (installment.status) {
        PaymentStatus.PAID -> MaterialTheme.colorScheme.primary
        PaymentStatus.OVERDUE -> MaterialTheme.colorScheme.error
        PaymentStatus.PENDING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(installment.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    installment.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("Échéance: ${installment.dueDate}", style = MaterialTheme.typography.bodySmall)
            Text("Montant: ${(installment.amountDue / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodySmall)
            Text("Payé: ${(installment.amountPaid / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodySmall)
            Text("Restant: ${(installment.remaining / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodySmall)
        }
    }
}
