package com.elimtiyaz.feature.academics

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.EmptyState
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone

/**
 * Grade entry screen — table of students × (D1, D2, Examen, Moyenne) for one
 * class + subject + term. Cells are inline-editable; the Moyenne column and
 * the class-average sticky header recompute on the fly.
 *
 * Reachable from [com.elimtiyaz.app.navigation.Route.GradeEntry].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeEntryScreen(
    classId: String,
    subjectId: String,
    nav: NavController,
    vm: GradeEntryViewModel = hiltViewModel(),
) {
    LaunchedEffect(classId, subjectId) { vm.load(classId, subjectId, "T1") }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.subject?.name ?: "Saisie des notes")
                        Text(
                            "Classe ${classId.takeLast(6)} · ${state.term}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            GradeSaveBar(
                isSaving = state.isSaving,
                canSave = state.rows.any { it.dirty } && vm.canEnterGrades(),
                onSave = { vm.save { _ -> /* feedback handled by snackbar LaunchedEffect below */ } },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            // Term selector.
            TermSelector(state.term, onChange = vm::changeTerm)
            // Sticky class-average header.
            ClassAverageHeader(
                classAverage = state.classAverage,
                passingCount = state.passingCount,
                failingCount = state.failingCount,
                missingCount = state.missingCount,
            )
            // Table header row.
            TableHeaderRow()
            // Rows.
            when {
                state.isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                state.rows.isEmpty() -> EmptyState(
                    title = "Aucun élève",
                    description = "Aucun élève dans cette classe.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                    verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
                ) {
                    items(state.rows, key = { it.studentId }) { row ->
                        GradeRowCard(
                            row = row,
                            onCellChange = { col, v -> vm.updateCell(row.studentId, col, v) },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(state.savedAt) {
        state.savedAt?.let { snackbar.showSnackbar("Notes enregistrées.") }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it.userMessage) }
    }
}

// ----- Term selector --------------------------------------------------------

@Composable
private fun TermSelector(term: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x4),
        horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
    ) {
        listOf("T1", "T2", "T3").forEach { t ->
            FilterChip(
                selected = term == t,
                onClick = { onChange(t) },
                label = { Text("Trimestre $t") },
            )
        }
    }
}

// ----- Sticky class average header ------------------------------------------

@Composable
private fun ClassAverageHeader(
    classAverage: Double?,
    passingCount: Int,
    failingCount: Int,
    missingCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(ElimtiyazSpacing.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Moyenne classe", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = classAverage?.let { String.format(java.util.Locale.FRANCE, "%.2f", it) } ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                color = if ((classAverage ?: 0.0) >= 10.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(ElimtiyazSpacing.x4))
        StatChip("Réussite", passingCount.toString(), StatusTone.Success)
        Spacer(Modifier.width(ElimtiyazSpacing.x2))
        StatChip("Échec", failingCount.toString(), StatusTone.Danger)
        Spacer(Modifier.width(ElimtiyazSpacing.x2))
        StatChip("Manquantes", missingCount.toString(), StatusTone.Neutral)
    }
}

@Composable
private fun StatChip(label: String, value: String, tone: StatusTone) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        StatusChip(label = label, tone = tone)
    }
}

// ----- Table header ---------------------------------------------------------

@Composable
private fun TableHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Élève", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        HeaderCell("D1", modifier = Modifier.weight(0.7f))
        HeaderCell("D2", modifier = Modifier.weight(0.7f))
        HeaderCell("Examen", modifier = Modifier.weight(0.8f))
        HeaderCell("Moy.", modifier = Modifier.weight(0.7f))
    }
}

@Composable
private fun HeaderCell(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

// ----- Grade row card -------------------------------------------------------

@Composable
private fun GradeRowCard(
    row: GradeRow,
    onCellChange: (GradeColumn, String) -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(initial = Formatters.initials(row.firstName, row.lastName), size = 28)
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Column {
                    Text(
                        Formatters.fullName(row.firstName, row.lastName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        row.studentCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            CellInput("D1", row.devoir1, onChange = { onCellChange(GradeColumn.Devoir1, it) }, modifier = Modifier.weight(0.7f))
            CellInput("D2", row.devoir2, onChange = { onCellChange(GradeColumn.Devoir2, it) }, modifier = Modifier.weight(0.7f))
            CellInput("Ex", row.examen, onChange = { onCellChange(GradeColumn.Examen, it) }, modifier = Modifier.weight(0.8f))
            AverageCell(row.average, modifier = Modifier.weight(0.7f))
        }
    }
}

@Composable
private fun CellInput(
    label: String,
    value: Double?,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    // Sync the local text with the external value only when it actually changes
    // (e.g. on initial load) so the user's in-progress typing isn't clobbered.
    LaunchedEffect(value) {
        val formatted = value?.let { formatGrade(it) } ?: ""
        if (formatted != text) text = formatted
    }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            // Allow digits, dot, comma, and empty. The VM clamps/parses.
            val sanitized = v.replace(',', '.').filter { c -> c.isDigit() || c == '.' }
            text = sanitized
            onChange(sanitized)
        },
        modifier = modifier.padding(horizontal = ElimtiyazSpacing.x1),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(8.dp),
        placeholder = { Text(label, style = MaterialTheme.typography.labelSmall) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
    )
}

@Composable
private fun AverageCell(average: Double?, modifier: Modifier = Modifier) {
    val (color, bg) = when {
        average == null -> MaterialTheme.colorScheme.onSurfaceVariant to Color.Transparent
        average >= 10.0 -> Color(0xFF3FA66E) to Color(0xFF3FA66E).copy(alpha = 0.15f)
        else -> Color(0xFFC0504D) to Color(0xFFC0504D).copy(alpha = 0.15f)
    }
    Box(
        modifier = modifier
            .padding(horizontal = ElimtiyazSpacing.x1)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(vertical = ElimtiyazSpacing.x2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = average?.let { formatGrade(it) } ?: "—",
            style = MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

// ----- Save bar -------------------------------------------------------------

@Composable
private fun GradeSaveBar(isSaving: Boolean, canSave: Boolean, onSave: () -> Unit) {
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

/** Format a grade value as a French decimal (e.g. 12.5 -> "12,5"). */
private fun formatGrade(value: Double): String =
    String.format(java.util.Locale.FRANCE, "%.2f", value).trimEnd('0').trimEnd(',').ifEmpty { "0" }
