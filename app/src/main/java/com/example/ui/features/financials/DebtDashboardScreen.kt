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
import com.example.domain.model.DebtSummary
import com.example.domain.repository.DebtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DebtDashboardViewModel @Inject constructor(
    private val debtRepository: DebtRepository,
) : ViewModel() {
    val debtors: StateFlow<List<DebtSummary>> = debtRepository.observeSummary()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDashboardScreen(
    onBack: () -> Unit,
    viewModel: DebtDashboardViewModel = hiltViewModel(),
) {
    val debtors by viewModel.debtors.collectAsState()
    val totalOutstanding = debtors.sumOf { it.outstandingAmount }
    val totalOverdue = debtors.filter { it.daysOverdue > 0 }.sumOf { it.outstandingAmount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Créances") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Total en circulation", style = MaterialTheme.typography.labelMedium)
                    Text("${(totalOutstanding / 100).formatDzd()} DZD", style = MaterialTheme.typography.headlineMedium)
                    if (totalOverdue > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("En retard: ${(totalOverdue / 100).formatDzd()} DZD", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(debtors) { debtor ->
                    DebtorCard(debtor)
                }
            }
        }
    }
}

@Composable
private fun DebtorCard(debtor: DebtSummary) {
    val bucketColor = when (debtor.bucket) {
        "0_30" -> MaterialTheme.colorScheme.primary
        "31_60" -> MaterialTheme.colorScheme.tertiary
        "61_90" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(debtor.parentName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(debtor.bucket, style = MaterialTheme.typography.labelSmall, color = bucketColor)
            }
            Spacer(Modifier.height(4.dp))
            Text("Téléphone: ${debtor.parentPhone}", style = MaterialTheme.typography.bodySmall)
            Text("Enfants: ${debtor.studentCount}", style = MaterialTheme.typography.bodySmall)
            Text("Montant: ${(debtor.outstandingAmount / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (debtor.daysOverdue > 0) {
                Text("En retard de ${debtor.daysOverdue} jours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
