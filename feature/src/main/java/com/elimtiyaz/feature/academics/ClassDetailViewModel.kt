package com.elimtiyaz.feature.academics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.AcademicClass
import com.elimtiyaz.domain.model.Assessment
import com.elimtiyaz.domain.model.AttendanceRecord
import com.elimtiyaz.domain.model.ClassSubject
import com.elimtiyaz.domain.model.Student
import com.elimtiyaz.domain.repository.AttendanceRepository
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.ClassRepository
import com.elimtiyaz.domain.repository.GradeRepository
import com.elimtiyaz.domain.repository.StudentRepository
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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import javax.inject.Inject

/**
 * View-model for the [ClassDetailScreen].
 *
 * Loads a single class with its roster, subject mappings, recent attendance
 * records (this week), and latest grades per subject. Each stream is collected
 * independently so a failure in one doesn't block the others.
 */
@HiltViewModel
class ClassDetailViewModel @Inject constructor(
    private val classes: ClassRepository,
    private val subjects: SubjectRepository,
    private val students: StudentRepository,
    private val attendance: AttendanceRepository,
    private val grades: GradeRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    /** Current session — gates the quick-action buttons in the header. */
    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val _uiState = MutableStateFlow(ClassDetailUiState())
    val uiState: StateFlow<ClassDetailUiState> = _uiState.asStateFlow()

    /** Begin loading everything for the given class id. Called from screen LaunchedEffect. */
    fun load(classId: String) {
        if (_uiState.value.classId == classId && _uiState.value.classInfo != null) return
        _uiState.update { it.copy(isLoading = true, error = null, classId = classId) }
        viewModelScope.launch {
            launch { collectClass(classId) }
            launch { collectRoster(classId) }
            launch { collectSubjects(classId) }
            launch { collectWeekAttendance(classId) }
            launch { collectRecentGrades(classId) }
        }
    }

    /** Re-fetch every stream for the current class id. Useful after a roll call or grade save. */
    fun refresh() {
        val id = _uiState.value.classId ?: return
        // Reset the classInfo so the screen shows the loading indicator.
        _uiState.update { it.copy(isLoading = true, error = null, classInfo = null) }
        load(id)
    }

    private suspend fun collectClass(id: String) {
        classes.classById(id).collect { result ->
            when (result) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, classInfo = result.data, error = null)
                }
                is Result.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = result.error)
                }
            }
        }
    }

    private suspend fun collectRoster(classId: String) {
        students.studentsByClass(classId).collect { result ->
            when (result) {
                is Result.Success -> _uiState.update {
                    it.copy(roster = result.data, error = null)
                }
                is Result.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    private suspend fun collectSubjects(classId: String) {
        subjects.subjectsByClass(classId).collect { result ->
            when (result) {
                is Result.Success -> _uiState.update {
                    it.copy(subjects = result.data, error = null)
                }
                is Result.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    private suspend fun collectWeekAttendance(classId: String) {
        // This-week window — used by the "Présences" tab summary.
        val today = Formatters.today()
        val monday = today.minus(DatePeriod(days = today.dayOfWeek.value - 1))
        // recordsByClass returns records for a single date; the summary uses a 7-day loop.
        val weekRecords = mutableListOf<AttendanceRecord>()
        var cursor = monday
        repeat(7) {
            val iso = Formatters.isoFromLocal(cursor)
            val r = attendance.recordsByClass(classId, iso).first()
            if (r is Result.Success) weekRecords += r.data
            cursor = cursor.plus(DatePeriod(days = 1))
        }
        _uiState.update {
            it.copy(weekAttendance = weekRecords.sortedByDescending { r -> r.date })
        }
    }

    private suspend fun collectRecentGrades(classId: String) {
        // Pull grades for each subject in the class for the current term T1.
        // Best-effort: a subject failure doesn't block others.
        subjects.subjectsByClass(classId).collect { result ->
            if (result is Result.Success) {
                val accumulated = mutableListOf<Assessment>()
                result.data.forEach { cs ->
                    val r = grades.gradesForClass(classId, cs.subjectId, "T1", currentAcademicYear()).first()
                    if (r is Result.Success) accumulated += r.data
                }
                _uiState.update {
                    it.copy(recentGrades = accumulated.sortedByDescending { g -> g.enteredAt })
                }
            }
        }
    }

    /** Default academic year — the current calendar year as a string. */
    private fun currentAcademicYear(): String =
        Formatters.today().year.toString()

    /** True iff the user can perform roll call on this class. */
    fun canRollCall(): Boolean = session.value?.can(Permission.RollCall) == true

    /** True iff the user can enter grades. */
    fun canEnterGrades(): Boolean = session.value?.can(Permission.EnterGrades) == true

    /** True iff the user can push homework. */
    fun canAssignHomework(): Boolean = session.value?.can(Permission.AssignHomework) == true
}

/**
 * Aggregated state for the Class Detail screen.
 *
 * Tabs are: Élèves (roster), Matières (subjects), Présences (attendance),
 * Notes (grades). The header card always shows the class snapshot.
 */
data class ClassDetailUiState(
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val classId: String? = null,
    val classInfo: AcademicClass? = null,
    val roster: List<Student> = emptyList(),
    val subjects: List<ClassSubject> = emptyList(),
    val weekAttendance: List<AttendanceRecord> = emptyList(),
    val recentGrades: List<Assessment> = emptyList(),
) {
    /** Latest grade per subject id — used by the Notes tab. */
    val latestGradeBySubject: Map<String, Assessment>
        get() = recentGrades
            .groupBy { it.subjectId }
            .mapNotNull { (subjectId, list) ->
                list.maxByOrNull { it.enteredAt }?.let { subjectId to it }
            }
            .toMap()

    /** Count of students per attendance status this week. */
    val weekStatusCounts: Map<String, Int>
        get() = weekAttendance.groupingBy { it.status }.eachCount()
}
