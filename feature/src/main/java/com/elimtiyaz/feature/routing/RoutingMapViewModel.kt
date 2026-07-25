package com.elimtiyaz.feature.routing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.core.common.onFailure
import com.elimtiyaz.core.common.onSuccess
import com.elimtiyaz.domain.model.GeoPoint
import com.elimtiyaz.domain.model.OptimizedRoute
import com.elimtiyaz.domain.model.RoutingShift
import com.elimtiyaz.domain.model.RoutingStop
import com.elimtiyaz.domain.model.Vehicle
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.RoutingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View-model for [Route.RoutingMap] — the live navigation screen.
 *
 * Loads the optimised route for a single vehicle, then tracks driver position
 * via the [RoutingForegroundService]'s companion-object [liveLocation] flow
 * (the service owns the FusedLocationProviderClient so location updates keep
 * arriving while the screen is off).
 *
 * Exposes:
 *  - [currentStop] / [nextStop] — the active and upcoming pickup.
 *  - [etaMin] / [distanceRemainingKm] — derived from the live position.
 *  - [stopsPickedUp] — count of completed stops so far.
 *  - [polyline] — ordered GeoPoints to draw on the map (OSRM polyline if
 *    available, straight-line fallback otherwise).
 *
 * Mutations:
 *  - [advanceStop] — marks the current stop as completed, advances the pointer,
 *    updates the foreground service notification.
 *  - [reorderStops] — drag-to-reorder: persists the new order in-memory and
 *    re-derives the polyline (a future iteration would also call a repository
 *    method to persist to Supabase).
 *  - [endTrip] — terminates the trip, calls [RoutingRepository.endTrip], and
 *    signals the screen to pop.
 */
