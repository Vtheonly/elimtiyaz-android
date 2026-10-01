package com.example.ui.features.routing

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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PlayArrow
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
import com.example.core.Permission
import com.example.core.Result
import com.example.domain.model.OptimizedRoute
import com.example.domain.model.RoutingShift
import com.example.domain.model.TripLog
import com.example.domain.model.Vehicle
import com.example.domain.repository.RoutingRepository
import com.example.session.SessionManager
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.display.ElChipVariant
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.feedback.ElLoadingBlock
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.theme.ElTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Routing hub ViewModel — restores the pre-redesign `RoutingViewModel` (commit a34333a).
 *
 * - Loads vehicles, stops, and trip history.
 * - Per-vehicle optimization cache.
 * - "Démarrer" action → opens RoutingMap.
 *
 * Entirely gated by [Permission.ACCESS_DRIVER_MODE] (Driver role only by default).
 *
 * T-460 pass G-c (issue #3 F-02): an explicit [isVehiclesLoaded] flag so the
 * empty state no longer renders during the initial repository read.
 */
@HiltViewModel
class RoutingViewModel @Inject constructor(
    private val routingRepository: RoutingRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _isVehiclesLoaded = MutableStateFlow(false)
    val isVehiclesLoaded: StateFlow<Boolean> = _isVehiclesLoaded.asStateFlow()

    val vehicles: StateFlow<List<Vehicle>> = routingRepository.observeVehicles()
        .mapNotNull { result ->
            when (result) {
                is Result.Ok -> result.value
                is Result.Err -> emptyList()
            }
        }
        .onEach { _isVehiclesLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentTrips: StateFlow<List<TripLog>> = routingRepository.observeTripHistory()
        .mapNotNull { result ->
            when (result) {
                is Result.Ok -> result.value
                is Result.Err -> emptyList()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

/**
 * T-460 pass G-c (issue #3 F-06): the raw-M3 routing hub → the design system.
 * The shift segmented control becomes ElChip FILTER pills; vehicle cards on
 * ElCard with ElButton actions; tri-state loading/empty (the F-02 fix).
 * The optimize/start contract is untouched.
 */
@Composable
fun RoutingScreen(
    onBack: () -> Unit,
    onNavigateToRoutingMap: (vehicleId: String) -> Unit,
    onNavigateToTripHistory: () -> Unit,
    viewModel: RoutingViewModel = hiltViewModel(),
) {
    val c = ElTheme.colors
    val vehicles by viewModel.vehicles.collectAsState()
    val recentTrips by viewModel.recentTrips.collectAsState()
    val optimisations by viewModel.optimisations.collectAsState()
    val optimising by viewModel.optimising.collectAsState()
    val shiftFilter by viewModel.shiftFilter.collectAsState()
    val error by viewModel.error.collectAsState()
    val isVehiclesLoaded by viewModel.isVehiclesLoaded.collectAsState()

    ElScaffold(
        topBar = {
            ElTopBar(
                title = "Tournées",
                onBack = onBack,
                actions = {
                    ElIconButton(
                        icon = Icons.Default.History,
                        onClick = onNavigateToTripHistory,
                        contentDescription = "Historique",
                        tint = c.textPrimary,
                        background = androidx.compose.ui.graphics.Color.Transparent,
                    )
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let {
                ElAlertBanner(
                    title = it,
                    severity = ElAlertSeverity.DANGER,
                )
            }

            // Shift filter
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(RoutingShift.Morning, RoutingShift.Afternoon, RoutingShift.Both).forEach { shift ->
                    ElChip(
                        text = shift.displayFr,
                        variant = ElChipVariant.FILTER,
                        selected = shiftFilter == shift,
                        onClick = { viewModel.onShiftFilter(shift) },
                    )
                }
            }

            when {
                !isVehiclesLoaded -> {
                    ElLoadingBlock(
                        message = "Chargement des véhicules…",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                vehicles.isEmpty() -> {
                    // T-324: real empty state (was a bare centered Text).
                    ElEmptyState(
                        icon = Icons.Default.LocalShipping,
                        title = "Aucun véhicule configuré",
                        subtitle = "Les véhicules et leurs tournées apparaîtront ici.",
                    )
                }
                else -> {
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
            }

            if (recentTrips.isNotEmpty()) {
                Text("Dernières tournées", style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                recentTrips.take(3).forEach { trip ->
                    ElCard(
                        modifier = Modifier.fillMaxWidth(),
                        size = ElCardSize.COMPACT,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Text("Véhicule : ${trip.vehicleId}", style = ElTheme.typography.bodySmall, color = c.textPrimary)
                            Text("Début : ${trip.startedAt}", style = ElTheme.typography.labelSmall, color = c.textSecondary)
                            Text("Arrêts : ${trip.stopsCompleted}/${trip.stopsPlanned}", style = ElTheme.typography.labelSmall, color = c.textSecondary)
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
    val c = ElTheme.colors
    ElCard(
        modifier = Modifier.fillMaxWidth(),
        size = ElCardSize.STANDARD,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Icon(Icons.Default.LocalShipping, contentDescription = null, tint = c.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(vehicle.plate, style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                    Text("Capacité : ${vehicle.capacity} • ${if (vehicle.hasWheelchairAccess) "PMR" else "Standard"}", style = ElTheme.typography.labelSmall, color = c.textSecondary)
                    Text("Chauffeur : ${vehicle.driverName ?: "Sans chauffeur"}", style = ElTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            optimised?.let { route ->
                Text("Arrêts : ${route.stops.size}", style = ElTheme.typography.bodySmall, color = c.textPrimary)
                Text("Distance : %.2f km".format(route.totalDistanceKm), style = ElTheme.typography.bodySmall, color = c.textPrimary)
                Text("Durée : %.0f min".format(route.totalDurationMin), style = ElTheme.typography.bodySmall, color = c.textPrimary)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ElButton(
                    text = if (optimised == null) "Optimiser" else "Re-optimiser",
                    onClick = onOptimise,
                    enabled = !isOptimising,
                    variant = ElButtonVariant.SECONDARY,
                    loading = isOptimising,
                    modifier = Modifier.weight(1f),
                )
                ElButton(
                    text = "Démarrer",
                    onClick = onStart,
                    enabled = optimised != null,
                    variant = ElButtonVariant.PRIMARY,
                    icon = Icons.Default.PlayArrow,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
