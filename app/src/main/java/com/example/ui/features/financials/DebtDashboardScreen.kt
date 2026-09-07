package com.example.ui.features.financials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.formatDzd
import com.example.domain.model.DebtSummary
import com.example.domain.repository.DebtRepository
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElTag
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.util.PhoneUtils
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

    var bucketFilter by remember { mutableStateOf<String?>(null) }
    val filtered = if (bucketFilter == null) debtors else debtors.filter { it.bucket == bucketFilter }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        ElTopBar(title = "Créances & Retards", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ElCard(modifier = Modifier.weight(1f), compact = true) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total créances", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("${(totalOutstanding / 100).formatDzd()} DA", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryBlue)
                        }
                    }
                    ElCard(modifier = Modifier.weight(1f), accent = DangerRed, compact = true) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total en retard", style = MaterialTheme.typography.labelSmall, color = DangerRed)
                            Spacer(Modifier.height(4.dp))
                            Text("${(totalOverdue / 100).formatDzd()} DA", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DangerRed)
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        null to "Toutes (${debtors.size})",
                        "0_30" to "0–30 j",
                        "31_60" to "31–60 j",
                        "61_90" to "61–90 j",
                        "180_plus" to "180+ j",
                    ).forEach { (b, label) ->
                        ElTag(
                            text = label,
                            selected = bucketFilter == b,
                            color = if (b == "180_plus") DangerRed else PrimaryBlue,
                            onClick = { bucketFilter = b },
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    ElEmptyState(
                        icon = Icons.Default.Call,
                        title = "Aucune créance",
                        message = "Toutes les familles sélectionnées sont à jour.",
                    )
                }
            } else {
                items(filtered) { debtor ->
                    val bucketColor = when (debtor.bucket) {
                        "0_30" -> PrimaryBlue
                        "31_60" -> MaterialTheme.colorScheme.tertiary
                        "61_90" -> MaterialTheme.colorScheme.secondary
                        else -> DangerRed
                    }
                    ElCard(
                        modifier = Modifier.fillMaxWidth(),
                        accent = if (debtor.daysOverdue > 0) DangerRed else null,
                        compact = true,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(debtor.parentName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("${debtor.studentCount} élève(s) rattaché(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                ElTag(text = debtor.bucket.replace("_", "–") + " j", color = bucketColor)
                            }
                            Spacer(Modifier.height(8.dp))
                            ElInfoRow(label = "Téléphone", value = debtor.parentPhone)
                            ElInfoRow(label = "Montant dû", value = "${(debtor.outstandingAmount / 100).formatDzd()} DZD", valueColor = DangerRed)

                            if (debtor.daysOverdue > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("En retard de ${debtor.daysOverdue} jours", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = DangerRed)
                                    if (debtor.parentPhone.isNotBlank()) {
                                        IconButton(onClick = { PhoneUtils.dial(context, debtor.parentPhone) }) {
                                            Icon(Icons.Default.Call, contentDescription = "Appeler", tint = SuccessGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}