package com.example.ui.features.routing

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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.domain.model.TripLog
import com.example.domain.repository.RoutingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Trip history ViewModel — restores the pre-redesign `TripHistoryViewModel` (commit a34333a).
 */
@HiltViewModel
class TripHistoryViewModel @Inject constructor(
    private val routingRepository: RoutingRepository,
) : ViewModel() {

    val trips: StateFlow<List<TripLog>> = routingRepository.observeTripHistory()
        .map { result -> when (result) { is Result.Ok -> result.value; is Result.Err -> emptyList() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selected = MutableStateFlow<TripLog?>(null)
    val selected: StateFlow<TripLog?> = _selected.asStateFlow()

    fun select(trip: TripLog?) { _selected.value = trip }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripHistoryScreen(
    onBack: () -> Unit,
    viewModel: TripHistoryViewModel = hiltViewModel(),
) {
    val trips by viewModel.trips.collectAsState()
    val selected by viewModel.selected.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique des tournées") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        if (trips.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Aucune tournée enregistrée.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(trips) { trip ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.select(trip) },
                        elevation = CardDefaults.cardElevation(1.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("Véhicule: ${trip.vehicleId}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Début: ${trip.startedAt}", style = MaterialTheme.typography.labelSmall)
                            trip.endedAt?.let { Text("Fin: $it", style = MaterialTheme.typography.labelSmall) }
                            Text("Arrêts: ${trip.stopsCompleted}/${trip.stopsPlanned}", style = MaterialTheme.typography.bodySmall)
                            Text("Distance: %.2f km".format(trip.totalDistanceKm), style = MaterialTheme.typography.bodySmall)
                            trip.notes?.let { Text("Notes: $it", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }

    selected?.let { trip ->
        AlertDialog(
            onDismissRequest = { viewModel.select(null) },
            title = { Text("Tournée du ${trip.startedAt.take(10)}") },
            text = {
                Column {
                    Text("Véhicule: ${trip.vehicleId}", style = MaterialTheme.typography.bodySmall)
                    Text("Chauffeur: ${trip.driverId}", style = MaterialTheme.typography.bodySmall)
                    Text("Début: ${trip.startedAt}", style = MaterialTheme.typography.bodySmall)
                    Text("Fin: ${trip.endedAt ?: "En cours"}", style = MaterialTheme.typography.bodySmall)
                    Text("Arrêts: ${trip.stopsCompleted}/${trip.stopsPlanned}", style = MaterialTheme.typography.bodySmall)
                    Text("Distance: %.2f km".format(trip.totalDistanceKm), style = MaterialTheme.typography.bodySmall)
                    trip.notes?.let { Text("Notes: $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.select(null) }) { Text("Fermer") } },
        )
    }
}
