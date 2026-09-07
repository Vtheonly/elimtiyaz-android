package com.example.ui.features.crm

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Whatsapp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LedgerEntry
import com.example.core.ParentLedgerSummary
import com.example.core.Result
import com.example.core.computeOverallGpa
import com.example.core.formatDzd
import com.example.core.GRADE_LEVEL_CODES
import com.example.core.isPassing
import com.example.domain.model.Assessment
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.model.Payment
import com.example.domain.model.Student
import com.example.domain.repository.AttendanceRepository
import com.example.domain.repository.GradeRepository
import com.example.domain.repository.InstallmentRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.PaymentRepository
import com.example.domain.repository.StudentRepository
import com.example.ui.components.ElAlertBanner
import com.example.ui.components.ElAlertSeverity
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElProgressBar
import com.example.ui.components.ElScaffold
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTopBar
import com.example.ui.components.ModernSecondaryTabRow
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold
import com.example.ui.theme.elDesignTokens
import com.example.ui.util.PhoneUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class AttendanceStats(
    val presentCount: Int = 0,
    val unexcusedCount: Int = 0,
    val excusedCount: Int = 0,
    val lateCount: Int = 0,
    val totalCount: Int = 0,
    val rate: Double = 100.0,
)

data class AcademicYearHistory(
    val academicYear: String,
    val termGpas: Map<String, Double?>,
    val yearlyGpa: Double?,
    val assessments: List<Assessment>,
    val gradeLevel: String?,
    val promotionOutcome: String?,
    val attendanceRate: Double?,
    val isArchived: Boolean,
)

