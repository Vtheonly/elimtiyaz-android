package com.example.ui.features.crm

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.core.Session
import com.example.domain.model.Student
import com.example.domain.repository.StudentRepository
import com.example.infrastructure.supabase.SupabaseClientProvider
import com.example.infrastructure.sync.PullSyncRepository
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
import kotlinx.coroutines.launch

@HiltViewModel
class StudentRosterViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val pullSyncRepository: PullSyncRepository,
    private val supabaseProvider: SupabaseClientProvider,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val students: StateFlow<List<Student>> = _query
        .flatMapLatest { q -> studentRepository.search(q) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _isConfigured = MutableStateFlow(supabaseProvider.isConfigured())
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    fun setQuery(q: String) { _query.value = q }

    fun refreshConfigState() {
        _isConfigured.value = supabaseProvider.isConfigured()
    }

    fun syncFromCloud() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        _syncMessage.value = null
        viewModelScope.launch {
            try {
                val res = pullSyncRepository.pullAll()
                when (res) {
                    is Result.Ok -> {
                        _syncMessage.value = "${res.value} enregistrements synchronisés depuis la base de données!"
                    }
                    is Result.Err -> {
                        _syncMessage.value = "Erreur: ${res.error.message}"
                    }
                }
            } catch (e: Exception) {
                _syncMessage.value = "Erreur de connexion: ${e.message}"
            } finally {
                _isSyncing.value = false
                _isConfigured.value = supabaseProvider.isConfigured()
            }
        }
    }

    fun saveConfig(url: String, anonKey: String) {
        supabaseProvider.saveConfig(url, anonKey)
        _isConfigured.value = supabaseProvider.isConfigured()
        syncFromCloud()
    }

    fun getSavedUrl(): String = supabaseProvider.getActiveUrl()
    fun getSavedKey(): String = supabaseProvider.getActiveAnonKey()

    fun clearMessage() { _syncMessage.value = null }
}

@Composable
fun StudentRosterScreen(
    session: Session,
    onStudentClick: (String) -> Unit,
    viewModel: StudentRosterViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val query by viewModel.query.collectAsState()
    val students by viewModel.students.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val isConfigured by viewModel.isConfigured.collectAsState()

    // Trigger load once on first composition
    LaunchedEffect(Unit) {
        if (isConfigured && students.size <= 6) {
            viewModel.syncFromCloud()
        }
    }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Status & Synchronization Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConfigured) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${students.size} élève${if (students.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    if (!isConfigured) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "(mode local)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Synchronisation...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (isConfigured) {
                        // FIX (out of context): the DB-config gear + banner were
                        // removed from this teacher-facing roster — connection
                        // setup now lives in Paramètres → Synchronisation. Only
                        // a plain refresh action remains, and only when a
                        // database is already configured.
                        IconButton(
                            onClick = { viewModel.syncFromCloud() },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rafraîchir depuis la base",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

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
                message = if (query.isBlank()) "Aucun élève inscrit. Créez une inscription famille depuis l'onglet Inscription." else "Essayez de modifier votre recherche.",
                modifier = Modifier.padding(top = 32.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(students, key = { it.id }) { student ->
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

