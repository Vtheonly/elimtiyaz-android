package com.elimtiyaz.feature.routing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.domain.model.TripLog
import com.elimtiyaz.domain.repository.RoutingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View-model for the Trip History screen.
 *
 * Streams [RoutingRepository.tripHistory] and exposes the currently-selected
 * log for the detail dialog via [selected].
 */
@HiltViewModel
class TripHistoryViewModel @Inject constructor(
    private val routing: RoutingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripHistoryUiState())
    val uiState: StateFlow<TripHistoryUiState> = _uiState.asStateFlow()

    init { load() }

    /** Re-collect the trip history. */
    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            routing.tripHistory().collect { result ->
                when (result) {
                    is Result.Success -> _uiState.update {
                        it.copy(isLoading = false, trips = result.data, error = null)
                    }
                    is Result.Failure -> _uiState.update {
                        it.copy(isLoading = false, error = result.error)
                    }
                }
            }
        }
    }

    /** Open the detail dialog for [trip]. */
    fun select(trip: TripLog?) {
        _uiState.update { it.copy(selected = trip) }
    }
}

/** Trip-history screen state. */
data class TripHistoryUiState(
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val trips: List<TripLog> = emptyList(),
    val selected: TripLog? = null,
)
