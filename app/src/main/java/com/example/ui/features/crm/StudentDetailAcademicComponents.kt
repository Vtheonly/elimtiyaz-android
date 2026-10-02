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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
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
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElAvatar
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
    val c = ElTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = c.primary)
        Text(label, style = ElTheme.typography.labelSmall, color = c.textSecondary)
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
    ElCard(modifier = modifier, size = ElCardSize.COMPACT) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = ElTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                subjectName,
                style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "%.2f / 20".format(average),
                style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
    val c = ElTheme.colors
    val avgColor = when {
        average == null -> c.warning
        passing -> c.success
        else -> c.danger
    }
    ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.COMPACT) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        subjectName,
                        style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    if (isExtracurricular) {
                        ElTag(text = "Hors programme", tone = ElTagTone.WARNING)
                    }
                }
                Text(
                    text = average?.let { "%.2f".format(it) } ?: "—",
                    style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = avgColor,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarkPill("D1", devoir1)
                MarkPill("D2", devoir2)
                MarkPill("Ex ×2", examen)
                Spacer(Modifier.weight(1f))
                Text(
                    "Coef ${if (coefficient == coefficient.toLong().toDouble()) "${coefficient.toLong()}" else "$coefficient"}",
                    style = ElTheme.typography.labelSmall,
                    color = c.textSecondary,
                )
            }

            if (average != null) {
                Spacer(Modifier.height(8.dp))
                ElLinearProgress(progress = (average / 20.0).toFloat())
                Spacer(Modifier.height(8.dp))
                Text(
                    if (passing) "Acquis (≥ $passingGrade/20)" else "À renforcer (< $passingGrade/20)",
                    style = ElTheme.typography.labelSmall,
                    color = avgColor,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Moyenne à paraître — les 3 notes doivent être saisies (formule (D1 + D2 + 2×Ex) / 4)",
                    style = ElTheme.typography.labelSmall,
                    color = c.warning,
                )
            }

            if (enteredAt.isNotBlank()) {
                Text(
                    "Saisie le ${enteredAt.take(10)}",
                    style = ElTheme.typography.labelSmall,
                    color = c.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun MarkPill(label: String, value: Double?) {
    val c = ElTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (value != null) c.primary.copy(alpha = 0.12f)
                else c.surfaceVariant.copy(alpha = 0.6f),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            "$label ${value?.let { if (it == it.toLong().toDouble()) "${it.toLong()}" else "$it" } ?: "—"}",
            style = ElTheme.typography.labelSmall,
            color = if (value != null) c.primary else c.textMuted,
        )
    }
}

@Composable
internal fun AcademicHistoryTab(
    history: List<AcademicYearHistory>,
    subjects: List<com.example.domain.model.Subject>,
    currentYear: String,
) {
    val c = ElTheme.colors
    val subjectById = subjects.associateBy { it.id }

    if (history.isEmpty()) {
        ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Historique académique", style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Aucun historique pour cet élève — les performances par trimestre apparaîtront ici au fil des années, avec le détail des bulletins et les décisions de promotion.",
                    style = ElTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Parcours complet — ${history.size} année(s)", style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Historique permanent en lecture seule : les années clôturées ne peuvent pas être modifiées (toute correction passe par une nouvelle entrée journalisée).",
                        style = ElTheme.typography.labelSmall,
                        color = c.textSecondary,
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
    val c = ElTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val isCurrent = year.academicYear == currentYear
    val gpaColor = when {
        year.yearlyGpa == null -> c.warning
        year.yearlyGpaSafe() >= 10.0 -> c.success
        else -> c.danger
    }

    ElCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Année ${year.academicYear}" + (if (isCurrent) " (en cours)" else ""),
                        style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        listOfNotNull(
                            year.gradeLevel?.let { "Niveau ${it.uppercase()}" },
                            year.attendanceRate?.let { "Présence %.0f%%".format(it) },
                        ).joinToString(" · "),
                        style = ElTheme.typography.labelSmall,
                        color = c.textSecondary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        year.yearlyGpa?.let { "%.2f / 20".format(it) } ?: "—",
                        style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = gpaColor,
                    )
                    Text(
                        if (expanded) "Bulletins ▲" else "Bulletins ▼",
                        style = ElTheme.typography.labelSmall,
                        color = c.primary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                year.termGpas.forEach { (term, gpa) ->
                    val tColor = when {
                        gpa == null -> c.textMuted
                        gpa >= 10.0 -> c.success
                        else -> c.danger
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tColor.copy(alpha = 0.10f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "$term ${gpa?.let { "%.2f".format(it) } ?: "—"}",
                            style = ElTheme.typography.labelSmall,
                            color = tColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            year.promotionOutcome?.let { outcome ->
                when (outcome) {
                    "promoted" -> ElTag(text = "APPROVED_FOR_PROMOTION", tone = ElTagTone.SUCCESS)
                    "graduated" -> ElTag(text = "DIPLÔMÉ", tone = ElTagTone.INFO)
                    else -> ElTag(text = "RETAINED_SAME_YEAR", tone = ElTagTone.DANGER)
                }
            } ?: run {
                if (!isCurrent) ElTag(text = "Année en attente de clôture", tone = ElTagTone.WARNING)
            }

            if (expanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Bulletin complet — ${year.academicYear}",
                    style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
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
                                style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                last.subjectAverage?.let { "%.2f".format(it) } ?: "—",
                                style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (last.isExtracurricular) c.warning else gpaColor,
                            )
                        }
                        Text(
                            "D1 ${last.devoir1 ?: "—"} · D2 ${last.devoir2 ?: "—"} · Examen ${last.examen ?: "—"} · Coef ${last.coefficient}",
                            style = ElTheme.typography.labelSmall,
                            color = c.textSecondary,
                        )
                    }
                }
                if (year.isArchived) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Année clôturée — lecture seule (append-only).",
                        style = ElTheme.typography.labelSmall,
                        color = c.textSecondary,
                    )
                }
            }
        }
    }
}

private fun AcademicYearHistory.yearlyGpaSafe(): Double = yearlyGpa ?: 0.0
