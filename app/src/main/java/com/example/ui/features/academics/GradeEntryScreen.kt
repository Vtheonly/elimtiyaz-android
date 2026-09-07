package com.example.ui.features.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.core.computeSubjectAverage
import com.example.core.isPassing
import com.example.domain.model.Student
import com.example.ui.components.ElAlertBanner
import com.example.ui.components.ElAlertSeverity
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElCard
import com.example.ui.components.ElDropdown
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElProgressBar
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import java.time.LocalDate

@Composable
fun GradeEntryScreen(
    session: Session,
    onNavigateToGradeEntry: (String) -> Unit = {},
    initialClassId: String? = null,
    onBack: (() -> Unit)? = null,
    viewModel: GradeEntryViewModel = hiltViewModel(),
) {
    val classes by viewModel.classes.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val students by viewModel.students.collectAsState()
    val classAssessments by viewModel.classAssessments.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    var selectedClassId by remember { mutableStateOf<String?>(initialClassId) }
    var selectedSubjectId by remember { mutableStateOf<String?>(null) }
    var term by remember { mutableStateOf("T1") }
    var editingStudent by remember { mutableStateOf<Student?>(null) }

    val academicYear = remember {
        val now = LocalDate.now()
        if (now.monthValue >= 9) "${now.year}-${now.year + 1}" else "${now.year - 1}-${now.year}"
    }

    LaunchedEffect(classes) {
        if (selectedClassId == null && classes.isNotEmpty()) {
            selectedClassId = classes.first().id
        }
    }
    LaunchedEffect(selectedClassId) {
        selectedClassId?.let {
            viewModel.loadSubjectsForClass(it)
            viewModel.loadStudentsForClass(it)
        }
    }
    LaunchedEffect(subjects) {
        if (selectedSubjectId == null && subjects.isNotEmpty()) {
            selectedSubjectId = subjects.first().id
        }
    }

    LaunchedEffect(selectedClassId, selectedSubjectId, term) {
        val cid = selectedClassId ?: return@LaunchedEffect
        val sid = selectedSubjectId ?: return@LaunchedEffect
        viewModel.loadGradebook(cid, sid, term, academicYear)
    }

    val selectedClass = classes.firstOrNull { it.id == selectedClassId }
    val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId }

    val assessmentsByStudent = remember(classAssessments) { classAssessments.associateBy { it.studentId } }
    val enteredCount = students.count { assessmentsByStudent[it.id]?.subjectAverage != null }
    val completeAverages = classAssessments.mapNotNull { it.subjectAverage }
    val classAverage = completeAverages.takeIf { it.isNotEmpty() }?.average()
    val passRate = completeAverages.takeIf { it.isNotEmpty() }?.let { list ->
        list.count { isPassing(it, selectedSubject?.passingGrade ?: 10.0) } * 100.0 / list.size
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onBack != null) {
            ElTopBar(
                title = "Carnet de notes — ${selectedClass?.name ?: ""}",
                subtitle = "${selectedSubject?.name ?: ""} • $term",
                onBack = onBack,
            )
        }

        // Trimester Selector Card
        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ElSectionHeader(title = "Trimestre d'évaluation")
                    Text(
                        text = "Année $academicYear",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("T1", "T2", "T3").forEach { t ->
                        ElTag(
                            text = "Trimestre $t",
                            color = PrimaryBlue,
                            selected = t == term,
                            onClick = { term = t },
                        )
                    }
                }
            }
        }

        if (classes.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Class,
                title = "Aucune classe",
                message = "Aucune classe n'est disponible pour la saisie des notes.",
            )
            return@Column
        }

        // Class & Subject Selectors
        ElDropdown(
            label = "Classe",
            selectedValue = selectedClass?.name ?: "",
            options = classes.map { it.name },
            onSelected = { name ->
                selectedClassId = classes.firstOrNull { it.name == name }?.id
                selectedSubjectId = null
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (subjects.isNotEmpty()) {
            ElDropdown(
                label = "Matière",
                selectedValue = selectedSubject?.name ?: "",
                options = subjects.map { "${it.name} (Coef ${it.coefficient})" },
                onSelected = { label ->
                    selectedSubjectId = subjects.firstOrNull { "${it.name} (Coef ${it.coefficient})" == label }?.id
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Live Class Statistics Card
        if (selectedSubject != null && students.isNotEmpty()) {
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ElSectionHeader(
                        title = "Statistiques de classe",
                        subtitle = "${selectedSubject.name} • Coef ${selectedSubject.coefficient}",
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$enteredCount / ${students.size}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = PrimaryBlue,
                            )
                            Text(
                                text = "Saisies complètes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = classAverage?.let { "%.2f / 20".format(it) } ?: "—",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = if ((classAverage ?: 0.0) >= 10.0) SuccessGreen else DangerRed,
                            )
                            Text(
                                text = "Moyenne générale",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = passRate?.let { "%.0f%%".format(it) } ?: "—",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = SuccessGreen,
                            )
                            Text(
                                text = "Taux de réussite",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    ElProgressBar(
                        progress = if (students.isEmpty()) 0f else enteredCount.toFloat() / students.size,
                    )
                }
            }
        }

        message?.let {
            ElAlertBanner(
                message = it,
                severity = if (it.contains("succès") || it.contains("enregistrée", ignoreCase = true)) ElAlertSeverity.Success else ElAlertSeverity.Warning,
            )
        }

        // Student Roster with Marks
        if (students.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Grade,
                title = "Aucun élève",
                message = "Aucun élève trouvé dans cette classe.",
            )
        } else if (selectedSubject != null) {
            ElSectionHeader(
                title = "Liste des élèves (${students.size})",
                subtitle = "Touchez un élève pour saisir ses notes",
            )

            students.forEach { student ->
                val assessment = assessmentsByStudent[student.id]
                val avg = assessment?.subjectAverage

                ElCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingStudent = student },
                    accent = when {
                        avg == null -> null
                        avg >= 10.0 -> SuccessGreen
                        else -> DangerRed
                    },
                    compact = true,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ElAvatar(initials = student.fullName, size = 40)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = student.fullName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Text(
                                    text = "D1: ${assessment?.devoir1?.let { "%.1f".format(it) } ?: "—"}  •  " +
                                        "D2: ${assessment?.devoir2?.let { "%.1f".format(it) } ?: "—"}  •  " +
                                        "Ex: ${assessment?.examen?.let { "%.1f".format(it) } ?: "—"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            if (avg != null) {
                                Text(
                                    text = "%.2f".format(avg),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (avg >= 10.0) SuccessGreen else DangerRed,
                                )
                                Text(
                                    text = if (avg >= 10.0) "Admis" else "Non acquis",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (avg >= 10.0) SuccessGreen else DangerRed,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Saisir",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Quick Student Grade Dialog ───────────────────────────────────────────
    editingStudent?.let { student ->
        val currentAssessment = assessmentsByStudent[student.id]
        var d1Text by remember(student.id) { mutableStateOf(currentAssessment?.devoir1?.toString() ?: "") }
        var d2Text by remember(student.id) { mutableStateOf(currentAssessment?.devoir2?.toString() ?: "") }
        var exText by remember(student.id) { mutableStateOf(currentAssessment?.examen?.toString() ?: "") }

        val d1Val = d1Text.toDoubleOrNull()
        val d2Val = d2Text.toDoubleOrNull()
        val exVal = exText.toDoubleOrNull()

        val previewAvg = computeSubjectAverage(
            d1Val, d2Val, exVal,
            selectedSubject?.coefficientDevoir1 ?: 1.0,
            selectedSubject?.coefficientDevoir2 ?: 1.0,
            selectedSubject?.coefficientExamen ?: 2.0,
        )

        AlertDialog(
            onDismissRequest = { editingStudent = null },
            title = {
                Column {
                    Text(
                        text = student.fullName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "${selectedSubject?.name ?: ""} • $term",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = d1Text,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull()?.let { v -> v in 0.0..20.0 } == true) d1Text = it },
                        label = { Text("Devoir 1 (/20)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = d2Text,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull()?.let { v -> v in 0.0..20.0 } == true) d2Text = it },
                        label = { Text("Devoir 2 (/20)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = exText,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull()?.let { v -> v in 0.0..20.0 } == true) exText = it },
                        label = { Text("Examen (/20) • Coef 2") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Calculated Average Preview Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    previewAvg == null -> MaterialTheme.colorScheme.surfaceVariant
                                    previewAvg >= 10.0 -> SuccessGreen.copy(alpha = 0.15f)
                                    else -> DangerRed.copy(alpha = 0.15f)
                                },
                            )
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Moyenne calculée", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = previewAvg?.let { "%.2f / 20".format(it) } ?: "— (3 notes requises)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = when {
                                    previewAvg == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    previewAvg >= 10.0 -> SuccessGreen
                                    else -> DangerRed
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cid = selectedClassId ?: return@TextButton
                        val sid = selectedSubjectId ?: return@TextButton
                        viewModel.enterGrade(
                            studentId = student.id,
                            subjectId = sid,
                            classId = cid,
                            term = term,
                            academicYear = academicYear,
                            devoir1 = d1Val,
                            devoir2 = d2Val,
                            examen = exVal,
                            coefficient = selectedSubject?.coefficient ?: 1.0,
                            actorId = session.userId,
                            actorName = session.displayName,
                            onSuccess = {
                                val currentIdx = students.indexOfFirst { it.id == student.id }
                                if (currentIdx in 0 until students.lastIndex) {
                                    editingStudent = students[currentIdx + 1]
                                } else {
                                    editingStudent = null
                                }
                            },
                        )
                    },
                    enabled = !busy && (d1Val != null || d2Val != null || exVal != null),
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingStudent = null }) {
                    Text("Fermer")
                }
            },
        )
    }
}