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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuditLog
import com.example.domain.repository.AuditRepository
import com.example.ui.components.AuditDiffSheet
import com.example.ui.components.ElTag
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.theme.PrimaryBlue
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

@Composable
fun AuditLogScreen(
    onBack: () -> Unit,
    viewModel: AuditLogViewModel = hiltViewModel(),
) {
    val logs by viewModel.logs.collectAsState()
    var selectedLog by remember { mutableStateOf<AuditLog?>(null) }

    ElScaffold(
        topBar = { ElTopBar(title = "Journal d'audit", onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(logs) { log ->
                AuditLogCard(log, onClick = { selectedLog = log })
            }
        }
    }

    // T-297: every audit record opens the field-level red/green diff sheet
    // (actor attribution + before/after rows) — the same renderer the
    // Personnel stream uses; ONE component, two entry points.
    AuditDiffSheet(
        log = selectedLog,
        onDismiss = { selectedLog = null },
    )
}

@Composable
private fun AuditLogCard(log: AuditLog, onClick: () -> Unit) {
    ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.COMPACT, onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row {
                Text(log.action, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, color = PrimaryBlue, fontSize = 13.sp), modifier = Modifier.weight(1f))
                Text(log.occurredAt.take(19).replace("T", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(log.actorName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                log.actorRole?.let { role -> ElTag(text = role, color = PrimaryBlue) }
            }
            Text("${log.entityType}/${log.entityId.take(8)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            log.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}