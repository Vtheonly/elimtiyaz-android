package com.elimtiyaz.feature.academics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AcademicLevel
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.AcademicClass
import com.elimtiyaz.domain.model.Homework
import com.elimtiyaz.domain.model.Subject
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.ClassRepository
import com.elimtiyaz.domain.repository.HomeworkRepository
import com.elimtiyaz.domain.repository.SubjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View-model for the Academics hub tab (root of the Pédagogie feature).
 *
 * Aggregates three parallel streams — classes (grouped by level), subjects, and
 * recently pushed homework — and exposes them through a single [uiState].
 * Tab-switching is purely UI-side; this VM only owns the data.
 *
 * @param classes  ClassRepository — list / level / detail.
 * @param subjects SubjectRepository — flat list of subjects.
 * @param homework HomeworkRepository — recent homework across all classes.
 * @param auth     AuthRepository — exposes the current [Session] for permission gating.
 */
@HiltViewModel
class AcademicsHubViewModel @Inject constructor(
    private val classes: ClassRepository,
    private val subjects: SubjectRepository,
    private val homework: HomeworkRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    /** Current session — used by the screen to gate FABs / menu items. */
    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val _uiState = MutableStateFlow(AcademicsHubUiState())
    val uiState: StateFlow<AcademicsHubUiState> = _uiState.asStateFlow()

    init { load() }

    /** Reload every stream from cache-then-network. Safe to call repeatedly. */
    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // The hub is rendered with three independent feeds so we collect each
            // in its own launch and let them flow into the same UiState.
            launch { collectClasses() }
            launch { collectSubjects() }
            launch { collectHomework() }
        }
    }

    /** Apply a search query across all three tabs (filtered in-memory). */
    fun onSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /** Filter the Matières tab by level key (AcademicLevel.key) or null = all. */
    fun onLevelFilter(level: AcademicLevel?) {
        _uiState.update { it.copy(levelFilter = level) }
    }

    private suspend fun collectClasses() {
        classes.classes().collect { result ->
            when (result) {
                is Result.Success -> {
                    val grouped = result.data.groupBy { AcademicLevel.from(it.level) }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            primaireClasses = grouped[AcademicLevel.Primaire].orEmpty(),
                            cemClasses = grouped[AcademicLevel.CEM].orEmpty(),
                            lyceeClasses = grouped[AcademicLevel.Lycee].orEmpty(),
                        )
                    }
                }
                is Result.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = result.error)
                }
            }
        }
    }

    private suspend fun collectSubjects() {
        subjects.subjects().collect { result ->
            when (result) {
                is Result.Success -> _uiState.update {
                    it.copy(subjects = result.data, error = null)
                }
                is Result.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    private suspend fun collectHomework() {
        // Homework is listed by class; the hub shows a flattened recent feed.
        // We re-snapshot per-class homework whenever the class list changes.
        classes.classes().collect { result ->
            if (result is Result.Success) {
                val latest = mutableListOf<Homework>()
                result.data.forEach { cls ->
                    val r = homework.homeworkForClass(cls.id).first()
                    if (r is Result.Success) latest += r.data
                }
                _uiState.update {
                    it.copy(
                        recentHomework = latest.sortedByDescending { h -> h.createdAt }.take(20),
                    )
                }
            }
        }
    }

    /** Convenience accessor for the screen — true iff the user can add classes. */
    fun canManageClasses(): Boolean = session.value?.can(Permission.ManageClasses) == true

    /** True iff the user can push homework. */
    fun canAssignHomework(): Boolean = session.value?.can(Permission.AssignHomework) == true
}

/**
 * Aggregated state for the Academics hub.
 *
 * The three lists are rendered by the three TabRow sections (Classes / Matières /
 * Devoirs). [searchQuery] filters all three lists client-side; [levelFilter]
 * only filters the Matières tab.
 */
data class AcademicsHubUiState(
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val searchQuery: String = "",
    val levelFilter: AcademicLevel? = null,
    val primaireClasses: List<AcademicClass> = emptyList(),
    val cemClasses: List<AcademicClass> = emptyList(),
    val lyceeClasses: List<AcademicClass> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val recentHomework: List<Homework> = emptyList(),
) {
    /** All classes flattened (used by the search filter). */
    val allClasses: List<AcademicClass>
        get() = primaireClasses + cemClasses + lyceeClasses

    /** Subjects filtered by the level picker (or all if null). */
    val filteredSubjects: List<Subject>
        get() = if (levelFilter == null) subjects
        else subjects.filter { it.level == levelFilter.key }

    /** Filtered classes by the search box. */
    fun filteredClasses(): List<AcademicClass> {
        val q = searchQuery.trim().lowercase()
        return if (q.isBlank()) allClasses
        else allClasses.filter {
            it.name.lowercase().contains(q) ||
                (it.homeroomTeacherName?.lowercase()?.contains(q) == true) ||
                (it.room?.lowercase()?.contains(q) == true)
        }
    }

    /** Filtered homework by the search box. */
    fun filteredHomework(): List<Homework> {
        val q = searchQuery.trim().lowercase()
        return if (q.isBlank()) recentHomework
        else recentHomework.filter {
            it.title.lowercase().contains(q) || it.subjectName.lowercase().contains(q)
        }
    }
}
