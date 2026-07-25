package com.elimtiyaz.feature.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.core.common.AttendanceStatus
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.domain.model.AttendanceSession
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.EmptyState
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 30-second roll call screen — sticky "Tous présents" + counter bar, per-student
 * status chip row (P / AE / AN / R), bottom save button.
 *
 * Reachable from [com.elimtiyaz.app.navigation.Route.RollCall].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RollCallScreen(
    classId: String,
    nav: NavController,
    vm: RollCallViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }

    // Initial load — pick today as the default date.
    LaunchedEffect(classId) {
        vm.load(classId, Formatters.isoFromLocal(Formatters.today()), AttendanceSession.Morning)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appel · ${state.classId?.takeLast(6) ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = "Changer la date")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // Sticky save button at the bottom — always visible.
            RollCallSaveBar(
                isSaving = state.isSaving,
                canSave = state.rows.isNotEmpty(),
                onSave = {
                    vm.save { _ -> /* feedback handled by snackbar LaunchedEffect below */ }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            // Date + session toggle.
            SessionHeader(
                dateIso = state.dateIso,
                session = state.session,
                onSessionChange = vm::changeSession,
            )
            // Sticky action bar — "Tous présents" + counter.
            StickyActionBar(
                absentCount = state.absentCount,
                hasAbsences = state.hasAbsences,
                onMarkAllPresent = vm::markAllPresent,
            )
            // Roster.
            if (state.isLoading) {
                LoadingState(modifier = Modifier.fillMaxSize())
            } else if (state.rows.isEmpty()) {
                EmptyState(
                    title = "Aucun élève",
                    description = "Cette classe n'a aucun élève inscrit.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                    verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
                ) {
                    items(state.rows, key = { it.studentId }) { row ->
                        RollCallRow(
                            row = row,
                            onStatusChange = { st -> vm.setStatus(row.studentId, st) },
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        RollCallDatePicker(
            initialIso = state.dateIso,
            onConfirm = { iso ->
                showDatePicker = false
                vm.changeDate(iso)
            },
            onDismiss = { showDatePicker = false },
        )
    }

    // Snackbar side-effect when a save completes.
    LaunchedEffect(state.savedAt) {
        state.savedAt?.let { snackbar.showSnackbar("Appel enregistré.") }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it.userMessage) }
    }
}

// ----- Header (date + session) ----------------------------------------------

@Composable
private fun SessionHeader(
    dateIso: String,
    session: AttendanceSession,
    onSessionChange: (AttendanceSession) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (dateIso.isNotBlank()) Formatters.date(dateIso) else "—",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = session == AttendanceSession.Morning,
            onClick = { onSessionChange(AttendanceSession.Morning) },
            label = { Text("Matin") },
        )
        Spacer(Modifier.width(ElimtiyazSpacing.x2))
        FilterChip(
            selected = session == AttendanceSession.Afternoon,
            onClick = { onSessionChange(AttendanceSession.Afternoon) },
            label = { Text("Après-midi") },
        )
    }
}

// ----- Sticky action bar ----------------------------------------------------

@Composable
private fun StickyActionBar(
    absentCount: Int,
    hasAbsences: Boolean,
    onMarkAllPresent: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onMarkAllPresent) {
            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(ElimtiyazSpacing.x2))
            Text("Tous présents")
        }
        Spacer(Modifier.weight(1f))
        if (hasAbsences) {
            StatusChip(label = "$absentCount absent(s)", tone = StatusTone.Danger)
        } else {
            StatusChip(label = "Tous présents", tone = StatusTone.Success)
        }
    }
}

// ----- Save bar -------------------------------------------------------------

@Composable
private fun RollCallSaveBar(isSaving: Boolean, canSave: Boolean, onSave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(ElimtiyazSpacing.x4),
    ) {
        Button(
            onClick = onSave,
            enabled = canSave && !isSaving,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text("Enregistrer")
            }
        }
    }
}

// ----- Row ------------------------------------------------------------------

@Composable
private fun RollCallRow(row: RollCallRow, onStatusChange: (AttendanceStatus) -> Unit) {
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(initial = Formatters.initials(row.firstName, row.lastName), size = 36)
                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        Formatters.fullName(row.firstName, row.lastName),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        row.studentCode,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x1)) {
                StatusButton(
                    label = "P",
                    full = "Présent",
                    tone = StatusTone.Success,
                    selected = row.status == AttendanceStatus.Present.key,
                    onClick = { onStatusChange(AttendanceStatus.Present) },
                    modifier = Modifier.weight(1f),
                )
                StatusButton(
                    label = "AE",
                    full = "Excusé",
                    tone = StatusTone.Info,
                    selected = row.status == AttendanceStatus.AbsentExcused.key,
                    onClick = { onStatusChange(AttendanceStatus.AbsentExcused) },
                    modifier = Modifier.weight(1f),
                )
                StatusButton(
                    label = "AN",
                    full = "Non excusé",
                    tone = StatusTone.Danger,
                    selected = row.status == AttendanceStatus.AbsentUnexcused.key,
                    onClick = { onStatusChange(AttendanceStatus.AbsentUnexcused) },
                    modifier = Modifier.weight(1f),
                )
                StatusButton(
                    label = "R",
                    full = "Retard",
                    tone = StatusTone.Warning,
                    selected = row.status == AttendanceStatus.Late.key,
                    onClick = { onStatusChange(AttendanceStatus.Late) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusButton(
    label: String,
    full: String,
    tone: StatusTone,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Match the design-system status palette (Architecture §4.1).
    val base = when (tone) {
        StatusTone.Success -> Color(0xFF3FA66E)
        StatusTone.Warning -> Color(0xFFC8A98C)
        StatusTone.Danger -> Color(0xFFC0504D)
        StatusTone.Info -> Color(0xFF349BD4)
        StatusTone.Neutral -> Color(0xFF3B464C)
    }
    val containerColor = if (selected) base else base.copy(alpha = 0.15f)
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(vertical = ElimtiyazSpacing.x2),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = contentColor, fontWeight = FontWeight.SemiBold)
            Text(full, style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}

// ----- Date picker ----------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RollCallDatePicker(
    initialIso: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = runCatching { Instant.parse(initialIso).toEpochMilliseconds() }
        .getOrDefault(System.currentTimeMillis())
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val date = Instant.fromEpochMilliseconds(millis)
                        .toLocalDateTime(TimeZone.UTC).date
                    onConfirm(Formatters.isoFromLocal(date))
                } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    ) {
        DatePicker(state = state)
    }
}
