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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.core.validateScore
import com.example.domain.model.Assessment
import com.example.domain.model.Subject
import com.example.ui.components.ElAlertBanner
import com.example.ui.components.ElAlertSeverity
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElDropdown
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElProgressBar
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTextField
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold
import java.time.LocalDate

/**
 * Saisie des notes — full class gradebook.
 *
 * ENRICHED (thin UI): previously a single-dropdown form (one student at a
 * time, free-text term, no class overview). Now a real "carnet de notes":
 *  - Term segmented selector (T1 / T2 / T3 — the canonical values).
 *  - Class + subject selectors (subject shows its coefficient).
 *  - Live class statistics: completion, class average, pass rate (against the
 *    subject's passing grade), min / max.
 *  - The FULL student roster with each student's existing marks and computed
 *    average chip — tap a student to edit inline.
 *  - The editor shows the CANONICAL [computeSubjectAverage] preview (the
 *    server trigger remains the authority) with the exact rule: the average
 *    only exists when ALL THREE marks are entered.
 */
@Composable
fun GradeEntryScreen(
    session: Session,
    onNavigateToGradeEntry: (String) -> Unit = {},
    /** Pre-selected class when opened standalone from ClassDetail. */
    initialClassId: String? = null,
    /** Back affordance when pushed as a standalone route (hidden when embedded in the hub). */
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
    var selectedStudentId by remember { mutableStateOf<String?>(null) }
    var term by remember { mutableStateOf("T1") }
    val academicYear = remember {
        val now = LocalDate.now()
        if (now.monthValue >= 9) "${now.year}-${now.year + 1}" else "${now.year - 1}-${now.year}"
    }

    var devoir1Text by remember { mutableStateOf("") }
    var devoir2Text by remember { mutableStateOf("") }
    var examenText by remember { mutableStateOf("") }
    // Whether an assessment already exists for the current selection — shown
    // as an explicit "this will replace it" hint.
    var hasExistingMark by remember { mutableStateOf(false) }

    val d1 = devoir1Text.toDoubleOrNull()
    val d2 = devoir2Text.toDoubleOrNull()
    val ex = examenText.toDoubleOrNull()
    // CANONICAL preview — the exact engine used by the persistence layer
    // (null while any mark is missing; examen weighted ×2; half-up rounding
    // at 2 decimals). The previous inline `(d1+d2+2*ex)/4` formula diverged
    // from the SQL trigger at .xx5 boundaries.
    val subjectAverage = computeSubjectAverage(d1, d2, ex)

    LaunchedEffect(classes) {
        if (selectedClassId == null && classes.isNotEmpty()) selectedClassId = classes.first().id
    }
    LaunchedEffect(selectedClassId) {
        selectedClassId?.let {
            viewModel.loadSubjectsForClass(it)
            viewModel.loadStudentsForClass(it)
        }
    }
    LaunchedEffect(subjects) {
        if (selectedSubjectId == null && subjects.isNotEmpty()) selectedSubjectId = subjects.first().id
    }
    LaunchedEffect(students) {
        if (selectedStudentId == null && students.isNotEmpty()) selectedStudentId = students.first().id
    }

    // Live gradebook for the selected (class, subject, term).
    LaunchedEffect(selectedClassId, selectedSubjectId, term) {
        val cid = selectedClassId ?: return@LaunchedEffect
        val sid = selectedSubjectId ?: return@LaunchedEffect
        viewModel.loadGradebook(cid, sid, term, academicYear)
    }

    // FIX (blind overwrite): reset the mark fields whenever the student,
    // subject, or term changes — previously student A's marks stayed in the
    // fields after switching to student B, and one tap on "Enregistrer"
    // silently overwrote B's assessment with A's marks.
    LaunchedEffect(selectedStudentId, selectedSubjectId, term) {
        devoir1Text = ""
        devoir2Text = ""
        examenText = ""
        // Pre-load the existing assessment so the edit is NOT blind.
        val sid = selectedStudentId ?: return@LaunchedEffect
        val subId = selectedSubjectId ?: return@LaunchedEffect
        viewModel.loadExistingMark(sid, subId, term, academicYear) { existing ->
            hasExistingMark = existing != null
            devoir1Text = existing?.devoir1?.let { trimZero(it) } ?: ""
            devoir2Text = existing?.devoir2?.let { trimZero(it) } ?: ""
            examenText = existing?.examen?.let { trimZero(it) } ?: ""
        }
    }

    val selectedClass = classes.firstOrNull { it.id == selectedClassId }
    val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId }
    val selectedStudent = students.firstOrNull { it.id == selectedStudentId }

    // ── Class-level statistics (canonical, derived from persisted rows) ────
    val assessmentsByStudent = classAssessments.groupBy { it.studentId }
    val enteredCount = students.count { it.id in assessmentsByStudent }
    val completeAverages = classAssessments.mapNotNull { it.subjectAverage }
    val classAverage = completeAverages.takeIf { it.isNotEmpty() }?.average()
    val passRate = completeAverages.takeIf { it.isNotEmpty() }?.let { list ->
        list.count { isPassing(it, selectedSubject?.passingGrade ?: 10.0) } * 100.0 / list.size
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            com.example.ui.components.ElTopBar(
                title = "Saisie des notes — ${selectedClass?.name ?: "…"}",
                onBack = onBack,
            )
        }

        // ── Header: live canonical average of the mark being entered ──────
        ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Saisie des Notes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subjectAverage?.let { "%.2f / 20".format(it) } ?: "— / 20",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
                            color = PrimaryBlue,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(selectedSubject?.name ?: "Matière…", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        selectedSubject?.let { subj ->
                            Text(
                                "Coef %.1f • Seuil %.0f/20%s".format(
                                    subj.coefficient,
                                    subj.passingGrade,
                                    if (subj.isExtracurricular) " • Hors programme" else "",
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(term, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (classes.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Class,
                title = "Aucune classe",
                message = "Créez d'abord une classe pour saisir des notes.",
            )
            return@Column
        }

        // ── Selectors ──────────────────────────────────────────────────────
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
                options = subjects.map { "${it.name} (coef ${trimZero(it.coefficient)})" },
                onSelected = { label -> selectedSubjectId = subjects.first { "${it.name} (coef ${trimZero(it.coefficient)})" == label }.id },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Term selector (canonical T1 / T2 / T3) ─────────────────────────
        // FIX: was a free-text field — any typo (e.g. "t1", "trim1") silently
        // created a term nothing would ever read back.
        ElSectionHeader(title = "Période")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("T1", "T2", "T3").forEach { t ->
                ElTag(
                    text = t,
                    color = PrimaryBlue,
                    selected = t == term,
                    onClick = { term = t },
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Année $academicYear",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        // ── Class statistics ───────────────────────────────────────────────
        if (selectedSubject != null) {
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ElSectionHeader(title = "Bilan de la classe — ${selectedSubject.name}")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatBlock(
                            value = "$enteredCount/${students.size}",
                            label = "Saisies",
                            color = PrimaryBlue,
                        )
                        StatBlock(
                            value = classAverage?.let { "%.2f".format(it) } ?: "—",
                            label = "Moy. classe",
                            color = if ((classAverage ?: 0.0) >= (selectedSubject.passingGrade)) SuccessGreen else DangerRed,
                        )
                        StatBlock(
                            value = passRate?.let { "%.0f%%".format(it) } ?: "—",
                            label = "Réussite",
                            color = SuccessGreen,
                        )
                        StatBlock(
                            value = completeAverages.maxOrNull()?.let { "%.1f".format(it) } ?: "—",
                            label = "Max",
                            color = WarmGold,
                        )
                    }
                    ElProgressBar(
                        progress = if (students.isEmpty()) 0f else enteredCount.toFloat() / students.size,
                    )
                    Text(
                        "Saisies complétées pour ${selectedSubject.name} • ${term} • seuil de réussite ${trimZero(selectedSubject.passingGrade)}/20",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        message?.let {
            ElAlertBanner(
                message = it,
                severity = if (it.startsWith("Note sauvegardée")) ElAlertSeverity.Success else ElAlertSeverity.Warning,
            )
        }

        // ── Class roster ("carnet de notes") ────────────────────────────────
        if (selectedSubject != null && students.isNotEmpty()) {
            ElSectionHeader(title = "Carnet de notes — ${students.size} élève(s)")
            Text(
                "Touchez un élève pour saisir ou modifier ses notes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            students.forEach { student ->
                val existing = assessmentsByStudent[student.id]?.firstOrNull()
                StudentGradeRow(
                    studentName = student.fullName,
                    studentCode = student.code,
                    assessment = existing,
                    passingGrade = selectedSubject.passingGrade,
                    isSelected = student.id == selectedStudentId,
                    onClick = { selectedStudentId = student.id },
                )
            }
        }

        // ── Editor for the selected student ────────────────────────────────
        if (selectedStudent != null && selectedSubject != null) {
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ElSectionHeader(title = "Évaluation : ${selectedStudent.fullName}")
                    if (hasExistingMark) {
                        Text(
                            "Note existante chargée — l'enregistrement la remplacera.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarkField(
                            value = devoir1Text,
                            onValueChange = { devoir1Text = it },
                            label = "Devoir 1 (/20)",
                            modifier = Modifier.weight(1f),
                        )
                        MarkField(
                            value = devoir2Text,
                            onValueChange = { devoir2Text = it },
                            label = "Devoir 2 (/20)",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    MarkField(
                        value = examenText,
                        onValueChange = { examenText = it },
                        label = "Examen (/20 — coefficient 2)",
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
                            Text(
                                "Formule : (D1 + D2 + 2 × Examen) / 4 — arrondi au centième (demi-point haut)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // Canonical rule surfaced in the UI: no average
                            // until ALL THREE marks exist.
                            if (d1 == null || d2 == null || ex == null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "La moyenne n'est calculée que lorsque les 3 notes sont saisies (règle officielle).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarmGold,
                                )
                            } else if (!validateScore(d1) || !validateScore(d2) || !validateScore(ex)) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Chaque note doit être comprise entre 0 et 20.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DangerRed,
                                )
                            }
                        }
                    }

                    ElButton(
                        text = if (hasExistingMark) "Mettre à jour la note" else "Enregistrer la note",
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
                                coefficient = selectedSubject?.coefficient ?: 1.0,
                                actorId = session.userId,
                                actorName = session.displayName,
                            )
                        },
                        fullWidth = true,
                        enabled = !busy && selectedStudentId != null && selectedSubjectId != null,
                    )
                }
            }
        }
    }
}

