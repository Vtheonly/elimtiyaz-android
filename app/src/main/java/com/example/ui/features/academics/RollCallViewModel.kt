package com.example.ui.features.academics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Role
import com.example.domain.model.AcademicClass
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.Student
import com.example.domain.repository.AttendanceRepository
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.RollCallEntry
import com.example.domain.repository.StudentRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _students = MutableStateFlow<List<Student>>(emptyList())
    val students: StateFlow<List<Student>> = _students.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now().toString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _selectedSession = MutableStateFlow("morning")
    val selectedSession: StateFlow<String> = _selectedSession.asStateFlow()

    private val _existingRecords = MutableStateFlow<Map<String, AttendanceRecord>>(emptyMap())
    val existingRecords: StateFlow<Map<String, AttendanceRecord>> = _existingRecords.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var studentsJob: Job? = null
    private var recordsJob: Job? = null

    fun setDate(date: String, classId: String?) {
        _selectedDate.value = date
        if (classId != null) loadExistingAttendance(classId, date)
    }

    fun setSession(session: String) {
        _selectedSession.value = session
    }

    fun loadStudentsForClass(classId: String) {
        studentsJob?.cancel()
        studentsJob = viewModelScope.launch {
            studentRepository.observeByClass(classId).collect { _students.value = it }
        }
        loadExistingAttendance(classId, _selectedDate.value)
    }

    private fun loadExistingAttendance(classId: String, date: String) {
        recordsJob?.cancel()
        recordsJob = viewModelScope.launch {
            attendanceRepository.observeByClass(classId, date).collect { list ->
                _existingRecords.value = list.associateBy { it.studentId }
            }
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
            _message.value = null
            val result = attendanceRepository.recordRollCall(classId, date, session, records, actorId, actorName)
            _busy.value = false
            result.onSuccess {
                _message.value = "Appel enregistré avec succès pour le $date (${records.size} élèves)."
                loadExistingAttendance(classId, date)
            }.onFailure { err ->
                _message.value = err.userMessage
            }
        }
    }

    fun clearMessage() { _message.value = null }
}