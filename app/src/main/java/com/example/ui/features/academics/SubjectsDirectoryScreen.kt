package com.example.ui.features.academics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.display.ElChipVariant
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.input.ElSearchBar
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.overlays.ElDialogShell
import com.example.ui.designsystem.theme.ElTheme
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

    /** T-460 pass J (issue #3 F-13): the text search — parity with the Student/Parents directories. */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun setQuery(q: String) { _query.value = q }

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

/**
 * T-460 pass J (issue #3 F-13): the subjects filter — level × domain × text
 * search. Pure so the §05.01 domain-split + search behaviour is unit-tested
 * without composition.
 *
 * Level rule (the T-fix preserved): subjects scoped "all" apply to every
 * level; domain rule (Vault §05.01): "scolarite" = formal academics,
 * "extracurricular" = clubs & therapy.
 */
internal fun filterSubjects(
    subjects: List<Subject>,
    level: String?,
    domain: String?,
    query: String,
): List<Subject> {
    val q = query.trim()
    return subjects
        .let { list -> if (level == null) list else list.filter { it.level == "all" || it.level == level } }
        .let { list ->
            when (domain) {
                null -> list
                "scolarite" -> list.filter { !it.isExtracurricular }
                else -> list.filter { it.isExtracurricular }
            }
        }
        .let { list ->
            if (q.isBlank()) list
            else list.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.code.contains(q, ignoreCase = true) ||
                    it.nameAr?.contains(q, ignoreCase = true) == true
            }
        }
}

