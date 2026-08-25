package com.example.ui.features.academics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Role
import com.example.domain.model.AcademicClass
import com.example.domain.model.Assessment
import com.example.domain.model.Student
import com.example.domain.model.Subject
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.EnterGradeInput
import com.example.domain.repository.GradeRepository
import com.example.domain.repository.StudentRepository
import com.example.domain.repository.SubjectRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class GradeEntryViewModel @Inject constructor(
    private val classRepository: ClassRepository,
    private val subjectRepository: SubjectRepository,
    private val studentRepository: StudentRepository,
    private val gradeRepository: GradeRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val classes: StateFlow<List<AcademicClass>> = classRepository.observe()
        .map { all ->
            val session = sessionManager.current()
            if (session?.role == Role.TEACHER) {
                val teacherId = session.userId
                val teacherName = session.displayName
                val scoped = all.filter {
                    it.homeroomTeacherId == teacherId ||
                    (it.homeroomTeacherName != null && it.homeroomTeacherName.equals(teacherName, ignoreCase = true))
                }
                if (scoped.isNotEmpty()) scoped else all.take(1)
            } else {
                all
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _subjects = MutableStateFlow<List<Subject>>(emptyList())
    val subjects: StateFlow<List<Subject>> = _subjects

    private val _students = MutableStateFlow<List<Student>>(emptyList())
    val students: StateFlow<List<Student>> = _students

    /**
     * FIX (thin UI): the existing assessments for the selected
     * (class, subject, term) — powers the class gradebook roster and the
     * class-level statistics (completion, class average, pass rate).
     */
    private val _classAssessments = MutableStateFlow<List<Assessment>>(emptyList())
    val classAssessments: StateFlow<List<Assessment>> = _classAssessments

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    // FIX (collector leak): each loadSubjectsForClass / loadStudentsForClass
    // call previously launched a NEW never-cancelled collect — switching
    // classes repeatedly stacked observers that kept overwriting each other.
    private var subjectsJob: Job? = null
    private var studentsJob: Job? = null
    private var gradebookJob: Job? = null

    fun loadSubjectsForClass(classId: String) {
        subjectsJob?.cancel()
        subjectsJob = viewModelScope.launch {
            subjectRepository.observeByClass(classId).collect { _subjects.value = it }
        }
    }

    fun loadStudentsForClass(classId: String) {
        studentsJob?.cancel()
        studentsJob = viewModelScope.launch {
            studentRepository.observeByClass(classId).collect { _students.value = it }
        }
    }

    /**
     * Observe the real assessment rows for (class, subject, term, year) so the
     * gradebook roster reflects persisted marks and updates live after saves.
     */
    fun loadGradebook(classId: String, subjectId: String, term: String, academicYear: String) {
        gradebookJob?.cancel()
        gradebookJob = viewModelScope.launch {
            gradeRepository.observeForClass(classId, subjectId, term, academicYear)
                .collect { _classAssessments.value = it }
        }
    }

    /**
     * FIX (blind edit): fetch the existing assessment for a
     * (student, subject, term, year) and hand it to the caller so the grade
     * entry form can pre-fill the fields instead of silently overwriting.
     */
    fun loadExistingMark(
        studentId: String,
        subjectId: String,
        term: String,
        academicYear: String,
        onLoaded: (com.example.domain.model.Assessment?) -> Unit,
    ) {
        viewModelScope.launch {
            val existing = gradeRepository
                .observeForStudent(studentId, term, academicYear)
                .firstOrNull()
                ?.firstOrNull { it.subjectId == subjectId }
            onLoaded(existing)
        }
    }

    fun enterGrade(
        studentId: String,
        subjectId: String,
        classId: String,
        term: String,
        academicYear: String,
        devoir1: Double?,
        devoir2: Double?,
        examen: Double?,
        coefficient: Double,
        actorId: String,
        actorName: String,
    ) {
        viewModelScope.launch {
            _busy.value = true
            val input = EnterGradeInput(
                studentId = studentId, subjectId = subjectId, classId = classId,
                term = term, academicYear = academicYear,
                devoir1 = devoir1, devoir2 = devoir2, examen = examen,
                coefficient = coefficient,
            )
            val result = gradeRepository.enterGrade(input, actorId, actorName)
            _busy.value = false
            result.onSuccess { assessment ->
                // The message mirrors the CANONICAL persisted average (server
                // trigger is the authority) — not a client-side guess.
                _message.value = assessment.subjectAverage?.let { avg ->
                    "Note sauvegardée — moyenne officielle : %.2f / 20.".format(avg)
                } ?: "Note sauvegardée (moyenne incomplète — 3 notes requises)."
            }
                .onFailure { _message.value = it.userMessage }
        }
    }

    fun clearMessage() { _message.value = null }
}
