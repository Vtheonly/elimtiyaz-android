package com.example.ui.features.academics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Role
import com.example.domain.model.AcademicClass
import com.example.domain.model.Student
import com.example.domain.repository.AttendanceRepository
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.RollCallEntry
import com.example.domain.repository.StudentRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RollCallViewModel @Inject constructor(
    private val classRepository: ClassRepository,
    private val studentRepository: StudentRepository,
    private val attendanceRepository: AttendanceRepository,
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

    private val _students = kotlinx.coroutines.flow.MutableStateFlow<List<Student>>(emptyList())
    val students: StateFlow<List<Student>> = _students

    private val _busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun loadStudentsForClass(classId: String) {
        viewModelScope.launch {
            studentRepository.observeByClass(classId).collect { _students.value = it }
        }
    }

    fun submitRollCall(
        classId: String,
        date: String,
        session: String,
        statuses: Map<String, AttendanceStatus>,
        lateTimes: Map<String, String>,
        actorId: String,
        actorName: String,
    ) {
        val records = statuses.map { (studentId, status) ->
            RollCallEntry(
                studentId = studentId,
                status = status.wireCode,
                note = if (status == AttendanceStatus.LATE) lateTimes[studentId]?.let { "Arrivée: $it" } else null,
            )
        }
        viewModelScope.launch {
            _busy.value = true
            val result = attendanceRepository.recordRollCall(classId, date, session, records, actorId, actorName)
            _busy.value = false
            result.onSuccess {
                _message.value = "Appel enregistré pour la classe (${records.size} élèves)."
            }.onFailure { err ->
                _message.value = err.userMessage
            }
        }
    }

    fun clearMessage() { _message.value = null }
}