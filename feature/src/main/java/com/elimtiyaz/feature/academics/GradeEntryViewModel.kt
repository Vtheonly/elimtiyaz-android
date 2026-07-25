package com.elimtiyaz.feature.academics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.Assessment
import com.elimtiyaz.domain.model.ClassSubject
import com.elimtiyaz.domain.model.Student
import com.elimtiyaz.domain.model.Subject
import com.elimtiyaz.domain.repository.AuthRepository
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
import javax.inject.Inject

/**
 * View-model for the Grade Entry screen.
 *
 * Renders a table of students × (D1, D2, Examen) for a single class + subject
 * + term. Cells are edited inline; the subject-average column is recomputed
 * on the fly via [GradeRepository.subjectAverage]. On save, every changed row
 * is persisted via [GradeRepository.enterGrade].
 *
 * Per master plan §06.02: `subjectAverage = (D1 + D2 + 2 * Examen) / 4`.
 */
@HiltViewModel
class GradeEntryViewModel @Inject constructor(
    private val grades: GradeRepository,
    private val students: StudentRepository,
    private val subjects: SubjectRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    /** Current session — gates the save button (Permission.EnterGrades). */
    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val _uiState = MutableStateFlow(GradeEntryUiState())
    val uiState: StateFlow<GradeEntryUiState> = _uiState.asStateFlow()

    /** Load students + existing grades for the given class/subject/term. */
    fun load(classId: String, subjectId: String, term: String) {
        if (_uiState.value.classId == classId &&
            _uiState.value.subjectId == subjectId &&
            _uiState.value.term == term &&
            _uiState.value.rows.isNotEmpty()
        ) return
        _uiState.update {
            it.copy(isLoading = true, error = null, classId = classId, subjectId = subjectId, term = term)
        }
        viewModelScope.launch {
            launch { collectSubjectInfo(subjectId) }
            launch { collectClassSubjectInfo(classId, subjectId) }
            launch { collectRosterAndGrades(classId, subjectId, term) }
        }
    }

    /** Switch term (T1 / T2 / T3) — reloads grades for the new term. */
    fun changeTerm(term: String) {
        val s = _uiState.value
        if (s.classId != null && s.subjectId != null) load(s.classId, s.subjectId, term)
    }

    /** Update a single cell. Re-computes the row's subject average immediately. */
    fun updateCell(studentId: String, column: GradeColumn, value: String) {
        val parsed: Double? = value.trim().let { v ->
            if (v.isBlank()) null
            else v.toDoubleOrNull()?.let { d -> if (d in 0.0..20.0) d else null }
        }
        _uiState.update { st ->
            st.copy(
                rows = st.rows.map { row ->
                    if (row.studentId != studentId) row
                    else {
                        val d1 = if (column == GradeColumn.Devoir1) parsed else row.devoir1
                        val d2 = if (column == GradeColumn.Devoir2) parsed else row.devoir2
                        val ex = if (column == GradeColumn.Examen) parsed else row.examen
                        val avg = grades.subjectAverage(d1, d2, ex)
                        row.copy(devoir1 = d1, devoir2 = d2, examen = ex, average = avg, dirty = true)
                    }
                }
            )
        }
    }

    /** Persist every changed row. [onDone] receives null on success or an error string. */
    fun save(onDone: (String?) -> Unit) {
        val st = _uiState.value
        val actor = session.value?.userId ?: return run { onDone("Session expirée.") }
        val cid = st.classId ?: return run { onDone("Classe invalide.") }
        val sid = st.subjectId ?: return run { onDone("Matière invalide.") }
        val coef = st.classSubject?.coefficient ?: st.subject?.coefficient ?: 1.0
        val year = st.academicYear
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            var lastError: String? = null
            st.rows.forEach { row ->
                // Skip rows where nothing was entered at all.
                if (row.devoir1 == null && row.devoir2 == null && row.examen == null) return@forEach
                // Skip unchanged rows.
                if (!row.dirty) return@forEach
                val r = grades.enterGrade(
                    studentId = row.studentId,
                    subjectId = sid,
                    classId = cid,
                    term = st.term,
                    academicYear = year,
                    devoir1 = row.devoir1,
                    devoir2 = row.devoir2,
                    examen = row.examen,
                    coefficient = coef,
                    enteredBy = actor,
                )
                if (r is Result.Failure) lastError = r.error.userMessage
            }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    savedAt = Formatters.nowIso(),
                    rows = it.rows.map { r -> r.copy(dirty = false) },
                )
            }
            onDone(lastError)
        }
    }

    private suspend fun collectSubjectInfo(subjectId: String) {
        val all = subjects.subjects().first()
        if (all is Result.Success) {
            val found = all.data.firstOrNull { it.id == subjectId }
            _uiState.update {
                it.copy(subject = found, academicYear = Formatters.today().year.toString())
            }
        }
    }

    private suspend fun collectClassSubjectInfo(classId: String, subjectId: String) {
        val r = subjects.subjectsByClass(classId).first()
        if (r is Result.Success) {
            val cs: ClassSubject? = r.data.firstOrNull { it.subjectId == subjectId }
            _uiState.update { it.copy(classSubject = cs) }
        }
    }

    private suspend fun collectRosterAndGrades(classId: String, subjectId: String, term: String) {
        val rosterR = students.studentsByClass(classId).first()
        val gradesR = grades.gradesForClass(classId, subjectId, term, Formatters.today().year.toString()).first()
        if (rosterR is Result.Failure) {
            _uiState.update { it.copy(isLoading = false, error = rosterR.error) }
            return
        }
        val roster = (rosterR as Result.Success).data
        val existing: Map<String, Assessment> = (gradesR as? Result.Success)?.data
            ?.associateBy { it.studentId }
            ?: emptyMap()
        val rows = roster.map { s ->
            val a = existing[s.id]
            GradeRow(
                studentId = s.id,
                studentCode = s.code,
                firstName = s.firstName,
                lastName = s.lastName,
                devoir1 = a?.devoir1,
                devoir2 = a?.devoir2,
                examen = a?.examen,
                average = a?.subjectAverage ?: grades.subjectAverage(a?.devoir1, a?.devoir2, a?.examen),
                dirty = false,
            )
        }
        _uiState.update {
            it.copy(isLoading = false, rows = rows, error = null)
        }
    }

    /** True iff the user can save grades. */
    fun canEnterGrades(): Boolean = session.value?.can(Permission.EnterGrades) == true
}

