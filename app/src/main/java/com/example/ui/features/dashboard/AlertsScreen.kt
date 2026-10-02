package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.domain.model.AppNotification
import com.example.domain.repository.NotificationRepository
import com.example.session.SessionManager
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.display.ElChipVariant
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.feedback.ElLoadingBlock
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.theme.ElTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Alerts inbox ViewModel — restores the pre-redesign `AlertsScreen`.
 *
 * - Full notification list, priority-sorted (urgent → high → medium → low)
 *   then by `createdAt` DESC.
 * - Filter chips by NotificationType.
 * - Tap → mark read + navigate to linked entity.
 * - "Tout marquer comme lu" bulk action.
 *
 * T-460 pass G-a (issue #3 F-02): an explicit [isLoaded] flag distinguishes
 * "still loading" from "genuinely empty" — the inbox previously showed the
 * empty state during the initial repository read.
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

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    // Raw notifications — observeForSession requires a non-null session.
    // Fall back to observe() when session is null (still shows broadcasts).
    private val rawNotifications: StateFlow<List<AppNotification>> = sessionManager.state
        .let { sf -> sf.flatMapLatest { s -> if (s != null) notificationRepository.observeForSession(s) else notificationRepository.observe() } }
        .onEach { _isLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val notifications: StateFlow<List<AppNotification>> = combine(
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

/**
 * T-460 pass G-a (issue #3 F-06 + F-14): the raw-M3 inbox → the design
 * system, rendered on the SAME [NotificationRow] language as the Dashboard
 * hub's notifications section. Raw type codes no longer render as-is (the
 * [notificationTypeMeta] French labels + icons + tones); "Non lu" is a tone
 * tag, not colour-only text; the loading/empty states are tri-state.
 */
@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    onNavigateToEntity: (entityType: String, entityId: String) -> Unit,
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val c = ElTheme.colors
    val notifications by viewModel.notifications.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoaded by viewModel.isLoaded.collectAsState()

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

    ElScaffold(
        topBar = {
            ElTopBar(
                title = "Alertes",
                onBack = onBack,
                actions = {
                    ElIconButton(
                        icon = Icons.Default.DoneAll,
                        onClick = { viewModel.markAllRead() },
                        contentDescription = "Tout marquer comme lu",
                        tint = c.primary,
                        background = androidx.compose.ui.graphics.Color.Transparent,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
        ) {
            error?.let { message ->
                ElAlertBanner(
                    title = message,
                    severity = ElAlertSeverity.DANGER,
                )
            }

            // Filter chips row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ElTheme.spacing.lg, vertical = ElTheme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
            ) {
                typeFilters.take(6).forEach { (code, label) ->
                    ElChip(
                        text = label,
                        variant = ElChipVariant.FILTER,
                        selected = typeFilter == code,
                        onClick = { viewModel.onTypeFilter(code) },
                    )
                }
            }

            when {
                !isLoaded -> {
                    ElLoadingBlock(
                        message = "Chargement des alertes…",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                notifications.isEmpty() -> {
                    ElEmptyState(
                        icon = Icons.Default.DoneAll,
                        title = "Aucune alerte",
                        subtitle = "Les notifications de gestion apparaîtront ici.",
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = ElTheme.spacing.lg,
                            vertical = ElTheme.spacing.sm,
                        ),
                        verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
                    ) {
                        items(notifications, key = { it.id }) { notif ->
                            val (typeLabel, typeIcon, typeTone) = notificationTypeMeta(notif.type)
                            NotificationRow(
                                title = notif.title,
                                body = notif.body,
                                icon = typeIcon,
                                iconTint = c.primary,
                                metaLabel = typeLabel,
                                metaTone = if (notif.priority == "urgent" || notif.priority == "high") {
                                    notificationPriorityTone(notif.priority)
                                } else {
                                    typeTone
                                },
                                timeLabel = notif.createdAt.take(10),
                                unread = notif.readAt == null,
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
}
