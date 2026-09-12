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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.feedback.ElLoadingBlock
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.components.tabs.ElTabRow
import com.example.ui.designsystem.components.data.ElGaugeArc
import com.example.ui.designsystem.theme.ElTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
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
                    val dayRecords = attendanceRepository.observeByClass(classId, day.toString()).first()
                    records.addAll(dayRecords)
                }
                _weekAttendance.value = records.sortedByDescending { it.date }

                // Recent grades: fetch per-subject.
                // FIX: previously read `subjects.value` BEFORE the lazily-started
                // shared flow had any subscriber — the list was always EMPTY at
                // init, so the "Dernières notes" tab never showed anything until
                // a manual reload. Await the first emission instead.
                // FIX: also fetch ALL terms (T1/T2/T3), not just "T1".
                val allGrades = mutableListOf<Assessment>()
                val now = Clock.System.todayIn(TimeZone.currentSystemDefault())
                val currentYear = if (now.monthNumber >= 9) "${now.year}-${now.year + 1}" else "${now.year - 1}-${now.year}"
                val subjectsList = subjectRepository.observeByClass(classId).first()
                subjectsList.forEach { subj ->
                    for (term in listOf("T1", "T2", "T3")) {
                        val g = gradeRepository.observeForClass(classId, subj.id, term, currentYear).first()
                        allGrades.addAll(g)
                    }
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
        sf.map { records ->
            records.groupingBy { it.status }.eachCount()
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    }
}

/**
 * T-323 (55th session, UI-313) — class detail on the canonical design system:
 * saveable selected tab, live tab counts, per-tab empty states, roster
 * avatars + matricule, subject weights, weighted counter pills (the old
 * unweighted Row could overflow on 320dp), humanized attendance statuses,
 * and grade accents. All inline action buttons stay RBAC-gated
 * (ROLL_CALL / ENTER_GRADES) — the reference proposal rendered them
 * unconditionally, which would leak dead affordances to unauthorized roles.
 */
