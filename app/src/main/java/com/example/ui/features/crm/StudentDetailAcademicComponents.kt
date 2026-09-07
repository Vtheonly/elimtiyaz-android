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
import com.example.ui.components.ElAlertBanner
import com.example.ui.components.ElAlertSeverity
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElProgressBar
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
import com.example.ui.util.PhoneUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

internal fun mentionFor(gpa: Double?): String = when {
    gpa == null -> "En attente des examens"
    gpa >= 16 -> "Très Bien"
    gpa >= 14 -> "Bien"
    gpa >= 12 -> "Assez Bien"
    gpa >= 10 -> "Passable"
    else -> "Insuffisant"
}

@Composable
internal fun GradeStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = PrimaryBlue)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SubjectHighlightCard(
    label: String,
    subjectName: String,
    average: Double,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    ElCard(modifier = modifier, compact = true) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                subjectName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "%.2f / 20".format(average),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
        }
    }
}

@Composable
internal fun SubjectGradeCard(
    subjectName: String,
    coefficient: Double,
    isExtracurricular: Boolean,
    devoir1: Double?,
    devoir2: Double?,
    examen: Double?,
    average: Double?,
    passing: Boolean,
    passingGrade: Double,
    enteredAt: String,
) {
    val avgColor = when {
        average == null -> WarmGold
        passing -> SuccessGreen
        else -> DangerRed
    }
    ElCard(modifier = Modifier.fillMaxWidth(), compact = true, accent = avgColor) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        subjectName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    if (isExtracurricular) {
                        ElTag(text = "Hors programme", color = WarmGold)
                    }
                }
                Text(
                    text = average?.let { "%.2f".format(it) } ?: "—",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = avgColor,
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MarkPill("D1", devoir1)
                MarkPill("D2", devoir2)
                MarkPill("Ex ×2", examen)
                Spacer(Modifier.weight(1f))
                Text(
                    "Coef ${if (coefficient == coefficient.toLong().toDouble()) "${coefficient.toLong()}" else "$coefficient"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (average != null) {
                Spacer(Modifier.height(8.dp))
                ElProgressBar(
                    progress = (average / 20.0).toFloat(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (passing) "Acquis (≥ $passingGrade/20)" else "À renforcer (< $passingGrade/20)",
                    style = MaterialTheme.typography.labelSmall,
                    color = avgColor,
                )
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Moyenne à paraître — les 3 notes doivent être saisies (formule (D1 + D2 + 2×Ex) / 4)",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarmGold,
                )
            }

            if (enteredAt.isNotBlank()) {
                Text(
                    "Saisie le ${enteredAt.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun MarkPill(label: String, value: Double?) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (value != null) PrimaryBlue.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            "$label ${value?.let { if (it == it.toLong().toDouble()) "${it.toLong()}" else "$it" } ?: "—"}",
            style = MaterialTheme.typography.labelSmall,
            color = if (value != null) PrimaryBlue else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
internal fun AcademicHistoryTab(
    history: List<AcademicYearHistory>,
    subjects: List<com.example.domain.model.Subject>,
    currentYear: String,
) {
    val subjectById = subjects.associateBy { it.id }

    if (history.isEmpty()) {
        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Historique académique", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Aucun historique pour cet élève — les performances par trimestre apparaîtront ici au fil des années, avec le détail des bulletins et les décisions de promotion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Parcours complet — ${history.size} année(s)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Historique permanent en lecture seule : les années clôturées ne peuvent pas être modifiées (toute correction passe par une nouvelle entrée journalisée).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(history, key = { it.academicYear }) { year ->
            AcademicYearCard(year = year, subjectById = subjectById, currentYear = currentYear)
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun AcademicYearCard(
    year: AcademicYearHistory,
    subjectById: Map<String, com.example.domain.model.Subject>,
    currentYear: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val isCurrent = year.academicYear == currentYear
    val gpaColor = when {
        year.yearlyGpa == null -> WarmGold
        year.yearlyGpaSafe() >= 10.0 -> SuccessGreen
        else -> DangerRed
    }

    ElCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        accent = gpaColor,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Année ${year.academicYear}" + (if (isCurrent) " (en cours)" else ""),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        listOfNotNull(
                            year.gradeLevel?.let { "Niveau ${it.uppercase()}" },
                            year.attendanceRate?.let { "Présence %.0f%%".format(it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        year.yearlyGpa?.let { "%.2f / 20".format(it) } ?: "—",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = gpaColor,
                    )
                    Text(
                        if (expanded) "Bulletins ▲" else "Bulletins ▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryBlue,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                year.termGpas.forEach { (term, gpa) ->
                    val tColor = when {
                        gpa == null -> MaterialTheme.colorScheme.outline
                        gpa >= 10.0 -> SuccessGreen
                        else -> DangerRed
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tColor.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "$term ${gpa?.let { "%.2f".format(it) } ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            year.promotionOutcome?.let { outcome ->
                when (outcome) {
                    "promoted" -> ElTag(text = "APPROVED_FOR_PROMOTION", color = SuccessGreen)
                    "graduated" -> ElTag(text = "DIPLÔMÉ", color = PrimaryBlue)
                    else -> ElTag(text = "RETAINED_SAME_YEAR", color = DangerRed)
                }
            } ?: run {
                if (!isCurrent) ElTag(text = "Année en attente de clôture", color = WarmGold)
            }

            if (expanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Bulletin complet — ${year.academicYear}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
                val bySubject = year.assessments.groupBy { it.subjectId }
                bySubject.forEach { (subjectId, rows) ->
                    val subject = subjectById[subjectId]
                    val name = subject?.name ?: subjectId
                    val last = rows.maxByOrNull { it.term } ?: rows.first()
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                name + if (last.isExtracurricular) " (hors programme)" else "",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                last.subjectAverage?.let { "%.2f".format(it) } ?: "—",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (last.isExtracurricular) WarmGold else gpaColor,
                            )
                        }
                        Text(
                            "D1 ${last.devoir1 ?: "—"} · D2 ${last.devoir2 ?: "—"} · Examen ${last.examen ?: "—"} · Coef ${last.coefficient}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (year.isArchived) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Année clôturée — lecture seule (append-only).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

private fun AcademicYearHistory.yearlyGpaSafe(): Double = yearlyGpa ?: 0.0
