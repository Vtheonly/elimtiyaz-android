package com.example.ui.features.academics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Permission
import com.example.core.Result
import com.example.domain.model.Subject
import com.example.domain.repository.CreateSubjectInput
import com.example.domain.repository.SubjectRepository
import com.example.domain.repository.UpdateSubjectInput
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Subjects directory ViewModel.
 *
 * Restored behavior (commit a34333a):
 *  - Lists all subjects.
 *  - Filter chips by `AcademicLevel` (primaire/cem/lycee).
 *  - Create / archive actions gated to MANAGE_SUBJECTS.
 *
 * Vault §05.06 / §05.07 additions:
 *  - Domain filter (Scolarité vs Hors programme — the strict domain split;
 *    club/therapy grades never feed the Scolarite GPA).
 *  - `updateSubject` edits (name, coefficient, passing grade) — coefficient
 *    changes are audited and trigger the automatic GPA recompute for the
 *    current year (repository side).
 *  - Creation supports the extracurricular flag (previously hardcoded false).
 */
@HiltViewModel
class SubjectsDirectoryViewModel @Inject constructor(
    private val subjectRepository: SubjectRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val subjects: StateFlow<List<Subject>> = subjectRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _levelFilter = MutableStateFlow<String?>(null)
    val levelFilter: StateFlow<String?> = _levelFilter.asStateFlow()

    // Vault §05.01 — domain filter: null = all, "scolarite" = formal core
    // academics, "extracurricular" = clubs & therapy programs.
    private val _domainFilter = MutableStateFlow<String?>(null)
    val domainFilter: StateFlow<String?> = _domainFilter.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val canManage: Boolean get() = sessionManager.current()?.can(Permission.MANAGE_SUBJECTS) == true

    fun onLevelFilter(level: String?) { _levelFilter.value = level }

    fun onDomainFilter(domain: String?) { _domainFilter.value = domain }

    fun archiveSubject(id: String) {
        if (!canManage) { _error.value = "Permission manquante : MANAGE_SUBJECTS."; return }
        viewModelScope.launch {
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val r = subjectRepository.archiveSubject(id, actorId, actorName)) {
                is Result.Ok -> {}
                is Result.Err -> _error.value = r.error.userMessage
            }
        }
    }

    // FIX (dead create dialog): the "Nouvelle matière" dialog was labelled
    // "Créer (mock)" and created NOTHING. Wired to the real repository.
    //
    // Vault §06.02 (iteration 2) — the create payload now carries the
    // three per-COMPONENT coefficients (D1 / D2 / Examen). Defaults
    // (1, 1, 2) preserve the historical recipe when the admin leaves the
    // fields at their default values.
    fun createSubject(
        name: String, code: String, level: String, coefficient: Double, isExtracurricular: Boolean,
        coefDevoir1: Double, coefDevoir2: Double, coefExamen: Double,
    ) {
        if (!canManage) { _error.value = "Permission manquante : MANAGE_SUBJECTS."; return }
        if (name.isBlank() || code.isBlank()) {
            _error.value = "Nom et code sont requis."
            return
        }
        viewModelScope.launch {
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            val result = subjectRepository.createSubject(
                CreateSubjectInput(
                    name = name.trim(),
                    nameAr = null,
                    code = code.trim().uppercase(),
                    level = level.trim().ifBlank { "all" },
                    coefficient = coefficient,
                    isExtracurricular = isExtracurricular,
                    coefficientDevoir1 = coefDevoir1,
                    coefficientDevoir2 = coefDevoir2,
                    coefficientExamen = coefExamen,
                ),
                actorId, actorName,
            )
            when (result) {
                is Result.Ok -> _message.value = "Matière « ${result.value.name} » créée."
                is Result.Err -> _error.value = result.error.userMessage
            }
        }
    }

    /**
     * Vault §05.06 + §06.02 — edit an existing subject (name / coefficient /
     * passing grade / per-component coefficients). The repository audits
     * the change, refreshes the current year's assessment coefficient
     * snapshots, and re-derives subjectAverage with the new per-component
     * weights (automatic GPA recompute).
     */
    fun updateSubject(
        id: String, name: String, coefficient: Double, passingGrade: Double,
        coefDevoir1: Double, coefDevoir2: Double, coefExamen: Double,
    ) {
        if (!canManage) { _error.value = "Permission manquante : MANAGE_SUBJECTS."; return }
        if (name.isBlank()) {
            _error.value = "Le nom est requis."
            return
        }
        if (coefficient <= 0.0) {
            _error.value = "Le coefficient doit être strictement positif."
            return
        }
        viewModelScope.launch {
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            val result = subjectRepository.updateSubject(
                id,
                UpdateSubjectInput(
                    name = name.trim(),
                    coefficient = coefficient,
                    passingGrade = passingGrade,
                    coefficientDevoir1 = coefDevoir1,
                    coefficientDevoir2 = coefDevoir2,
                    coefficientExamen = coefExamen,
                ),
                actorId, actorName,
            )
            when (result) {
                is Result.Ok -> _message.value =
                    "Matière mise à jour — les moyennes de l'année en cours seront recalculées."
                is Result.Err -> _error.value = result.error.userMessage
            }
        }
    }

    fun clearMessages() {
        _error.value = null
        _message.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsDirectoryScreen(
    onBack: () -> Unit,
    viewModel: SubjectsDirectoryViewModel = hiltViewModel(),
) {
    val subjects by viewModel.subjects.collectAsState()
    val levelFilter by viewModel.levelFilter.collectAsState()
    val domainFilter by viewModel.domainFilter.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var archiveTarget by remember { mutableStateOf<Subject?>(null) }
    // Vault §05.06 — edit dialog state.
    var editTarget by remember { mutableStateOf<Subject?>(null) }

    // FIX (broken level filter): subjects scoped "all" apply to every level —
    // the previous strict equality filter showed an empty list under each chip.
    val filtered = subjects
        .let { list -> if (levelFilter == null) list else list.filter { it.level == "all" || it.level == levelFilter } }
        .let { list ->
            when (domainFilter) {
                null -> list
                "scolarite" -> list.filter { !it.isExtracurricular }
                else -> list.filter { it.isExtracurricular }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Matières") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
                actions = {
                    if (viewModel.canManage) {
                        IconButton(onClick = { showCreateDialog = true }) { Icon(Icons.Default.Add, contentDescription = "Nouvelle matière") }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp)) }

            // Vault §05.01 — domain split filter (Scolarite vs Clubs/Therapy).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                FilterChip(selected = domainFilter == null, onClick = { viewModel.onDomainFilter(null) }, label = { Text("Tous domaines") })
                FilterChip(selected = domainFilter == "scolarite", onClick = { viewModel.onDomainFilter("scolarite") }, label = { Text("Scolarité") })
                FilterChip(selected = domainFilter == "extracurricular", onClick = { viewModel.onDomainFilter("extracurricular") }, label = { Text("Clubs & Thérapie") })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                FilterChip(selected = levelFilter == null, onClick = { viewModel.onLevelFilter(null) }, label = { Text("Tous") })
                FilterChip(selected = levelFilter == "primaire", onClick = { viewModel.onLevelFilter("primaire") }, label = { Text("Primaire") })
                FilterChip(selected = levelFilter == "cem", onClick = { viewModel.onLevelFilter("cem") }, label = { Text("CEM") })
                FilterChip(selected = levelFilter == "lycee", onClick = { viewModel.onLevelFilter("lycee") }, label = { Text("Lycée") })
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { subj ->
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Row {
                                Text(subj.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (subj.isExtracurricular) Text("Hors programme", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Text("Code: ${subj.code} • Niveau: ${subj.level} • Coef: ${subj.coefficient}", style = MaterialTheme.typography.labelSmall)
                            Text("Seuil réussite: ${subj.passingGrade}/20", style = MaterialTheme.typography.labelSmall)
                            // Vault §06.02 — surface the per-COMPONENT
                            // coefficients so an admin can read the active
                            // subject-average recipe at a glance.
                            Text(
                                "Pondération: D1 × ${subj.coefficientDevoir1} • D2 × ${subj.coefficientDevoir2} • Ex × ${subj.coefficientExamen}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (viewModel.canManage) {
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    // Vault §05.06 — coefficient edit (audited +
                                    // triggers the GPA recompute server-repo side).
                                    IconButton(onClick = { editTarget = subj }) { Icon(Icons.Default.Edit, contentDescription = "Modifier") }
                                    IconButton(onClick = { archiveTarget = subj }) { Icon(Icons.Default.Archive, contentDescription = "Archiver") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var level by remember { mutableStateOf("all") }
        var coef by remember { mutableStateOf("1") }
        // Vault §06.02 (iteration 2) — per-COMPONENT coefficients. Defaults
        // (1, 1, 2) preserve the historical recipe (Examen weighted ×2).
        var coefD1 by remember { mutableStateOf("1") }
        var coefD2 by remember { mutableStateOf("1") }
        var coefEx by remember { mutableStateOf("2") }
        // Vault §05.07 — extracurricular toggle (clubs & therapy programs).
        var extracurricularLabel by remember { mutableStateOf("Scolarité") }
        val domainOptions = listOf("Scolarité", "Hors programme (club / thérapie)")
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nouvelle matière") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom *") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code *") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = level, onValueChange = { level = it }, label = { Text("Niveau (all/primaire/cem/lycee)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = coef, onValueChange = { coef = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Coefficient (scolarité)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    // Vault §06.02 — per-component coefficients for the
                    // subject-average recipe (D1×c1 + D2×c2 + Ex×c3) / (c1+c2+c3).
                    OutlinedTextField(value = coefD1, onValueChange = { coefD1 = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Coef. Devoir 1") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = coefD2, onValueChange = { coefD2 = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Coef. Devoir 2") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = coefEx, onValueChange = { coefEx = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Coef. Examen") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        domainOptions.forEach { opt ->
                            FilterChip(
                                selected = extracurricularLabel == opt,
                                onClick = { extracurricularLabel = opt },
                                label = { Text(if (opt == "Scolarité") "Scolarité" else "Hors programme") },
                            )
                        }
                    }
                    Text(
                        "Les matières hors programme (clubs, thérapie) sont exclues du GPA de scolarité. " +
                            "Recette par défaut : (D1 + D2 + 2×Examen) / 4.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createSubject(
                            name, code, level,
                            coef.toDoubleOrNull() ?: 1.0,
                            extracurricularLabel != "Scolarité",
                            coefD1.toDoubleOrNull() ?: 1.0,
                            coefD2.toDoubleOrNull() ?: 1.0,
                            coefEx.toDoubleOrNull() ?: 2.0,
                        )
                        showCreateDialog = false
                    },
                    enabled = name.isNotBlank() && code.isNotBlank(),
                ) { Text("Créer") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Annuler") } },
        )
    }

    // Vault §05.06 + §06.02 — edit dialog: name + coefficient + passing
    // grade + the three per-component coefficients.
    editTarget?.let { subj ->
        var name by remember(subj.id) { mutableStateOf(subj.name) }
        var coef by remember(subj.id) { mutableStateOf(if (subj.coefficient == subj.coefficient.toLong().toDouble()) "${subj.coefficient.toLong()}" else "${subj.coefficient}") }
        var passing by remember(subj.id) { mutableStateOf(if (subj.passingGrade == subj.passingGrade.toLong().toDouble()) "${subj.passingGrade.toLong()}" else "${subj.passingGrade}") }
        var coefD1 by remember(subj.id) { mutableStateOf(if (subj.coefficientDevoir1 == subj.coefficientDevoir1.toLong().toDouble()) "${subj.coefficientDevoir1.toLong()}" else "${subj.coefficientDevoir1}") }
        var coefD2 by remember(subj.id) { mutableStateOf(if (subj.coefficientDevoir2 == subj.coefficientDevoir2.toLong().toDouble()) "${subj.coefficientDevoir2.toLong()}" else "${subj.coefficientDevoir2}") }
        var coefEx by remember(subj.id) { mutableStateOf(if (subj.coefficientExamen == subj.coefficientExamen.toLong().toDouble()) "${subj.coefficientExamen.toLong()}" else "${subj.coefficientExamen}") }
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("Modifier — ${subj.name}") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom *") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = coef,
                        onValueChange = { coef = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Coefficient (scolarité)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = coefD1,
                        onValueChange = { coefD1 = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Coef. Devoir 1") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = coefD2,
                        onValueChange = { coefD2 = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Coef. Devoir 2") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = coefEx,
                        onValueChange = { coefEx = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Coef. Examen") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passing,
                        onValueChange = { passing = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Seuil de réussite (/20)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Un changement de coefficient est journalisé et déclenche le recalcul automatique des moyennes de l'année en cours (les années archivées restent immuables).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateSubject(
                            subj.id,
                            name,
                            coef.toDoubleOrNull() ?: subj.coefficient,
                            passing.toDoubleOrNull() ?: subj.passingGrade,
                            coefD1.toDoubleOrNull() ?: subj.coefficientDevoir1,
                            coefD2.toDoubleOrNull() ?: subj.coefficientDevoir2,
                            coefEx.toDoubleOrNull() ?: subj.coefficientExamen,
                        )
                        editTarget = null
                    },
                    enabled = name.isNotBlank() && (coef.toDoubleOrNull() ?: 0.0) > 0.0,
                ) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("Annuler") } },
        )
    }

    archiveTarget?.let { subj ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            title = { Text("Archiver la matière") },
            text = { Text("Archiver « ${subj.name} » ?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.archiveSubject(subj.id)
                    archiveTarget = null
                }) { Text("Archiver") }
            },
            dismissButton = { TextButton(onClick = { archiveTarget = null }) { Text("Annuler") } },
        )
    }
}
