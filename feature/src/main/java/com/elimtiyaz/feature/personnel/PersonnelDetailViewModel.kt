package com.elimtiyaz.feature.personnel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.Personnel
import com.elimtiyaz.domain.model.ReleveEntry
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.PersonnelRepository
import com.elimtiyaz.domain.repository.ReleveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DateTimeUnit
import javax.inject.Inject

/**
 * PersonnelDetailViewModel — powers Route.PersonnelDetail.
 *
 * Loads a single personnel plus its Relevé entries for the current week
 * (Monday→Sunday). Exposes:
 *  - [state.personnel] for the header card (avatar, name, category, status,
 *    phone, email, hire date, salary — salary is admin-only per spec).
 *  - [state.weekEntries] for the "Heures cette semaine" card; the screen
 *    derives the progress bar and the per-day breakdown.
 *  - [state.recentEntries] for the "Relevé récent" section (last 10 by date).
 *
 * The personnel id is read from the navigation [SavedStateHandle] arg
 * `personnelId` so the screen can simply call `hiltViewModel()`.
 */
@HiltViewModel
class PersonnelDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val personnelRepo: PersonnelRepository,
    private val releveRepo: ReleveRepository,
    auth: AuthRepository,
) : ViewModel() {

    /** Latest session — gates the edit action and salary visibility. */
    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val personnelId: String = savedStateHandle.get<String>("personnelId").orEmpty()

    private val _state = MutableStateFlow(PersonnelDetailUiState())
    val state: StateFlow<PersonnelDetailUiState> = _state.asStateFlow()

    init { reload() }

    /** Re-fetch personnel + current-week Relevé entries. */
    fun reload() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            personnelRepo.personnel(personnelId).collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(isLoading = false, personnel = result.data, error = null)
                    }
                    is Result.Failure -> _state.update {
                        it.copy(isLoading = false, error = result.error)
                    }
                }
            }
        }
        loadWeekReleve()
    }

    /** Refresh the current-week Relevé entries (Monday→Sunday). */
    fun loadWeekReleve() {
        val today = Formatters.today()
        val monday = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
        val sunday = monday.plus(6, DateTimeUnit.DAY)
        val fromIso = Formatters.isoFromLocal(monday)
        val toIso = Formatters.isoFromLocal(sunday)
        viewModelScope.launch {
            releveRepo.releveByPersonnel(personnelId, fromIso, toIso).collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(weekEntries = result.data.sortedBy { e -> e.date })
                    }
                    is Result.Failure -> _state.update { it.copy(weekEntries = emptyList()) }
                }
            }
        }
    }
}

/** Personnel detail screen state. */
data class PersonnelDetailUiState(
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val personnel: Personnel? = null,
    val weekEntries: List<ReleveEntry> = emptyList(),
) {
    /** Last 10 Relevé entries (most recent first) — used by the "Relevé récent" section. */
    val recentEntries: List<ReleveEntry>
        get() = weekEntries.sortedByDescending { e -> e.recordedAt }.take(10)

    /** Total hours logged this week, derived from the week's entries. */
    val hoursLoggedThisWeek: Double
        get() = weekEntries.sumOf { e ->
            val out = e.hoursOut ?: e.hoursIn
            (out - e.hoursIn).coerceAtLeast(0.0)
        }

    /** Hours target for the week (from the personnel record). */
    val hoursTarget: Int get() = personnel?.weeklyHoursTarget ?: 0

    /** Per-day breakdown (Mon→Sun) of logged hours. Used by the bar chart card. */
    val perDayBreakdown: Map<DayOfWeek, Double>
        get() {
            val map = linkedMapOf<DayOfWeek, Double>()
            DayOfWeek.values().forEach { d -> map[d] = 0.0 }
            weekEntries.forEach { e ->
                runCatching {
                    val date = Formatters.localDateFromIso(e.date)
                    val out = e.hoursOut ?: e.hoursIn
                    val hours = (out - e.hoursIn).coerceAtLeast(0.0)
                    map[date.dayOfWeek] = (map[date.dayOfWeek] ?: 0.0) + hours
                }
            }
            return map
        }
}
