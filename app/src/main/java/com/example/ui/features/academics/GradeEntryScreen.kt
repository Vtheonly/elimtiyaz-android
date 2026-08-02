package com.example.ui.features.academics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Class
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.ui.components.ElAlertBanner
import com.example.ui.components.ElAlertSeverity
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElDropdown
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElGradientStatCard
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTextField
import com.example.ui.theme.PrimaryBlue
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun GradeEntryScreen(
    session: Session,
    onNavigateToGradeEntry: (String) -> Unit = {},
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
