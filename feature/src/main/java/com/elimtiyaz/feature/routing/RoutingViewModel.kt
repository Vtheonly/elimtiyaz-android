package com.elimtiyaz.feature.routing

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
import com.elimtiyaz.domain.model.TripLog
import com.elimtiyaz.domain.model.Vehicle
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.RoutingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View-model for [Route.Routing] — the driver-mode hub tab.
 *
 * Aggregates the list of [Vehicle]s with their assigned [RoutingStop]s (filtered
 * by shift) and exposes per-vehicle actions:
 *  - **Optimiser** — calls [RoutingRepository.optimizeRoute] and stores the
 *    resulting [OptimizedRoute] inline so the card can show the ordered stops
 *    with distance + duration.
 *  - **Démarrer** — calls [RoutingRepository.startTrip] with the current
 *    driver's id from [Session.userId]; on success the screen navigates to the
 *    RoutingMap screen.
 *
 * Permission gating: the whole feature requires [Permission.AccessDriverMode].
 * The hub screen reads [canAccessDriverMode] and renders an "Accès refusé"
 * panel when it is missing.
 */
@HiltViewModel
class RoutingViewModel @Inject constructor(
    private val routing: RoutingRepository,
    auth: AuthRepository,
) : ViewModel() {

    /** Current session — powers permission gating on the hub screen. */
    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val _uiState = MutableStateFlow(RoutingUiState())
    val uiState: StateFlow<RoutingUiState> = _uiState.asStateFlow()

    init { load() }

    /** Convenience: true iff the user can enter driver mode. */
    fun canAccessDriverMode(): Boolean = session.value?.can(Permission.AccessDriverMode) == true

    /** Reload every stream (vehicles + stops + trip history). Safe to call repeatedly. */
    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            launch { collectVehicles() }
            launch { collectStops() }
            launch { collectTripHistory() }
        }
    }

    /** Apply a shift filter (Matin / Après-midi / Les deux). */
    fun onShiftFilter(shift: RoutingShiftFilter) {
        _uiState.update { it.copy(shiftFilter = shift) }
    }

    /**
     * Optimise the route for a single vehicle against the current shift filter.
     * Stores the result inline on the per-vehicle optimisation map.
     */
    fun optimise(vehicleId: String) {
        val shift = when (_uiState.value.shiftFilter) {
            RoutingShiftFilter.Morning -> RoutingShift.Morning
            RoutingShiftFilter.Afternoon -> RoutingShift.Afternoon
            RoutingShiftFilter.Both -> RoutingShift.Both
        }
        _uiState.update {
            it.copy(optimising = it.optimising + vehicleId)
        }
        viewModelScope.launch {
            val result = routing.optimizeRoute(vehicleId, shift.name)
            result.onSuccess { route ->
                _uiState.update {
                    it.copy(
                        optimisations = it.optimisations + (vehicleId to route),
                        optimising = it.optimising - vehicleId,
                        error = null,
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        optimising = it.optimising - vehicleId,
                        error = err,
                    )
                }
            }
        }
    }

    /**
     * Start a new trip for the given vehicle — uses the current driver's id
     * from [Session.userId]. Returns the created [TripLog] via [onResult].
     */
    fun startTrip(vehicleId: String, onResult: (TripLog?) -> Unit) {
        val driverId = session.value?.userId ?: run {
            _uiState.update { it.copy(error = AppError.Auth("Session expirée.")) }
            onResult(null)
            return
        }
        viewModelScope.launch {
            when (val r = routing.startTrip(vehicleId, driverId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(activeTrips = it.activeTrips + (vehicleId to r.data)) }
                    onResult(r.data)
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(error = r.error) }
                    onResult(null)
                }
            }
        }
    }

    /** Clear an inline error after the user dismisses the snackbar. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ───────────────────── collectors ─────────────────────

    private suspend fun collectVehicles() {
        routing.vehicles().collect { result ->
            when (result) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, vehicles = result.data, error = null)
                }
                is Result.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = result.error)
                }
            }
        }
    }

    private suspend fun collectStops() {
        routing.stops().collect { result ->
            when (result) {
                is Result.Success -> _uiState.update {
                    it.copy(allStops = result.data, error = null)
                }
                is Result.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    private suspend fun collectTripHistory() {
        routing.tripHistory().collect { result ->
            when (result) {
                is Result.Success -> _uiState.update {
                    it.copy(recentTrips = result.data, error = null)
                }
                is Result.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }
}

/** Hub-level shift filter — `Both` means "show stops for either shift". */
enum class RoutingShiftFilter(val displayFr: String) {
    Morning("Matin"),
    Afternoon("Après-midi"),
    Both("Les deux"),
}

/** Composite state for the Routing hub screen. */
data class RoutingUiState(
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val allStops: List<RoutingStop> = emptyList(),
    val recentTrips: List<TripLog> = emptyList(),
    val shiftFilter: RoutingShiftFilter = RoutingShiftFilter.Morning,
    /** Per-vehicle optimisation results keyed by vehicleId. */
    val optimisations: Map<String, OptimizedRoute> = emptyMap(),
    /** Per-vehicle "Optimiser" in-flight flags. */
    val optimising: Set<String> = emptySet(),
    /** Per-vehicle active (in-progress) trips keyed by vehicleId. */
    val activeTrips: Map<String, TripLog> = emptyMap(),
) {
    /** Stops visible for the current shift filter. */
    val filteredStops: List<RoutingStop>
        get() = when (shiftFilter) {
            RoutingShiftFilter.Morning -> allStops.filter { it.shift == RoutingShift.Morning }
            RoutingShiftFilter.Afternoon -> allStops.filter { it.shift == RoutingShift.Afternoon }
            RoutingShiftFilter.Both -> allStops
        }

    /**
     * Pre-optimisation estimate per vehicle: stop count + straight-line distance
     * + naive duration. The "Optimiser" button replaces these with the real
     * values from the repository.
     */
    fun summaryFor(vehicle: Vehicle): VehicleRouteSummary {
        val stops = filteredStops
        val optimised = optimisations[vehicle.id]
        if (optimised != null) {
            return VehicleRouteSummary(
                stopCount = optimised.stops.size,
                totalDistanceKm = optimised.totalDistanceKm,
                totalDurationMin = optimised.totalDurationMin,
                isOptimised = true,
            )
        }
        // Pre-optimisation estimate: straight-line distance from anchor through
        // all stops in their current order.
        val anchor = GeoPoint(35.6911, -0.6417) // Oran city centre — school anchor
        val points = listOf(anchor) + stops.map { GeoPoint(it.lat, it.lng) }
        var distance = 0.0
        for (i in 1 until points.size) {
            distance += TspSolver.haversineKm(points[i - 1], points[i])
        }
        return VehicleRouteSummary(
            stopCount = stops.size,
            totalDistanceKm = distance,
            totalDurationMin = distance * 2.5, // ~2.5 min/km urban
            isOptimised = false,
        )
    }
}

/** Per-vehicle summary shown on the hub card. */
data class VehicleRouteSummary(
    val stopCount: Int,
    val totalDistanceKm: Double,
    val totalDurationMin: Double,
    val isOptimised: Boolean,
)