@HiltViewModel
class RoutingMapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val routing: RoutingRepository,
    auth: AuthRepository,
    private val osrm: OsrmClient,
) : ViewModel() {

    /** Current session — used to fetch the driver id and gate access. */
    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val _uiState = MutableStateFlow(RoutingMapUiState())
    val uiState: StateFlow<RoutingMapUiState> = _uiState.asStateFlow()

    /** True iff the user can be in driver mode at all. */
    fun canAccessDriverMode(): Boolean = session.value?.can(Permission.AccessDriverMode) == true

    /** True iff ACCESS_FINE_LOCATION has been granted. */
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Bootstrap the screen for [vehicleId]. Loads the vehicle + its stops,
     * then optimises the route (repository → fallback to local TSP solver),
     * then attempts to fetch a real OSRM polyline.
     */
    fun load(vehicleId: String, preview: Boolean = false) {
        _uiState.update {
            it.copy(isLoading = true, error = null, preview = preview, vehicleId = vehicleId)
        }
        viewModelScope.launch {
            launch { collectVehicleAndOptimise(vehicleId) }
            launch { collectForegroundLocation() }
        }
    }

    /**
     * Mark the current stop as completed and advance to the next. When the
     * last stop is reached, the screen's "Terminer la tournée" button takes
     * over (we don't auto-end here so the driver can confirm).
     */
    fun advanceStop() {
        val s = _uiState.value
        val stops = s.stops
        if (stops.isEmpty()) return
        val newIdx = (s.currentStopIndex + 1).coerceAtMost(stops.size)
        val pickedUp = (newIdx).coerceAtLeast(0)
        _uiState.update {
            it.copy(currentStopIndex = newIdx, stopsPickedUp = pickedUp)
        }
        // Notify the foreground service so its sticky notification stays fresh.
        if (!s.preview) {
            val label = "Tournée — ${s.vehicle?.driverName ?: "Driver"}"
            RoutingForegroundService.updateProgress(
                context = appContext,
                tripLabel = label,
                stopIndex = newIdx,
                stopTotal = stops.size,
            )
        }
    }

    /**
     * Drag-to-reorder: move stop at [from] to [to] and re-derive the polyline.
     * Persists in-memory only (Supabase persistence is a v2 concern).
     */
    fun reorderStops(from: Int, to: Int) {
        val s = _uiState.value
        if (from == to || from !in s.stops.indices || to !in s.stops.indices) return
        val reordered = s.stops.toMutableList().apply {
            val moved = removeAt(from)
            add(to, moved)
        }.mapIndexed { idx, stop -> stop.copy(orderInRoute = idx + 1) }
        _uiState.update { it.copy(stops = reordered, currentStopIndex = 0, stopsPickedUp = 0) }
        viewModelScope.launch { refreshPolyline(reordered) }
    }

    /**
     * Terminate the trip — calls [RoutingRepository.endTrip] with the final
     * stats and signals the screen to pop via [onResult].
     */
    fun endTrip(onResult: (Boolean) -> Unit) {
        val s = _uiState.value
        val tripId = s.activeTripId ?: return onResult(true) // preview mode — just pop
        viewModelScope.launch {
            when (val r = routing.endTrip(tripId, s.stopsPickedUp, s.distanceCoveredKm)) {
                is Result.Success -> {
                    _uiState.update { it.copy(activeTripId = null, tripEnded = true) }
                    onResult(true)
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(error = r.error) }
                    onResult(false)
                }
            }
        }
    }

    /** Clear an inline error after the user dismisses the snackbar. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ───────────────────── collectors ─────────────────────

    private suspend fun collectVehicleAndOptimise(vehicleId: String) {
        // 1) Load all vehicles to find the one we're navigating.
        routing.vehicles().collect { result ->
            when (result) {
                is Result.Success -> {
                    val vehicle = result.data.firstOrNull { it.id == vehicleId } ?: run {
                        _uiState.update {
                            it.copy(isLoading = false, error = AppError.NotFound("Véhicule $vehicleId"))
                        }
                        return@collect
                    }
                    _uiState.update { it.copy(vehicle = vehicle) }
                    // Trigger optimisation once we know the vehicle.
                    optimiseRouteFor(vehicle)
                }
                is Result.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = result.error)
                }
            }
        }
    }

    /** Optimise the route via the repository, then optionally refine the polyline via OSRM. */
    private suspend fun optimiseRouteFor(vehicle: Vehicle) {
        // Pick the shift from the time of day — morning shift before noon.
        val shift = if (System.currentTimeMillis() % 86_400_000L < 43_200_000L) {
            RoutingShift.Morning
        } else RoutingShift.Afternoon
        val result = routing.optimizeRoute(vehicle.id, shift.name)
        result.onSuccess { route ->
            // Apply our local 2-opt refinement as a safety net (the repository
            // may return stops in their stored order without optimisation).
            val improved = TspSolver.twoOptImprove(route.stops)
            val refined = route.copy(stops = improved)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    stops = improved,
                    basePolyline = refined.polyline,
                    totalDistanceKm = refined.totalDistanceKm,
                    totalDurationMin = refined.totalDurationMin,
                )
            }
            // Try to fetch a real road polyline from OSRM. On failure, fall
            // back to the straight-line polyline we already have.
            refreshPolyline(improved)
            // Start a trip if not in preview mode.
            if (!_uiState.value.preview && _uiState.value.activeTripId == null) {
                startTripFor(vehicle)
            }
        }.onFailure { err ->
            _uiState.update { it.copy(isLoading = false, error = err) }
        }
    }

    /** Start a trip and remember its id so [endTrip] can patch it. */
    private fun startTripFor(vehicle: Vehicle) {
        val driverId = session.value?.userId ?: return
        viewModelScope.launch {
            when (val r = routing.startTrip(vehicle.id, driverId)) {
                is Result.Success -> _uiState.update { it.copy(activeTripId = r.data.id) }
                is Result.Failure -> _uiState.update { it.copy(error = r.error) }
            }
        }
    }

    /**
     * Refresh the polyline: try OSRM first (real road geometry), fall back to
     * the straight-line path between consecutive stops.
     */
    private suspend fun refreshPolyline(stops: List<RoutingStop>) {
        if (stops.size < 2) {
            _uiState.update { it.copy(polyline = stops.map { GeoPoint(it.lat, it.lng) }) }
            return
        }
        val points = stops.map { GeoPoint(it.lat, it.lng) }
        val osrmRoute = osrm.route(points)
        if (osrmRoute != null) {
            _uiState.update {
                it.copy(
                    polyline = osrmRoute.geometry,
                    polylineSource = PolylineSource.Osrm,
                    totalDistanceKm = osrmRoute.distanceKm,
                    totalDurationMin = osrmRoute.durationMin,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    polyline = points,
                    polylineSource = PolylineSource.StraightLine,
                )
            }
        }
    }

    /** Collect live location updates from the foreground service. */
    private suspend fun collectForegroundLocation() {
        RoutingForegroundService.liveLocation.collect { loc ->
            if (loc != null) updateLivePosition(loc)
        }
    }

    /** Recompute ETA + distance remaining based on the latest position. */
    private fun updateLivePosition(loc: GeoPoint) {
        val s = _uiState.value
        if (s.stops.isEmpty()) return
        val currentStop = s.stops.getOrNull(s.currentStopIndex) ?: s.stops.last()
        val distanceToCurrent = TspSolver.haversineKm(loc, GeoPoint(currentStop.lat, currentStop.lng))
        // Distance covered so far = total minus remaining (rough estimate using
        // straight-line from current pos to current stop, plus haversine from
        // current stop to the end of the route).
        var remaining = distanceToCurrent
        for (i in (s.currentStopIndex + 1) until s.stops.size) {
            val a = s.stops[i - 1]
            val b = s.stops[i]
            remaining += TspSolver.haversineKm(
                GeoPoint(a.lat, a.lng), GeoPoint(b.lat, b.lng),
            )
        }
        val totalKm = if (s.totalDistanceKm > 0) s.totalDistanceKm else s.polylineDistanceKm
        val covered = (totalKm - remaining).coerceAtLeast(0.0)
        // ETA in minutes — assume 2.5 min/km urban speed plus 1 min per stop.
        val etaMin = (remaining * 2.5) + (s.stops.size - s.currentStopIndex)
        _uiState.update {
            it.copy(
                currentLocation = loc,
                distanceCoveredKm = covered,
                distanceRemainingKm = remaining,
                etaMin = etaMin,
            )
        }
    }
}

