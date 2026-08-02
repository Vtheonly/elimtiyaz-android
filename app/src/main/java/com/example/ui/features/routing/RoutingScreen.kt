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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.example.core.Permission
import com.example.core.Result
import com.example.domain.model.OptimizedRoute
import com.example.domain.model.RoutingShift
import com.example.domain.model.TripLog
import com.example.domain.model.Vehicle
import com.example.domain.repository.RoutingRepository
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
 * Routing hub ViewModel — restores the pre-redesign `RoutingViewModel` (commit a34333a).
 *
 * - Loads vehicles, stops, and trip history.
 * - Per-vehicle optimization cache.
 * - "Démarrer" action → opens RoutingMap.
 *
 * Entirely gated by [Permission.ACCESS_DRIVER_MODE] (Driver role only by default).
 */
@HiltViewModel
class RoutingViewModel @Inject constructor(
    private val routingRepository: RoutingRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val vehicles: StateFlow<List<Vehicle>> = routingRepository.observeVehicles()
        .let { flow ->
            kotlinx.coroutines.flow.mapNotNull(flow) { result ->
                when (result) {
                    is Result.Ok -> result.value
                    is Result.Err -> emptyList()
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentTrips: StateFlow<List<TripLog>> = routingRepository.observeTripHistory()
        .let { flow ->
            kotlinx.coroutines.flow.mapNotNull(flow) { result ->
                when (result) {
                    is Result.Ok -> result.value
                    is Result.Err -> emptyList()
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _optimisations = MutableStateFlow<Map<String, OptimizedRoute>>(emptyMap())
    val optimisations: StateFlow<Map<String, OptimizedRoute>> = _optimisations.asStateFlow()

    private val _optimising = MutableStateFlow<Set<String>>(emptySet())
    val optimising: StateFlow<Set<String>> = _optimising.asStateFlow()

    private val _shiftFilter = MutableStateFlow(RoutingShift.Morning)
    val shiftFilter: StateFlow<RoutingShift> = _shiftFilter.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onShiftFilter(shift: RoutingShift) { _shiftFilter.value = shift }

    fun optimise(vehicleId: String) {
        if (vehicleId in _optimising.value) return
        _optimising.value = _optimising.value + vehicleId
        viewModelScope.launch {
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val r = routingRepository.optimizeRoute(vehicleId, _shiftFilter.value, actorId, actorName)) {
                is Result.Ok -> _optimisations.value = _optimisations.value + (vehicleId to r.value)
                is Result.Err -> _error.value = r.error.userMessage
            }
            _optimising.value = _optimising.value - vehicleId
        }
    }

    fun startTrip(vehicleId: String, onResult: (TripLog?) -> Unit) {
        viewModelScope.launch {
            val driverId = sessionManager.currentUserId() ?: "system"
            val driverName = sessionManager.currentDisplayName() ?: "System"
            when (val r = routingRepository.startTrip(vehicleId, driverId, driverName)) {
                is Result.Ok -> onResult(r.value)
                is Result.Err -> { _error.value = r.error.userMessage; onResult(null) }
            }
        }
    }

    fun clearError() { _error.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingScreen(
    onBack: () -> Unit,
    onNavigateToRoutingMap: (vehicleId: String) -> Unit,
    onNavigateToTripHistory: () -> Unit,
    viewModel: RoutingViewModel = hiltViewModel(),
) {
    val vehicles by viewModel.vehicles.collectAsState()
    val recentTrips by viewModel.recentTrips.collectAsState()
    val optimisations by viewModel.optimisations.collectAsState()
    val optimising by viewModel.optimising.collectAsState()
    val shiftFilter by viewModel.shiftFilter.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tournées") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
                actions = {
                    IconButton(onClick = onNavigateToTripHistory) { Icon(Icons.Default.History, contentDescription = "Historique") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }

            // Shift filter
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val shifts = listOf(RoutingShift.Morning, RoutingShift.Afternoon, RoutingShift.Both)
                shifts.forEachIndexed { idx, shift ->
                    SegmentedButton(
                        selected = shiftFilter == shift,
                        onClick = { viewModel.onShiftFilter(shift) },
                        shape = SegmentedButtonDefaults.itemShape(idx, shifts.size),
                    ) { Text(shift.displayFr) }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (vehicles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucun véhicule configuré.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vehicles) { vehicle ->
                        VehicleCard(
                            vehicle = vehicle,
                            optimised = optimisations[vehicle.id],
                            isOptimising = vehicle.id in optimising,
                            onOptimise = { viewModel.optimise(vehicle.id) },
                            onStart = {
                                viewModel.startTrip(vehicle.id) { trip ->
                                    if (trip != null) onNavigateToRoutingMap(vehicle.id)
                                }
                            },
                        )
                    }
                }
            }

            if (recentTrips.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Dernières tournées", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                recentTrips.take(3).forEach { trip ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Text("Véhicule: ${trip.vehicleId}", style = MaterialTheme.typography.bodySmall)
                            Text("Début: ${trip.startedAt}", style = MaterialTheme.typography.labelSmall)
                            Text("Arrêts: ${trip.stopsCompleted}/${trip.stopsPlanned}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleCard(
    vehicle: Vehicle,
    optimised: OptimizedRoute?,
    isOptimising: Boolean,
    onOptimise: () -> Unit,
    onStart: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(vehicle.plate, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Capacité: ${vehicle.capacity} • ${if (vehicle.hasWheelchairAccess) "PMR" else "Standard"}", style = MaterialTheme.typography.labelSmall)
                    vehicle.driverName?.let { Text("Chauffeur: $it", style = MaterialTheme.typography.labelSmall) }
                }
            }
            Spacer(Modifier.height(8.dp))
            optimised?.let { route ->
                Text("Arrêts: ${route.stops.size}", style = MaterialTheme.typography.bodySmall)
                Text("Distance: %.2f km".format(route.totalDistanceKm), style = MaterialTheme.typography.bodySmall)
                Text("Durée: %.0f min".format(route.totalDurationMin), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.TextButton(onClick = onOptimise, enabled = !isOptimising) {
                    Text(if (isOptimising) "Optimisation…" else if (optimised == null) "Optimiser" else "Re-optimiser")
                }
                androidx.compose.material3.Button(onClick = onStart, enabled = optimised != null) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(" Démarrer")
                }
            }
        }
    }
}
