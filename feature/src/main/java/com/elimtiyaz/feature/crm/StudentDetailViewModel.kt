package com.elimtiyaz.feature.crm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.Assessment
import com.elimtiyaz.domain.model.AttendanceRecord
import com.elimtiyaz.domain.model.Payment
import com.elimtiyaz.domain.model.Student
import com.elimtiyaz.domain.repository.AttendanceRepository
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.GradeRepository
import com.elimtiyaz.domain.repository.PaymentRepository
import com.elimtiyaz.domain.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * StudentDetailViewModel — powers Route.StudentDetail.
 *
 * Loads one student plus the four sub-collections needed for the screen's
 * four tabs:
 *  - Infos            → student object (parent ref + medical + transport + photo)
 *  - Académique       → current-term assessments + academic history timeline
 *  - Présences        → this-month attendance records + counts
 *  - Paiements        → payments linked to this student
 */
@HiltViewModel
class StudentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val studentRepo: StudentRepository,
    private val gradeRepo: GradeRepository,
    private val attendanceRepo: AttendanceRepository,
    private val paymentRepo: PaymentRepository,
    auth: AuthRepository,
) : ViewModel() {

    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val studentId: String = savedStateHandle.get<String>("studentId").orEmpty()

    private val _state = MutableStateFlow(StudentDetailUiState())
    val state: StateFlow<StudentDetailUiState> = _state.asStateFlow()

    init { reload() }

    /** Re-fetch all student data. */
    fun reload() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            studentRepo.student(studentId).collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(isLoading = false, student = result.data, error = null)
                    }
                    is Result.Failure -> _state.update {
                        it.copy(isLoading = false, error = result.error)
                    }
                }
            }
        }
        viewModelScope.launch {
            gradeRepo.gradesForStudent(studentId, term = null, academicYear = currentAcademicYear())
                .collect { result ->
                    when (result) {
                        is Result.Success -> _state.update { it.copy(assessments = result.data) }
                        is Result.Failure -> _state.update { it.copy(assessments = emptyList()) }
                    }
                }
        }
        viewModelScope.launch {
            val (from, to) = monthRange()
            attendanceRepo.recordsByStudent(studentId, from, to).collect { result ->
                when (result) {
                    is Result.Success -> _state.update { it.copy(attendance = result.data) }
                    is Result.Failure -> _state.update { it.copy(attendance = emptyList()) }
                }
            }
        }
        viewModelScope.launch {
            paymentRepo.paymentsByStudent(studentId).collect { result ->
                when (result) {
                    is Result.Success -> _state.update { it.copy(payments = result.data) }
                    is Result.Failure -> _state.update { it.copy(payments = emptyList()) }
                }
            }
        }
    }

    /** Promote the student to the next grade year — UI gates on [Permission.PromoteStudent]. */
    fun promote(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val r = studentRepo.promote(listOf(studentId), currentAcademicYear())) {
                is Result.Success -> {
                    _state.update {
                        it.copy(student = r.data.firstOrNull { s -> s.id == studentId } ?: it.student)
                    }
                    onResult(true, null)
                }
                is Result.Failure -> onResult(false, r.error.userMessage)
            }
        }
    }

    private fun currentAcademicYear(): String {
        val now = com.elimtiyaz.core.common.Formatters.today()
        // Academic year starts September — if before September, use previous year.
        val year = if (now.monthNumber >= 9) now.year else now.year - 1
        return "${year}-${year + 1}"
    }

    private fun monthRange(): Pair<String, String> {
        val today = com.elimtiyaz.core.common.Formatters.today()
        val first = kotlinx.datetime.LocalDate(today.year, today.monthNumber, 1)
        val last = kotlinx.datetime.LocalDate(today.year, today.monthNumber, 28)
        return com.elimtiyaz.core.common.Formatters.isoFromLocal(first) to
            com.elimtiyaz.core.common.Formatters.isoFromLocal(last)
    }
}

/** Student detail screen state. */
data class StudentDetailUiState(
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val student: Student? = null,
    val assessments: List<Assessment> = emptyList(),
    val attendance: List<AttendanceRecord> = emptyList(),
    val payments: List<Payment> = emptyList(),
) {
    /** Number of presences this month. */
    val presentCount: Int get() = attendance.count { it.status == "present" }
    /** Number of excused absences this month. */
    val excusedCount: Int get() = attendance.count { it.status == "absent_excused" }
    /** Number of unexcused absences this month. */
    val unexcusedCount: Int get() = attendance.count { it.status == "absent_unexcused" }
    /** Number of late arrivals this month. */
    val lateCount: Int get() = attendance.count { it.status == "late" }
}
