package com.example.ui.features.routing

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.theme.ElTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.core.Permission
import com.example.core.Result
import com.example.domain.model.GeoPoint
import com.example.domain.model.OptimizedRoute
import com.example.domain.model.RoutingStop
import com.example.domain.model.Vehicle
import com.example.domain.repository.RoutingRepository
import com.example.infrastructure.routing.RoutingForegroundService
import com.example.infrastructure.routing.TspSolver
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Routing map ViewModel — restores the pre-redesign `RoutingMapViewModel` (commit a34333a).
 *
 * - Loads vehicle + optimizes route on init.
 * - Tracks live driver position via [RoutingForegroundService.liveLocation].
 * - "Avancer" → next stop, updates foreground notification.
 * - "Terminer" → endTrip, stops foreground service.
 */
@HiltViewModel
class RoutingMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routingRepository: RoutingRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val vehicleId: String = savedStateHandle["vehicleId"] ?: ""

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _vehicle = MutableStateFlow<Vehicle?>(null)
    val vehicle: StateFlow<Vehicle?> = _vehicle.asStateFlow()

    private val _stops = MutableStateFlow<List<RoutingStop>>(emptyList())
    val stops: StateFlow<List<RoutingStop>> = _stops.asStateFlow()

    private val _currentStopIndex = MutableStateFlow(0)
    val currentStopIndex: StateFlow<Int> = _currentStopIndex.asStateFlow()

    private val _stopsPickedUp = MutableStateFlow(0)
    val stopsPickedUp: StateFlow<Int> = _stopsPickedUp.asStateFlow()

    private val _activeTripId = MutableStateFlow<String?>(null)
    val activeTripId: StateFlow<String?> = _activeTripId.asStateFlow()

    private val _tripEnded = MutableStateFlow(false)
    val tripEnded: StateFlow<Boolean> = _tripEnded.asStateFlow()

    private val _totalDistanceKm = MutableStateFlow(0.0)
    val totalDistanceKm: StateFlow<Double> = _totalDistanceKm.asStateFlow()

    private val _distanceRemainingKm = MutableStateFlow(0.0)
    val distanceRemainingKm: StateFlow<Double> = _distanceRemainingKm.asStateFlow()

    private val _etaMin = MutableStateFlow(0.0)
    val etaMin: StateFlow<Double> = _etaMin.asStateFlow()

    val liveLocation: StateFlow<GeoPoint?> = RoutingForegroundService.liveLocation
    val lastSpeedKmh: StateFlow<Double> = RoutingForegroundService.lastSpeedKmh

    val currentStop: StateFlow<RoutingStop?> = kotlinx.coroutines.flow.combine(_stops, _currentStopIndex) { list, idx ->
        list.getOrNull(idx)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    init { load() }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load all vehicles, find the matching one
                val vehiclesResult = routingRepository.observeVehicles().first()
                val vehiclesList = when (vehiclesResult) {
                    is Result.Ok -> vehiclesResult.value
                    is Result.Err -> emptyList()
                }
                _vehicle.value = vehiclesList.firstOrNull { it.id == vehicleId }

                // Determine shift by time-of-day (mirror desktop)
                val hourOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val shift = if (hourOfDay < 12) com.example.domain.model.RoutingShift.Morning else com.example.domain.model.RoutingShift.Afternoon

                // Optimize
                val actorId = sessionManager.currentUserId() ?: "system"
                val actorName = sessionManager.currentDisplayName() ?: "System"
                when (val r = routingRepository.optimizeRoute(vehicleId, shift, actorId, actorName)) {
                    is Result.Ok -> {
                        _stops.value = r.value.stops
                        _totalDistanceKm.value = r.value.totalDistanceKm
                        // Start trip if not in preview
                        if (_activeTripId.value == null) {
                            val driverId = sessionManager.currentUserId() ?: "system"
                            val driverName = sessionManager.currentDisplayName() ?: "System"
                            when (val trip = routingRepository.startTrip(vehicleId, driverId, driverName)) {
                                is Result.Ok -> _activeTripId.value = trip.value.id
                                is Result.Err -> _error.value = trip.error.userMessage
                            }
                        }
                    }
                    is Result.Err -> _error.value = r.error.userMessage
                }
            } catch (t: Throwable) {
                _error.value = t.message ?: "Erreur de chargement."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun advanceStop() {
        val list = _stops.value
        if (list.isEmpty()) return
        val newIdx = (_currentStopIndex.value + 1).coerceAtMost(list.size)
        _currentStopIndex.value = newIdx
        _stopsPickedUp.value = newIdx
        // Update foreground notification
        // (caller is responsible for calling RoutingForegroundService.updateProgress)
        updateDistanceRemaining()
    }

    fun endTrip(onResult: (Boolean) -> Unit) {
        val tripId = _activeTripId.value
        if (tripId == null) {
            _tripEnded.value = true
            onResult(true)
            return
        }
        viewModelScope.launch {
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val r = routingRepository.endTrip(tripId, _stopsPickedUp.value, _totalDistanceKm.value, actorId, actorName)) {
                is Result.Ok -> { _activeTripId.value = null; _tripEnded.value = true; onResult(true) }
                is Result.Err -> { _error.value = r.error.userMessage; onResult(false) }
            }
        }
    }

    private fun updateDistanceRemaining() {
        val list = _stops.value
        val idx = _currentStopIndex.value
        if (list.isEmpty() || idx >= list.size) {
            _distanceRemainingKm.value = 0.0
            _etaMin.value = 0.0
            return
        }
        var remaining = 0.0
        for (i in (idx + 1) until list.size) {
            val prev = GeoPoint(list[i - 1].lat, list[i - 1].lng)
            val cur = GeoPoint(list[i].lat, list[i].lng)
            remaining += TspSolver.haversineKm(prev, cur)
        }
        // Add distance from current location to current stop
        val curStop = list[idx]
        val cur = GeoPoint(curStop.lat, curStop.lng)
        liveLocation.value?.let { loc ->
            remaining += TspSolver.haversineKm(loc, cur)
        }
        _distanceRemainingKm.value = remaining
        // ETA: 2.5 min/km urban + 1 min per remaining stop
        _etaMin.value = remaining * 2.5 + (list.size - idx)
    }
}

