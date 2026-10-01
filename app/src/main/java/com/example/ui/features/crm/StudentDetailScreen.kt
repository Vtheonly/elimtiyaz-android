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
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.components.display.ElChipVariant
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElInfoRow
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.components.tabs.ElScrollableTabRow
import com.example.ui.util.PhoneUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@Composable
fun StudentDetailScreen(
    studentId: String,
    onBack: () -> Unit,
    onNavigateToCounter: (parentId: String?, studentId: String?) -> Unit = { _, _ -> },
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
    val familySummary by viewModel.familySummary.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val classAssessments by viewModel.classAssessments.collectAsState()
    val termGpas by viewModel.termGpas.collectAsState()
    val academicHistory by viewModel.academicHistory.collectAsState()
    val bulletinBusy by viewModel.bulletinBusy.collectAsState()
    val bulletinShareRequest by viewModel.bulletinShareRequest.collectAsState()
    val context = LocalContext.current
    val c = ElTheme.colors

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Profil & Famille", "Notes & Bulletins", "Présences & Retards", "Finances & Échéances", "Historique")
    var selectedTerm by remember { mutableStateOf("T1") }
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
                actions = {
                    if (student != null) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifier l'élève")
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            student?.let { s ->
                ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ElAvatar(initials = s.fullName, size = ElAvatarSize.L)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.fullName, style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text("Matricule: ${s.code} • ${s.gradeLevel.uppercase()}", style = ElTheme.typography.bodySmall, color = c.textSecondary)
                        }
                        ElTag(
                            text = if (s.status == "active") "Inscrit" else s.status,
                            tone = if (s.status == "active") ElTagTone.SUCCESS else ElTagTone.DANGER,
                        )
                    }
                }
            }

            ElScrollableTabRow(
                tabs = tabs,
                selectedIndex = selectedTab,
                onSelected = { selectedTab = it },
            )

            when (selectedTab) {
                0 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        student?.let { s ->
                            ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ElSectionHeader(title = "Renseignements Généraux")
                                    ElInfoRow(label = "Date de naissance", value = s.birthDate)
                                    ElInfoRow(label = "Cycle scolaire", value = s.level.replaceFirstChar { it.uppercase() })
                                    ElInfoRow(label = "Niveau d'études", value = s.gradeLevel.uppercase())
                                    ElInfoRow(label = "Date d'inscription", value = s.enrollmentDate.take(10))
                                    s.medicalNotes?.let { ElInfoRow(label = "Notes médicales", value = it, valueTint = c.danger) }
                                }
                            }
                        }
                    }

                    item {
                        parent?.let { p ->
                            ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ElSectionHeader(title = "Tuteur Légal / Parent")
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ElAvatar(initials = p.fullName, size = ElAvatarSize.M)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(p.fullName, style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            Text("Code: ${p.code} • ${p.phone}", style = ElTheme.typography.bodySmall, color = c.textSecondary)
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clip(com.example.ui.designsystem.theme.ElShapes.small)
                                                .background(c.success)
                                                .clickable { PhoneUtils.dial(context, p.phone) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Appeler", color = Color.White, style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clip(com.example.ui.designsystem.theme.ElShapes.small)
                                                .background(c.success)
                                                .clickable { PhoneUtils.openWhatsApp(context, p.whatsapp ?: p.phone) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Whatsapp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("WhatsApp", color = Color.White, style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (siblings.isNotEmpty()) {
                        item {
                            ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ElSectionHeader(title = "Fratrie inscrite (${siblings.size})")
                                    siblings.forEach { sib ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                ElAvatar(initials = sib.fullName, size = ElAvatarSize.S)
                                                Spacer(Modifier.width(8.dp))
                                                Text(sib.fullName, style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                            }
                                            ElTag(text = sib.gradeLevel.uppercase(), tone = ElTagTone.INFO)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }

                1 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("T1", "T2", "T3").forEach { t ->
                                ElChip(
                                    text = t,
                                    variant = ElChipVariant.FILTER,
                                    selected = t == selectedTerm,
                                    onClick = { selectedTerm = t },
                                )
                            }
                        }
                    }

                    val subjectById = subjects.associateBy { it.id }
                    val gpa = computeOverallGpa(assessments)
                    val mention = mentionFor(gpa)

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
                        ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Moyenne Générale — $selectedTerm", style = ElTheme.typography.labelSmall, color = c.textSecondary)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = gpa?.let { "%.2f / 20".format(it) } ?: "En attente des examens",
                                            style = ElTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if ((gpa ?: 0.0) >= 10.0) c.success else c.danger,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = when {
                                                gpa == null -> "Moyenne calculée dès que toutes les notes sont saisies"
                                                isPassing(gpa) -> "Admis • Mention $mention"
                                                else -> "Moyenne inférieure au seuil de passage • $mention"
                                            },
                                            style = ElTheme.typography.bodySmall,
                                            color = c.textSecondary,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            if (rankIdx >= 0) "${rankIdx + 1}${if (rankIdx + 1 == 1) "er" else "e"}" else "—",
                                            style = ElTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                            color = c.primary,
                                        )
                                        Text(
                                            if (classGpas.isNotEmpty()) "sur ${classGpas.size}" else "rang",
                                            style = ElTheme.typography.labelSmall,
                                            color = c.textSecondary,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                ElLinearProgress(progress = ((gpa ?: 0.0) / 20.0).toFloat())
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
                        if (termGpas.values.any { it != null }) {
                            ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Progression de l'année", style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
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
                                                    .clip(com.example.ui.designsystem.theme.ElShapes.small)
                                                    .background(
                                                        when {
                                                            isCurrent -> c.primary.copy(alpha = 0.12f)
                                                            termGpa != null && isPassing(termGpa) -> c.success.copy(alpha = 0.08f)
                                                            termGpa != null -> c.danger.copy(alpha = 0.08f)
                                                            else -> c.surfaceVariant.copy(alpha = 0.5f)
                                                        },
                                                    )
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                            ) {
                                                Text(
                                                    t,
                                                    style = ElTheme.typography.labelSmall,
                                                    color = if (isCurrent) c.primary else c.textSecondary,
                                                )
                                                Text(
                                                    termGpa?.let { "%.2f".format(it) } ?: "—",
                                                    style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = when {
                                                        termGpa == null -> c.textMuted
                                                        isPassing(termGpa) -> c.success
                                                        else -> c.danger
                                                    },
                                                )
                                                if (isCurrent) {
                                                    Text("Trimestre affiché", style = ElTheme.typography.labelSmall, color = c.primary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        if (bestSubject != null || weakestSubject != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                bestSubject?.let { (name, avg) ->
                                    SubjectHighlightCard(
                                        label = "Point fort",
                                        subjectName = name,
                                        average = avg,
                                        color = c.success,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                weakestSubject?.let { (name, avg) ->
                                    SubjectHighlightCard(
                                        label = "À renforcer",
                                        subjectName = name,
                                        average = avg,
                                        color = c.danger,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    if (assessments.isEmpty()) {
                        item {
                            ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                                Text("Aucune note saisie pour ce trimestre.", modifier = Modifier.padding(16.dp), style = ElTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        item {
                            Text(
                                "Détail par matière",
                                style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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

                        item {
                            ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        "Bulletin officiel — $selectedTerm",
                                        style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Génère le bulletin PDF (notes, coefficients, moyenne générale, mention, rang) prêt à partager avec la famille.",
                                        style = ElTheme.typography.bodySmall,
                                        color = c.textSecondary,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    com.example.ui.designsystem.components.button.ElButton(
                                        text = if (bulletinBusy) "Génération…" else "Générer le bulletin $selectedTerm",
                                        onClick = { viewModel.generateBulletin(studentId, selectedTerm) },
                                        fullWidth = true,
                                        enabled = !bulletinBusy && assessments.isNotEmpty(),
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }

                2 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                ElSectionHeader(title = "Bilan des présences")
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("%.1f%%".format(attendanceStats.rate), style = ElTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = c.success)
                                        Text("Assiduité", style = ElTheme.typography.labelSmall)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${attendanceStats.presentCount}", style = ElTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = c.primary)
                                        Text("Présents", style = ElTheme.typography.labelSmall)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${attendanceStats.unexcusedCount}", style = ElTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = c.danger)
                                        Text("Injustifiées", style = ElTheme.typography.labelSmall)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${attendanceStats.lateCount}", style = ElTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = c.warning)
                                        Text("Retards", style = ElTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    if (attendanceRecords.isEmpty()) {
                        item {
                            ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                                Text("Aucune absence ou retard enregistré.", modifier = Modifier.padding(16.dp), style = ElTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        items(attendanceRecords.take(30)) { rec ->
                            val (badgeTone, label) = when (rec.status) {
                                "present" -> ElTagTone.SUCCESS to "Présent"
                                "absent_unexcused" -> ElTagTone.DANGER to "Absence non justifiée"
                                "absent_excused" -> ElTagTone.WARNING to "Absence excusée"
                                "late" -> ElTagTone.INFO to "Retard"
                                else -> ElTagTone.NEUTRAL to rec.status
                            }

                            ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.COMPACT) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rec.date, style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        rec.note?.let {
                                            Text(it, style = ElTheme.typography.bodySmall, color = c.textSecondary)
                                        }
                                    }
                                    ElTag(text = label, tone = badgeTone)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }

                3 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        val studentDue = familySummary?.totalCharged ?: 0L
                        val studentPaid = familySummary?.totalPaid ?: 0L
                        val studentRest = (familySummary?.totalOutstanding ?: 0L).coerceAtLeast(0L)
                        val ownDue = installments.filter { it.status != com.example.core.PaymentStatus.CANCELLED }.sumOf { it.amountDue }
                        val ownPaid = installments.sumOf { it.amountPaid }

                        ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                ElSectionHeader(title = "Finances — part de cet élève")
                                Spacer(Modifier.height(6.dp))
                                ElInfoRow(label = "Tranches de cet élève (dû)", value = "${(ownDue / 100).formatDzd()} DZD")
                                ElInfoRow(label = "Tranches de cet élève (payé)", value = "${(ownPaid / 100).formatDzd()} DZD", valueTint = c.success)

                                Spacer(Modifier.height(10.dp))
                                ElButton(
                                    text = "Encaisser pour cet élève",
                                    onClick = { student?.let { s -> onNavigateToCounter(s.parentId, s.id) } },
                                    variant = com.example.ui.designsystem.components.button.ElButtonVariant.PRIMARY,
                                    icon = Icons.Default.Payments,
                                    fullWidth = true,
                                )

                                Spacer(Modifier.height(10.dp))
                                ElSectionHeader(title = "Solde familial consolidé (tous enfants)")
                                Spacer(Modifier.height(4.dp))
                                ElInfoRow(label = "Total scolarité & transport", value = "${(studentDue / 100).formatDzd()} DZD")
                                ElInfoRow(label = "Total réglé", value = "${(studentPaid / 100).formatDzd()} DZD", valueTint = c.success)
                                ElInfoRow(label = "Reste à payer", value = "${(studentRest / 100).formatDzd()} DZD", valueTint = if (studentRest > 0) c.danger else c.success)
                            }
                        }
                    }

                    if (installments.isNotEmpty()) {
                        item {
                            Text("Échéancier des tranches", style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        items(installments) { inst ->
                            val statusTone = when (inst.status.name) {
                                "PAID" -> ElTagTone.SUCCESS
                                "PARTIAL" -> ElTagTone.WARNING
                                "OVERDUE" -> ElTagTone.DANGER
                                else -> ElTagTone.INFO
                            }
                            ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.COMPACT) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(inst.label, style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        ElTag(text = inst.status.name, tone = statusTone)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    ElInfoRow(label = "Échéance", value = inst.dueDate)
                                    ElInfoRow(label = "Montant dû", value = "${(inst.amountDue / 100).formatDzd()} DZD")
                                    ElInfoRow(label = "Payé", value = "${(inst.amountPaid / 100).formatDzd()} DZD", valueTint = c.success)
                                }
                            }
                        }
                    }

                    if (payments.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(6.dp))
                            Text("Reçus d'encaissements", style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        items(payments) { p ->
                            ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.COMPACT) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(p.receiptNumber, style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("${p.method.name} • ${p.collectedAt.take(10)}", style = ElTheme.typography.bodySmall, color = c.textSecondary)
                                    }
                                    Text("+${(p.amount / 100).formatDzd()} DZD", style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = c.success)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }

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
                ElAlertBanner(message = it, severity = ElAlertSeverity.SUCCESS, title = "Modifications enregistrées")
            }
        }
    }

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
                    com.example.ui.designsystem.components.input.ElDropdown(
                        label = "Niveau scolaire",
                        selectedValue = gradeLevel,
                        options = GRADE_LEVEL_CODES.map { code ->
                            com.example.ui.designsystem.components.input.ElDropdownOption(value = code, label = code)
                        },
                        onSelected = { option -> gradeLevel = option.value },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(value = medicalNotes, onValueChange = { medicalNotes = it }, label = { Text("Notes médicales") }, modifier = Modifier.fillMaxWidth())
                    Text("Matricule ${s.code} — non modifiable.", style = ElTheme.typography.labelSmall, color = c.textSecondary)
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
