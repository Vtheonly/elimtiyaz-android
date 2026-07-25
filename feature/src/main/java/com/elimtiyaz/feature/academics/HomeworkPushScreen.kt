package com.elimtiyaz.feature.academics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.Homework
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Homework Push screen — form (title / description / subject / due date /
 * attachments) above a list of past homework with a "Renvoyer" re-push button.
 *
 * Reachable from [com.elimtiyaz.app.navigation.Route.HomeworkPush].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkPushScreen(
    classId: String,
    nav: NavController,
    vm: HomeworkPushViewModel = hiltViewModel(),
) {
    LaunchedEffect(classId) { vm.load(classId) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        uris.forEach { uri -> vm.addAttachment(uri.toString()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devoirs · Classe ${classId.takeLast(6)}") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        if (state.isLoading && state.pastHomework.isEmpty()) {
            LoadingState(modifier = Modifier.padding(inner).fillMaxSize())
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(ElimtiyazSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x4),
        ) {
            // Push form.
            item {
                PushFormCard(
                    state = state,
                    onTitleChange = vm::titleChanged,
                    onDescriptionChange = vm::descriptionChanged,
                    onSubjectChange = vm::subjectChanged,
                    onDueDateChange = vm::dueDateChanged,
                    onAddAttachment = { galleryLauncher.launch("image/*") },
                    onRemoveAttachment = vm::removeAttachment,
                    onSubmit = { vm.push { _ -> /* feedback handled by snackbar LaunchedEffect below */ } },
                    canSubmit = vm.canAssignHomework(),
                )
            }
            // Past homework list.
            item {
                Text(
                    "Devoirs récents",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (state.pastHomework.isEmpty()) {
                item {
                    ElImtiyazCard {
                        Text(
                            "Aucun devoir assigné à cette classe.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(ElimtiyazSpacing.x4),
                        )
                    }
                }
            } else {
                items(state.pastHomework, key = { it.id }) { h ->
                    PastHomeworkCard(
                        homework = h,
                        onRePush = { vm.rePush(h.id) { _ -> /* feedback via snackbar */ } },
                    )
                }
            }
        }
    }

    LaunchedEffect(state.savedAt) {
        state.savedAt?.let { snackbar.showSnackbar("Devoir envoyé. Notification FCM déclenchée.") }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it.userMessage) }
    }
}

// ----- Push form ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PushFormCard(
    state: HomeworkPushUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onDueDateChange: (String) -> Unit,
    onAddAttachment: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSubmit: () -> Unit,
    canSubmit: Boolean,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var subjectMenuExpanded by remember { mutableStateOf(false) }

    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Text("Nouveau devoir", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(ElimtiyazSpacing.x3))

            // Subject dropdown.
            ExposedDropdownMenuBox(
                expanded = subjectMenuExpanded,
                onExpandedChange = { subjectMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = state.subjectOptions.firstOrNull { it.id == state.selectedSubjectId }?.name
                        ?: "Sélectionner…",
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Matière") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = subjectMenuExpanded,
                    onDismissRequest = { subjectMenuExpanded = false },
                ) {
                    state.subjectOptions.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text("${opt.code} — ${opt.name}") },
                            onClick = {
                                onSubjectChange(opt.id)
                                subjectMenuExpanded = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x3))

            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text("Titre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x3))

            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x3))

            // Due date row.
            OutlinedTextField(
                value = if (state.dueDate.isNotBlank()) Formatters.date(state.dueDate) else "",
                onValueChange = { },
                readOnly = true,
                label = { Text("Échéance") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = "Choisir une date")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x3))

            // Attachments row.
            Text("Pièces jointes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = false,
                    onClick = onAddAttachment,
                    leadingIcon = { Icon(Icons.Outlined.Photo, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Galerie") },
                )
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                FilterChip(
                    selected = false,
                    onClick = onAddAttachment, // CameraX capture is a v2 nicety — gallery covers v1.
                    leadingIcon = { Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Caméra") },
                )
            }
            if (state.attachments.isNotEmpty()) {
                Spacer(Modifier.height(ElimtiyazSpacing.x2))
                Column(verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x1)) {
                    state.attachments.forEach { uri ->
                        AttachmentChip(uri = uri, onRemove = { onRemoveAttachment(uri) })
                    }
                }
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x4))

            Button(
                onClick = onSubmit,
                enabled = canSubmit && !state.isSaving,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text("Envoyer le devoir")
                }
            }
        }
    }

    if (showDatePicker) {
        HomeworkDatePicker(
            initialIso = state.dueDate,
            onConfirm = {
                showDatePicker = false
                onDueDateChange(it)
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun AttachmentChip(uri: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = ElimtiyazSpacing.x3, vertical = ElimtiyazSpacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(ElimtiyazSpacing.x2))
        Text(
            uri.substringAfterLast('/').take(28),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "Retirer", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

// ----- Past homework card ---------------------------------------------------

@Composable
private fun PastHomeworkCard(homework: Homework, onRePush: () -> Unit) {
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(label = homework.subjectName, tone = StatusTone.Info)
                Spacer(Modifier.weight(1f))
                StatusChip(label = "${homework.acknowledgedCount} confirmés", tone = StatusTone.Neutral)
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(homework.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (homework.description.isNotBlank()) {
                Spacer(Modifier.height(ElimtiyazSpacing.x1))
                Text(
                    homework.description.take(160) + if (homework.description.length > 160) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text(
                    "Échéance ${Formatters.date(homework.dueDate)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = onRePush,
                    label = { Text("Renvoyer") },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
}

// ----- Date picker ----------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeworkDatePicker(
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
