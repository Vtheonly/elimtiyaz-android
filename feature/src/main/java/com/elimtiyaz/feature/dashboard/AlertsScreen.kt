package com.elimtiyaz.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.EmptyState
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.OfflineBanner
import com.elimtiyaz.domain.model.AppNotification
import com.elimtiyaz.domain.model.NotificationType

/**
 * Alerts center — the full notification feed, grouped by day, with type filter
 * chips and a "Tout marquer comme lu" action. Tapping a notification marks it
 * as read and deep-links to its entity (parent / student / payment / expense…).
 *
 * Reuses [DashboardViewModel] since notifications are already exposed there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    nav: NavController,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<NotificationType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alertes") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.markAllNotificationsRead() },
                        enabled = state.unreadAlertsCount > 0,
                    ) {
                        Icon(Icons.Outlined.DoneAll, contentDescription = "Tout marquer comme lu")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.isOffline) OfflineBanner()

            // Filter chips
            FilterChipsRow(
                selected = filter,
                onSelect = { filter = if (filter == it) null else it },
                unreadByType = unreadByType(state.recentNotifications),
            )

            val filtered = if (filter == null) {
                state.recentNotifications
            } else {
                state.recentNotifications.filter { it.type == filter }
            }

            when {
                state.isLoading && filtered.isEmpty() -> LoadingState(message = "Chargement des alertes…")
                filtered.isEmpty() -> EmptyState(
                    title = if (filter == null) "Aucune alerte" else "Aucune alerte de ce type",
                    description = if (filter == null) "Vous êtes à jour." else "Essayez un autre filtre.",
                    icon = Icons.Outlined.Notifications,
                )
                else -> AlertsList(
                    notifications = filtered,
                    onTap = { n ->
                        vm.markNotificationRead(n.id)
                        navigateToNotificationEntity(n, nav)
                    },
                )
            }
        }
    }
}

/** Horizontal scrollable row of filter chips — one per [NotificationType]. */
@Composable
private fun FilterChipsRow(
    selected: NotificationType?,
    onSelect: (NotificationType) -> Unit,
    unreadByType: Map<NotificationType, Int>,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
        horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
    ) {
        items(NotificationType.values().toList()) { type ->
            val unread = unreadByType[type] ?: 0
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = {
                    Text(
                        text = if (unread > 0) "${notificationTypeLabel(type)} ($unread)" else notificationTypeLabel(type),
                    )
                },
            )
        }
    }
}

/** Notifications grouped by day, rendered as a sectioned list. */
@Composable
private fun AlertsList(
    notifications: List<AppNotification>,
    onTap: (AppNotification) -> Unit,
) {
    val grouped = remember(notifications) { groupByDay(notifications) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = ElimtiyazSpacing.x4,
            vertical = ElimtiyazSpacing.x2,
        ),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
    ) {
        grouped.forEach { (dayLabel, items) ->
            item(key = "header-$dayLabel") {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        top = ElimtiyazSpacing.x3,
                        bottom = ElimtiyazSpacing.x1,
                    ),
                )
            }
            items(items = items, key = { it.id }) { n ->
                NotificationRow(
                    notification = n,
                    onClick = { onTap(n) },
                )
            }
        }
    }
}

/** Group notifications by dd/MM/yyyy, preserving reverse-chronological order. */
private fun groupByDay(notifications: List<AppNotification>): List<Pair<String, List<AppNotification>>> {
    return notifications
        .sortedByDescending { it.createdAt }
        .groupBy { Formatters.date(it.createdAt) }
        .toList()
}

/** Count unread notifications per [NotificationType] — powers the filter chip badges. */
private fun unreadByType(notifications: List<AppNotification>): Map<NotificationType, Int> =
    notifications
        .filter { it.readAt == null }
        .groupingBy { it.type }
        .eachCount()
