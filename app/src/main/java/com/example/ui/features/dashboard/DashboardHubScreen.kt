package com.example.ui.features.dashboard

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Session
import com.example.domain.model.AppNotification
import com.example.domain.model.DashboardKpi
import com.example.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _kpis = MutableStateFlow<DashboardKpi?>(null)
    val kpis: StateFlow<DashboardKpi?> = _kpis.asStateFlow()

    val alerts: StateFlow<List<AppNotification>> = kotlinx.coroutines.flow.flowOf(emptyList())
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@Composable
fun DashboardHubScreen(
    session: Session,
    onNavigateToStudent: (String) -> Unit,
    onNavigateToParent: (String) -> Unit,
    onNavigateToCounterPayment: () -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val kpis by viewModel.kpis.collectAsState()
    val alerts by viewModel.alerts.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // KPI grid
        kpis?.let { k ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                KpiCard("Élèves", k.totalStudents.toString(), Modifier.weight(1f))
                KpiCard("Parents", k.totalParents.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                KpiCard("Revenu mensuel", "${(k.monthlyRevenue / 100).formatDzd()} DZD", Modifier.weight(1f))
                KpiCard("Personnel", k.totalStaff.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                KpiCard("Créances", "${(k.outstandingDebt / 100).formatDzd()} DZD", Modifier.weight(1f))
                KpiCard("Dépenses", k.pendingExpenses.toString(), Modifier.weight(1f))
            }
        } ?: run {
            Text("Chargement des KPIs...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Alerts feed
        Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Alertes récentes", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (alerts.isEmpty()) {
                    Text("Aucune alerte", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    alerts.take(5).forEach { alert ->
                        Text("• ${alert.title}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Quick actions
        Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Actions rapides", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.TextButton(onClick = onNavigateToCounterPayment) { Text("Encaisser un paiement") }
                androidx.compose.material3.TextButton(onClick = onNavigateToDebtDashboard) { Text("Voir les créances") }
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(elevation = CardDefaults.cardElevation(2.dp), modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun Long.formatDzd(): String = "%,.0f".format(this.toDouble())