@Composable
fun SubjectsDirectoryScreen(
    onBack: () -> Unit,
    viewModel: SubjectsDirectoryViewModel = hiltViewModel(),
) {
    val subjects by viewModel.subjects.collectAsState()
    val query by viewModel.query.collectAsState()
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
    // T-460 pass J (F-13): the filter is now the pure, unit-tested function
    // (level × domain × the NEW text search).
    val filtered = filterSubjects(subjects, levelFilter, domainFilter, query)

    val c = ElTheme.colors

    ElScaffold(
        topBar = {
            ElTopBar(
                title = "Matières",
                onBack = onBack,
                actions = {
                    if (viewModel.canManage) {
                        ElIconButton(
                            icon = Icons.Default.Add,
                            onClick = { showCreateDialog = true },
                            contentDescription = "Nouvelle matière",
                            tint = c.primary,
                            background = androidx.compose.ui.graphics.Color.Transparent,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            error?.let {
                ElAlertBanner(
                    title = "Erreur",
                    message = it,
                    severity = ElAlertSeverity.DANGER,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            message?.let {
                ElAlertBanner(
                    title = "Succès",
                    message = it,
                    severity = ElAlertSeverity.SUCCESS,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Vault §05.01 — domain split filter (Scolarite vs Clubs/Therapy).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                ElChip(text = "Tous domaines", variant = ElChipVariant.FILTER, selected = domainFilter == null, onClick = { viewModel.onDomainFilter(null) })
                ElChip(text = "Scolarité", variant = ElChipVariant.FILTER, selected = domainFilter == "scolarite", onClick = { viewModel.onDomainFilter("scolarite") })
                ElChip(text = "Clubs & Thérapie", variant = ElChipVariant.FILTER, selected = domainFilter == "extracurricular", onClick = { viewModel.onDomainFilter("extracurricular") })
            }

            // T-460 pass J (issue #3 F-13): text-search parity (name, code,
            // Arabic name) — the Student/Parents directories' search language.
            ElSearchBar(
                query = query,
                onQueryChange = viewModel::setQuery,
                placeholder = "Nom, code matière…",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                ElChip(text = "Tous", variant = ElChipVariant.FILTER, selected = levelFilter == null, onClick = { viewModel.onLevelFilter(null) })
                ElChip(text = "Primaire", variant = ElChipVariant.FILTER, selected = levelFilter == "primaire", onClick = { viewModel.onLevelFilter("primaire") })
                ElChip(text = "CEM", variant = ElChipVariant.FILTER, selected = levelFilter == "cem", onClick = { viewModel.onLevelFilter("cem") })
                ElChip(text = "Lycée", variant = ElChipVariant.FILTER, selected = levelFilter == "lycee", onClick = { viewModel.onLevelFilter("lycee") })
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (filtered.isEmpty()) {
                    item {
                        // T-323: the filtered-empty state — the old screen showed
                        // a BLANK list when the level/domain chips excluded
                        // everything.
                        ElEmptyState(
                            title = "Aucune matière trouvée",
                            subtitle = if (query.isBlank()) {
                                "Aucune matière ne correspond aux critères sélectionnés."
                            } else {
                                "Aucune matière ne correspond à « $query »."
                            },
                        )
                    }
                }
                items(filtered) { subj ->
                    ElCard(
                        modifier = Modifier.fillMaxWidth(),
                        size = ElCardSize.STANDARD,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    subj.name,
                                    style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = c.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (subj.isExtracurricular) {
                                    ElTag(text = "Hors programme", tone = ElTagTone.WARNING, size = ElTagSize.SM)
                                }
                            }
                            Text("Code : ${subj.code} • Niveau : ${subj.level} • Coef : ${subj.coefficient}", style = ElTheme.typography.labelSmall, color = c.textSecondary)
                            Text("Seuil réussite : ${subj.passingGrade}/20", style = ElTheme.typography.labelSmall, color = c.textSecondary)
                            // Vault §06.02 — surface the per-COMPONENT
                            // coefficients so an admin can read the active
                            // subject-average recipe at a glance.
                            Text(
                                "Pondération : D1 × ${subj.coefficientDevoir1} • D2 × ${subj.coefficientDevoir2} • Ex × ${subj.coefficientExamen}",
                                style = ElTheme.typography.labelSmall,
                                color = c.textSecondary,
                            )
                            if (viewModel.canManage) {
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                    // Vault §05.06 — coefficient edit (audited +
                                    // triggers the GPA recompute server-repo side).
                                    ElIconButton(
                                        icon = Icons.Default.Edit,
                                        onClick = { editTarget = subj },
                                        contentDescription = "Modifier",
                                        tint = c.primary,
                                        background = androidx.compose.ui.graphics.Color.Transparent,
                                        size = 36,
                                        iconSize = 18,
                                    )
                                    ElIconButton(
                                        icon = Icons.Default.Archive,
                                        onClick = { archiveTarget = subj },
                                        contentDescription = "Archiver",
                                        tint = c.danger,
                                        background = androidx.compose.ui.graphics.Color.Transparent,
                                        size = 36,
                                        iconSize = 18,
                                    )
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
        ElDialogShell(onDismissRequest = { showCreateDialog = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Nouvelle matière",
                    style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ElTheme.colors.textPrimary,
                )
                ElTextField(value = name, onValueChange = { name = it }, label = "Nom *", modifier = Modifier.fillMaxWidth())
                ElTextField(value = code, onValueChange = { code = it }, label = "Code *", modifier = Modifier.fillMaxWidth())
                // T-323: structured level selection — the free-text
                // "all/primaire/cem/lycee" field was error-prone (a typo
                // silently broke the level filter for that subject).
                LevelDropdown(level = level, onLevelChange = { level = it })
                // Vault §06.02 — per-component coefficients for the
                // subject-average recipe (D1×c1 + D2×c2 + Ex×c3) / (c1+c2+c3).
                ElTextField(value = coef, onValueChange = { coef = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = "Coefficient (scolarité)", modifier = Modifier.fillMaxWidth())
                ElTextField(value = coefD1, onValueChange = { coefD1 = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = "Coef. Devoir 1", modifier = Modifier.fillMaxWidth())
                ElTextField(value = coefD2, onValueChange = { coefD2 = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = "Coef. Devoir 2", modifier = Modifier.fillMaxWidth())
                ElTextField(value = coefEx, onValueChange = { coefEx = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = "Coef. Examen", modifier = Modifier.fillMaxWidth())
                // Vault §05.07 — extracurricular toggle (clubs & therapy programs).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    domainOptions.forEach { opt ->
                        ElChip(
                            text = if (opt == "Scolarité") "Scolarité" else "Hors programme",
                            variant = ElChipVariant.FILTER,
                            selected = extracurricularLabel == opt,
                            onClick = { extracurricularLabel = opt },
                        )
                    }
                }
                Text(
                    "Les matières hors programme (clubs, thérapie) sont exclues du GPA de scolarité. " +
                        "Recette par défaut : (D1 + D2 + 2×Examen) / 4.",
                    style = ElTheme.typography.labelSmall,
                    color = ElTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ElButton(
                        text = "Annuler",
                        onClick = { showCreateDialog = false },
                        variant = ElButtonVariant.GHOST,
                        modifier = Modifier.weight(1f),
                    )
                    ElButton(
                        text = "Créer",
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
                        variant = ElButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
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
        ElDialogShell(onDismissRequest = { editTarget = null }) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Modifier — ${subj.name}",
                    style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ElTheme.colors.textPrimary,
                )
                ElTextField(value = name, onValueChange = { name = it }, label = "Nom *", modifier = Modifier.fillMaxWidth())
                ElTextField(
                    value = coef,
                    onValueChange = { coef = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "Coefficient (scolarité)",
                    modifier = Modifier.fillMaxWidth(),
                )
                ElTextField(
                    value = coefD1,
                    onValueChange = { coefD1 = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "Coef. Devoir 1",
                    modifier = Modifier.fillMaxWidth(),
                )
                ElTextField(
                    value = coefD2,
                    onValueChange = { coefD2 = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "Coef. Devoir 2",
                    modifier = Modifier.fillMaxWidth(),
                )
                ElTextField(
                    value = coefEx,
                    onValueChange = { coefEx = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "Coef. Examen",
                    modifier = Modifier.fillMaxWidth(),
                )
                ElTextField(
                    value = passing,
                    onValueChange = { passing = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "Seuil de réussite (/20)",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Un changement de coefficient est journalisé et déclenche le recalcul automatique des moyennes de l'année en cours (les années archivées restent immuables).",
                    style = ElTheme.typography.labelSmall,
                    color = ElTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ElButton(
                        text = "Annuler",
                        onClick = { editTarget = null },
                        variant = ElButtonVariant.GHOST,
                        modifier = Modifier.weight(1f),
                    )
                    ElButton(
                        text = "Enregistrer",
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
                        variant = ElButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    archiveTarget?.let { subj ->
        ElDialogShell(onDismissRequest = { archiveTarget = null }) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Archiver la matière",
                    style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ElTheme.colors.textPrimary,
                )
                Text(
                    "Archiver « ${subj.name} » ?",
                    style = ElTheme.typography.bodyMedium,
                    color = ElTheme.colors.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ElButton(
                        text = "Annuler",
                        onClick = { archiveTarget = null },
                        variant = ElButtonVariant.GHOST,
                        modifier = Modifier.weight(1f),
                    )
                    ElButton(
                        text = "Archiver",
                        onClick = {
                            viewModel.archiveSubject(subj.id)
                            archiveTarget = null
                        },
                        variant = ElButtonVariant.DANGER,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * T-323: structured level picker for the create dialog. The codes are the
 * canonical level filters used by the directory chips (Vault §05.01);
 * "all" keeps the historical cross-level semantics. Replaces the old
 * free-text field (a typo silently broke the level filter for the subject).
 */
@Composable
private fun LevelDropdown(level: String, onLevelChange: (String) -> Unit) {
    val options = listOf(
        "Toutes les sections (all)" to "all",
        "Primaire" to "primaire",
        "CEM" to "cem",
        "Lycée" to "lycee",
    )
    com.example.ui.designsystem.components.input.ElDropdown(
        options = options.map { com.example.ui.designsystem.components.input.ElDropdownOption(value = it.second, label = it.first) },
        selectedValue = level,
        onSelected = { option -> onLevelChange(option.value) },
        label = "Niveau",
        modifier = Modifier.fillMaxWidth(),
    )
}
