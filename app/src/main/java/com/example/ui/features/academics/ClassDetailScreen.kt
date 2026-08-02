package com.example.ui.features.academics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Permission
import com.example.domain.model.AcademicClass
import com.example.domain.model.Assessment
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.Student
import com.example.domain.model.Subject
import com.example.domain.repository.AttendanceRepository
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.GradeRepository
import com.example.domain.repository.StudentRepository
import com.example.domain.repository.SubjectRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

/**
 * Class detail ViewModel.
 *
 * Restored behavior (commit a34333a):
 *  - 4-tab layout: Élèves / Matières / Présences / Notes.
 *  - Aggregates: classInfo, roster, subjects, week attendance, recent grades.
 *  - RBAC-gated action visibility (roll-call / grade-entry / homework-push).
 */
@HiltViewModel
class ClassDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val classRepository: ClassRepository,
    private val studentRepository: StudentRepository,
    private val subjectRepository: SubjectRepository,
    private val attendanceRepository: AttendanceRepository,
    private val gradeRepository: GradeRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val classId: String = savedStateHandle["classId"] ?: ""

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val classInfo: StateFlow<AcademicClass?> = classRepository.observeById(classId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val roster: StateFlow<List<Student>> = studentRepository.observeByClass(classId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val subjects: StateFlow<List<Subject>> = subjectRepository.observeByClass(classId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _weekAttendance = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val weekAttendance: StateFlow<List<AttendanceRecord>> = _weekAttendance.asStateFlow()

    private val _recentGrades = MutableStateFlow<List<Assessment>>(emptyList())
    val recentGrades: StateFlow<List<Assessment>> = _recentGrades.asStateFlow()

    val canRollCall: Boolean get() = sessionManager.current()?.can(Permission.ROLL_CALL) == true
    val canEnterGrades: Boolean get() = sessionManager.current()?.can(Permission.ENTER_GRADES) == true
    val canAssignHomework: Boolean get() = sessionManager.current()?.can(Permission.ASSIGN_HOMEWORK) == true

    init { load(classId) }

    fun load(classId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Week attendance: 7 days from Monday
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                val monday = today.minus(today.dayOfWeek.value - 1, DateTimeUnit.DAY)
                val records = mutableListOf<AttendanceRecord>()
                for (i in 0 until 7) {
                    val day = monday.plus(i, DateTimeUnit.DAY)
                    val dayRecords = attendanceRepository.observeByClass(classId, day.toString())
                    records.addAll(dayRecords)
                }
                _weekAttendance.value = records.sortedByDescending { it.date }

                // Recent grades: fetch per-subject
                val allGrades = mutableListOf<Assessment>()
                val currentYear = Clock.System.todayIn(TimeZone.currentSystemDefault()).year.let {
                    val m = Clock.System.todayIn(TimeZone.currentSystemDefault()).monthNumber
                    if (m >= 9) "$it-${it + 1}" else "${it - 1}-$it"
                }
                subjects.value.forEach { subj ->
                    val g = gradeRepository.observeForClass(classId, subj.id, "T1", currentYear)
                    allGrades.addAll(g)
                }
                _recentGrades.value = allGrades.sortedByDescending { it.enteredAt }
            } catch (t: Throwable) {
                _error.value = t.message ?: "Erreur de chargement."
            } finally {
                _isLoading.value = false
            }
        }
    }

    val weekStatusCounts: StateFlow<Map<String, Int>> = _weekAttendance.asStateFlow().let { sf ->
        kotlinx.coroutines.flow.combine(sf) { records ->
            records.groupingBy { it.status }.eachCount()
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    }
}

private fun kotlinx.datetime.LocalDate.plus(value: Int, unit: kotlinx.datetime.DateTimeUnit.Day): kotlinx.datetime.LocalDate =
    kotlinx.datetime.plus(this, value, unit, TimeZone.currentSystemDefault())

private val kotlinx.datetime.LocalDate.monthNumber: Int get() = this.monthNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(
    onBack: () -> Unit,
    onNavigateToStudent: (String) -> Unit,
    onNavigateToRollCall: (String) -> Unit,
    onNavigateToGradeEntry: (String) -> Unit,
    onNavigateToHomeworkPush: (String) -> Unit,
    viewModel: ClassDetailViewModel = hiltViewModel(),
) {
    val classInfo by viewModel.classInfo.collectAsState()
    val roster by viewModel.roster.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val weekAttendance by viewModel.weekAttendance.collectAsState()
    val recentGrades by viewModel.recentGrades.collectAsState()
    val weekStatusCounts by viewModel.weekStatusCounts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Élèves", "Matières", "Présences", "Notes")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(classInfo?.name ?: "Classe") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
                actions = {
                    if (viewModel.canRollCall) {
                        IconButton(onClick = { onNavigateToRollCall(viewModel.classId) }) {
                            Icon(Icons.Default.Assignment, contentDescription = "Appel")
                        }
                    }
                    if (viewModel.canEnterGrades) {
                        IconButton(onClick = { onNavigateToGradeEntry(viewModel.classId) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Notes")
                        }
                    }
                    if (viewModel.canAssignHomework) {
                        IconButton(onClick = { onNavigateToHomeworkPush(viewModel.classId) }) {
                            Icon(Icons.Default.People, contentDescription = "Devoir")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Header card
            classInfo?.let { cls ->
                Card(
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(cls.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Niveau: ${cls.level}", style = MaterialTheme.typography.bodySmall)
                        cls.homeroomTeacherName?.let { Text("Prof principal: $it", style = MaterialTheme.typography.bodySmall) }
                        cls.room?.let { Text("Salle: $it", style = MaterialTheme.typography.bodySmall) }
                        Spacer(Modifier.height(8.dp))
                        val enrolledPct = if (cls.capacity > 0) cls.enrolledCount.toFloat() / cls.capacity else 0f
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { enrolledPct },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${cls.enrolledCount} / ${cls.capacity} élèves", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, label ->
                    androidx.compose.material3.Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label) },
                    )
                }
            }

            when (selectedTab) {
                0 -> LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(roster) { student ->
                        Card(modifier = Modifier.fillMaxWidth(), onClick = { onNavigateToStudent(student.id) }) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(student.fullName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(student.code, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                1 -> LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(subjects) { subj ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(subj.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Code: ${subj.code} • Coef: ${subj.coefficient}", style = MaterialTheme.typography.labelSmall)
                                if (subj.isExtracurricular) {
                                    Text("Hors programme (non comptée dans la moyenne)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
                2 -> Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Cette semaine", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusCountChip("Présents", weekStatusCounts["present"] ?: 0, MaterialTheme.colorScheme.primary)
                        StatusCountChip("Retards", weekStatusCounts["late"] ?: 0, MaterialTheme.colorScheme.secondary)
                        StatusCountChip("Excusés", weekStatusCounts["absent_excused"] ?: 0, MaterialTheme.colorScheme.tertiary)
                        StatusCountChip("Non excusés", weekStatusCounts["absent_unexcused"] ?: 0, MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(weekAttendance) { rec ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(rec.date, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                    Text(rec.status, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                3 -> Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Dernières notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val passing = recentGrades.count { (it.subjectAverage ?: 0.0) >= 10.0 }
                    val failing = recentGrades.count { (it.subjectAverage ?: 0.0) < 10.0 && it.subjectAverage != null }
                    val missing = recentGrades.count { it.subjectAverage == null }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusCountChip("Évaluations", recentGrades.size, MaterialTheme.colorScheme.primary)
                        StatusCountChip("≥ 10", passing, MaterialTheme.colorScheme.tertiary)
                        StatusCountChip("< 10", failing, MaterialTheme.colorScheme.error)
                        StatusCountChip("Manquantes", missing, MaterialTheme.colorScheme.outline)
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(recentGrades) { g ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                    Text("Matière: ${g.subjectId}", style = MaterialTheme.typography.labelSmall)
                                    Text("D1=${g.devoir1 ?: "-"}  D2=${g.devoir2 ?: "-"}  Ex=${g.examen ?: "-"}", style = MaterialTheme.typography.bodySmall)
                                    g.subjectAverage?.let { avg ->
                                        Text("Moy: $avg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (avg >= 10.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCountChip(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
