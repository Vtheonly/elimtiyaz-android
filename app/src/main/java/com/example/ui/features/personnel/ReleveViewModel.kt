package com.example.ui.features.personnel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Personnel
import com.example.domain.model.ReleveEntry
import com.example.domain.repository.PersonnelRepository
import com.example.domain.repository.ReleveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// ── 2. Teacher Activity Ledger (Relevé) ───────────────────────────────────

/**
 * Activity / compliance view derived from [PersonnelRepository] +
 * [ReleveRepository].
 *
 * FIX (ignored route argument): `Routes.Releve(personnelId)` always rendered
 * the all-personnel compliance list — the argument was dropped on the floor.
 * The ViewModel now reads `personnelId` from [SavedStateHandle]: when present
 * (navigated from a personnel profile) the screen shows THAT person's ledger
 * entries; otherwise the directory-wide weekly compliance list.
 *
 * FIX (stale compliance): weekly compliance is now COMPUTED from the real
 * `releve_entries` rows of the current ISO week instead of the never-updated
 * `weeklyHoursLogged` column (which nothing ever wrote).
 */
@HiltViewModel
class ReleveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    personnelRepository: PersonnelRepository,
    releveRepository: ReleveRepository,
) : ViewModel() {

    /** Route argument — blank when the screen is opened from the Personnel hub. */
    val personnelId: String = savedStateHandle["personnelId"] ?: ""

    val personnel: StateFlow<List<Personnel>> = personnelRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** All recent activity entries (real rows from the `releve_entries` table). */
    val entries: StateFlow<List<ReleveEntry>> = releveRepository.observeRecent()
        .map { result -> (result as? com.example.core.Result.Ok)?.value ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** The focused staff member (when opened from a profile). */
    val focusedPersonnel: StateFlow<Personnel?> = combine(personnel, entries) { list, _ ->
        list.firstOrNull { it.id == personnelId }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** Real entries of the focused staff member (all-time, most recent first). */
    val focusedEntries: StateFlow<List<ReleveEntry>> = combine(entries, focusedPersonnel) { list, person ->
        if (person == null) emptyList() else list.filter { it.personnelId == person.id }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Minutes logged during the CURRENT ISO week per personnel — computed from
     * real entries (Monday 00:00 → now), keyed by personnelId.
     */
    val weeklyMinutesByPersonnel: StateFlow<Map<String, Long>> = entries
        .map { list ->
            val weekStart = LocalDate.now(ZoneOffset.UTC)
                .minusDays((LocalDate.now(ZoneOffset.UTC).dayOfWeek.value - 1).toLong())
                .toString()
            list.filter { it.date >= weekStart }
                .groupBy { it.personnelId }
                .mapValues { (_, rows) -> rows.sumOf { it.durationMinutes ?: 0L } }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
}
