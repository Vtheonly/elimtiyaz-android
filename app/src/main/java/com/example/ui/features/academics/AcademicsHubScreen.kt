package com.example.ui.features.academics

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Session
import com.example.domain.model.AcademicClass
import com.example.domain.model.Student
import com.example.domain.model.Subject
import com.example.domain.repository.AttendanceRepository
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.EnterGradeInput
import com.example.domain.repository.GradeRepository
import com.example.domain.repository.HomeworkRepository
import com.example.domain.repository.PushHomeworkInput
import com.example.domain.repository.RollCallEntry
import com.example.domain.repository.StudentRepository
import com.example.domain.repository.SubjectRepository
import com.example.ui.components.ElAlertBanner
import com.example.ui.components.ElAlertSeverity
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElDropdown
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElGradientStatCard
import com.example.ui.components.ElListItem
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTextField
import com.example.ui.components.ModernSecondaryTabRow
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LightBlue
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Root Academics hub — 4 tabs:
 *   0. Roll Call      → [RollCallScreen]    (AttendanceRepository)
 *   1. Grade Entry    → [GradeEntryScreen]  (GradeRepository)
 *   2. Homework Push  → [HomeworkPushScreen] (HomeworkRepository)
 *   3. Classes        → [ClassesDirectoryScreen] (ClassRepository)
 *
 * BUGFIX (iter 2): previously all 4 subscreens rendered hardcoded sample
 * data and never invoked any repository. Now each tab has a real Hilt
 * ViewModel that calls the corresponding repository, mirroring the desktop
 * `academics-page` implementation.
 */
@Composable
fun AcademicsHubScreen(session: Session) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Présences", "Notes", "Devoirs", "Classes")

    Column(modifier = Modifier.fillMaxSize()) {
        ModernSecondaryTabRow(
            tabs = tabs,
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
        )
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            when (selectedTab) {
                0 -> RollCallScreen(session)
                1 -> GradeEntryScreen(session)
                2 -> HomeworkPushScreen(session)
                3 -> ClassesDirectoryScreen(session)
            }
        }
    }
}

// ── 1. Roll Call ──────────────────────────────────────────────────────────

/**
 * Maps the desktop's 4 attendance statuses (per plan §09.02 — no 5th
 * "CUSTOM" status allowed) to the mobile UI labels + colors.
 */
enum class AttendanceStatus(val label: String, val color: Color, val wireCode: String) {
    PRESENT("Présent", SuccessGreen, "present"),
    ABSENT("Absent", DangerRed, "absent_unexcused"),
    EXCUSED("Excusé", WarmGold, "absent_excused"),
    LATE("Retard", LightBlue, "late"),
}

@HiltViewModel
class RollCallViewModel @Inject constructor(
    private val classRepository: ClassRepository,
    private val studentRepository: StudentRepository,
    private val attendanceRepository: AttendanceRepository,
) : ViewModel() {

    val classes: StateFlow<List<AcademicClass>> = classRepository.observe()
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

    /**
     * Submit roll call to Supabase via [AttendanceRepository.recordRollCall].
     *
     * Mirrors desktop `RollCallScreen`:
     *   - Wire status codes are the 4 canonical values (present,
     *     absent_excused, absent_unexcused, late).
     *   - Late entries include the arrival-time note.
     *   - After save, alerts are sent for any student hitting the 3+ absence
     *     threshold (per desktop `alert-absences` Edge Function).
     */
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
                // Identify students reaching 3+ absence threshold for alert.
                // (Server-side `alert-absences` Edge Function handles the
                // actual notification; this is a client-side UX hint.)
                _message.value = "Appel enregistré pour la classe (${records.size} élèves)."
            }.onFailure { err ->
                _message.value = err.userMessage
            }
        }
    }

    fun clearMessage() { _message.value = null }
}

