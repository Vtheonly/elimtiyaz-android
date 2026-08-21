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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Whatsapp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElCard
import com.example.ui.components.ElInfoRow
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
import kotlinx.coroutines.launch

data class AttendanceStats(
    val presentCount: Int = 0,
    val unexcusedCount: Int = 0,
    val excusedCount: Int = 0,
    val lateCount: Int = 0,
    val totalCount: Int = 0,
    val rate: Double = 100.0,
)

@HiltViewModel
class StudentDetailViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val parentRepository: ParentRepository,
    private val gradeRepository: GradeRepository,
    private val attendanceRepository: AttendanceRepository,
    private val installmentRepository: InstallmentRepository,
    private val paymentRepository: PaymentRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    private val _student = MutableStateFlow<Student?>(null)
    val student: StateFlow<Student?> = _student.asStateFlow()

    private val _parent = MutableStateFlow<Parent?>(null)
    val parent: StateFlow<Parent?> = _parent.asStateFlow()

    private val _siblings = MutableStateFlow<List<Student>>(emptyList())
    val siblings: StateFlow<List<Student>> = _siblings.asStateFlow()

    private val _assessments = MutableStateFlow<List<Assessment>>(emptyList())
    val assessments: StateFlow<List<Assessment>> = _assessments.asStateFlow()

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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(studentId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            studentRepository.observeById(studentId).collect { s ->
                _student.value = s
                if (s != null) {
                    parentRepository.observeById(s.parentId).collect { p -> _parent.value = p }
                    studentRepository.observeByParent(s.parentId).collect { sibs ->
                        _siblings.value = sibs.filter { it.id != studentId }
                    }
                    when (val result = ledgerRepository.summary(s.parentId)) {
                        is Result.Ok -> _familySummary.value = result.value
                        is Result.Err -> _error.value = result.error.userMessage
                    }

                    gradeRepository.observeForStudent(studentId, "T1", "2026-2027").collect { list ->
                        _assessments.value = list
                    }

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

                    installmentRepository.observeByStudent(studentId).collect {
                        _installments.value = it
                    }
                    paymentRepository.observeByStudent(studentId).collect {
                        _payments.value = it
                    }
                }
                _isLoading.value = false
            }
        }
    }
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
    val context = LocalContext.current
    val tokens = elDesignTokens()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Profil & Famille", "Notes & Bulletins", "Présences & Retards", "Finances & Échéances")

    ElScaffold(
        topBar = { ElTopBar(title = student?.fullName ?: "Dossier Élève", onBack = onBack) },
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
                        val gpa = computeOverallGpa(assessments)
                        ElCard(modifier = Modifier.fillMaxWidth(), accent = if ((gpa ?: 0.0) >= 10.0) SuccessGreen else DangerRed) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Moyenne Générale du Trimestre", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = gpa?.let { "%.2f / 20".format(it) } ?: "En attente des examens",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if ((gpa ?: 0.0) >= 10.0) SuccessGreen else DangerRed,
                                )
                                Text(
                                    text = if ((gpa ?: 0.0) >= 10.0) "Admis • Résultats satisfaisants" else "Moyenne inférieure au seuil de passage",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
                        items(assessments) { a ->
                            ElCard(modifier = Modifier.fillMaxWidth(), compact = true) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(a.subjectId.uppercase(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        Text(
                                            text = a.subjectAverage?.let { "Moy: %.2f".format(it) } ?: "En cours",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if ((a.subjectAverage ?: 0.0) >= 10.0) SuccessGreen else DangerRed,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Devoir 1: ${a.devoir1 ?: "—"}  •  Devoir 2: ${a.devoir2 ?: "—"}  •  Examen: ${a.examen ?: "—"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text("Coefficient : ${a.coefficient}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        val studentDue = familySummary?.totalCharged ?: 0L
                        val studentPaid = familySummary?.totalPaid ?: 0L
                        val studentRest = (familySummary?.totalOutstanding ?: 0L).coerceAtLeast(0L)

                        ElCard(modifier = Modifier.fillMaxWidth(), accent = if (studentRest > 0) DangerRed else SuccessGreen) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                ElSectionHeader(title = "État financier de l'élève")
                                Spacer(Modifier.height(6.dp))
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
            }
        }
    }
}