@Composable
fun ClassDetailScreen(
    onBack: () -> Unit,
    onNavigateToStudent: (String) -> Unit,
    onNavigateToRollCall: (String) -> Unit,
    onNavigateToGradeEntry: (String) -> Unit,
    onNavigateToHomeworkPush: (String) -> Unit,
    viewModel: ClassDetailViewModel = hiltViewModel(),
) {
    val c = ElTheme.colors
    val classInfo by viewModel.classInfo.collectAsState()
    val roster by viewModel.roster.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val weekAttendance by viewModel.weekAttendance.collectAsState()
    val recentGrades by viewModel.recentGrades.collectAsState()
    val weekStatusCounts by viewModel.weekStatusCounts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        "Élèves (${roster.size})",
        "Matières (${subjects.size})",
        "Présences",
        "Notes",
    )

    ElScaffold(
        topBar = {
            ElTopBar(
                title = classInfo?.name ?: "Classe",
                subtitle = classInfo?.academicYear?.let { "Année scolaire $it" },
                onBack = onBack,
                actions = {
                    if (viewModel.canRollCall) {
                        androidx.compose.material3.IconButton(onClick = { onNavigateToRollCall(viewModel.classId) }) {
                            androidx.compose.material3.Icon(
                                Icons.Default.FactCheck,
                                contentDescription = "Faire l'appel",
                            )
                        }
                    }
                    if (viewModel.canEnterGrades) {
                        androidx.compose.material3.IconButton(onClick = { onNavigateToGradeEntry(viewModel.classId) }) {
                            androidx.compose.material3.Icon(
                                Icons.Default.EditNote,
                                contentDescription = "Saisir les notes",
                            )
                        }
                    }
                    if (viewModel.canAssignHomework) {
                        androidx.compose.material3.IconButton(onClick = { onNavigateToHomeworkPush(viewModel.classId) }) {
                            androidx.compose.material3.Icon(
                                Icons.Default.AddTask,
                                contentDescription = "Diffuser un devoir",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Header card — capacity gauge + gender demographics
            classInfo?.let { cls ->
                ElCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            cls.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Niveau : ${cls.level.uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                        cls.homeroomTeacherName?.let {
                            Text(
                                "Prof principal : $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                        cls.room?.let {
                            Text(
                                "Salle : $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        // PARITY-003 — the desktop see-details-modal capacity
                        // GAUGE twin (semi-circle arc, tone: danger >=100 /
                        // gold >=80 / success; percent = round(count/cap*100))
                        // + the gender chips (desktop demographics gender rows).
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ElGaugeArc(
                                percent = if (cls.capacity > 0) {
                                    (cls.enrolledCount.toFloat() / cls.capacity * 100).toInt()
                                } else 0,
                                caption = "${cls.enrolledCount} / ${cls.capacity} inscrits",
                            )
                            val boys = roster.count { it.gender == "male" || it.gender == "M" }
                            val girls = roster.count { it.gender == "female" || it.gender == "F" }
                            val total = roster.size.coerceAtLeast(1)
                            Column(horizontalAlignment = Alignment.End) {
                                val boysPct = Math.round(boys.toDouble() / total * 100).toInt()
                                val girlsPct = Math.round(girls.toDouble() / total * 100).toInt()
                                Text(
                                    "Garçons : $boys ($boysPct%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Text(
                                    "Filles : $girls ($girlsPct%)",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                if (roster.size > boys + girls) {
                                    val uns = roster.size - boys - girls
                                    Text("Non spécifié : $uns", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            error?.let {
                Text(
                    it,
                    color = c.danger,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            ElTabRow(
                tabs = tabs,
                selectedIndex = selectedTab,
                onSelected = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            when (selectedTab) {
                0 -> if (isLoading) {
                    ElLoadingBlock(modifier = Modifier.fillMaxWidth().padding(16.dp))
                } else if (roster.isEmpty()) {
                    ElEmptyState(
                        title = "Aucun élève",
                        subtitle = "Aucun élève n'est inscrit dans cette classe.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(roster) { student ->
                            ElCard(
                                modifier = Modifier.fillMaxWidth(),
                                size = com.example.ui.designsystem.components.card.ElCardSize.COMPACT,
                                onClick = { onNavigateToStudent(student.id) },
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    ElAvatar(initials = student.fullName, size = ElAvatarSize.S)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            student.fullName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "Matricule : ${student.code}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = c.textSecondary,
                                        )
                                    }
                                    when (student.gender) {
                                        "male", "M" -> ElTag(text = "M", tone = ElTagTone.INFO)
                                        "female", "F" -> ElTag(text = "F", tone = ElTagTone.INFO)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> if (subjects.isEmpty()) {
                    ElEmptyState(
                        title = "Aucune matière",
                        subtitle = "Aucune matière n'est rattachée à cette classe.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(subjects) { subj ->
                            ElCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            subj.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        ElTag(text = "Coef ${subj.coefficient}", tone = ElTagTone.INFO)
                                    }
                                    Text(
                                        "Code : ${subj.code} • Seuil : ${subj.passingGrade}/20",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = c.textSecondary,
                                    )
                                    Text(
                                        "Pondération : D1 ×${subj.coefficientDevoir1} · D2 ×${subj.coefficientDevoir2} · Examen ×${subj.coefficientExamen}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = c.textSecondary,
                                    )
                                    if (subj.isExtracurricular) {
                                        Text(
                                            "Hors programme (non comptée dans la moyenne)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = c.warning,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> if (weekAttendance.isEmpty()) {
                    ElEmptyState(
                        title = "Aucune présence enregistrée",
                        subtitle = "Les relevés d'appel de la semaine apparaîtront ici.",
                        actionLabel = if (viewModel.canRollCall) "Faire l'appel" else null,
                        onAction = if (viewModel.canRollCall) {
                            { onNavigateToRollCall(viewModel.classId) }
                        } else null,
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Bilan hebdomadaire",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (viewModel.canRollCall) {
                                ElButton(
                                    text = "Faire l'appel",
                                    onClick = { onNavigateToRollCall(viewModel.classId) },
                                    variant = ElButtonVariant.SECONDARY,
                                    size = com.example.ui.designsystem.components.button.ElButtonSize.SMALL,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // Weighted pills — the old unweighted Row overflowed on
                        // narrow screens (4 chips × intrinsic width).
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            StatusCountChip(
                                "Présents", weekStatusCounts["present"] ?: 0, c.primary,
                                modifier = Modifier.weight(1f),
                            )
                            StatusCountChip(
                                "Retards", weekStatusCounts["late"] ?: 0, c.warning,
                                modifier = Modifier.weight(1f),
                            )
                            StatusCountChip(
                                "Excusés", weekStatusCounts["absent_excused"] ?: 0, c.info,
                                modifier = Modifier.weight(1f),
                            )
                            StatusCountChip(
                                "Non excusés", weekStatusCounts["absent_unexcused"] ?: 0, c.danger,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // 7-day window × roster — cap at 40 records for scroll health.
                            items(weekAttendance.take(40)) { rec ->
                                ElCard(modifier = Modifier.fillMaxWidth(), size = com.example.ui.designsystem.components.card.ElCardSize.COMPACT) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            rec.date,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.weight(1f),
                                        )
                                        val (label, tone) = attendanceStatusLabel(rec.status)
                                        ElTag(text = label, tone = tone)
                                    }
                                    rec.note?.let { note ->
                                        Text(
                                            note,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = c.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> if (recentGrades.isEmpty()) {
                    ElEmptyState(
                        title = "Aucune note",
                        subtitle = "Les évaluations de la classe apparaîtront ici.",
                        actionLabel = if (viewModel.canEnterGrades) "Saisir des notes" else null,
                        onAction = if (viewModel.canEnterGrades) {
                            { onNavigateToGradeEntry(viewModel.classId) }
                        } else null,
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Dernières notes",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (viewModel.canEnterGrades) {
                                ElButton(
                                    text = "Saisir des notes",
                                    onClick = { onNavigateToGradeEntry(viewModel.classId) },
                                    variant = ElButtonVariant.SECONDARY,
                                    size = com.example.ui.designsystem.components.button.ElButtonSize.SMALL,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // FIX (raw ids): rows showed raw subjectIds ("sub-math") —
                        // resolve real subject names + coefficients.
                        val subjectById = subjects.associateBy { it.id }
                        // Class-level canonical summary: average of every computed
                        // subject average + share of passing marks.
                        val computedAverages = recentGrades.mapNotNull { it.subjectAverage }
                        val passingCount = computedAverages.count { it >= 10.0 }
                        val failing = computedAverages.count { it < 10.0 }
                        val missing = recentGrades.count { it.subjectAverage == null }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusCountChip("Évaluations", recentGrades.size, c.primary, modifier = Modifier.weight(1f))
                            StatusCountChip("≥ 10", passingCount, c.success, modifier = Modifier.weight(1f))
                            StatusCountChip("< 10", failing, c.danger, modifier = Modifier.weight(1f))
                            StatusCountChip("Manquantes", missing, c.textSecondary, modifier = Modifier.weight(1f))
                        }
                        if (computedAverages.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            val classAvg = computedAverages.average()
                            Text(
                                "Moyenne générale de la classe : %.2f / 20 • Réussite : %.0f%%".format(
                                    classAvg,
                                    passingCount * 100.0 / computedAverages.size,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (classAvg >= 10.0) c.primary else c.danger,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(recentGrades.take(30)) { g ->
                                val subject = subjectById[g.subjectId]
                                ElCard(modifier = Modifier.fillMaxWidth(), size = com.example.ui.designsystem.components.card.ElCardSize.COMPACT) {
                                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                "${subject?.name ?: g.subjectId} • ${g.term}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                "Coef ${g.coefficient}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = c.textSecondary,
                                            )
                                        }
                                        Text(
                                            "D1 : ${g.devoir1 ?: "—"} · D2 : ${g.devoir2 ?: "—"} · Examen : ${g.examen ?: "—"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = c.textSecondary,
                                        )
                                        g.subjectAverage?.let { avg ->
                                            Text(
                                                "Moyenne : %.1f / 20".format(avg),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    avg >= 10.0 -> c.success
                                                    else -> c.danger
                                                },
                                            )
                                        } ?: Text(
                                            "Moyenne manquante",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = c.warning,
                                        )
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

/** Humanized attendance status + tone (T-323: raw codes no longer leak). */
internal fun attendanceStatusLabel(status: String): Pair<String, ElTagTone> = when (status) {
    "present" -> "Présent" to ElTagTone.SUCCESS
    "late" -> "Retard" to ElTagTone.WARNING
    "absent_excused" -> "Absence excusée" to ElTagTone.INFO
    "absent_unexcused" -> "Absence non excusée" to ElTagTone.DANGER
    else -> status to ElTagTone.NEUTRAL
}

@Composable
private fun StatusCountChip(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.1f), MaterialTheme.shapes.small)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
