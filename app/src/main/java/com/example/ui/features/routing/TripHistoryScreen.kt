package com.example.ui.features.routing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
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
import com.example.core.Result
import com.example.domain.model.TripLog
import com.example.domain.repository.RoutingRepository
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.feedback.ElLoadingBlock
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.overlays.ElDialogShell
import com.example.ui.designsystem.theme.ElTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * Trip history ViewModel — restores the pre-redesign `TripHistoryViewModel` (commit a34333a).
 *
 * T-460 pass G-c (issue #3 F-02): the isLoaded flag distinguishes the initial
 * repository read from a genuinely empty history.
 */
@HiltViewModel
class TripHistoryViewModel @Inject constructor(
    private val routingRepository: RoutingRepository,
) : ViewModel() {

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    val trips: StateFlow<List<TripLog>> = routingRepository.observeTripHistory()
        .map { result -> when (result) { is Result.Ok -> result.value; is Result.Err -> emptyList() } }
        .onEach { _isLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selected = MutableStateFlow<TripLog?>(null)
    val selected: StateFlow<TripLog?> = _selected.asStateFlow()

    fun select(trip: TripLog?) { _selected.value = trip }
}

/**
 * T-460 pass G-c (issue #3 F-06): the raw-M3 trip history → the design
 * system (ElScaffold/ElTopBar/ElCard + the ElDialogShell detail).
 */
@Composable
fun TripHistoryScreen(
    onBack: () -> Unit,
    viewModel: TripHistoryViewModel = hiltViewModel(),
) {
    val c = ElTheme.colors
    val trips by viewModel.trips.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val isLoaded by viewModel.isLoaded.collectAsState()

    ElScaffold(
        topBar = {
            ElTopBar(
                title = "Historique des tournées",
                onBack = onBack,
            )
        },
    ) { padding ->
        when {
            !isLoaded -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    ElLoadingBlock(message = "Chargement de l'historique…")
                }
            }
            trips.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    ElEmptyState(
                        icon = Icons.Default.Route,
                        title = "Aucune tournée enregistrée",
                        subtitle = "Les tournées terminées apparaîtront ici.",
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(ElTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
                ) {
                    items(trips) { trip ->
                        ElCard(
                            modifier = Modifier.fillMaxWidth(),
                            size = ElCardSize.STANDARD,
                            onClick = { viewModel.select(trip) },
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(ElTheme.spacing.md)) {
                                Text("Véhicule : ${trip.vehicleId}", style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                                Text("Début : ${trip.startedAt}", style = ElTheme.typography.labelSmall, color = c.textSecondary)
                                trip.endedAt?.let { Text("Fin : $it", style = ElTheme.typography.labelSmall, color = c.textSecondary) }
                                Text("Arrêts : ${trip.stopsCompleted}/${trip.stopsPlanned}", style = ElTheme.typography.bodySmall, color = c.textPrimary)
                                Text("Distance : %.2f km".format(trip.totalDistanceKm), style = ElTheme.typography.bodySmall, color = c.textPrimary)
                                trip.notes?.let { Text("Notes : $it", style = ElTheme.typography.labelSmall, color = c.textSecondary) }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { trip ->
        ElDialogShell(onDismissRequest = { viewModel.select(null) }) {
            Column(modifier = Modifier.fillMaxWidth().padding(ElTheme.spacing.xl), verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm)) {
                Text(
                    "Tournée du ${trip.startedAt.take(10)}",
                    style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                )
                Text("Véhicule : ${trip.vehicleId}", style = ElTheme.typography.bodySmall, color = c.textSecondary)
                Text("Chauffeur : ${trip.driverId}", style = ElTheme.typography.bodySmall, color = c.textSecondary)
                Text("Début : ${trip.startedAt}", style = ElTheme.typography.bodySmall, color = c.textSecondary)
                Text("Fin : ${trip.endedAt ?: "En cours"}", style = ElTheme.typography.bodySmall, color = c.textSecondary)
                Text("Arrêts : ${trip.stopsCompleted}/${trip.stopsPlanned}", style = ElTheme.typography.bodySmall, color = c.textSecondary)
                Text("Distance : %.2f km".format(trip.totalDistanceKm), style = ElTheme.typography.bodySmall, color = c.textSecondary)
                trip.notes?.let { Text("Notes : $it", style = ElTheme.typography.bodySmall, color = c.textSecondary) }
                Spacer(Modifier.height(ElTheme.spacing.sm))
                ElButton(
                    text = "Fermer",
                    onClick = { viewModel.select(null) },
                    variant = ElButtonVariant.GHOST,
                    fullWidth = true,
                )
            }
        }
    }
}
