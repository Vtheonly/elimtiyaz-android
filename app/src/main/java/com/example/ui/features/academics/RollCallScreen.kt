package com.example.ui.features.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.display.ElChipVariant
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.input.ElDropdown
import com.example.ui.designsystem.components.input.ElDropdownOption
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.overlays.ElDialogShell
import com.example.ui.designsystem.theme.ElPillShape
import com.example.ui.designsystem.theme.ElTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RollCallScreen(
    session: Session,
    onNavigateToRollCall: (String) -> Unit = {},
    initialClassId: String? = null,
    onBack: (() -> Unit)? = null,
    viewModel: RollCallViewModel = hiltViewModel(),
) {
    val classes by viewModel.classes.collectAsState()
    val students by viewModel.students.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedSession by viewModel.selectedSession.collectAsState()
    val existingRecords by viewModel.existingRecords.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val c = ElTheme.colors

    var selectedClassId by remember { mutableStateOf<String?>(initialClassId) }
    val statuses = remember { mutableStateMapOf<String, AttendanceStatus>() }
    val lateTimes = remember { mutableStateMapOf<String, String>() }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(classes) {
        if (selectedClassId == null && classes.isNotEmpty()) {
            selectedClassId = classes.first().id
        }
    }

    LaunchedEffect(selectedClassId) {
        selectedClassId?.let { viewModel.loadStudentsForClass(it) }
    }

    LaunchedEffect(students, existingRecords) {
        students.forEach { student ->
            val existing = existingRecords[student.id]
            if (existing != null) {
                val statusEnum = when (existing.status) {
                    "absent_unexcused" -> AttendanceStatus.ABSENT
                    "absent_excused" -> AttendanceStatus.EXCUSED
                    "late" -> AttendanceStatus.LATE
                    else -> AttendanceStatus.PRESENT
                }
                statuses[student.id] = statusEnum
                if (existing.note != null && existing.note.contains("Arrivée")) {
                    lateTimes[student.id] = existing.note.substringAfter("Arrivée:").trim()
                }
            } else if (!statuses.containsKey(student.id)) {
                statuses[student.id] = AttendanceStatus.PRESENT
            }
        }
    }

    val selectedClass = classes.firstOrNull { it.id == selectedClassId }

    val formattedDisplayDate = remember(selectedDate) {
        runCatching {
            val d = LocalDate.parse(selectedDate)
            d.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.FRENCH))
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.FRENCH) else it.toString() }
        }.getOrDefault(selectedDate)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (onBack != null) {
            ElTopBar(
                title = "Appel — ${selectedClass?.name ?: ""}",
                subtitle = formattedDisplayDate,
                onBack = onBack,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── 1. Paramètres de la séance Card ───────────────────────────────
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ElSectionHeader(title = "Paramètres de la séance")
                        if (selectedDate != LocalDate.now().toString()) {
                            ElChip(
                                text = "Aujourd'hui",
                                variant = ElChipVariant.FILTER,
                                onClick = {
                                    viewModel.setDate(LocalDate.now().toString(), selectedClassId)
                                },
                            )
                        }
                    }

                    // Interactive Date Picker Field
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, c.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = c.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Date de l'appel :",
                                    style = ElTheme.typography.labelSmall,
                                    color = c.textSecondary,
                                )
                                Text(
                                    text = formattedDisplayDate,
                                    style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = c.textPrimary,
                                )
                            }
                        }
                        Text(
                            text = "Changer",
                            style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = c.primary,
                        )
                    }

                    // Session Selector Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Créneau horaire",
                            style = ElTheme.typography.labelSmall,
                            color = c.textSecondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "morning" to "Matin",
                                "afternoon" to "Après-midi",
                                "both" to "Journée entière",
                            ).forEach { (code, label) ->
                                ElChip(
                                    text = label,
                                    variant = ElChipVariant.FILTER,
                                    selected = selectedSession == code,
                                    onClick = { viewModel.setSession(code) },
                                )
                            }
                        }
                    }
                }
            }

            if (classes.isEmpty()) {
                ElEmptyState(
                    icon = Icons.Default.Class,
                    title = "Aucune classe",
                    subtitle = "Aucune classe n'est disponible.",
                )
                return@Column
            }

            // ── 2. Sélection de classe ─────────────────────────────────────────
            ElDropdown(
                label = "Classe sélectionnée",
                options = classes.map { ElDropdownOption(value = it.id, label = it.name) },
                selectedValue = selectedClassId,
                onSelected = { option ->
                    selectedClassId = option.value
                    statuses.clear()
                    lateTimes.clear()
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // ── 3. Barre de comptage & Bouton Tous présents ───────────────────
            if (students.isNotEmpty()) {
                val presentCount = statuses.values.count { it == AttendanceStatus.PRESENT }
                val absentCount = statuses.values.count { it == AttendanceStatus.ABSENT }
                val excusedCount = statuses.values.count { it == AttendanceStatus.EXCUSED }
                val lateCount = statuses.values.count { it == AttendanceStatus.LATE }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        AttendanceStatBadge(
                            count = presentCount,
                            label = "présent${if (presentCount > 1) "s" else ""}",
                            color = c.success,
                            active = presentCount > 0,
                        )
                        if (absentCount > 0) {
                            AttendanceStatBadge(
                                count = absentCount,
                                label = "absent${if (absentCount > 1) "s" else ""}",
                                color = c.danger,
                                active = true,
                            )
                        }
                        if (excusedCount > 0) {
                            AttendanceStatBadge(
                                count = excusedCount,
                                label = "excusé${if (excusedCount > 1) "s" else ""}",
                                color = c.warning,
                                active = true,
                            )
                        }
                        if (lateCount > 0) {
                            AttendanceStatBadge(
                                count = lateCount,
                                label = "retard${if (lateCount > 1) "s" else ""}",
                                color = c.info,
                                active = true,
                            )
                        }
                    }

                    ElButton(
                        text = "Tous présents",
                        onClick = {
                            students.forEach { s -> statuses[s.id] = AttendanceStatus.PRESENT }
                        },
                        variant = ElButtonVariant.SECONDARY,
                        icon = Icons.Default.DoneAll,
                    )
                }
            }

            // ── 4. Liste des élèves ───────────────────────────────────────────
            if (students.isEmpty()) {
                ElEmptyState(
                    icon = Icons.Default.Class,
                    title = "Aucun élève",
                    subtitle = "Aucun élève trouvé dans cette classe.",
                )
            } else {
                students.forEach { student ->
                    val currentStatus = statuses[student.id] ?: AttendanceStatus.PRESENT
                    val isLate = currentStatus == AttendanceStatus.LATE

                    val (accentColor, statusLabel) = when (currentStatus) {
                        AttendanceStatus.PRESENT -> c.success to "Présent"
                        AttendanceStatus.ABSENT -> c.danger to "Absent"
                        AttendanceStatus.EXCUSED -> c.warning to "Excusé"
                        AttendanceStatus.LATE -> c.info to (lateTimes[student.id] ?: "Retard")
                    }

                    ElCard(
                        modifier = Modifier.fillMaxWidth(),
                        size = ElCardSize.STANDARD,
                        border = BorderStroke(ElTheme.borders.thin, accentColor.copy(alpha = 0.45f)),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // En-tête de l'élève
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ElAvatar(initials = student.fullName, size = ElAvatarSize.M)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        student.fullName,
                                        style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = c.textPrimary,
                                    )
                                    Text(
                                        "Matricule: ${student.code}",
                                        style = ElTheme.typography.bodySmall,
                                        color = c.textSecondary,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(ElPillShape)
                                        .background(accentColor.copy(alpha = 0.15f))
                                        .border(1.dp, accentColor.copy(alpha = 0.35f), ElPillShape)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = statusLabel,
                                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = accentColor,
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Sélecteur de statut segmenté
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AttendanceStatus.values().forEach { st ->
                                    val isSelected = currentStatus == st
                                    AttendanceSegmentButton(
                                        status = st,
                                        isSelected = isSelected,
                                        onClick = {
                                            statuses[student.id] = st
                                            if (st == AttendanceStatus.LATE && !lateTimes.containsKey(student.id)) {
                                                lateTimes[student.id] = "08:15"
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            // Panneau pour l'heure de retard
                            if (isLate) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(c.info.copy(alpha = 0.10f))
                                        .border(1.dp, c.info.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = c.info,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Heure d'arrivée :",
                                        style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = c.textPrimary,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    ElTextField(
                                        value = lateTimes[student.id] ?: "08:15",
                                        onValueChange = { lateTimes[student.id] = it },
                                        modifier = Modifier.width(100.dp),
                                        singleLine = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            message?.let {
                ElAlertBanner(
                    title = if (it.contains("succès", ignoreCase = true)) "Appel Enregistré" else "Information",
                    message = it,
                    severity = if (it.contains("succès", ignoreCase = true)) ElAlertSeverity.SUCCESS else ElAlertSeverity.WARNING,
                )
            }

            // ── 5. Bouton de validation ────────────────────────────────────────
            ElButton(
                text = "Valider l'appel du $selectedDate (${selectedClass?.name ?: ""})",
                onClick = {
                    val cid = selectedClassId ?: return@ElButton
                    val currentStudentIds = students.map { it.id }.toSet()
                    val scopedStatuses = statuses.filterKeys { it in currentStudentIds }
                    val scopedLateTimes = lateTimes.filterKeys { it in currentStudentIds }
                    viewModel.submitRollCall(
                        classId = cid,
                        date = selectedDate,
                        session = selectedSession,
                        statuses = scopedStatuses,
                        lateTimes = scopedLateTimes,
                        actorId = session.userId,
                        actorName = session.displayName,
                    )
                },
                fullWidth = true,
                icon = Icons.Default.Send,
                enabled = !busy && students.isNotEmpty(),
            )
        }
    }

    if (showDatePicker) {
        val initialEpochMillis = remember(selectedDate) {
            runCatching {
                LocalDate.parse(selectedDate)
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant()
                    .toEpochMilli()
            }.getOrDefault(System.currentTimeMillis())
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialEpochMillis)

        // T-460 H2 (issue #3 F-10): the raw M3 DatePickerDialog → the DS dialog
        // shell (the ElDatePicker component's internal language: ElDialogShell +
        // the M3 DatePicker + DS buttons). The UTC wire format and the
        // viewModel.setDate contract preserved verbatim.
        ElDialogShell(onDismissRequest = { showDatePicker = false }) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                DatePicker(state = datePickerState)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    ElButton(
                        text = "Annuler",
                        onClick = { showDatePicker = false },
                        variant = ElButtonVariant.GHOST,
                    )
                    ElButton(
                        text = "OK",
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val date = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.of("UTC"))
                                    .toLocalDate()
                                    .toString()
                                viewModel.setDate(date, selectedClassId)
                            }
                            showDatePicker = false
                        },
                        variant = ElButtonVariant.PRIMARY,
                    )
                }
            }
        }
    }
}

