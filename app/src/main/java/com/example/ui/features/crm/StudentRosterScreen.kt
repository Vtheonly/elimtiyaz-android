package com.example.ui.features.crm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
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
import com.example.core.Session
import com.example.domain.model.Student
import com.example.domain.repository.StudentRepository
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElTag
import com.example.ui.components.ElTextField
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StudentRosterViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val students: StateFlow<List<Student>> = _query
        .flatMapLatest { q -> studentRepository.search(q) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setQuery(q: String) { _query.value = q }
}

@Composable
fun StudentRosterScreen(
    session: Session,
    onStudentClick: (String) -> Unit,
    viewModel: StudentRosterViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val students by viewModel.students.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        ElTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = "Rechercher un élève",
            placeholder = "Nom, code...",
            leadingIcon = Icons.Default.Person,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )

        if (students.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Person,
                title = "Aucun élève trouvé",
                message = "Essayez de modifier votre recherche ou ajoutez un nouvel élève.",
                modifier = Modifier.padding(top = 32.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(students) { student ->
                    ElCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onStudentClick(student.id) },
                        compact = true,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ElAvatar(initials = student.fullName, size = 44)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(student.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(student.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${student.gradeLevel} • ${student.level}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (student.status != "active") {
                                ElTag(
                                    text = student.status,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
