package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Parent
import com.example.domain.model.Student
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.StudentRepository
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.theme.ElTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.ui.designsystem.components.feedback.ElEmptyState

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val parentRepository: ParentRepository,
    private val studentRepository: StudentRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _parents = MutableStateFlow<List<Parent>>(emptyList())
    val parents: StateFlow<List<Parent>> = _parents.asStateFlow()

    private val _students = MutableStateFlow<List<Student>>(emptyList())
    val students: StateFlow<List<Student>> = _students.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null

    fun onQueryChange(q: String) {
        _query.value = q
        searchJob?.cancel()
        if (q.isBlank()) {
            _parents.value = emptyList()
            _students.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(150)
            _isSearching.value = true
            try {
                _parents.value = parentRepository.search(q.trim()).first()
                _students.value = studentRepository.search(q.trim()).first()
            } finally {
                _isSearching.value = false
            }
        }
    }
}

@Composable
fun GlobalSearchScreen(
    onBack: () -> Unit,
    onNavigateToParent: (String) -> Unit,
    onNavigateToStudent: (String) -> Unit,
    viewModel: GlobalSearchViewModel = hiltViewModel(),
) {
    val c = ElTheme.colors
    val query by viewModel.query.collectAsState()
    val parents by viewModel.parents.collectAsState()
    val students by viewModel.students.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    // T-460 pass E: raw M3 Scaffold/TopAppBar/Card/OutlinedTextField → the DS
    // equivalents (the audit's §2.2 "Global search ❌ LEGACY" entry).
    ElScaffold(
        topBar = { ElTopBar(title = "Recherche globale", onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(ElTheme.spacing.lg)) {
            ElTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = "Rechercher un parent ou un élève…",
                placeholder = "Nom, matricule, téléphone...",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (isSearching) {
                Spacer(Modifier.height(ElTheme.spacing.sm))
                Text("Recherche en cours…", style = ElTheme.typography.bodySmall, color = c.primary)
            }

            Spacer(Modifier.height(ElTheme.spacing.md))

            // T-460 pass H (issue #3 F-03): the honest no-results state (the screen
            // previously showed nothing at all for a non-blank query with no hits).
            if (!isSearching && query.isNotBlank() && parents.isEmpty() && students.isEmpty()) {
                ElEmptyState(
                    title = "Aucun résultat",
                    subtitle = "Aucun parent ou élève ne correspond à « ${query.trim()} ».",
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
            ) {
                if (parents.isNotEmpty()) {
                    item {
                        Text(
                            "Parents trouvés (${parents.size})",
                            style = ElTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary,
                        )
                    }
                    items(parents) { parent ->
                        ElCard(
                            modifier = Modifier.fillMaxWidth(),
                            size = ElCardSize.COMPACT,
                            onClick = { onNavigateToParent(parent.id) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ElAvatar(initials = parent.fullName, size = ElAvatarSize.M)
                                Spacer(Modifier.width(ElTheme.spacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(parent.fullName, style = ElTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = c.textPrimary)
                                    Text("${parent.code} • Tél: ${parent.phone}", style = ElTheme.typography.labelSmall, color = c.textSecondary)
                                }
                                ElTag(text = "Parent", tone = ElTagTone.INFO, size = ElTagSize.MD)
                            }
                        }
                    }
                }

                if (students.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(ElTheme.spacing.sm))
                        Text(
                            "Élèves trouvés (${students.size})",
                            style = ElTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary,
                        )
                    }
                    items(students) { student ->
                        ElCard(
                            modifier = Modifier.fillMaxWidth(),
                            size = ElCardSize.COMPACT,
                            onClick = { onNavigateToStudent(student.id) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ElAvatar(initials = student.fullName, size = ElAvatarSize.M)
                                Spacer(Modifier.width(ElTheme.spacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(student.fullName, style = ElTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = c.textPrimary)
                                    Text("${student.code} • ${student.gradeLevel.uppercase()}", style = ElTheme.typography.labelSmall, color = c.textSecondary)
                                }
                                ElTag(text = student.level, tone = ElTagTone.NEUTRAL, size = ElTagSize.MD)
                            }
                        }
                    }
                }

                if (parents.isEmpty() && students.isEmpty() && query.isNotBlank() && !isSearching) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(ElTheme.spacing.xxl), contentAlignment = Alignment.Center) {
                            Text("Aucun résultat pour « $query ».", style = ElTheme.typography.bodyMedium, color = c.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
