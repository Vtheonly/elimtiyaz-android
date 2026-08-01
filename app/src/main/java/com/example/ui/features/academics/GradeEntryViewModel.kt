package com.example.ui.features.academics

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AcademicClass
import com.example.domain.model.Student
import com.example.domain.model.Subject
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.EnterGradeInput
import com.example.domain.repository.GradeRepository
import com.example.domain.repository.StudentRepository
import com.example.domain.repository.SubjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ── 2. Grade Entry ────────────────────────────────────────────────────────

@HiltViewModel
class GradeEntryViewModel @Inject constructor(
    private val classRepository: ClassRepository,
    private val subjectRepository: SubjectRepository,
    private val studentRepository: StudentRepository,
    private val gradeRepository: GradeRepository,
) : ViewModel() {

    val classes: StateFlow<List<AcademicClass>> = classRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _subjects = kotlinx.coroutines.flow.MutableStateFlow<List<Subject>>(emptyList())
    val subjects: StateFlow<List<Subject>> = _subjects

    private val _students = kotlinx.coroutines.flow.MutableStateFlow<List<Student>>(emptyList())
    val students: StateFlow<List<Student>> = _students

    private val _busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun loadSubjectsForClass(classId: String) {
        viewModelScope.launch {
            subjectRepository.observeByClass(classId).collect { _subjects.value = it }
        }
    }

    fun loadStudentsForClass(classId: String) {
        viewModelScope.launch {
            studentRepository.observeByClass(classId).collect { _students.value = it }
        }
    }

    /**
     * Enter a grade via [GradeRepository.enterGrade]. The desktop's
     * `compute_grade_subject_average()` trigger will auto-compute
     * `subject_average = (d1 + d2 + 2*ex) / 4.0` server-side.
     */
    fun enterGrade(
        studentId: String,
        subjectId: String,
        classId: String,
        term: String,
        academicYear: String,
        devoir1: Double?,
        devoir2: Double?,
        examen: Double?,
        coefficient: Int,
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
            result.onSuccess { _message.value = "Note sauvegardée (moyenne auto-calculée par le serveur)." }
                .onFailure { _message.value = it.userMessage }
        }
    }

    fun clearMessage() { _message.value = null }
}

@Composable