/**
 * Bouton de statut d'appel haute lisibilité :
 * - Actif : fond plein de couleur sémantique vive, texte blanc gras.
 * - Inactif : fond discret ardoise sombre (`surfaceVariant`), bordure subtile, texte net et lisible.
 */
@Composable
private fun AttendanceSegmentButton(
    status: AttendanceStatus,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    val resolvedStatusColor = status.resolvedColor()
    val backgroundColor = if (isSelected) {
        resolvedStatusColor
    } else {
        c.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = if (isSelected) {
        if (status == AttendanceStatus.EXCUSED) c.background else c.textOnColor
    } else {
        c.textSecondary.copy(alpha = 0.85f)
    }

    val borderColor = if (isSelected) {
        resolvedStatusColor
    } else {
        c.outline.copy(alpha = 0.2f)
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(ElPillShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, ElPillShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = status.label,
            style = ElTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = contentColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** Badge d'état de présence avec pastille colorée */
@Composable
private fun AttendanceStatBadge(
    count: Int,
    label: String,
    color: Color,
    active: Boolean,
) {
    val c = ElTheme.colors
    val badgeColor = if (active) color else c.textSecondary
    val badgeBg = if (active) color.copy(alpha = 0.15f) else c.surfaceVariant.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .clip(ElPillShape)
            .background(badgeBg)
            .border(1.dp, badgeColor.copy(alpha = 0.3f), ElPillShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "$count $label",
                style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = badgeColor,
            )
        }
    }
}