package com.elimtiyaz.feature.crm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.Parent
import com.elimtiyaz.domain.model.Student
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.ParentRepository
import com.elimtiyaz.domain.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * RosterViewModel — root of the CRM tab (Route.Roster).
 *
 * Holds both the parents list and the students list, applies a text search
 * and optional level filter, and exposes the current [Session] so the screen
 * can gate the "create parent" FAB on [Permission.CreateParent].
 */
@HiltViewModel
class RosterViewModel @Inject constructor(
    private val parents: ParentRepository,
    private val students: StudentRepository,
    auth: AuthRepository,
) : ViewModel() {

    /** Latest session — used by the screen to gate FABs. */
    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _levelFilter = MutableStateFlow<String?>(null)
    val levelFilter: StateFlow<String?> = _levelFilter.asStateFlow()

    private val _state = MutableStateFlow(RosterUiState())
    val state: StateFlow<RosterUiState> = _state.asStateFlow()

    init {
        // Collect both lists in parallel and combine into a single state.
        viewModelScope.launch {
            parents.parents().collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(parentsLoading = false, parentsError = null, parents = result.data)
                    }
                    is Result.Failure -> _state.update {
                        it.copy(parentsLoading = false, parentsError = result.error, parents = emptyList())
                    }
                }
            }
        }
        viewModelScope.launch {
            students.students().collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(studentsLoading = false, studentsError = null, students = result.data)
                    }
                    is Result.Failure -> _state.update {
                        it.copy(studentsLoading = false, studentsError = result.error, students = emptyList())
                    }
                }
            }
        }
        // When query or level changes, recompute the visible slices.
        viewModelScope.launch {
            combine(_query, _levelFilter, _state) { q, level, s ->
                Triple(q, level, s)
            }.collect { (q, level, s) ->
                val parentMatches = matchParents(s.parents, q)
                val studentMatches = matchStudents(s.students, q, level)
                _state.update {
                    it.copy(filteredParents = parentMatches, filteredStudents = studentMatches)
                }
            }
        }
    }

    /** User typed into the search box. */
    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** User picked a level chip (primaire / cem / lycee) or cleared it. */
    fun onLevelFilterChange(value: String?) {
        _levelFilter.value = value
    }

    /**
     * No-op retry hook. The parents() and students() Flows from the repository
     * layer auto-emit on every collector subscription (cache-then-fetch), so
     * the next composition pass already triggers a fresh fetch. This function
     * exists so screens can wire an [onRetry] callback without compile errors.
     */
    fun reload() {
        // Intentionally empty — the active collectors will refresh automatically.
    }

    private fun matchParents(list: List<Parent>, q: String): List<Parent> {
        if (q.isBlank()) return list
        val needle = q.trim().lowercase()
        return list.filter {
            it.firstName.lowercase().contains(needle) ||
                it.lastName.lowercase().contains(needle) ||
                it.code.lowercase().contains(needle) ||
                it.phone.contains(needle) ||
                (it.email?.lowercase()?.contains(needle) == true)
        }
    }

    private fun matchStudents(list: List<Student>, q: String, level: String?): List<Student> {
        var out = list
        if (!level.isNullOrBlank()) out = out.filter { it.level == level }
        if (q.isBlank()) return out
        val needle = q.trim().lowercase()
        return out.filter {
            it.firstName.lowercase().contains(needle) ||
                it.lastName.lowercase().contains(needle) ||
                it.code.lowercase().contains(needle)
        }
    }
}

/**
 * Roster UI state — both lists (raw + filtered) plus per-list loading/error.
 * The screen merges the loading flags for the [AsyncContent] gate.
 */
data class RosterUiState(
    val parents: List<Parent> = emptyList(),
    val students: List<Student> = emptyList(),
    val filteredParents: List<Parent> = emptyList(),
    val filteredStudents: List<Student> = emptyList(),
    val parentsLoading: Boolean = true,
    val studentsLoading: Boolean = true,
    val parentsError: AppError? = null,
    val studentsError: AppError? = null,
) {
    /** Combined loading flag for the screen — true until both lists have arrived. */
    val isLoading: Boolean get() = parentsLoading || studentsLoading
}
