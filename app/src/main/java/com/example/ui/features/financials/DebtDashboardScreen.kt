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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.formatDzd
import com.example.domain.model.DebtSummary
import com.example.domain.repository.DebtRepository
import com.example.ui.components.ElCard
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElTag
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
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

@Composable
fun DebtDashboardScreen(
    onBack: () -> Unit,
    viewModel: DebtDashboardViewModel = hiltViewModel(),
) {
    val debtors by viewModel.debtors.collectAsState()
    val totalOutstanding = debtors.sumOf { it.outstandingAmount }
    val totalOverdue = debtors.filter { it.daysOverdue > 0 }.sumOf { it.outstandingAmount }

    Column(modifier = Modifier.fillMaxSize()) {
        ElTopBar(title = "Créances", onBack = onBack)

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ElCard(modifier = Modifier.fillMaxWidth(), accent = DangerRed) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Total en circulation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${(totalOutstanding / 100).formatDzd()} DZD", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                    if (totalOverdue > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("En retard: ${(totalOverdue / 100).formatDzd()} DZD", color = DangerRed, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        else -> DangerRed
    }
    ElCard(
        modifier = Modifier.fillMaxWidth(),
        accent = if (debtor.daysOverdue > 0) DangerRed else null,
        compact = true,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(debtor.parentName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                ElTag(text = debtor.bucket, color = bucketColor)
            }
            Spacer(Modifier.height(8.dp))
            ElInfoRow(label = "Téléphone", value = debtor.parentPhone)
            ElInfoRow(label = "Enfants", value = debtor.studentCount.toString())
            ElInfoRow(label = "Montant", value = "${(debtor.outstandingAmount / 100).formatDzd()} DZD", valueColor = MaterialTheme.colorScheme.primary)
            if (debtor.daysOverdue > 0) {
                Text("En retard de ${debtor.daysOverdue} jours", style = MaterialTheme.typography.bodySmall, color = DangerRed)
            }
        }
    }
}