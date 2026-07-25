package com.elimtiyaz.feature.academics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.AttendanceStatus
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.AttendanceRecord
import com.elimtiyaz.domain.model.AttendanceSession
import com.elimtiyaz.domain.model.Student
import com.elimtiyaz.domain.repository.AttendanceRepository
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
 * View-model for the 30-second roll call screen (master plan §09.01).
 *
 * Loads the class roster, defaults every student to `Present`, and lets the
 * teacher cycle through P / AE / AN / R for each row. On save, the entire
 * batch is sent to [AttendanceRepository.recordRollCall] which also triggers
 * the §09.03 absence alert when a student reaches 3 absences.
 *
 * The whole flow is offline-first: a write failure enqueues the batch on the
 * sync queue and the UI shows a banner (handled by the data layer).
 */
@HiltViewModel
class RollCallViewModel @Inject constructor(
    private val students: StudentRepository,
    private val attendance: AttendanceRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    /** Current session — used for `recordedBy` on save and to gate the screen. */
    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val _uiState = MutableStateFlow(RollCallUiState())
    val uiState: StateFlow<RollCallUiState> = _uiState.asStateFlow()

    /** Tracks the in-flight load coroutine so a date/session change cancels the previous one. */
    private var loadJob: Job? = null

    /** Load roster for the given class and default everyone to Present. */
    fun load(classId: String, dateIso: String, session: AttendanceSession) {
        loadJob?.cancel()
        _uiState.update {
            it.copy(isLoading = true, error = null, classId = classId, dateIso = dateIso, session = session)
        }
        loadJob = viewModelScope.launch {
            students.studentsByClass(classId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val rows = result.data.map { s ->
                            RollCallRow(
                                studentId = s.id,
                                studentCode = s.code,
                                firstName = s.firstName,
                                lastName = s.lastName,
                                status = AttendanceStatus.Present.key,
                            )
                        }
                        _uiState.update {
                            it.copy(isLoading = false, roster = result.data, rows = rows, error = null)
                        }
                        // Also fetch any existing records for this date+session so the
                        // teacher can resume instead of overwriting.
                        loadExistingRecords(classId, dateIso)
                    }
                    is Result.Failure -> _uiState.update {
                        it.copy(isLoading = false, error = result.error)
                    }
                }
            }
        }
    }

    /** Pre-fill statuses from already-saved records so we resume cleanly. */
    private suspend fun loadExistingRecords(classId: String, dateIso: String) {
        val r = attendance.recordsByClass(classId, dateIso).first()
        if (r is Result.Success) {
            val byStudent = r.data.associateBy { it.studentId }
            _uiState.update { st ->
                st.copy(
                    rows = st.rows.map { row ->
                        val rec = byStudent[row.studentId]
                        if (rec != null) row.copy(status = rec.status, existingRecordId = rec.id)
                        else row
                    }
                )
            }
        }
    }

    /** Change a single student's status — invoked by tapping a status chip. */
    fun setStatus(studentId: String, status: AttendanceStatus) {
        _uiState.update { st ->
            st.copy(rows = st.rows.map { if (it.studentId == studentId) it.copy(status = status.key) else it })
        }
    }

    /** Convenience — bulk-set every row to Present (the "Tous présents" button). */
    fun markAllPresent() {
        _uiState.update { st ->
            st.copy(rows = st.rows.map { it.copy(status = AttendanceStatus.Present.key) })
        }
    }

    /** Persist the roll call batch; [onDone] receives null on success or an error string. */
    fun save(onDone: (String?) -> Unit) {
        val st = _uiState.value
        val actor = session.value?.userId ?: return run { onDone("Session expirée.") }
        if (st.classId == null) return run { onDone("Classe invalide.") }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val statusesMap: Map<String, String> = st.rows.associate { it.studentId to it.status }
            when (val r = attendance.recordRollCall(
                classId = st.classId,
                date = st.dateIso,
                session = st.session,
                statuses = statusesMap,
                recordedBy = actor,
            )) {
                is Result.Success -> {
                    // Trigger absence alerts per §09.03 — any absent student may have hit the
                    // 3-absence threshold. alertAbsences() is idempotent on the server side.
                    val absentIds = r.data
                        .filter { it.status != AttendanceStatus.Present.key }
                        .map { it.id }
                    if (absentIds.isNotEmpty()) {
                        attendance.alertAbsences(absentIds)
                    }
                    _uiState.update {
                        it.copy(isSaving = false, savedRecords = r.data, savedAt = Formatters.nowIso())
                    }
                    onDone(null)
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(isSaving = false, error = r.error) }
                    onDone(r.error.userMessage)
                }
            }
        }
    }

    /** Update the selected date — reloads existing records for the new date. */
    fun changeDate(dateIso: String) {
        val cid = _uiState.value.classId ?: return
        load(cid, dateIso, _uiState.value.session)
    }

    /** Update the selected session (Morning / Afternoon). */
    fun changeSession(session: AttendanceSession) {
        val cid = _uiState.value.classId ?: return
        load(cid, _uiState.value.dateIso, session)
    }

    /** True iff the user is allowed to record roll call. */
    fun canRollCall(): Boolean = session.value?.can(Permission.RollCall) == true
}

/**
 * Roll-call screen state.
 *
 * [rows] is the editable matrix; [savedRecords] is set after a successful save
 * and the screen uses [savedAt] to show a confirmation toast.
 */
data class RollCallUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: AppError? = null,
    val classId: String? = null,
    val dateIso: String = "",
    val session: AttendanceSession = AttendanceSession.Morning,
    val roster: List<Student> = emptyList(),
    val rows: List<RollCallRow> = emptyList(),
    val savedRecords: List<AttendanceRecord> = emptyList(),
    val savedAt: String? = null,
) {
    /** Number of students in each non-Present status — used by the sticky counter. */
    val absentCount: Int
        get() = rows.count { it.status != AttendanceStatus.Present.key }

    /** True iff at least one row is not Present — disables nothing but flags the counter. */
    val hasAbsences: Boolean get() = absentCount > 0
}

/** A single row in the roll call grid. */
data class RollCallRow(
    val studentId: String,
    val studentCode: String,
    val firstName: String,
    val lastName: String,
    val status: String,                    // AttendanceStatus.key
    val existingRecordId: String? = null,  // populated when resuming an existing record
)