/** Mark input with canonical 0–20 validation (mirrors [validateScore]). */
@Composable
private fun MarkField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    ElTextField(
        value = value,
        onValueChange = { raw ->
            // Accept empty, a lone decimal separator, or any prefix that
            // parses to a value within the canonical 0–20 range.
            if (raw.isEmpty() || raw == "." || raw == "," || raw.toDoubleOrNull()?.let { it in 0.0..20.0 } == true) {
                onValueChange(raw)
            }
        },
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** One roster row: student + existing marks + canonical average chip. */
@Composable
private fun StudentGradeRow(
    studentName: String,
    studentCode: String,
    assessment: Assessment?,
    passingGrade: Double,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val average = assessment?.subjectAverage
    val complete = average != null
    val passing = complete && isPassing(average, passingGrade)
    val hasAnyMark = assessment?.let { it.devoir1 != null || it.devoir2 != null || it.examen != null } == true

    ElCard(
        modifier = Modifier.fillMaxWidth(),
        compact = true,
        onClick = onClick,
        accent = when {
            isSelected -> PrimaryBlue
            complete && !passing -> DangerRed
            complete -> SuccessGreen
            hasAnyMark -> WarmGold
            else -> null
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(studentName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(studentCode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MarkChip("D1", assessment?.devoir1)
                    MarkChip("D2", assessment?.devoir2)
                    MarkChip("Ex ×2", assessment?.examen)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (complete) {
                    Text(
                        "%.2f".format(average),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (passing) SuccessGreen else DangerRed,
                    )
                    Text(
                        if (passing) "Acquis" else "À renforcer",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (passing) SuccessGreen else DangerRed,
                    )
                } else if (hasAnyMark) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = WarmGold, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Incomplète", style = MaterialTheme.typography.labelSmall, color = WarmGold)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("À saisir", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                if (isSelected) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue),
                    )
                }
            }
        }
    }
}

/** Small "D1 15,5"-style chip; greyed when the mark is not entered yet. */
@Composable
private fun MarkChip(label: String, value: Double?) {
    val text = value?.let { trimZero(it) } ?: "—"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (value != null) PrimaryBlue.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                )
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                "$label $text",
                style = MaterialTheme.typography.labelSmall,
                color = if (value != null) PrimaryBlue else MaterialTheme.colorScheme.outline,
                fontWeight = if (value != null) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Render a Double without a trailing ".0" (e.g. 15.0 -> "15"). */
private fun trimZero(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
