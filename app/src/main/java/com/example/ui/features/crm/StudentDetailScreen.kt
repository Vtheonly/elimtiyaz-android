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

/**
 * Vault §04.07 / §06.05 — one year of the permanent, append-only Student
 * Academic History. Grade level + promotion outcome are reconstructed from
 * the `student.promote` audit trail (each entry carries from/to/decision/year).
 */
data class AcademicYearHistory(
    val academicYear: String,
    /** Canonical GPA per term (T1/T2/T3) — null when the term has no grades. */
    val termGpas: Map<String, Double?>,
    /** Canonical yearly GPA over every term's assessments. */
    val yearlyGpa: Double?,
    /** Every assessment row of the year (subject breakdown + D1/D2/Examen). */
    val assessments: List<Assessment>,
    /** Grade level held during that year (from the promotion audit trail). */
    val gradeLevel: String?,
    /** Promotion outcome: promoted | repeated | graduated (null = pending). */
    val promotionOutcome: String?,
    /** Attendance rate for the year (0–100). */
    val attendanceRate: Double?,
    /** True when the year is closed (any promote audit exists for it). */
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

    /** All subjects — resolves assessment subjectIds to display names. */
    private val _subjects = MutableStateFlow<List<com.example.domain.model.Subject>>(emptyList())
    val subjects: StateFlow<List<com.example.domain.model.Subject>> = _subjects.asStateFlow()

    /**
     * Class-wide assessments for the selected term — backs the student's rank
     * and the class average, both derived from the canonical GPA engine.
     */
    private val _classAssessments = MutableStateFlow<List<Assessment>>(emptyList())
    val classAssessments: StateFlow<List<Assessment>> = _classAssessments.asStateFlow()

    /** Canonical GPA per term (T1 / T2 / T3) — real progression history. */
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

    /** Vault §04.07 — permanent per-student academic history (all years). */
    private val _academicHistory = MutableStateFlow<List<AcademicYearHistory>>(emptyList())
    val academicHistory: StateFlow<List<AcademicYearHistory>> = _academicHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    // FIX (coroutine leak): the previous implementation nested one `collect`
    // inside another inside another — every student emission spawned NEW inner
    // collectors that were never cancelled, re-fetching the ledger summary
    // per emission and duplicating work until the screen was destroyed.
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
                // Subject catalogue — resolves subjectIds to names/coefficients.
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
                            // FIX (hardcoded term): grades are re-observable per
                            // term — the UI now exposes a term selector.
                            gradesJob?.cancel()
                            gradesJob = launch {
                                gradeRepository.observeForStudent(studentId, term, year).collect { list ->
                                    _assessments.value = list
                                }
                            }
                        }
                        // Per-term GPA history (T1/T2/T3) — canonical engine.
                        launch { loadTermGpas(studentId, year) }
                        // Class-wide assessments for rank + class average.
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
                        // Vault §04.07 — permanent academic history (all years).
                        launch { loadAcademicHistory(studentId, s.gradeLevel) }
                    }
                }
                _isLoading.value = false
            }
        }
    }

    /** Re-observe grades for a different term without reloading everything. */
    fun loadGradesForTerm(studentId: String, term: String, academicYear: String? = null) {
        val year = academicYear ?: currentAcademicYear()
        gradesJob?.cancel()
        gradesJob = viewModelScope.launch {
            gradeRepository.observeForStudent(studentId, term, year)
                .collect { list -> _assessments.value = list }
        }
        // Class-wide view follows the selected term too (rank + class average).
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

    /**
     * Vault §04.07 / §06.05 — permanent academic history: every year the
     * student has assessments for, with term-by-term GPAs (canonical engine),
     * subject breakdown, attendance rate, and the promotion outcome
     * reconstructed from the `student.promote` audit trail.
     *
     * READ-ONLY by construction — this loader never mutates anything; past
     * years can only be superseded by a new audit-logged entry (append-only).
     */
    private suspend fun loadAcademicHistory(studentId: String, currentGradeLevel: String) {
        val all = gradeRepository.observeAllForStudent(studentId).firstOrNull().orEmpty()
        if (all.isEmpty()) {
            _academicHistory.value = emptyList()
            return
        }
        val attendance = attendanceRepository.observeByStudent(studentId).firstOrNull().orEmpty()

        // Promotion audit trail: each `student.promote` entry carries
        // after={"decision":…,"year":…,"from":…,"to":…} — `from` is the
        // grade held DURING that year, `to` the next year's grade.
        val promoteAudits = auditRepository
            .observeByEntity("student", studentId)
            .firstOrNull().orEmpty()
            .filter { it.action == "student.promote" }
        val outcomeByYear = mutableMapOf<String, Pair<String, String?>>() // year -> (decision, from)
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
                    // Academic year "2026-2027" covers 2026-09 .. 2027-06.
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

    /**
     * Canonical GPA per term — one entry per term that has any assessment.
     * Computed with [com.example.core.computeOverallGpa] (coefficient-weighted,
     * extracurricular excluded, incomplete averages skipped).
     */
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

    /** FIX (missing edit feature): persist edits via updateStudent. */
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

    // ── Bulletin PDF (entity-specific report, per desktop spec §5.1) ──────

    private val _bulletinBusy = MutableStateFlow(false)
    val bulletinBusy: StateFlow<Boolean> = _bulletinBusy.asStateFlow()

    /** One-shot share request: the freshly generated bulletin file. */
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

    /** Called by the UI once the share intent has been dispatched. */
    fun consumeBulletinShareRequest() { _bulletinShareRequest.value = null }
}

@Composable
fun StudentDetailScreen(
    studentId: String,
    onBack: () -> Unit,
    viewModel: StudentDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(studentId) { viewModel.load(studentId) }
    val student by viewModel.student.collectAsState()
    val parent by viewModel.parent.collectAsState()
    val siblings by viewModel.siblings.collectAsState()
    val assessments by viewModel.assessments.collectAsState()
    val attendanceRecords by viewModel.attendanceRecords.collectAsState()
    val attendanceStats by viewModel.attendanceStats.collectAsState()
    val installments by viewModel.installments.collectAsState()
    val payments by viewModel.payments.collectAsState()
    // TIER 4 FIX (bypass #1) — collect the canonical `ParentLedgerSummary`
    // already materialized by the ViewModel from `ledgerRepository.summary()`
    // (which calls `LedgerEngine.computeParentSummary`). Used by the
    // Finances tab to avoid inline installment sums.
    val familySummary by viewModel.familySummary.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val classAssessments by viewModel.classAssessments.collectAsState()
    val termGpas by viewModel.termGpas.collectAsState()
    val academicHistory by viewModel.academicHistory.collectAsState()
    val bulletinBusy by viewModel.bulletinBusy.collectAsState()
    val bulletinShareRequest by viewModel.bulletinShareRequest.collectAsState()
    val context = LocalContext.current
    val tokens = elDesignTokens()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Profil & Famille", "Notes & Bulletins", "Présences & Retards", "Finances & Échéances", "Historique")
    // FIX (hardcoded T1): term selector for the grades tab — previously only
    // "T1" of a hardcoded academic year was ever shown.
    var selectedTerm by remember { mutableStateOf("T1") }
    // FIX (missing edit feature): edit dialog state.
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTerm) {
        viewModel.loadGradesForTerm(studentId, selectedTerm)
    }
    LaunchedEffect(saveMessage) {
        if (saveMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessages()
        }
    }
    // Share the freshly generated bulletin PDF (ACTION_SEND via FileProvider —
    // same mechanism as the payment-receipt share).
    LaunchedEffect(bulletinShareRequest) {
        bulletinShareRequest?.let { file ->
            runCatching {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Bulletin ${selectedTerm}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Partager le bulletin"))
            }
            viewModel.consumeBulletinShareRequest()
        }
    }

    ElScaffold(
        topBar = {
            ElTopBar(
                title = student?.fullName ?: "Dossier Élève",
                onBack = onBack,
                // FIX (read-only dossier): an edit action is now available —
                // `updateStudent` existed in the repository but was never
                // reachable from any UI.
                actions = {
                    if (student != null) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifier l'élève")
                        }
                    }
                },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            student?.let { s ->
                ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ElAvatar(initials = s.fullName, size = 52)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text("Matricule: ${s.code} • ${s.gradeLevel.uppercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ElTag(
                            text = if (s.status == "active") "Inscrit" else s.status,
                            color = if (s.status == "active") SuccessGreen else DangerRed,
                        )
                    }
                }
            }

            ModernSecondaryTabRow(
                tabs = tabs,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
            )

            when (selectedTab) {
                // ── 1. PROFIL & FAMILLE ──────────────────────────────────────
                0 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        student?.let { s ->
                            ElCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ElSectionHeader(title = "Renseignements Généraux")
                                    ElInfoRow(label = "Date de naissance", value = s.birthDate)
                                    ElInfoRow(label = "Cycle scolaire", value = s.level.replaceFirstChar { it.uppercase() })
                                    ElInfoRow(label = "Niveau d'études", value = s.gradeLevel.uppercase())
                                    ElInfoRow(label = "Date d'inscription", value = s.enrollmentDate.take(10))
                                    s.medicalNotes?.let { ElInfoRow(label = "Notes médicales", value = it, valueColor = DangerRed) }
                                }
                            }
                        }
                    }

                    item {
                        parent?.let { p ->
                            ElCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ElSectionHeader(title = "Tuteur Légal / Parent")
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ElAvatar(initials = p.fullName, size = 40)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(p.fullName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            Text("Code: ${p.code} • ${p.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clip(MaterialTheme.shapes.small)
                                                .background(tokens.successBrush)
                                                .clickable {
                                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.phone}"))
                                                    context.startActivity(intent)
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Appeler", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clip(MaterialTheme.shapes.small)
                                                .background(tokens.successBrush)
                                                .clickable {
                                                    val clean = (p.whatsapp ?: p.phone).replace("[^0-9]".toRegex(), "")
                                                    val formatted = if (clean.startsWith("0")) "213${clean.substring(1)}" else clean
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$formatted"))
                                                    context.startActivity(intent)
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Whatsapp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("WhatsApp", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (siblings.isNotEmpty()) {
                        item {
                            ElCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ElSectionHeader(title = "Fratrie inscrite (${siblings.size})")
                                    siblings.forEach { sib ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                ElAvatar(initials = sib.fullName, size = 32)
                                                Spacer(Modifier.width(8.dp))
                                                Text(sib.fullName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                            }
                                            ElTag(text = sib.gradeLevel.uppercase(), color = PrimaryBlue)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 2. NOTES & BULLETINS ──────────────────────────────────────
                1 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        // Term selector (T1 / T2 / T3).
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("T1", "T2", "T3").forEach { t ->
                                ElTag(
                                    text = t,
                                    color = if (t == selectedTerm) PrimaryBlue else MaterialTheme.colorScheme.outline,
                                    selected = t == selectedTerm,
                                    onClick = { selectedTerm = t },
                                )
                            }
                        }
                    }

                    // Subject-name resolution + canonical derived metrics.
                    val subjectById = subjects.associateBy { it.id }
                    val gpa = computeOverallGpa(assessments)
                    val mention = mentionFor(gpa)

                    // Class rank + class average — canonical GPA per classmate.
                    val classGpas = classAssessments
                        .groupBy { it.studentId }
                        .map { (sid, list) -> sid to computeOverallGpa(list) }
                        .filter { it.second != null }
                        .sortedByDescending { it.second!! }
                    val rankIdx = classGpas.indexOfFirst { it.first == studentId }
                    val classAverage = classGpas.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.average()
                    val evaluatedCount = assessments.count { it.subjectAverage != null && !it.isExtracurricular }
                    val bestSubject = assessments
                        .filter { !it.isExtracurricular && it.subjectAverage != null }
                        .maxByOrNull { it.subjectAverage!! }
                        ?.let { (subjectById[it.subjectId]?.name ?: it.subjectId) to it.subjectAverage!! }
                    val weakestSubject = assessments
                        .filter { !it.isExtracurricular && it.subjectAverage != null }
                        .minByOrNull { it.subjectAverage!! }
                        ?.let { (subjectById[it.subjectId]?.name ?: it.subjectId) to it.subjectAverage!! }

                    item {
                        // ── GPA hero with mention + rank ──
                        ElCard(modifier = Modifier.fillMaxWidth(), accent = if ((gpa ?: 0.0) >= 10.0) SuccessGreen else DangerRed) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Moyenne Générale — $selectedTerm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = gpa?.let { "%.2f / 20".format(it) } ?: "En attente des examens",
                                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if ((gpa ?: 0.0) >= 10.0) SuccessGreen else DangerRed,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = when {
                                                gpa == null -> "Moyenne calculée dès que toutes les notes sont saisies"
                                                isPassing(gpa) -> "Admis • Mention $mention"
                                                else -> "Moyenne inférieure au seuil de passage • $mention"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            if (rankIdx >= 0) "${rankIdx + 1}${if (rankIdx + 1 == 1) "er" else "e"}" else "—",
                                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                            color = PrimaryBlue,
                                        )
                                        Text(
                                            if (classGpas.isNotEmpty()) "sur ${classGpas.size}" else "rang",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                ElProgressBar(progress = ((gpa ?: 0.0) / 20.0).toFloat())
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    GradeStat("Matières évaluées", "$evaluatedCount")
                                    GradeStat("Moy. classe", classAverage?.let { "%.2f".format(it) } ?: "—")
                                    GradeStat(
                                        "Écart",
                                        if (gpa != null && classAverage != null) "%+.2f".format(gpa - classAverage) else "—",
                                    )
                                }
                            }
                        }
                    }

                    item {
                        // ── Per-term progression (canonical GPA per term) ──
                        if (termGpas.values.any { it != null }) {
                            ElCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Progression de l'année", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        listOf("T1", "T2", "T3").forEach { t ->
                                            val termGpa = termGpas[t]
                                            val isCurrent = t == selectedTerm
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .clip(MaterialTheme.shapes.small)
                                                    .background(
                                                        when {
                                                            isCurrent -> PrimaryBlue.copy(alpha = 0.12f)
                                                            termGpa != null && isPassing(termGpa) -> SuccessGreen.copy(alpha = 0.08f)
                                                            termGpa != null -> DangerRed.copy(alpha = 0.08f)
                                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                        },
                                                    )
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                            ) {
                                                Text(
                                                    t,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isCurrent) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Text(
                                                    termGpa?.let { "%.2f".format(it) } ?: "—",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = when {
                                                        termGpa == null -> MaterialTheme.colorScheme.outline
                                                        isPassing(termGpa) -> SuccessGreen
                                                        else -> DangerRed
                                                    },
                                                )
                                                if (isCurrent) {
                                                    Text("Trimestre affiché", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        // ── Strengths / focus areas (derived, canonical values) ──
                        if (bestSubject != null || weakestSubject != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                bestSubject?.let { (name, avg) ->
                                    SubjectHighlightCard(
                                        label = "Point fort",
                                        subjectName = name,
                                        average = avg,
                                        color = SuccessGreen,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                weakestSubject?.let { (name, avg) ->
                                    SubjectHighlightCard(
                                        label = "À renforcer",
                                        subjectName = name,
                                        average = avg,
                                        color = DangerRed,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    if (assessments.isEmpty()) {
                        item {
                            ElCard(modifier = Modifier.fillMaxWidth()) {
                                Text("Aucune note saisie pour ce trimestre.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        item {
                            Text(
                                "Détail par matière",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                        items(assessments.sortedWith(
                            compareByDescending<Assessment> { it.isExtracurricular }
                                .thenBy { subjectById[it.subjectId]?.name ?: it.subjectId },
                        )) { a ->
                            val subject = subjectById[a.subjectId]
                            val avg = a.subjectAverage
                            val passingGrade = subject?.passingGrade ?: 10.0
                            SubjectGradeCard(
                                subjectName = subject?.name ?: a.subjectId,
                                coefficient = a.coefficient,
                                isExtracurricular = a.isExtracurricular,
                                devoir1 = a.devoir1,
                                devoir2 = a.devoir2,
                                examen = a.examen,
                                average = avg,
                                passing = avg != null && isPassing(avg, passingGrade),
                                passingGrade = passingGrade,
                                enteredAt = a.enteredAt,
                            )
                        }

                        // ── Bulletin PDF (entity-specific report) ──
                        item {
                            ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        "Bulletin officiel — $selectedTerm",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Génère le bulletin PDF (notes, coefficients, moyenne générale, mention, rang) prêt à partager avec la famille.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    com.example.ui.components.ElButton(
                                        text = if (bulletinBusy) "Génération…" else "Générer le bulletin $selectedTerm",
                                        onClick = { viewModel.generateBulletin(studentId, selectedTerm) },
                                        fullWidth = true,
                                        enabled = !bulletinBusy && assessments.isNotEmpty(),
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 3. PRÉSENCES & RETARDS ───────────────────────────────────
                2 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        ElCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                ElSectionHeader(title = "Bilan des présences")
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("%.1f%%".format(attendanceStats.rate), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = SuccessGreen)
                                        Text("Assiduité", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${attendanceStats.presentCount}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PrimaryBlue)
                                        Text("Présents", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${attendanceStats.unexcusedCount}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = DangerRed)
                                        Text("Injustifiées", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${attendanceStats.lateCount}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = WarmGold)
                                        Text("Retards", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    if (attendanceRecords.isEmpty()) {
                        item {
                            ElCard(modifier = Modifier.fillMaxWidth()) {
                                Text("Aucune absence ou retard enregistré.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        items(attendanceRecords.take(30)) { rec ->
                            val (badgeColor, label) = when (rec.status) {
                                "present" -> SuccessGreen to "Présent"
                                "absent_unexcused" -> DangerRed to "Absence non justifiée"
                                "absent_excused" -> WarmGold to "Absence excusée"
                                "late" -> PrimaryBlue to "Retard"
                                else -> MaterialTheme.colorScheme.onSurfaceVariant to rec.status
                            }

                            ElCard(modifier = Modifier.fillMaxWidth(), compact = true) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rec.date, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        rec.note?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    ElTag(text = label, color = badgeColor)
                                }
                            }
                        }
                    }
                }

                // ── 4. FINANCES & ÉCHÉANCES ──────────────────────────────────
                3 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        // TIER 4 FIX (bypass #1) — replace the inline
                        // `installments.sumOf { amountDue / amountPaid }` with
                        // the canonical `ParentLedgerSummary` (produced by
                        // `LedgerEngine.computeParentSummary`). The canonical
                        // engine correctly excludes reversed originals, applies
                        // overdue rules, and includes adjustments/credits — the
                        // inline sum missed all three.
                        // FIX (mislabel): the summary is FAMILY-scoped — for
                        // multi-child families the previous "État financier de
                        // l'élève" header wrongly presented sibling totals as
                        // this student's own finances.
                        val studentDue = familySummary?.totalCharged ?: 0L
                        val studentPaid = familySummary?.totalPaid ?: 0L
                        val studentRest = (familySummary?.totalOutstanding ?: 0L).coerceAtLeast(0L)
                        val ownDue = installments.filter { it.status != com.example.core.PaymentStatus.CANCELLED }.sumOf { it.amountDue }
                        val ownPaid = installments.sumOf { it.amountPaid }

                        ElCard(modifier = Modifier.fillMaxWidth(), accent = if (studentRest > 0) DangerRed else SuccessGreen) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                ElSectionHeader(title = "Finances — part de cet élève")
                                Spacer(Modifier.height(6.dp))
                                ElInfoRow(label = "Tranches de cet élève (dû)", value = "${(ownDue / 100).formatDzd()} DZD")
                                ElInfoRow(label = "Tranches de cet élève (payé)", value = "${(ownPaid / 100).formatDzd()} DZD", valueColor = SuccessGreen)
                                Spacer(Modifier.height(8.dp))
                                ElSectionHeader(title = "Solde familial consolidé (tous enfants)")
                                Spacer(Modifier.height(4.dp))
                                ElInfoRow(label = "Total scolarité & transport", value = "${(studentDue / 100).formatDzd()} DZD")
                                ElInfoRow(label = "Total réglé", value = "${(studentPaid / 100).formatDzd()} DZD", valueColor = SuccessGreen)
                                ElInfoRow(label = "Reste à payer", value = "${(studentRest / 100).formatDzd()} DZD", valueColor = if (studentRest > 0) DangerRed else SuccessGreen)
                            }
                        }
                    }

                    if (installments.isNotEmpty()) {
                        item {
                            Text("Échéancier des tranches", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        items(installments) { inst ->
                            val statusColor = when (inst.status.name) {
                                "PAID" -> SuccessGreen
                                "PARTIAL" -> WarmGold
                                "OVERDUE" -> DangerRed
                                else -> PrimaryBlue
                            }
                            ElCard(modifier = Modifier.fillMaxWidth(), compact = true) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(inst.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        ElTag(text = inst.status.name, color = statusColor)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    ElInfoRow(label = "Échéance", value = inst.dueDate)
                                    ElInfoRow(label = "Montant dû", value = "${(inst.amountDue / 100).formatDzd()} DZD")
                                    ElInfoRow(label = "Payé", value = "${(inst.amountPaid / 100).formatDzd()} DZD", valueColor = SuccessGreen)
                                }
                            }
                        }
                    }

                    if (payments.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(6.dp))
                            Text("Reçus d'encaissements", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        items(payments) { p ->
                            ElCard(modifier = Modifier.fillMaxWidth(), compact = true) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(p.receiptNumber, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("${p.method.name} • ${p.collectedAt.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("+${(p.amount / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = SuccessGreen)
                                }
                            }
                        }
                    }
                }

                // ── 5. HISTORIQUE ACADÉMIQUE (vault §04.07 / §06.05) ─────
                // Permanent, append-only history embedded in the Student
                // Profile drawer — NOT a separate top-level page.
                4 -> AcademicHistoryTab(
                    history = academicHistory,
                    subjects = subjects,
                    currentYear = run {
                        val now = java.time.LocalDate.now()
                        if (now.monthValue >= 9) "${now.year}-${now.year + 1}" else "${now.year - 1}-${now.year}"
                    },
                )
            }

            saveMessage?.let {
                ElAlertBanner(message = it, severity = ElAlertSeverity.Success, title = "Modifications enregistrées")
            }
        }
    }

    // FIX (missing edit feature): edit dialog — first class UI for
    // `updateStudent` (identity + placement + medical notes).
    if (showEditDialog && student != null) {
        val s = student!!
        var firstName by remember { mutableStateOf(s.firstName) }
        var lastName by remember { mutableStateOf(s.lastName) }
        var birthDate by remember { mutableStateOf(s.birthDate) }
        var gradeLevel by remember { mutableStateOf(s.gradeLevel) }
        var medicalNotes by remember { mutableStateOf(s.medicalNotes ?: "") }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Modifier l'élève") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("Prénom") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Nom") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = birthDate, onValueChange = { birthDate = it }, label = { Text("Date de naissance (AAAA-MM-JJ)") }, modifier = Modifier.fillMaxWidth())
                    com.example.ui.components.ElDropdown(
                        label = "Niveau scolaire",
                        selectedValue = gradeLevel,
                        options = GRADE_LEVEL_CODES,
                        onSelected = { gradeLevel = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(value = medicalNotes, onValueChange = { medicalNotes = it }, label = { Text("Notes médicales") }, modifier = Modifier.fillMaxWidth())
                    Text("Matricule ${s.code} — non modifiable.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateStudent(
                            studentId = s.id,
                            firstName = firstName.trim(),
                            lastName = lastName.trim(),
                            birthDate = birthDate.trim(),
                            gradeLevel = gradeLevel,
                            medicalNotes = medicalNotes.trim().ifBlank { null },
                        )
                        showEditDialog = false
                    },
                    enabled = firstName.isNotBlank() && lastName.isNotBlank(),
                ) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Annuler") }
            },
        )
    }
}
// ── Notes & Bulletins helpers (canonical display, no re-derived logic) ─────

/** Standard French bulletin mention — presentation only. */
private fun mentionFor(gpa: Double?): String = when {
    gpa == null -> "En attente des examens"
    gpa >= 16 -> "Très Bien"
    gpa >= 14 -> "Bien"
    gpa >= 12 -> "Assez Bien"
    gpa >= 10 -> "Passable"
    else -> "Insuffisant"
}

@Composable
private fun GradeStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = PrimaryBlue)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Compact "Point fort / À renforcer" card — canonical subject average. */
@Composable
private fun SubjectHighlightCard(
    label: String,
    subjectName: String,
    average: Double,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    ElCard(modifier = modifier, compact = true) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                subjectName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "%.2f / 20".format(average),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
        }
    }
}

/**
 * Full per-subject grade card: marks with the ×2 examen weight made explicit,
 * canonical subject average, coefficient, per-subject progress bar and the
 * pass/fail verdict against the SUBJECT's own passing grade.
 */
@Composable
private fun SubjectGradeCard(
    subjectName: String,
    coefficient: Double,
    isExtracurricular: Boolean,
    devoir1: Double?,
    devoir2: Double?,
    examen: Double?,
    average: Double?,
    passing: Boolean,
    passingGrade: Double,
    enteredAt: String,
) {
    val avgColor = when {
        average == null -> WarmGold
        passing -> SuccessGreen
        else -> DangerRed
    }
    ElCard(modifier = Modifier.fillMaxWidth(), compact = true, accent = avgColor) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        subjectName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    if (isExtracurricular) {
                        ElTag(text = "Hors programme", color = WarmGold)
                    }
                }
                Text(
                    text = average?.let { "%.2f".format(it) } ?: "—",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = avgColor,
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MarkPill("D1", devoir1)
                MarkPill("D2", devoir2)
                MarkPill("Ex ×2", examen)
                Spacer(Modifier.weight(1f))
                Text(
                    "Coef ${if (coefficient == coefficient.toLong().toDouble()) "${coefficient.toLong()}" else "$coefficient"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (average != null) {
                Spacer(Modifier.height(8.dp))
                ElProgressBar(
                    progress = (average / 20.0).toFloat(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (passing) "Acquis (≥ $passingGrade/20)" else "À renforcer (< $passingGrade/20)",
                    style = MaterialTheme.typography.labelSmall,
                    color = avgColor,
                )
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Moyenne à paraître — les 3 notes doivent être saisies (formule (D1 + D2 + 2×Ex) / 4)",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarmGold,
                )
            }

            if (enteredAt.isNotBlank()) {
                Text(
                    "Saisie le ${enteredAt.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun MarkPill(label: String, value: Double?) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (value != null) PrimaryBlue.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            "$label ${value?.let { if (it == it.toLong().toDouble()) "${it.toLong()}" else "$it" } ?: "—"}",
            style = MaterialTheme.typography.labelSmall,
            color = if (value != null) PrimaryBlue else MaterialTheme.colorScheme.outline,
        )
    }
}

// ── Vault §04.07 / §06.05 — Student Academic History (append-only) ──────────

/**
 * The permanent Academic History tab: term-by-term performance for every
 * enrolled year. Clicking a year expands the complete report card (subject
 * breakdown with Devoir 1 / Devoir 2 / Examen, coefficients, canonical
 * averages), attendance rate, and the promotion outcome
 * (APPROVED_FOR_PROMOTION / RETAINED_SAME_YEAR / GRADUATED).
 *
 * Archived years are strictly READ-ONLY (vault rule: corrections require a
 * new audit-logged entry that supersedes the original — no in-place edits).
 */
@Composable
private fun AcademicHistoryTab(
    history: List<AcademicYearHistory>,
    subjects: List<com.example.domain.model.Subject>,
    currentYear: String,
) {
    val subjectById = subjects.associateBy { it.id }

    if (history.isEmpty()) {
        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Historique académique", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Aucun historique pour cet élève — les performances par trimestre apparaîtront ici au fil des années, avec le détail des bulletins et les décisions de promotion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Parcours complet — ${history.size} année(s)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Historique permanent en lecture seule : les années clôturées ne peuvent pas être modifiées (toute correction passe par une nouvelle entrée journalisée).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(history, key = { it.academicYear }) { year ->
            AcademicYearCard(year = year, subjectById = subjectById, currentYear = currentYear)
        }
    }
}

/** One academic year: summary row + expandable full report card. */
@Composable
private fun AcademicYearCard(
    year: AcademicYearHistory,
    subjectById: Map<String, com.example.domain.model.Subject>,
    currentYear: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val isCurrent = year.academicYear == currentYear
    val gpaColor = when {
        year.yearlyGpa == null -> WarmGold
        year.yearlyGpaSafe() >= 10.0 -> SuccessGreen
        else -> DangerRed
    }

    ElCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        accent = gpaColor,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Année ${year.academicYear}" + (if (isCurrent) " (en cours)" else ""),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        listOfNotNull(
                            year.gradeLevel?.let { "Niveau ${it.uppercase()}" },
                            year.attendanceRate?.let { "Présence %.0f%%".format(it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        year.yearlyGpa?.let { "%.2f / 20".format(it) } ?: "—",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = gpaColor,
                    )
                    Text(
                        if (expanded) "Bulletins ▲" else "Bulletins ▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryBlue,
                    )
                }
            }

            // Term-by-term GPA chips.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                year.termGpas.forEach { (term, gpa) ->
                    val tColor = when {
                        gpa == null -> MaterialTheme.colorScheme.outline
                        gpa >= 10.0 -> SuccessGreen
                        else -> DangerRed
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tColor.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "$term ${gpa?.let { "%.2f".format(it) } ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Promotion outcome (audit trail).
            year.promotionOutcome?.let { outcome ->
                when (outcome) {
                    "promoted" -> ElTag(text = "APPROVED_FOR_PROMOTION", color = SuccessGreen)
                    "graduated" -> ElTag(text = "DIPLÔMÉ", color = PrimaryBlue)
                    else -> ElTag(text = "RETAINED_SAME_YEAR", color = DangerRed)
                }
            } ?: run {
                if (!isCurrent) ElTag(text = "Année en attente de clôture", color = WarmGold)
            }

            if (expanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Bulletin complet — ${year.academicYear}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
                val bySubject = year.assessments.groupBy { it.subjectId }
                bySubject.forEach { (subjectId, rows) ->
                    val subject = subjectById[subjectId]
                    val name = subject?.name ?: subjectId
                    // Latest term row per subject shows the year's final marks.
                    val last = rows.maxByOrNull { it.term } ?: rows.first()
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                name + if (last.isExtracurricular) " (hors programme)" else "",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                last.subjectAverage?.let { "%.2f".format(it) } ?: "—",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (last.isExtracurricular) WarmGold else gpaColor,
                            )
                        }
                        Text(
                            "D1 ${last.devoir1 ?: "—"} · D2 ${last.devoir2 ?: "—"} · Examen ${last.examen ?: "—"} · Coef ${last.coefficient}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (year.isArchived) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Année clôturée — lecture seule (append-only).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

/** Null-safe yearly GPA accessor for color derivation. */
private fun AcademicYearHistory.yearlyGpaSafe(): Double = yearlyGpa ?: 0.0
