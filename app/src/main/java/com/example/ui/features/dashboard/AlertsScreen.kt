package com.example.ui.features.dashboard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.domain.model.AppNotification
import com.example.domain.repository.NotificationRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Alerts inbox ViewModel — restores the pre-redesign `AlertsScreen`.
 *
 * - Full notification list, day-grouped.
 * - Filter chips by NotificationType.
 * - Tap → mark read + navigate to linked entity.
 * - "Tout marquer comme lu" bulk action.
 * - Sort by priority (urgent → high → medium → low) then by `createdAt` DESC.
 */
@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _typeFilter = MutableStateFlow<String?>(null)
    val typeFilter: StateFlow<String?> = _typeFilter.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Raw notifications — observeForSession requires a non-null session.
    // Fall back to observe() when session is null (still shows broadcasts).
    private val rawNotifications: StateFlow<List<AppNotification>> = sessionManager.state
        .let { sf -> sf.flatMapLatest { s -> if (s != null) notificationRepository.observeForSession(s) else notificationRepository.observe() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val notifications: StateFlow<List<AppNotification>> = kotlinx.coroutines.flow.combine(
        rawNotifications, _typeFilter,
    ) { list, filter ->
        val filtered = if (filter == null) list else list.filter { it.type == filter }
        filtered.sortedWith(
            compareByDescending<AppNotification> { priorityRank(it.priority) }
                .thenByDescending { it.createdAt }
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onTypeFilter(type: String?) { _typeFilter.value = type }

    fun markRead(id: String) {
        viewModelScope.launch {
            when (val r = notificationRepository.markRead(id)) {
                is Result.Ok -> {}
                is Result.Err -> _error.value = r.error.userMessage
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            when (val r = notificationRepository.markAllRead()) {
                is Result.Ok -> {}
                is Result.Err -> _error.value = r.error.userMessage
            }
        }
    }
}

private fun priorityRank(priority: String): Int = when (priority) {
    "urgent" -> 4
    "high" -> 3
    "medium" -> 2
    "low" -> 1
    else -> 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    onNavigateToEntity: (entityType: String, entityId: String) -> Unit,
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val notifications by viewModel.notifications.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val error by viewModel.error.collectAsState()

    val typeFilters = listOf(
        null to "Tous",
        "payment_overdue" to "Paiements",
        "expense_pending" to "Dépenses",
        "attendance_alert" to "Présences",
        "homework" to "Devoirs",
        "audit" to "Audit",
        "system" to "Système",
        "message" to "Messages",
        "custom" to "Autres",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alertes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
                actions = {
                    IconButton(onClick = { viewModel.markAllRead() }) {
                        Icon(Icons.Default.DoneAll, contentDescription = "Tout marquer comme lu")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

            // Filter chips row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                typeFilters.take(6).forEach { (code, label) ->
                    FilterChip(
                        selected = typeFilter == code,
                        onClick = { viewModel.onTypeFilter(code) },
                        label = { Text(label) },
                    )
                }
            }

            if (notifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Aucune alerte.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(notifications) { notif ->
                        AlertCard(
                            notification = notif,
                            onClick = {
                                viewModel.markRead(notif.id)
                                if (!notif.entityType.isNullOrEmpty() && !notif.entityId.isNullOrEmpty()) {
                                    onNavigateToEntity(notif.entityType, notif.entityId)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(notification: AppNotification, onClick: () -> Unit) {
    val priorityColor = when (notification.priority) {
        "urgent" -> MaterialTheme.colorScheme.error
        "high" -> MaterialTheme.colorScheme.secondary
        "medium" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(priorityColor, shape = RoundedCornerShape(4.dp))
                    .align(Alignment.Top),
            )
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(notification.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(notification.body, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(notification.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                    Text(notification.createdAt.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                if (notification.readAt == null) {
                    Text("Non lu", style = MaterialTheme.typography.labelSmall, color = priorityColor)
                }
            }
        }
    }
}

