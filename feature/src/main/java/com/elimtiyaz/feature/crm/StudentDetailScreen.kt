package com.elimtiyaz.feature.crm

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.AcademicLevel
import com.elimtiyaz.core.common.AttendanceStatus
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.PaymentMethod
import com.elimtiyaz.core.common.PaymentStatus
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.TenancyTier
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.EmptyState
import com.elimtiyaz.core.ui.ErrorState
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.Assessment
import com.elimtiyaz.domain.model.AttendanceRecord
import com.elimtiyaz.domain.model.AcademicHistoryEntry
import com.elimtiyaz.domain.model.Payment
import com.elimtiyaz.domain.model.PromotionDecision
import com.elimtiyaz.domain.model.Student
import kotlinx.coroutines.launch

/**
 * StudentDetailScreen — full profile of a student (Route.StudentDetail).
 *
 * 4-tab layout: Infos | Académique | Présences | Paiements.
 * TopAppBar actions: back / edit (gated by EditStudent) / promote (gated by
 * PromoteStudent).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    nav: NavController,
    vm: StudentDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val canEdit = session?.can(Permission.EditStudent) ?: false
    val canPromote = session?.can(Permission.PromoteStudent) ?: false

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showPromoteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.student?.code ?: "Élève") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (canEdit) {
                        IconButton(onClick = {
                            scope.launch {
                                snackbar.showSnackbar("La modification de l'élève sera disponible dans une prochaine version.")
                            }
                        }) { Icon(Icons.Outlined.Edit, contentDescription = "Modifier") }
                    }
                    if (canPromote) {
                        IconButton(onClick = { showPromoteDialog = true }) {
                            Icon(Icons.Outlined.TrendingUp, contentDescription = "Promouvoir")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(inner))
            state.error != null -> ErrorState(state.error!!, onRetry = vm::reload, modifier = Modifier.padding(inner))
            state.student == null -> EmptyState(
                title = "Élève introuvable",
                description = "Cet élève n'existe plus ou l'identifiant est invalide.",
                modifier = Modifier.padding(inner),
            )
            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(inner)) {
                    StudentHeader(student = state.student!!, onParentClick = { parentId ->
                        nav.navigate(Route.ParentDetail.build(parentId))
                    })
                    HorizontalDivider()
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("Infos", modifier = Modifier.padding(ElimtiyazSpacing.x3)) }
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("Académique", modifier = Modifier.padding(ElimtiyazSpacing.x3)) }
                        Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) { Text("Présences", modifier = Modifier.padding(ElimtiyazSpacing.x3)) }
                        Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }) { Text("Paiements", modifier = Modifier.padding(ElimtiyazSpacing.x3)) }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (selectedTab) {
                            0 -> InfosTab(state.student!!, onCallParent = {
                                val phone = state.student?.parent?.phone ?: return@InfosTab
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                context.startActivity(intent)
                            })
                            1 -> AcademicTab(
                                history = state.student?.academicHistory.orEmpty(),
                                assessments = state.assessments,
                            )
                            2 -> AttendanceTab(records = state.attendance, summary = AttendanceSummary(
                                present = state.presentCount,
                                excused = state.excusedCount,
                                unexcused = state.unexcusedCount,
                                late = state.lateCount,
                            ))
                            3 -> PaymentsTab(payments = state.payments)
                        }
                    }
                }
            }
        }
    }

    if (showPromoteDialog && state.student != null) {
        AlertDialog(
            onDismissRequest = { showPromoteDialog = false },
            title = { Text("Promotion de l'élève") },
            text = {
                val name = state.student!!.let { Formatters.fullName(it.firstName, it.lastName) }
                Text("Confirmer la promotion de $name vers l'année scolaire suivante ?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showPromoteDialog = false
                    vm.promote { ok, msg ->
                        scope.launch {
                            snackbar.showSnackbar(
                                if (ok) "Élève promu avec succès." else (msg ?: "Erreur lors de la promotion."),
                            )
                        }
                    }
                }) { Text("Promouvoir") }
            },
            dismissButton = { TextButton(onClick = { showPromoteDialog = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun StudentHeader(student: Student, onParentClick: (String) -> Unit) {
    val level = AcademicLevel.from(student.level)
    val tier = TenancyTier.from(student.transportTier)
    val parentName = student.parent?.let { Formatters.fullName(it.firstName, it.lastName) } ?: "—"

    ElImtiyazCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ElimtiyazSpacing.x6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvatarCircle(
                initial = Formatters.initials(student.firstName, student.lastName),
                size = 80,
                backgroundColor = ElimtiyazColors.DeepBlue,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Text(
                text = Formatters.fullName(student.firstName, student.lastName),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = student.code,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
            HeaderRow("Date de naissance", CrmFormat.date(student.birthDate))
            HeaderRow("Niveau", "${level?.displayFr ?: student.level} — Année ${student.gradeYear}")
            HeaderRow("Classe", student.classId ?: "—")
            HeaderRow("Transport", tier?.displayFr ?: student.transportTier ?: "—")
            HeaderRow("Statut", student.status.name)
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            // Tappable parent row.
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = { student.parent?.id?.let(onParentClick) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(ElimtiyazSpacing.x3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.School, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(ElimtiyazSpacing.x2))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Parent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(parentName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ElimtiyazSpacing.x1),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InfosTab(student: Student, onCallParent: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(ElimtiyazSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
    ) {
        item { SectionTitle("Informations générales") }
        item {
            ElImtiyazCard {
                Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
                    HeaderRow("Notes médicales", student.medicalNotes?.ifBlank { null } ?: "Aucune")
                    HeaderRow("Tier de transport", student.transportTier ?: "—")
                    HeaderRow("Photo", student.photoUrl ?: "Non disponible")
                }
            }
        }
        item { SectionTitle("Contact parent") }
        item {
            ElImtiyazCard(onClick = onCallParent) {
                Row(
                    modifier = Modifier.padding(ElimtiyazSpacing.x4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(ElimtiyazSpacing.x3))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = student.parent?.let { Formatters.fullName(it.firstName, it.lastName) } ?: "Parent",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = student.parent?.phone ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AcademicTab(
    history: List<AcademicHistoryEntry>,
    assessments: List<Assessment>,
) {
    LazyColumn(
        contentPadding = PaddingValues(ElimtiyazSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
    ) {
        item { SectionTitle("Historique académique") }
        if (history.isEmpty()) {
            item { EmptyItem("Aucun historique académique enregistré.") }
        } else {
            items(history, key = { it.academicYear }) { entry -> HistoryTimelineRow(entry) }
        }
        item { HorizontalDivider() }
        item { SectionTitle("Notes du trimestre en cours") }
        if (assessments.isEmpty()) {
            item { EmptyItem("Aucune note saisie pour le trimestre courant.") }
        } else {
            items(assessments, key = { it.id }) { assessment -> GradeRow(assessment) }
        }
    }
}

@Composable
private fun HistoryTimelineRow(entry: AcademicHistoryEntry) {
    val decision = when (entry.decision) {
        PromotionDecision.Promoted -> StatusTone.Success
        PromotionDecision.Graduated -> StatusTone.Success
        PromotionDecision.Repeated -> StatusTone.Warning
        PromotionDecision.Transferred -> StatusTone.Info
    }
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.academicYear,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusChip(label = entry.decision.name, tone = decision)
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(
                text = "${AcademicLevel.from(entry.level)?.displayFr ?: entry.level} — Année ${entry.gradeYear}" +
                    (entry.className?.let { " • $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = ElimtiyazSpacing.x2), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Moyenne", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${entry.gpa} / 20", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            }
            entry.rank?.let { rank ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Rang", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$rank", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                }
            }
            entry.narrative?.let { narrative ->
                Spacer(Modifier.height(ElimtiyazSpacing.x2))
                Text(narrative, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GradeRow(assessment: Assessment) {
    val avg = assessment.subjectAverage
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Matière ${assessment.subjectId.take(6)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Coef. ${assessment.coefficient}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GradeCell("D1", assessment.devoir1)
                GradeCell("D2", assessment.devoir2)
                GradeCell("Examen", assessment.examen)
                GradeCell("Moy.", avg)
            }
        }
    }
}

@Composable
private fun GradeCell(label: String, value: Double?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value?.let { String.format(java.util.Locale.FRANCE, "%.2f", it) } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AttendanceTab(records: List<AttendanceRecord>, summary: AttendanceSummary) {
    LazyColumn(
        contentPadding = PaddingValues(ElimtiyazSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
    ) {
        item { SectionTitle("Résumé du mois") }
        item {
            ElImtiyazCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x4),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SummaryCell("Présent", summary.present, StatusTone.Success)
                    SummaryCell("Excusé", summary.excused, StatusTone.Info)
                    SummaryCell("Non excusé", summary.unexcused, StatusTone.Danger)
                    SummaryCell("Retard", summary.late, StatusTone.Warning)
                }
            }
        }
        item { SectionTitle("Relevé récent") }
        if (records.isEmpty()) {
            item { EmptyItem("Aucune présence enregistrée ce mois.") }
        } else {
            items(records, key = { it.id }) { record -> AttendanceRow(record) }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: Int, tone: StatusTone) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(ElimtiyazSpacing.x1))
        StatusChip(label = label, tone = tone)
    }
}

@Composable
private fun AttendanceRow(record: AttendanceRecord) {
    val status = AttendanceStatus.from(record.status)
    val tone = when (status) {
        AttendanceStatus.Present -> StatusTone.Success
        AttendanceStatus.AbsentExcused -> StatusTone.Info
        AttendanceStatus.AbsentUnexcused -> StatusTone.Danger
        AttendanceStatus.Late -> StatusTone.Warning
        null -> StatusTone.Neutral
    }
    ElImtiyazCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x4),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = CrmFormat.date(record.date),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Session: ${record.session.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                record.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            StatusChip(label = status?.displayFr ?: record.status, tone = tone)
        }
    }
}

@Composable
private fun PaymentsTab(payments: List<Payment>) {
    LazyColumn(
        contentPadding = PaddingValues(ElimtiyazSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
    ) {
        item { SectionTitle("Paiements de l'élève") }
        if (payments.isEmpty()) {
            item { EmptyItem("Aucun paiement enregistré pour cet élève.") }
        } else {
            items(payments, key = { it.id }) { payment -> StudentPaymentRow(payment) }
        }
    }
}

@Composable
private fun StudentPaymentRow(payment: Payment) {
    val method = PaymentMethod.from(payment.method)
    val status = PaymentStatus.from(payment.status)
    val tone = when (status) {
        PaymentStatus.Paid -> StatusTone.Success
        PaymentStatus.Partial -> StatusTone.Warning
        PaymentStatus.Overdue -> StatusTone.Danger
        PaymentStatus.Pending -> StatusTone.Info
        else -> StatusTone.Neutral
    }
    ElImtiyazCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x4),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = CrmFormat.currency(payment.amount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${method?.displayFr ?: payment.method} • ${CrmFormat.date(payment.collectedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = payment.receiptNumber,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(label = status?.displayFr ?: payment.status, tone = tone)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun EmptyItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Bundle of attendance counts passed into [AttendanceTab]. */
private data class AttendanceSummary(
    val present: Int,
    val excused: Int,
    val unexcused: Int,
    val late: Int,
)
