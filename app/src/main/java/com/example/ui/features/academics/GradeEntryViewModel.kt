package com.example.ui.features.academics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Role
import com.example.domain.model.AcademicClass
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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