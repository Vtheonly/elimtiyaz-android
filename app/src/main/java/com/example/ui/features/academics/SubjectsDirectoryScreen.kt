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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.domain.repository.SubjectRepository
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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val canManage: Boolean get() = sessionManager.current()?.can(Permission.MANAGE_SUBJECTS) == true

    fun onLevelFilter(level: String?) { _levelFilter.value = level }

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

    fun createSubject(name: String, code: String, level: String, coefficient: Int) {
        if (!canManage) { _error.value = "Permission manquante : MANAGE_SUBJECTS."; return }
        if (name.isBlank() || code.isBlank()) {
            _error.value = "Le nom et le code sont obligatoires."
            return
        }
        viewModelScope.launch {
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            val input = com.example.domain.repository.CreateSubjectInput(
                name = name.trim(),
                nameAr = null,
                code = code.trim().uppercase(),
                level = level.trim().lowercase().ifBlank { "primaire" },
                coefficient = coefficient.coerceAtLeast(1),
                isExtracurricular = false,
            )
            when (val r = subjectRepository.createSubject(input, actorId, actorName)) {
                is Result.Ok -> {}
                is Result.Err -> _error.value = r.error.userMessage
            }
        }
    }

    fun clearError() { _error.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsDirectoryScreen(
    onBack: () -> Unit,
    viewModel: SubjectsDirectoryViewModel = hiltViewModel(),
) {
    val subjects by viewModel.subjects.collectAsState()
    val levelFilter by viewModel.levelFilter.collectAsState()
    val error by viewModel.error.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var archiveTarget by remember { mutableStateOf<Subject?>(null) }

    val filtered = if (levelFilter == null) subjects else subjects.filter { it.level == levelFilter }

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
                            if (viewModel.canManage) {
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
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
        var level by remember { mutableStateOf("primaire") }
        var coef by remember { mutableStateOf("1") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nouvelle matière") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom *") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code *") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(value = level, onValueChange = { level = it }, label = { Text("Niveau (primaire/cem/lycee)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(value = coef, onValueChange = { coef = it.filter { c -> c.isDigit() } }, label = { Text("Coefficient") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val coefInt = coef.toIntOrNull() ?: 1
                    viewModel.createSubject(name, code, level, coefInt)
                    showCreateDialog = false
                }) { Text("Créer") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Annuler") } },
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
