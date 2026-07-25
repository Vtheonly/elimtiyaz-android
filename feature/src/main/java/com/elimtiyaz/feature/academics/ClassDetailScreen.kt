package com.elimtiyaz.feature.academics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Room
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.AcademicLevel
import com.elimtiyaz.core.common.AttendanceStatus
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AsyncContent
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.ListRow
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.AcademicClass
import com.elimtiyaz.domain.model.Assessment
import com.elimtiyaz.domain.model.AttendanceSession
import com.elimtiyaz.domain.model.ClassSubject
import com.elimtiyaz.domain.model.Student

/**
 * Single class detail screen — header + 4 tabs (Élèves / Matières / Présences / Notes)
 * plus quick actions for Appel, Saisie notes, Devoirs.
 *
 * Reached via [Route.ClassDetail] from the Academics hub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(
    classId: String,
    nav: NavController,
    vm: ClassDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(classId) { vm.load(classId) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSubjectPicker by remember { mutableStateOf(false) }

    val canRollCall = session?.can(Permission.RollCall) == true
    val canGrades = session?.can(Permission.EnterGrades) == true
    val canHomework = session?.can(Permission.AssignHomework) == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.classInfo?.name ?: "Classe") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            // Header card — always visible above the tabs.
            ClassHeaderCard(state.classInfo)
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            QuickActionsRow(
                canRollCall = canRollCall,
                canGrades = canGrades,
                canHomework = canHomework,
                onRollCall = { nav.navigate(Route.RollCall.build(classId)) },
                onGradeEntry = { showSubjectPicker = true },
                onHomework = { nav.navigate(Route.HomeworkPush.build(classId)) },
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            SecondaryTabRow(selectedTabIndex = tab) {
                listOf("Élèves", "Matières", "Présences", "Notes").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            when (tab) {
                0 -> RosterTab(state.roster, isLoading = state.isLoading, onStudentClick = { id -> nav.navigate(Route.StudentDetail.build(id)) })
                1 -> SubjectsTab(state.subjects, isLoading = state.isLoading)
                2 -> AttendanceTab(state)
                3 -> GradesTab(state)
            }
        }
    }

    if (showSubjectPicker) {
        SubjectPickerDialog(
            subjects = state.subjects,
            onDismiss = { showSubjectPicker = false },
            onPick = { cs ->
                showSubjectPicker = false
                nav.navigate(Route.GradeEntry.build(classId, cs.subjectId))
            },
        )
    }
}

// ----- Header ---------------------------------------------------------------

@Composable
private fun ClassHeaderCard(c: AcademicClass?) {
    if (c == null) {
        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text("Chargement…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(initial = c.name.firstOrNull()?.toString() ?: "?", size = 48)
                Spacer(Modifier.width(ElimtiyazSpacing.x4))
                Column(modifier = Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${AcademicLevel.from(c.level)?.displayFr ?: c.level} · Année ${c.academicYear}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(
                    label = "${c.enrolledCount}/${c.capacity}",
                    tone = if (c.enrolledCount >= c.capacity) StatusTone.Danger else StatusTone.Success,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x4)) {
                HeaderFact(icon = Icons.Outlined.Person, label = "Titulaire", value = c.homeroomTeacherName ?: "—", modifier = Modifier.weight(1f))
                HeaderFact(icon = Icons.Outlined.Room, label = "Salle", value = c.room ?: "—", modifier = Modifier.weight(1f))
                HeaderFact(icon = Icons.Outlined.Group, label = "Effectif", value = "${c.enrolledCount} / ${c.capacity}", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeaderFact(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(ElimtiyazSpacing.x2))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ----- Quick actions --------------------------------------------------------

@Composable
private fun QuickActionsRow(
    canRollCall: Boolean,
    canGrades: Boolean,
    canHomework: Boolean,
    onRollCall: () -> Unit,
    onGradeEntry: () -> Unit,
    onHomework: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ElimtiyazSpacing.x4),
        horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
    ) {
        if (canRollCall) {
            OutlinedButton(onClick = onRollCall, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.HowToReg, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text("Appel")
            }
        }
        if (canGrades) {
            OutlinedButton(onClick = onGradeEntry, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text("Notes")
            }
        }
        if (canHomework) {
            OutlinedButton(onClick = onHomework, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Assignment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text("Devoirs")
            }
        }
    }
}

// ----- Tab: Roster ----------------------------------------------------------

@Composable
private fun RosterTab(roster: List<Student>, isLoading: Boolean, onStudentClick: (String) -> Unit) {
    AsyncContent(
        isLoading = isLoading,
        error = null,
        items = roster,
        emptyTitle = "Aucun élève",
        emptyDescription = "Aucun élève n'est inscrit dans cette classe.",
        emptyIcon = Icons.Outlined.Group,
    ) { list ->
        LazyColumn(
            contentPadding = PaddingValues(ElimtiyazSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
        ) {
            item {
                Text(
                    "${list.size} élève(s)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(list, key = { it.id }) { s ->
                ElImtiyazCard(onClick = { onStudentClick(s.id) }) {
                    ListRow(
                        leading = { AvatarCircle(initial = Formatters.initials(s.firstName, s.lastName), size = 36) },
                        title = Formatters.fullName(s.firstName, s.lastName),
                        subtitle = "${s.code} · né(e) ${Formatters.date(s.birthDate)}",
                        trailing = { StatusChip(label = s.status.name, tone = if (s.status.name == "Active") StatusTone.Success else StatusTone.Warning) },
                    )
                }
            }
        }
    }
}

// ----- Tab: Subjects --------------------------------------------------------

@Composable
private fun SubjectsTab(subjects: List<ClassSubject>, isLoading: Boolean) {
    AsyncContent(
        isLoading = isLoading,
        error = null,
        items = subjects,
        emptyTitle = "Aucune matière",
        emptyDescription = "Aucune matière n'est assignée à cette classe.",
        emptyIcon = Icons.Outlined.MenuBook,
    ) { list ->
        LazyColumn(
            contentPadding = PaddingValues(ElimtiyazSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
        ) {
            items(list, key = { it.id }) { cs ->
                ElImtiyazCard {
                    Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(ElimtiyazSpacing.x2))
                            Text(
                                "Matière #${cs.subjectId.takeLast(6)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.weight(1f))
                            StatusChip(label = "Coef ${cs.coefficient}", tone = StatusTone.Neutral)
                        }
                        Spacer(Modifier.height(ElimtiyazSpacing.x2))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(ElimtiyazSpacing.x2))
                            Text(
                                cs.teacherName ?: "Enseignant non assigné",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(ElimtiyazSpacing.x2))
                            Text(
                                "${cs.weeklyHours} h/sem",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----- Tab: Attendance ------------------------------------------------------

@Composable
private fun AttendanceTab(state: ClassDetailUiState) {
    if (state.isLoading && state.weekAttendance.isEmpty()) {
        LoadingState()
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ElimtiyazSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
    ) {
        Text("Cette semaine", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (state.weekAttendance.isEmpty()) {
            ElImtiyazCard {
                Text(
                    "Aucun appel enregistré cette semaine.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(ElimtiyazSpacing.x4),
                )
            }
        } else {
            AttendanceSummaryCard(state.weekStatusCounts, state.weekAttendance.size)
            // Group by date and show the most recent 5 sessions.
            val byDate = state.weekAttendance.groupBy { it.date }.entries.take(5)
            byDate.forEach { (date, records) ->
                ElImtiyazCard {
                    Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
                        Text(Formatters.date(date), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(ElimtiyazSpacing.x2))
                        records.take(5).forEach { r ->
                            AttendanceRow(r.studentId, r.status, r.session)
                        }
                        if (records.size > 5) {
                            Spacer(Modifier.height(ElimtiyazSpacing.x1))
                            Text(
                                "+ ${records.size - 5} autres",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceSummaryCard(counts: Map<String, Int>, total: Int) {
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Text("$total enregistrements", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2)) {
                AttendanceStatus.values().forEach { st ->
                    val count = counts[st.key] ?: 0
                    val tone = when (st) {
                        AttendanceStatus.Present -> StatusTone.Success
                        AttendanceStatus.Late -> StatusTone.Warning
                        AttendanceStatus.AbsentExcused -> StatusTone.Info
                        AttendanceStatus.AbsentUnexcused -> StatusTone.Danger
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(count.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(ElimtiyazSpacing.x1))
                        StatusChip(label = st.displayFr, tone = tone)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceRow(studentId: String, status: String, session: AttendanceSession) {
    val st = AttendanceStatus.from(status)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ElimtiyazSpacing.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarCircle(initial = studentId.firstOrNull()?.toString() ?: "?", size = 24)
        Spacer(Modifier.width(ElimtiyazSpacing.x2))
        Text(
            "Élève ${studentId.takeLast(6)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            session.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(ElimtiyazSpacing.x2))
        val tone = when (st) {
            AttendanceStatus.Present -> StatusTone.Success
            AttendanceStatus.Late -> StatusTone.Warning
            AttendanceStatus.AbsentExcused -> StatusTone.Info
            AttendanceStatus.AbsentUnexcused -> StatusTone.Danger
            null -> StatusTone.Neutral
        }
        StatusChip(label = st?.displayFr ?: status, tone = tone)
    }
}

// ----- Tab: Grades ----------------------------------------------------------

@Composable
private fun GradesTab(state: ClassDetailUiState) {
    if (state.isLoading && state.recentGrades.isEmpty()) {
        LoadingState()
        return
    }
    val latest = state.latestGradeBySubject
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ElimtiyazSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
    ) {
        Text("Dernières notes (T1)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (latest.isEmpty()) {
            ElImtiyazCard {
                Text(
                    "Aucune note saisie pour cette classe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(ElimtiyazSpacing.x4),
                )
            }
        } else {
            latest.forEach { (subjectId, a) -> GradeCard(subjectId, a) }
        }
    }
}

@Composable
private fun GradeCard(subjectId: String, a: Assessment) {
    val avg = a.subjectAverage
    val tone = when {
        avg == null -> StatusTone.Neutral
        avg >= 10.0 -> StatusTone.Success
        else -> StatusTone.Danger
    }
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text(
                    "Matière ${subjectId.takeLast(6)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(
                    label = if (avg != null) String.format(java.util.Locale.FRANCE, "%.2f", avg) else "—",
                    tone = tone,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3)) {
                GradeCell("D1", a.devoir1, modifier = Modifier.weight(1f))
                GradeCell("D2", a.devoir2, modifier = Modifier.weight(1f))
                GradeCell("Examen", a.examen, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GradeCell(label: String, value: Double?, modifier: Modifier = Modifier) {
    val tone = when {
        value == null -> StatusTone.Neutral
        value >= 10.0 -> StatusTone.Success
        else -> StatusTone.Danger
    }
    val color = when (tone) {
        StatusTone.Success -> MaterialTheme.colorScheme.primary
        StatusTone.Danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (value != null) String.format(java.util.Locale.FRANCE, "%.1f", value) else "—",
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ----- Subject picker dialog (for grade entry) ------------------------------

@Composable
private fun SubjectPickerDialog(
    subjects: List<ClassSubject>,
    onDismiss: () -> Unit,
    onPick: (ClassSubject) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choisir une matière") },
        text = {
            Column {
                if (subjects.isEmpty()) {
                    Text(
                        "Aucune matière assignée à cette classe. Assignez d'abord une matière depuis la fiche classe.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    subjects.forEach { cs ->
                        TextButton(onClick = { onPick(cs) }) {
                            Text("Matière #${cs.subjectId.takeLast(6)} · Coef ${cs.coefficient}")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