@HiltViewModel
class StudentDetailViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val parentRepository: ParentRepository,
    private val gradeRepository: GradeRepository,
    private val subjectRepository: com.example.domain.repository.SubjectRepository,
    private val attendanceRepository: AttendanceRepository,
    private val installmentRepository: InstallmentRepository,
    private val paymentRepository: PaymentRepository,
    private val ledgerRepository: LedgerRepository,
    private val auditRepository: com.example.domain.repository.AuditRepository,
    private val pdfRepository: com.example.domain.repository.PdfRepository,
    private val sessionManager: com.example.session.SessionManager,
) : ViewModel() {

    private val _student = MutableStateFlow<Student?>(null)
    val student: StateFlow<Student?> = _student.asStateFlow()

    private val _parent = MutableStateFlow<Parent?>(null)
    val parent: StateFlow<Parent?> = _parent.asStateFlow()

    private val _siblings = MutableStateFlow<List<Student>>(emptyList())
    val siblings: StateFlow<List<Student>> = _siblings.asStateFlow()

    private val _assessments = MutableStateFlow<List<Assessment>>(emptyList())
    val assessments: StateFlow<List<Assessment>> = _assessments.asStateFlow()

    private val _subjects = MutableStateFlow<List<com.example.domain.model.Subject>>(emptyList())
    val subjects: StateFlow<List<com.example.domain.model.Subject>> = _subjects.asStateFlow()

    private val _classAssessments = MutableStateFlow<List<Assessment>>(emptyList())
    val classAssessments: StateFlow<List<Assessment>> = _classAssessments.asStateFlow()

    private val _termGpas = MutableStateFlow<Map<String, Double?>>(emptyMap())
    val termGpas: StateFlow<Map<String, Double?>> = _termGpas.asStateFlow()

    private val _attendanceRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val attendanceRecords: StateFlow<List<AttendanceRecord>> = _attendanceRecords.asStateFlow()

    private val _attendanceStats = MutableStateFlow(AttendanceStats())
    val attendanceStats: StateFlow<AttendanceStats> = _attendanceStats.asStateFlow()

    private val _installments = MutableStateFlow<List<Installment>>(emptyList())
    val installments: StateFlow<List<Installment>> = _installments.asStateFlow()

    private val _payments = MutableStateFlow<List<Payment>>(emptyList())
    val payments: StateFlow<List<Payment>> = _payments.asStateFlow()

    private val _ledgerEntries = MutableStateFlow<List<LedgerEntry>>(emptyList())
    val ledgerEntries: StateFlow<List<LedgerEntry>> = _ledgerEntries.asStateFlow()

    private val _familySummary = MutableStateFlow<ParentLedgerSummary?>(null)
    val familySummary: StateFlow<ParentLedgerSummary?> = _familySummary.asStateFlow()

    private val _academicHistory = MutableStateFlow<List<AcademicYearHistory>>(emptyList())
    val academicHistory: StateFlow<List<AcademicYearHistory>> = _academicHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null
    private var detailJob: kotlinx.coroutines.Job? = null
    private var gradesJob: kotlinx.coroutines.Job? = null
    private var classGradesJob: kotlinx.coroutines.Job? = null

    fun load(studentId: String, term: String = "T1", academicYear: String? = null) {
        loadJob?.cancel()
        detailJob?.cancel()
        gradesJob?.cancel()
        classGradesJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            val year = academicYear ?: currentAcademicYear()
            launch {
                subjectRepository.observe().collect { _subjects.value = it }
            }
            studentRepository.observeById(studentId).collect { s ->
                _student.value = s
                detailJob?.cancel()
                if (s != null) {
                    detailJob = viewModelScope.launch {
                        launch {
                            parentRepository.observeById(s.parentId).collect { p -> _parent.value = p }
                        }
                        launch {
                            studentRepository.observeByParent(s.parentId).collect { sibs ->
                                _siblings.value = sibs.filter { it.id != studentId }
                            }
                        }
                        launch {
                            when (val result = ledgerRepository.summary(s.parentId)) {
                                is Result.Ok -> _familySummary.value = result.value
                                is Result.Err -> _error.value = result.error.userMessage
                            }
                        }
                        launch {
                            gradesJob?.cancel()
                            gradesJob = launch {
                                gradeRepository.observeForStudent(studentId, term, year).collect { list ->
                                    _assessments.value = list
                                }
                            }
                        }
                        launch { loadTermGpas(studentId, year) }
                        s.classId?.let { cid ->
                            classGradesJob?.cancel()
                            classGradesJob = launch {
                                gradeRepository.observeForClass(cid, term, year).collect { list ->
                                    _classAssessments.value = list
                                }
                            }
                        }
                        launch {
                            attendanceRepository.observeByStudent(studentId).collect { attList ->
                                _attendanceRecords.value = attList
                                val present = attList.count { it.status == "present" }
                                val unexcused = attList.count { it.status == "absent_unexcused" }
                                val excused = attList.count { it.status == "absent_excused" }
                                val lates = attList.count { it.status == "late" }
                                val total = attList.size
                                val rate = if (total > 0) (present.toDouble() / total.toDouble() * 100.0) else 100.0
                                _attendanceStats.value = AttendanceStats(present, unexcused, excused, lates, total, rate)
                            }
                        }
                        launch {
                            installmentRepository.observeByStudent(studentId).collect {
                                _installments.value = it
                            }
                        }
                        launch {
                            paymentRepository.observeByStudent(studentId).collect {
                                _payments.value = it
                            }
                        }
                        launch { loadAcademicHistory(studentId, s.gradeLevel) }
                    }
                }
                _isLoading.value = false
            }
        }
    }

    fun loadGradesForTerm(studentId: String, term: String, academicYear: String? = null) {
        val year = academicYear ?: currentAcademicYear()
        gradesJob?.cancel()
        gradesJob = viewModelScope.launch {
            gradeRepository.observeForStudent(studentId, term, year)
                .collect { list -> _assessments.value = list }
        }
        val classId = _student.value?.classId
        if (classId != null) {
            classGradesJob?.cancel()
            classGradesJob = viewModelScope.launch {
                gradeRepository.observeForClass(classId, term, year).collect { list ->
                    _classAssessments.value = list
                }
            }
        }
    }

    private suspend fun loadAcademicHistory(studentId: String, currentGradeLevel: String) {
        val all = gradeRepository.observeAllForStudent(studentId).firstOrNull().orEmpty()
        if (all.isEmpty()) {
            _academicHistory.value = emptyList()
            return
        }
        val attendance = attendanceRepository.observeByStudent(studentId).firstOrNull().orEmpty()

        val promoteAudits = auditRepository
            .observeByEntity("student", studentId)
            .firstOrNull().orEmpty()
            .filter { it.action == "student.promote" }
        val outcomeByYear = mutableMapOf<String, Pair<String, String?>>()
        promoteAudits.forEach { log ->
            val after = log.afterJson ?: return@forEach
            val decision = Regex("\\\"decision\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(after)?.groupValues?.get(1)
            val year = Regex("\\\"year\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(after)?.groupValues?.get(1)
            val from = Regex("\\\"from\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(after)?.groupValues?.get(1)
            if (decision != null && year != null) outcomeByYear[year] = decision to from
        }

        val currentYear = currentAcademicYear()
        val history = all.groupBy { it.academicYear }
            .map { (year, rows) ->
                val termGpas = linkedMapOf<String, Double?>()
                for (t in listOf("T1", "T2", "T3")) {
                    val termRows = rows.filter { it.term == t }
                    termGpas[t] = if (termRows.isEmpty()) null else com.example.core.computeOverallGpa(termRows)
                }
                val yearlyAttendance = attendance.filter {
                    val y = it.date.take(4).toIntOrNull() ?: return@filter false
                    val parts = year.split("-")
                    val startYear = parts.getOrNull(0)?.toIntOrNull()
                    val endYear = parts.getOrNull(1)?.takeLast(2)?.toIntOrNull()?.let { 2000 + it }
                    if (startYear != null && endYear != null) y in startYear..endYear else y == startYear
                }
                val attRate = if (yearlyAttendance.isEmpty()) null
                else yearlyAttendance.count { it.status == "present" }.toDouble() / yearlyAttendance.size * 100.0
                val audit = outcomeByYear[year]
                val gradeLevel = audit?.second
                    ?: if (year == currentYear) currentGradeLevel else null
                AcademicYearHistory(
                    academicYear = year,
                    termGpas = termGpas,
                    yearlyGpa = com.example.core.computeOverallGpa(rows),
                    assessments = rows,
                    gradeLevel = gradeLevel,
                    promotionOutcome = audit?.first,
                    attendanceRate = attRate,
                    isArchived = audit != null,
                )
            }
            .sortedByDescending { it.academicYear }
        _academicHistory.value = history
    }

    private suspend fun loadTermGpas(studentId: String, year: String) {
        val gpas = linkedMapOf<String, Double?>()
        for (t in listOf("T1", "T2", "T3")) {
            val list = gradeRepository.observeForStudent(studentId, t, year).firstOrNull().orEmpty()
            gpas[t] = if (list.isEmpty()) null else com.example.core.computeOverallGpa(list)
        }
        _termGpas.value = gpas
    }

    private fun currentAcademicYear(): String {
        val now = java.time.LocalDate.now()
        return if (now.monthValue >= 9) "${now.year}-${now.year + 1}" else "${now.year - 1}-${now.year}"
    }

    fun updateStudent(
        studentId: String,
        firstName: String,
        lastName: String,
        birthDate: String,
        gradeLevel: String,
        medicalNotes: String?,
    ) {
        viewModelScope.launch {
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            val result = studentRepository.updateStudent(
                studentId,
                com.example.domain.repository.UpdateStudentInput(
                    firstName = firstName.ifBlank { null },
                    lastName = lastName.ifBlank { null },
                    birthDate = birthDate.ifBlank { null },
                    gradeLevel = gradeLevel.ifBlank { null },
                    level = gradeLevel.ifBlank { null }?.let { com.example.core.academicLevelForGradeCode(it) },
                    medicalNotes = medicalNotes,
                ),
                actorId,
                actorName,
            )
            when (result) {
                is Result.Ok -> _saveMessage.value = "Élève mis à jour."
                is Result.Err -> _error.value = result.error.userMessage
            }
        }
    }

    fun clearMessages() {
        _error.value = null
        _saveMessage.value = null
    }

    private val _bulletinBusy = MutableStateFlow(false)
    val bulletinBusy: StateFlow<Boolean> = _bulletinBusy.asStateFlow()

    private val _bulletinShareRequest = MutableStateFlow<java.io.File?>(null)
    val bulletinShareRequest: StateFlow<java.io.File?> = _bulletinShareRequest.asStateFlow()

    fun generateBulletin(studentId: String, term: String, academicYear: String? = null) {
        if (_bulletinBusy.value) return
        viewModelScope.launch {
            _bulletinBusy.value = true
            when (val r = pdfRepository.generateStudentBulletin(studentId, term, academicYear ?: currentAcademicYear())) {
                is Result.Ok -> {
                    _bulletinShareRequest.value = r.value
                    _saveMessage.value = "Bulletin $term généré."
                }
                is Result.Err -> _error.value = r.error.userMessage
            }
            _bulletinBusy.value = false
        }
    }

    fun consumeBulletinShareRequest() { _bulletinShareRequest.value = null }
}
