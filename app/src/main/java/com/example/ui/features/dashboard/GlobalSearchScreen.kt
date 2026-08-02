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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Global search ViewModel — restores the pre-redesign `GlobalSearchScreen`.
 *
 * - Debounced 220ms search across parents + students.
 * - Parallel queries.
 * - Results grouped by type.
 */
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
        if (q.length < 2) {
            _parents.value = emptyList()
            _students.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(220)
            _isSearching.value = true
            try {
                // search() returns Flow<List<T>>; snapshot via first()
                val pResult = kotlinx.coroutines.async {
                    parentRepository.search(q).first()
                }
                val sResult = kotlinx.coroutines.async {
                    studentRepository.search(q).first()
                }
                _parents.value = pResult.await()
                _students.value = sResult.await()
            } finally {
                _isSearching.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onBack: () -> Unit,
    onNavigateToParent: (String) -> Unit,
    onNavigateToStudent: (String) -> Unit,
    viewModel: GlobalSearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val parents by viewModel.parents.collectAsState()
    val students by viewModel.students.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recherche globale") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Rechercher un parent ou un élève…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (isSearching) {
                Spacer(Modifier.height(8.dp))
                Text("Recherche…", style = MaterialTheme.typography.bodySmall)
            }

            if (query.length < 2) {
                Spacer(Modifier.height(16.dp))
                Text("Saisissez au moins 2 caractères.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (parents.isNotEmpty()) {
                    item { Text("Parents (${parents.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                    items(parents) { parent ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onNavigateToParent(parent.id) },
                            elevation = CardDefaults.cardElevation(1.dp),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null)
                                Spacer(Modifier.height(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(parent.fullName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("${parent.code} • ${parent.phone}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                if (students.isNotEmpty()) {
                    item { Spacer(Modifier.height(16.dp)) }
                    item { Text("Élèves (${students.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                    items(students) { student ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onNavigateToStudent(student.id) },
                            elevation = CardDefaults.cardElevation(1.dp),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null)
                                Spacer(Modifier.height(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(student.fullName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("${student.code} • ${student.level}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                if (parents.isEmpty() && students.isEmpty() && query.length >= 2 && !isSearching) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Aucun résultat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}