@Composable
fun RoutingMapScreen(
    onBack: () -> Unit,
    onTripEnded: () -> Unit,
    viewModel: RoutingMapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val vehicle by viewModel.vehicle.collectAsState()
    val stops by viewModel.stops.collectAsState()
    val currentStopIndex by viewModel.currentStopIndex.collectAsState()
    val stopsPickedUp by viewModel.stopsPickedUp.collectAsState()
    val currentStop by viewModel.currentStop.collectAsState()
    val liveLocation by viewModel.liveLocation.collectAsState()
    val etaMin by viewModel.etaMin.collectAsState()
    val distanceRemaining by viewModel.distanceRemainingKm.collectAsState()
    val tripEnded by viewModel.tripEnded.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var hasLocationPermission by remember { mutableStateOf(false) }

    // Permission flow: use the centralized helper so "permanently denied" is
    // handled correctly. The previous implementation requested
    // ACCESS_FINE_LOCATION on every screen entry but never read the result
    // and never offered a path to system settings when the user checked
    // "Don't ask again". The foreground service also started unconditionally
    // — now it's gated on the granted state.
    val locationPerm = com.example.ui.permissions.rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    hasLocationPermission = locationPerm.state is com.example.ui.permissions.PermissionState.Granted

    // Auto-request on first entry (NotDetermined). Don't re-request after a
    // denial — the helper already tracks "have we asked?" so the user can
    // tap a retry button instead of being pestered on every recomposition.
    LaunchedEffect(locationPerm.state) {
        if (locationPerm.state is com.example.ui.permissions.PermissionState.NotDetermined) {
            locationPerm.request()
        }
    }

    // Start foreground service ONLY when the permission is granted. The
    // previous implementation started it unconditionally, which caused
    // `ContextCompat.checkSelfPermission` inside the service to silently
    // skip location updates — the user saw a "tracking" notification but
    // no actual location data.
    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val tripLabel = "Tournée ${vehicle?.plate ?: ""}"
            RoutingForegroundService.startTracking(context, tripLabel, 0, stops.size)
        }
        onDispose {
            RoutingForegroundService.stopTracking(context)
        }
    }

    // Update notification when stop index changes
    LaunchedEffect(currentStopIndex, stops) {
        val tripLabel = "Tournée ${vehicle?.plate ?: ""}"
        RoutingForegroundService.updateProgress(context, tripLabel, currentStopIndex, stops.size)
    }

    if (tripEnded) {
        LaunchedEffect(Unit) { onTripEnded() }
        return
    }

    val c = ElTheme.colors

    ElScaffold(
        topBar = {
            ElTopBar(
                title = vehicle?.plate ?: "Tournée",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Route visualization — Canvas projection of the REAL optimized
            // stop coordinates + live driver position (offline-first; no map
            // SDK dependency). The polyline follows the TSP/OSRM stop order.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(c.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (stops.isNotEmpty()) {
                    RouteCanvas(
                        stops = stops,
                        currentIndex = currentStopIndex,
                        liveLocation = liveLocation,
                        routeColor = c.primary,
                        currentStopColor = c.danger,
                        stopColor = c.textSecondary,
                        liveColor = c.success,
                    )
                } else {
                    Text(if (isLoading) "Chargement…" else "Aucun arrêt.", style = ElTheme.typography.bodySmall, color = c.textSecondary)
                }
            }

            error?.let {
                com.example.ui.designsystem.components.display.ElAlertBanner(
                    title = it,
                    severity = com.example.ui.designsystem.components.display.ElAlertSeverity.DANGER,
                )
            }

            // Bottom sheet with current stop info
            currentStop?.let { stop ->
                com.example.ui.designsystem.components.card.ElCard(
                    modifier = Modifier.fillMaxWidth().padding(ElTheme.spacing.lg),
                    size = com.example.ui.designsystem.components.card.ElCardSize.STANDARD,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(ElTheme.spacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.md)) {
                            androidx.compose.material3.Icon(Icons.Default.LocationOn, contentDescription = null, tint = c.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Arrêt ${currentStopIndex + 1} / ${stops.size}", style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                                Text(stop.studentName, style = ElTheme.typography.bodyMedium, color = c.textPrimary)
                                Text(stop.address, style = ElTheme.typography.bodySmall, color = c.textSecondary)
                            }
                        }
                        Spacer(Modifier.height(ElTheme.spacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.md)) {
                            Text("Distance : %.2f km".format(distanceRemaining), style = ElTheme.typography.labelSmall, color = c.textSecondary)
                            Text("ETA : %.0f min".format(etaMin), style = ElTheme.typography.labelSmall, color = c.textSecondary)
                        }
                        Spacer(Modifier.height(ElTheme.spacing.md))
                        Row(horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm)) {
                            com.example.ui.designsystem.components.button.ElButton(
                                text = "Avancer",
                                onClick = { viewModel.advanceStop() },
                                variant = com.example.ui.designsystem.components.button.ElButtonVariant.PRIMARY,
                                icon = Icons.Default.CheckCircle,
                                modifier = Modifier.weight(1f),
                            )
                            com.example.ui.designsystem.components.button.ElButton(
                                text = "Terminer",
                                onClick = { viewModel.endTrip { onTripEnded() } },
                                variant = com.example.ui.designsystem.components.button.ElButtonVariant.DANGER,
                                icon = Icons.Default.Stop,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Stop list (scrollable)
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = ElTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.xs)) {
                items(stops) { stop ->
                    val idx = stops.indexOf(stop)
                    com.example.ui.designsystem.components.card.ElCard(
                        modifier = Modifier.fillMaxWidth(),
                        size = com.example.ui.designsystem.components.card.ElCardSize.COMPACT,
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(ElTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            Text("#${idx + 1}", style = ElTheme.typography.labelSmall, color = c.textSecondary, modifier = Modifier.weight(0.2f))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stop.studentName, style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                                Text(stop.address, style = ElTheme.typography.labelSmall, color = c.textSecondary)
                            }
                            if (idx < currentStopIndex) {
                                androidx.compose.material3.Icon(Icons.Default.CheckCircle, contentDescription = null, tint = c.success)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteCanvas(
    stops: List<RoutingStop>,
    currentIndex: Int,
    liveLocation: GeoPoint?,
    routeColor: Color,
    currentStopColor: Color,
    stopColor: Color,
    liveColor: Color,
) {
    if (stops.isEmpty()) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val allLats = stops.map { it.lat } + (liveLocation?.let { listOf(it.lat) } ?: emptyList())
        val allLngs = stops.map { it.lng } + (liveLocation?.let { listOf(it.lng) } ?: emptyList())
        val minLat = allLats.min()
        val maxLat = allLats.max()
        val minLng = allLngs.min()
        val maxLng = allLngs.max()
        val latRange = (maxLat - minLat).coerceAtLeast(0.001)
        val lngRange = (maxLng - minLng).coerceAtLeast(0.001)

        fun project(lat: Double, lng: Double): Offset {
            val x = ((lng - minLng) / lngRange).toFloat() * size.width
            val y = (1 - ((lat - minLat) / latRange).toFloat()) * size.height
            return Offset(x, y)
        }

        // Polyline
        val points = stops.map { project(it.lat, it.lng) }
        for (i in 1 until points.size) {
            drawLine(
                color = routeColor,
                start = points[i - 1],
                end = points[i],
                strokeWidth = 4f,
            )
        }

        // Stops
        points.forEachIndexed { idx, p ->
            drawCircle(
                color = if (idx == currentIndex) currentStopColor else stopColor,
                radius = if (idx == currentIndex) 12f else 6f,
                center = p,
            )
        }

        // Live driver position
        liveLocation?.let { loc ->
            val p = project(loc.lat, loc.lng)
            drawCircle(color = liveColor, radius = 10f, center = p)
        }
    }
}