/** Where the rendered polyline came from. */
enum class PolylineSource(val displayFr: String) {
    Osrm("OSRM (route réelle)"),
    StraightLine("Ligne droite (hors-ligne)"),
}

/** Composite state for the live map screen. */
data class RoutingMapUiState(
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val preview: Boolean = false,
    val vehicleId: String = "",
    val vehicle: Vehicle? = null,
    val stops: List<RoutingStop> = emptyList(),
    val polyline: List<GeoPoint> = emptyList(),
    val basePolyline: List<GeoPoint> = emptyList(),
    val polylineSource: PolylineSource = PolylineSource.StraightLine,
    val totalDistanceKm: Double = 0.0,
    val totalDurationMin: Double = 0.0,
    val currentStopIndex: Int = 0,
    val stopsPickedUp: Int = 0,
    val activeTripId: String? = null,
    val tripEnded: Boolean = false,
    val currentLocation: GeoPoint? = null,
    val distanceCoveredKm: Double = 0.0,
    val distanceRemainingKm: Double = 0.0,
    val etaMin: Double = 0.0,
) {
    /** The stop the driver is currently heading to. */
    val currentStop: RoutingStop?
        get() = stops.getOrNull(currentStopIndex)

    /** The stop after [currentStop], or null if this is the last stop. */
    val nextStop: RoutingStop?
        get() = stops.getOrNull(currentStopIndex + 1)

    /** Total straight-line polyline distance — fallback when OSRM is unavailable. */
    val polylineDistanceKm: Double
        get() {
            if (polyline.size < 2) return 0.0
            var sum = 0.0
            for (i in 1 until polyline.size) {
                sum += TspSolver.haversineKm(polyline[i - 1], polyline[i])
            }
            return sum
        }

    /** Convenience for the screen: trip is at the last stop. */
    val isAtLastStop: Boolean get() = currentStopIndex >= stops.size - 1

    /** Convenience for the screen: optimised route payload (for the map preview). */
    val optimizedRoute: OptimizedRoute?
        get() = vehicle?.let { v ->
            OptimizedRoute(
                vehicle = v,
                stops = stops,
                totalDistanceKm = totalDistanceKm,
                totalDurationMin = totalDurationMin,
                polyline = polyline,
            )
        }
}
