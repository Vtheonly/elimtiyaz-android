package com.example.ui.features.settings

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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuditLog
import com.example.domain.repository.AuditRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val auditRepository: AuditRepository,
) : ViewModel() {
    val logs: StateFlow<List<AuditLog>> = auditRepository.observe(limit = 200)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    onBack: () -> Unit,
    viewModel: AuditLogViewModel = hiltViewModel(),
) {
    val logs by viewModel.logs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journal d'audit") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(logs) { log ->
                AuditLogCard(log)
            }
        }
    }
}

@Composable
private fun AuditLogCard(log: AuditLog) {
    Card(elevation = CardDefaults.cardElevation(1.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row {
                Text(log.action, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text(log.occurredAt.take(19).replace("T", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text("${log.actorName} • ${log.entityType}/${log.entityId.take(8)}", style = MaterialTheme.typography.bodySmall)
            log.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
