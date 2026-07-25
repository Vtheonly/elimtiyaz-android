package com.elimtiyaz.feature.personnel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.EmptyState
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.ReleveEntry
import kotlinx.coroutines.launch

/**
 * ReleveScreen — clock-in / clock-out style form (Route.Releve).
 *
 * TopAppBar with personnel name + back. The form (date, hoursIn, hoursOut,
 * activity, optional class + subject) sits above today's entries list. On
 * "Enregistrer" the VM calls [com.elimtiyaz.domain.repository.ReleveRepository.logEntry]
 * and writes an audit row via `AuditRepository.log("releve.create", …)`.
 *
 * Time inputs accept either `HH:MM` or decimal hours (`8.5`). Validation
 * requires hoursIn; hoursOut (when provided) must be strictly after hoursIn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleveScreen(
    nav: NavController,
    vm: ReleveViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val personnelName = state.personnel?.let { Formatters.fullName(it.firstName, it.lastName) } ?: "Relevé"

    // Auto-dismiss the screen on a successful submit.
    LaunchedEffect(state.todayEntries.size) { /* refresh anchor — no-op */ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(personnelName, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(ElimtiyazSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
        ) {
            // Header — personnel context.
            ElImtiyazCard {
                Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
                    Text(
                        text = "Nouveau relevé d'heures",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(ElimtiyazSpacing.x1))
                    Text(
                        text = "Cible hebdo. ${state.personnel?.weeklyHoursTarget ?: 0} h",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Date — defaults to today.
            val todayIso = remember { Formatters.isoFromLocal(Formatters.today()) }
            OutlinedTextField(
                value = state.date.ifBlank { todayIso },
                onValueChange = vm::dateChanged,
                label = { Text("Date (ISO)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
            )

            // Hours in / out — accept HH:MM or decimal.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
            ) {
                OutlinedTextField(
                    value = state.hoursIn,
                    onValueChange = vm::hoursInChanged,
                    label = { Text("Arrivée (HH:MM)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Outlined.AccessTime, contentDescription = null) },
                )
                OutlinedTextField(
                    value = state.hoursOut,
                    onValueChange = vm::hoursOutChanged,
                    label = { Text("Sortie (optionnel)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            // Activity dropdown.
            var activityExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = activityExpanded, onExpandedChange = { activityExpanded = it }) {
                OutlinedTextField(
                    value = state.activity.label,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Activité") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(activityExpanded) },
                )
                androidx.compose.material3.ExposedDropdownMenu(
                    expanded = activityExpanded,
                    onDismissRequest = { activityExpanded = false },
                ) {
                    ReleveActivity.values().forEach { a ->
                        DropdownMenuItem(
                            text = { Text(a.label) },
                            onClick = { vm.activityChanged(a); activityExpanded = false },
                        )
                    }
                }
            }

            // Optional class + subject free-text IDs.
            OutlinedTextField(
                value = state.classId.orEmpty(),
                onValueChange = vm::classIdChanged,
                label = { Text("Classe (optionnel)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.subjectId.orEmpty(),
                onValueChange = vm::subjectIdChanged,
                label = { Text("Matière (optionnel)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    vm.submit { ok, msg ->
                        scope.launch {
                            if (ok) snackbar.showSnackbar("Relevé enregistré.") else snackbar.showSnackbar(msg ?: "Erreur")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = state.canSubmit,
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(end = ElimtiyazSpacing.x2).height(20.dp),
                    )
                    Text("Enregistrement…")
                } else {
                    Text("Enregistrer", fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider()

            // Today's entries list.
            Text(
                text = "Saisies du jour",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            TodayEntriesList(entries = state.todayEntries)
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
        }
    }
}

@Composable
private fun TodayEntriesList(entries: List<ReleveEntry>) {
    if (entries.isEmpty()) {
        EmptyState(
            title = "Aucune saisie aujourd'hui",
            description = "Les saisies enregistrées aujourd'hui apparaîtront ici.",
            icon = Icons.Outlined.Schedule,
            modifier = Modifier.height(160.dp),
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2)) {
            entries.forEach { e -> TodayEntryRow(e) }
        }
    }
}

@Composable
private fun TodayEntryRow(entry: ReleveEntry) {
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.activity,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(
                    label = "${entry.hoursIn.toInt()}h" + (entry.hoursOut?.let { "→${it.toInt()}h" } ?: ""),
                    tone = StatusTone.Info,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x1))
            Text(
                text = Formatters.dateTime(entry.recordedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.classId?.let { cid ->
                Text(
                    text = "Classe ${cid.take(8)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