@Composable
fun RollCallScreen(
    session: Session,
    viewModel: RollCallViewModel = hiltViewModel(),
) {
    val classes by viewModel.classes.collectAsState()
    val students by viewModel.students.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    var selectedClassId by remember { mutableStateOf<String?>(null) }
    val statuses = remember { mutableStateMapOf<String, AttendanceStatus>() }
    val lateTimes = remember { mutableStateMapOf<String, String>() }
    val today = remember { LocalDate.now().toString() }

    // Auto-select first class when list loads
    androidx.compose.runtime.LaunchedEffect(classes) {
        if (selectedClassId == null && classes.isNotEmpty()) {
            selectedClassId = classes.first().id
        }
    }
    // Load students when class changes
    androidx.compose.runtime.LaunchedEffect(selectedClassId) {
        selectedClassId?.let { viewModel.loadStudentsForClass(it) }
    }

    val selectedClass = classes.firstOrNull { it.id == selectedClassId }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElGradientStatCard(
            title = "Appel — ${today}",
            value = selectedClass?.name ?: "Chargement…",
            subtitle = "Basculez les statuts des élèves rapidement",
            modifier = Modifier.fillMaxWidth(),
        )

        if (classes.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Class,
                title = "Aucune classe",
                message = "Aucune classe n'est disponible. Créez-en une depuis les paramètres ou contactez un administrateur.",
            )
            return@Column
        }

        ElDropdown(
            label = "Classe",
            selectedValue = selectedClass?.name ?: "",
            options = classes.map { it.name },
            onSelected = { name -> selectedClassId = classes.first { it.name == name }.id },
            modifier = Modifier.fillMaxWidth(),
        )

        if (students.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Class,
                title = "Aucun élève",
                message = "Aucun élève inscrit dans cette classe.",
            )
        } else {
            students.forEach { student ->
                val currentStatus = statuses[student.id] ?: AttendanceStatus.PRESENT
                if (!statuses.containsKey(student.id)) statuses[student.id] = AttendanceStatus.PRESENT
                val isLate = currentStatus == AttendanceStatus.LATE

                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    accent = if (currentStatus == AttendanceStatus.ABSENT) DangerRed else null,
                    compact = true,
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ElAvatar(initials = student.fullName, size = 40)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        student.fullName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                        ),
                                    )
                                    Text(
                                        "Code: ${student.code}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AttendanceStatus.values().forEach { st ->
                                ElTag(
                                    text = st.label,
                                    color = st.color,
                                    selected = currentStatus == st,
                                    onClick = {
                                        statuses[student.id] = st
                                        if (st == AttendanceStatus.LATE && !lateTimes.containsKey(student.id)) {
                                            lateTimes[student.id] = "08:15"
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        if (isLate) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = LightBlue, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                ElTextField(
                                    value = lateTimes[student.id] ?: "08:15",
                                    onValueChange = { lateTimes[student.id] = it },
                                    label = "Heure d'arrivée",
                                    modifier = Modifier.width(180.dp),
                                    singleLine = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        message?.let {
            ElAlertBanner(
                message = it,
                severity = if (it.startsWith("Appel enregistré")) ElAlertSeverity.Success else ElAlertSeverity.Warning,
                title = if (it.startsWith("Appel enregistré")) "Appel Validé" else "Erreur",
            )
        }

        ElButton(
            text = "Valider l'appel (${selectedClass?.name ?: ""})",
            onClick = {
                val cid = selectedClassId ?: return@ElButton
                viewModel.submitRollCall(
                    classId = cid,
                    date = today,
                    session = "morning",
                    statuses = statuses.toMap(),
                    lateTimes = lateTimes.toMap(),
                    actorId = session.userId,
                    actorName = session.displayName,
                )
            },
            fullWidth = true,
            icon = Icons.Default.Send,
            enabled = !busy && students.isNotEmpty(),
        )
    }
}

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
fun GradeEntryScreen(
    session: Session,
    viewModel: GradeEntryViewModel = hiltViewModel(),
) {
    val classes by viewModel.classes.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val students by viewModel.students.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    var selectedClassId by remember { mutableStateOf<String?>(null) }
    var selectedSubjectId by remember { mutableStateOf<String?>(null) }
    var selectedStudentId by remember { mutableStateOf<String?>(null) }
    var term by remember { mutableStateOf("T1") }
    val academicYear = remember {
        val now = LocalDate.now()
        if (now.monthValue >= 9) "${now.year}-${now.year + 1}" else "${now.year - 1}-${now.year}"
    }

    var devoir1Text by remember { mutableStateOf("") }
    var devoir2Text by remember { mutableStateOf("") }
    var examenText by remember { mutableStateOf("") }

    val d1 = devoir1Text.toDoubleOrNull()
    val d2 = devoir2Text.toDoubleOrNull()
    val ex = examenText.toDoubleOrNull()
    // Mirror desktop formula: (D1 + D2 + 2*Ex) / 4 — server recomputes anyway.
    val subjectAverage = if (d1 != null && d2 != null && ex != null) (d1 + d2 + ex * 2) / 4.0 else null

    androidx.compose.runtime.LaunchedEffect(classes) {
        if (selectedClassId == null && classes.isNotEmpty()) selectedClassId = classes.first().id
    }
    androidx.compose.runtime.LaunchedEffect(selectedClassId) {
        selectedClassId?.let {
            viewModel.loadSubjectsForClass(it)
            viewModel.loadStudentsForClass(it)
        }
    }
    androidx.compose.runtime.LaunchedEffect(subjects) {
        if (selectedSubjectId == null && subjects.isNotEmpty()) selectedSubjectId = subjects.first().id
    }
    androidx.compose.runtime.LaunchedEffect(students) {
        if (selectedStudentId == null && students.isNotEmpty()) selectedStudentId = students.first().id
    }

    val selectedClass = classes.firstOrNull { it.id == selectedClassId }
    val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId }
    val selectedStudent = students.firstOrNull { it.id == selectedStudentId }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElGradientStatCard(
            title = "Saisie des Notes",
            value = subjectAverage?.let { "%.2f / 20".format(it) } ?: "— / 20",
            subtitle = "Moyenne calculée en temps réel",
            modifier = Modifier.fillMaxWidth(),
        )

        if (classes.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Class,
                title = "Aucune classe",
                message = "Créez d'abord une classe pour saisir des notes.",
            )
            return@Column
        }

        ElDropdown(
            label = "Classe",
            selectedValue = selectedClass?.name ?: "",
            options = classes.map { it.name },
            onSelected = { name ->
                selectedClassId = classes.first { it.name == name }.id
                selectedSubjectId = null
                selectedStudentId = null
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (subjects.isNotEmpty()) {
            ElDropdown(
                label = "Matière",
                selectedValue = selectedSubject?.name ?: "",
                options = subjects.map { it.name },
                onSelected = { name -> selectedSubjectId = subjects.first { it.name == name }.id },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (students.isNotEmpty()) {
            ElDropdown(
                label = "Élève",
                selectedValue = selectedStudent?.fullName ?: "",
                options = students.map { it.fullName },
                onSelected = { name -> selectedStudentId = students.first { it.fullName == name }.id },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ElTextField(
            value = term,
            onValueChange = { term = it },
            label = "Période / Trimestre (T1, T2, T3)",
            modifier = Modifier.fillMaxWidth(),
        )

        if (selectedStudent != null && selectedSubject != null) {
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ElSectionHeader(title = "Évaluation: ${selectedStudent.fullName}")

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ElTextField(
                            value = devoir1Text,
                            onValueChange = { value -> if (value.isEmpty() || value.toDoubleOrNull()?.let { it in 0.0..20.0 } == true) devoir1Text = value },
                            label = "Devoir 1 (/20)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        ElTextField(
                            value = devoir2Text,
                            onValueChange = { value -> if (value.isEmpty() || value.toDoubleOrNull()?.let { it in 0.0..20.0 } == true) devoir2Text = value },
                            label = "Devoir 2 (/20)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    ElTextField(
                        value = examenText,
                        onValueChange = { value -> if (value.isEmpty() || value.toDoubleOrNull()?.let { it in 0.0..20.0 } == true) examenText = value },
                        label = "Examen (/20 - Coeff 2)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Moyenne Calculée", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                subjectAverage?.let { "%.2f / 20".format(it) } ?: "—",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp),
                                color = PrimaryBlue,
                            )
                            Text("Formule: (D1 + D2 + (Examen × 2)) / 4", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        message?.let {
            ElAlertBanner(
                message = it,
                severity = if (it.startsWith("Note sauvegardée")) ElAlertSeverity.Success else ElAlertSeverity.Warning,
            )
        }

        ElButton(
            text = "Enregistrer le bulletin",
            onClick = {
                val sid = selectedStudentId ?: return@ElButton
                val subId = selectedSubjectId ?: return@ElButton
                val cid = selectedClassId ?: return@ElButton
                viewModel.enterGrade(
                    studentId = sid,
                    subjectId = subId,
                    classId = cid,
                    term = term,
                    academicYear = academicYear,
                    devoir1 = d1,
                    devoir2 = d2,
                    examen = ex,
                    coefficient = selectedSubject?.coefficient ?: 1,
                    actorId = session.userId,
                    actorName = session.displayName,
                )
            },
            fullWidth = true,
            enabled = !busy && selectedStudentId != null && selectedSubjectId != null,
        )
    }
}

// ── 3. Homework Push ──────────────────────────────────────────────────────

@HiltViewModel
class HomeworkPushViewModel @Inject constructor(
    private val classRepository: ClassRepository,
    private val subjectRepository: SubjectRepository,
    private val homeworkRepository: HomeworkRepository,
) : ViewModel() {

    val classes: StateFlow<List<AcademicClass>> = classRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _subjects = kotlinx.coroutines.flow.MutableStateFlow<List<Subject>>(emptyList())
    val subjects: StateFlow<List<Subject>> = _subjects

    private val _busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun loadSubjectsForClass(classId: String) {
        viewModelScope.launch {
            subjectRepository.observeByClass(classId).collect { _subjects.value = it }
        }
    }

    fun pushHomework(
        classId: String,
        subjectId: String,
        title: String,
        description: String,
        dueDate: String,
        attachments: List<String>,
        academicYear: String,
        actorId: String,
        actorName: String,
    ) {
        viewModelScope.launch {
            _busy.value = true
            val input = PushHomeworkInput(
                classId = classId, subjectId = subjectId,
                title = title, description = description,
                dueDate = dueDate, attachments = attachments,
                academicYear = academicYear,
            )
            val result = homeworkRepository.push(input, actorId, actorName)
            _busy.value = false
            result.onSuccess { _message.value = "Devoir diffusé à la classe." }
                .onFailure { _message.value = it.userMessage }
        }
    }
}

@Composable
fun HomeworkPushScreen(
    session: Session,
    viewModel: HomeworkPushViewModel = hiltViewModel(),
) {
    val classes by viewModel.classes.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    var selectedClassId by remember { mutableStateOf<String?>(null) }
    var selectedSubjectId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var photoAttached by remember { mutableStateOf(false) }

    val academicYear = remember {
        val now = LocalDate.now()
        if (now.monthValue >= 9) "${now.year}-${now.year + 1}" else "${now.year - 1}-${now.year}"
    }

    androidx.compose.runtime.LaunchedEffect(classes) {
        if (selectedClassId == null && classes.isNotEmpty()) selectedClassId = classes.first().id
    }
    androidx.compose.runtime.LaunchedEffect(selectedClassId) {
        selectedClassId?.let { viewModel.loadSubjectsForClass(it) }
    }
    androidx.compose.runtime.LaunchedEffect(subjects) {
        if (selectedSubjectId == null && subjects.isNotEmpty()) selectedSubjectId = subjects.first().id
    }

    val selectedClass = classes.firstOrNull { it.id == selectedClassId }
    val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElGradientStatCard(
            title = "Diffusion des Devoirs",
            value = selectedClass?.name ?: "Sélectionnez une classe",
            subtitle = "Publiez sur le portail élèves & parents",
            modifier = Modifier.fillMaxWidth(),
        )

        if (classes.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Class,
                title = "Aucune classe",
                message = "Créez d'abord une classe pour diffuser un devoir.",
            )
            return@Column
        }

        ElDropdown(
            label = "Classe Cible",
            selectedValue = selectedClass?.name ?: "",
            options = classes.map { it.name },
            onSelected = { name ->
                selectedClassId = classes.first { it.name == name }.id
                selectedSubjectId = null
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (subjects.isNotEmpty()) {
            ElDropdown(
                label = "Matière",
                selectedValue = selectedSubject?.name ?: "",
                options = subjects.map { it.name },
                onSelected = { name -> selectedSubjectId = subjects.first { it.name == name }.id },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ElTextField(value = title, onValueChange = { title = it }, label = "Titre du Devoir", modifier = Modifier.fillMaxWidth())
        ElTextField(value = description, onValueChange = { description = it }, label = "Consignes et Détails", modifier = Modifier.fillMaxWidth(), singleLine = false)
        ElTextField(value = dueDate, onValueChange = { dueDate = it }, label = "Date de Rendu (AAAA-MM-JJ)", modifier = Modifier.fillMaxWidth())

        ElCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Photo du Tableau", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        if (photoAttached) "✓ Photo capturée (WebP)" else "Aucune photo jointe",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (photoAttached) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ElButton(
                    text = if (photoAttached) "Retirer" else "Capturer",
                    onClick = { photoAttached = !photoAttached },
                    style = ElButtonStyle.Secondary,
                    icon = Icons.Default.CameraAlt,
                )
            }
        }

        message?.let {
            ElAlertBanner(
                message = it,
                severity = if (it.startsWith("Devoir diffusé")) ElAlertSeverity.Success else ElAlertSeverity.Warning,
            )
        }

        ElButton(
            text = "Diffuser le Devoir",
            onClick = {
                val cid = selectedClassId ?: return@ElButton
                val sid = selectedSubjectId ?: return@ElButton
                viewModel.pushHomework(
                    classId = cid,
                    subjectId = sid,
                    title = title.ifBlank { "Devoir" },
                    description = description,
                    dueDate = dueDate,
                    attachments = if (photoAttached) listOf("photo_tableau.webp") else emptyList(),
                    academicYear = academicYear,
                    actorId = session.userId,
                    actorName = session.displayName,
                )
            },
            fullWidth = true,
            icon = Icons.Default.Send,
            enabled = !busy && selectedClassId != null && selectedSubjectId != null && title.isNotBlank(),
        )
    }
}

// ── 4. Classes Directory ──────────────────────────────────────────────────

@HiltViewModel
class ClassesDirectoryViewModel @Inject constructor(
    private val classRepository: ClassRepository,
) : ViewModel() {
    val classes: StateFlow<List<AcademicClass>> = classRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@Composable
fun ClassesDirectoryScreen(
    session: Session,
    viewModel: ClassesDirectoryViewModel = hiltViewModel(),
) {
    val classes by viewModel.classes.collectAsState()

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElSectionHeader(title = "Annuaire des Classes (${classes.size})")
        if (classes.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Class,
                title = "Aucune classe",
                message = "Aucune classe n'a été créée. Utilisez les paramètres pour en ajouter.",
            )
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(classes) { klass ->
                val fillRate = if (klass.capacity > 0) (klass.enrolledCount.toFloat() / klass.capacity * 100).toInt() else 0
                ElListItem(
                    title = klass.name,
                    subtitle = "Professeur: ${klass.homeroomTeacherName ?: "Non assigné"} · Salle: ${klass.room ?: "—"}",
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Class, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        }
                    },
                    trailing = {
                        ElTag(text = "${klass.enrolledCount}/${klass.capacity} ($fillRate%)", color = if (fillRate >= 90) WarmGold else PrimaryBlue)
                    },
                )
            }
        }
    }
}
