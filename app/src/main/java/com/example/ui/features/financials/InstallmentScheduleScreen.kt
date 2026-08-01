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
import com.example.core.PaymentStatus
import com.example.core.formatDzd
import com.example.domain.model.Installment
import com.example.domain.repository.InstallmentRepository
import com.example.ui.components.ElCard
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElProgressBar
import com.example.ui.components.ElScaffold
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class InstallmentScheduleViewModel @Inject constructor(
    private val installmentRepository: InstallmentRepository,
) : ViewModel() {
    val installments: StateFlow<List<Installment>> = kotlinx.coroutines.flow.flowOf(emptyList<Installment>())
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@Composable
fun InstallmentScheduleScreen(
    onBack: () -> Unit,
    viewModel: InstallmentScheduleViewModel = hiltViewModel(),
) {
    val installments by viewModel.installments.collectAsState()

    ElScaffold(
        topBar = { ElTopBar(title = "Tranches", onBack = onBack) },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (installments.isEmpty()) {
                Text("Selectionnez un parent pour voir ses tranches.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val totalDue = installments.sumOf { it.amountDue }
                val totalPaid = installments.sumOf { it.amountPaid }
                val progress = if (totalDue > 0) totalPaid.toFloat() / totalDue.toFloat() else 0f

                ElCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), accent = PrimaryBlue) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        ElSectionHeader(title = "Progression")
                        Spacer(Modifier.height(8.dp))
                        ElProgressBar(progress = progress)
                        Spacer(Modifier.height(8.dp))
                        Text("${(totalPaid / 100).formatDzd()} / ${(totalDue / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
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
    val (statusColor, statusText) = when (installment.status) {
        PaymentStatus.PAID -> SuccessGreen to "Paye"
        PaymentStatus.OVERDUE -> DangerRed to "En retard"
        PaymentStatus.PENDING -> PrimaryBlue to "En attente"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to installment.status.name
    }
    ElCard(
        modifier = Modifier.fillMaxWidth(),
        accent = statusColor,
        compact = true,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(installment.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                ElTag(text = statusText, color = statusColor, selected = true)
            }
            Spacer(Modifier.height(8.dp))
            ElInfoRow(label = "Echeance", value = installment.dueDate)
            ElInfoRow(label = "Montant", value = "${(installment.amountDue / 100).formatDzd()} DZD")
            ElInfoRow(label = "Paye", value = "${(installment.amountPaid / 100).formatDzd()} DZD", valueColor = SuccessGreen)
            ElInfoRow(label = "Restant", value = "${(installment.remaining / 100).formatDzd()} DZD", valueColor = if (installment.remaining > 0) DangerRed else SuccessGreen)
        }
    }
}
