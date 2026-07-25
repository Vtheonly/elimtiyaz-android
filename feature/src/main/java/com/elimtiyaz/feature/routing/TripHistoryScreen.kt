package com.elimtiyaz.feature.routing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AsyncContent
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.TripLog

/**
 * Past trip logs — date, driver, stops completed/planned, distance, duration.
 *
 * Tap a row to open a detail dialog showing the full breakdown (started/ended
 * timestamps, stops ratio, distance, notes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripHistoryScreen(
    nav: NavController,
    vm: TripHistoryViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique des tournées", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { inner ->
        Box(modifier = Modifier.padding(inner)) {
            AsyncContent(
                isLoading = state.isLoading,
                error = state.error,
                items = state.trips,
                onRetry = vm::load,
                emptyTitle = "Aucune tournée",
                emptyDescription = "L'historique des tournées apparaîtra ici.",
                emptyIcon = Icons.Outlined.History,
            ) { trips ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                    verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
                ) {
                    items(trips, key = { it.id }) { trip ->
                        TripLogRow(trip = trip, onClick = { vm.select(trip) })
                    }
                }
            }
        }
    }

    state.selected?.let { trip ->
        TripDetailDialog(trip = trip, onDismiss = { vm.select(null) })
    }
}

// ───────────── trip row ─────────────

@Composable
private fun TripLogRow(trip: TripLog, onClick: () -> Unit) {
    ElImtiyazCard(onClick = onClick) {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = ElimtiyazColors.PrimaryBlue,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        Formatters.dateTime(trip.startedAt),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(ElimtiyazSpacing.x1))
                        Text(
                            "Chauffeur ${trip.driverId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val ratio = if (trip.stopsPlanned == 0) 0f else trip.stopsCompleted.toFloat() / trip.stopsPlanned
                val tone = when {
                    ratio >= 1f -> StatusTone.Success
                    ratio >= 0.5f -> StatusTone.Warning
                    else -> StatusTone.Danger
                }
                StatusChip(
                    label = "${trip.stopsCompleted}/${trip.stopsPlanned}",
                    tone = tone,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DetailStat(icon = Icons.Outlined.Route, label = "Distance", value = "${String.format(java.util.Locale.FRANCE, "%.1f", trip.totalDistanceKm)} km")
                DetailStat(
                    icon = Icons.Outlined.AccessTime,
                    label = "Durée",
                    value = trip.endedAt?.let { end ->
                        val startMs = runCatching { kotlinx.datetime.Instant.parse(trip.startedAt).toEpochMilliseconds() }.getOrDefault(0L)
                        val endMs = runCatching { kotlinx.datetime.Instant.parse(end).toEpochMilliseconds() }.getOrDefault(0L)
                        val min = ((endMs - startMs) / 60_000L).coerceAtLeast(0)
                        "${min} min"
                    } ?: "—",
                )
                DetailStat(icon = Icons.Outlined.CheckCircle, label = "Arrêts", value = "${trip.stopsCompleted}/${trip.stopsPlanned}")
            }
        }
    }
}

@Composable
private fun DetailStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(ElimtiyazSpacing.x1))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ───────────── trip detail dialog ─────────────

@Composable
private fun TripDetailDialog(trip: TripLog, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Tournée du ${Formatters.dateTime(trip.startedAt)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                DialogLine(label = "Véhicule", value = trip.vehicleId)
                DialogLine(label = "Chauffeur", value = trip.driverId)
                DialogLine(label = "Début", value = Formatters.dateTime(trip.startedAt))
                DialogLine(label = "Fin", value = trip.endedAt?.let { Formatters.dateTime(it) } ?: "—")
                DialogLine(label = "Arrêts", value = "${trip.stopsCompleted} / ${trip.stopsPlanned}")
                DialogLine(label = "Distance", value = "${String.format(java.util.Locale.FRANCE, "%.2f", trip.totalDistanceKm)} km")
                trip.notes?.let { DialogLine(label = "Notes", value = it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

@Composable
private fun DialogLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ElimtiyazSpacing.x1),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