/** Which column is being edited. */
enum class GradeColumn { Devoir1, Devoir2, Examen }

/**
 * Grade entry screen state.
 *
 * [classAverage] is the live recomputed mean of all row averages — shown in
 * the sticky header.
 */
data class GradeEntryUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: AppError? = null,
    val classId: String? = null,
    val subjectId: String? = null,
    val term: String = "T1",
    val academicYear: String = "",
    val subject: Subject? = null,
    val classSubject: ClassSubject? = null,
    val rows: List<GradeRow> = emptyList(),
    val savedAt: String? = null,
) {
    /** Live class average — mean of every non-null row average. */
    val classAverage: Double?
        get() = rows.mapNotNull { it.average }.takeIf { it.isNotEmpty() }?.average()

    /** Count of students with a passing average (≥10). */
    val passingCount: Int get() = rows.count { it.average != null && it.average >= 10.0 }

    /** Count of students with a failing average (<10). */
    val failingCount: Int get() = rows.count { it.average != null && it.average < 10.0 }

    /** Count of students with no grade entered. */
    val missingCount: Int get() = rows.count { it.average == null }
}

/** A single editable row in the grade-entry table. */
data class GradeRow(
    val studentId: String,
    val studentCode: String,
    val firstName: String,
    val lastName: String,
    val devoir1: Double?,
    val devoir2: Double?,
    val examen: Double?,
    val average: Double?,
    val dirty: Boolean = false,
)
