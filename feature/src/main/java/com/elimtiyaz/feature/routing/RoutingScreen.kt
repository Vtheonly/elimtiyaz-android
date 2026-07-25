package com.elimtiyaz.feature.routing

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Accessible
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BusAlert
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AsyncContent
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.EmptyState
import com.elimtiyaz.core.ui.ErrorState
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.Vehicle
import kotlinx.coroutines.launch

/**
 * Routing hub screen — entry point of the driver-mode feature.
 *
 * Permission gate: the entire feature requires [Permission.AccessDriverMode].
 * Users without it see an "Accès refusé" panel instead of the vehicle list.
 *
 * The hub renders the shift filter (Matin / Après-midi / Les deux) and a
 * vertically-scrolling list of vehicle cards. Each card shows plate, driver,
 * capacity badge, wheelchair-lift icon, stop count, total distance + duration,
 * and the three action buttons: **Optimiser**, **Démarrer**, **Voir sur carte**.
 *
 * The "+ Affecter" FAB opens an assignment bottom sheet (a placeholder UI in
 * v1 — the actual student-to-vehicle assignment is a v2 concern).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingScreen(
    nav: NavController,
    vm: RoutingViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAssignmentSheet by remember { mutableStateOf(false) }

    val canAccess = session?.can(Permission.AccessDriverMode) == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tournées", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Route.TripHistory.route) }) {
                        Icon(Icons.Outlined.History, contentDescription = "Historique")
                    }
                },
            )
        },
        floatingActionButton = {
            if (canAccess) {
                ExtendedFloatingActionButton(
                    onClick = { showAssignmentSheet = true },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Affecter") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        if (!canAccess) {
            AccessDeniedPanel(modifier = Modifier.padding(inner))
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            ShiftFilterRow(state.shiftFilter, vm::onShiftFilter)
            when {
                state.isLoading && state.vehicles.isEmpty() -> LoadingState(message = "Chargement des véhicules…")
                state.error != null && state.vehicles.isEmpty() -> ErrorState(
                    error = state.error!!,
                    onRetry = vm::load,
                    modifier = Modifier.fillMaxSize(),
                )
                state.vehicles.isEmpty() -> EmptyState(
                    title = "Aucun véhicule",
                    description = "Aucun véhicule n'est enregistré pour la rentrée.",
                    icon = Icons.Outlined.DirectionsBus,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                    verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
                ) {
                    items(state.vehicles, key = { it.id }) { v ->
                        val summary = state.summaryFor(v)
                        val isOptimising = v.id in state.optimising
                        val optimised = state.optimisations[v.id]
                        VehicleCard(
                            vehicle = v,
                            summary = summary,
                            isOptimising = isOptimising,
                            optimisedStops = optimised?.stops ?: emptyList(),
                            onOptimise = { vm.optimise(v.id) },
                            onStart = {
                                vm.startTrip(v.id) { trip ->
                                    if (trip != null) {
                                        nav.navigate(Route.RoutingMap.build(v.id))
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Impossible de démarrer la tournée.")
                                        }
                                    }
                                }
                            },
                            onPreview = { nav.navigate(Route.RoutingMap.build(v.id)) },
                        )
                    }
                }
            }
        }
    }

    if (showAssignmentSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAssignmentSheet = false },
            sheetState = sheetState,
        ) {
            AssignmentSheetContent(
                vehicles = state.vehicles,
                onClose = { showAssignmentSheet = false },
                onAssign = { _ ->
                    scope.launch {
                        showAssignmentSheet = false
                        snackbarHostState.showSnackbar("Affectation enregistrée.")
                    }
                },
            )
        }
    }

    // Surface errors via snackbar without overwriting the screen-level error state.
    val err = state.error
    androidx.compose.runtime.LaunchedEffect(err) {
        if (err != null && state.vehicles.isNotEmpty()) {
            snackbarHostState.showSnackbar(err.userMessage)
            vm.clearError()
        }
    }
}

// ───────────── shift filter row ─────────────

@Composable
private fun ShiftFilterRow(
    current: RoutingShiftFilter,
    onFilter: (RoutingShiftFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
        horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
    ) {
        items(RoutingShiftFilter.values().toList()) { f ->
            FilterChip(
                selected = current == f,
                onClick = { onFilter(f) },
                label = { Text(f.displayFr) },
            )
        }
    }
}

// ───────────── vehicle card ─────────────

@Composable
private fun VehicleCard(
    vehicle: Vehicle,
    summary: VehicleRouteSummary,
    isOptimising: Boolean,
    optimisedStops: List<com.elimtiyaz.domain.model.RoutingStop>,
    onOptimise: () -> Unit,
    onStart: () -> Unit,
    onPreview: () -> Unit,
) {
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DirectionsBus,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        vehicle.plate,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Chauffeur : ${vehicle.driverName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (vehicle.hasWheelchairLift) {
                    Icon(
                        Icons.Outlined.Accessible,
                        contentDescription = "À accès réduit",
                        tint = ElimtiyazColors.PrimaryBlue,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    label = "Capacité ${vehicle.capacity}",
                    tone = StatusTone.Neutral,
                )
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                StatusChip(
                    label = "${summary.stopCount} arrêts",
                    tone = StatusTone.Info,
                )
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                if (summary.isOptimised) {
                    StatusChip(label = "Optimisée", tone = StatusTone.Success)
                }
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SummaryStat(
                    icon = Icons.Outlined.Route,
                    label = "Distance",
                    value = "${String.format(java.util.Locale.FRANCE, "%.1f", summary.totalDistanceKm)} km",
                )
                SummaryStat(
                    icon = Icons.Outlined.Schedule,
                    label = "Durée",
                    value = "${summary.totalDurationMin.toInt()} min",
                )
                SummaryStat(
                    icon = Icons.Outlined.LocationOn,
                    label = "Arrêts",
                    value = "${summary.stopCount}",
                )
            }
            if (optimisedStops.isNotEmpty()) {
                Spacer(Modifier.height(ElimtiyazSpacing.x3))
                Text(
                    "Itinéraire optimisé",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(ElimtiyazSpacing.x1))
                optimisedStops.take(5).forEachIndexed { i, stop ->
                    Text(
                        "${i + 1}. ${stop.studentName} — ${stop.address}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (optimisedStops.size > 5) {
                    Text(
                        "+ ${optimisedStops.size - 5} autres arrêts…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
            ) {
                OutlinedButton(
                    onClick = onOptimise,
                    enabled = !isOptimising,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isOptimising) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(Icons.Outlined.Route, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(ElimtiyazSpacing.x2))
                    Text("Optimiser")
                }
                FilledTonalButton(
                    onClick = onPreview,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(ElimtiyazSpacing.x2))
                    Text("Carte")
                }
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text("Démarrer la tournée")
            }
        }
    }
}

@Composable
private fun SummaryStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.height(ElimtiyazSpacing.x1))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ───────────── access-denied panel ─────────────

@Composable
private fun AccessDeniedPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(ElimtiyazSpacing.x6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = ElimtiyazColors.DangerRed,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(ElimtiyazSpacing.x4))
        Text(
            "Accès refusé",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(ElimtiyazSpacing.x2))
        Text(
            "Le mode conducteur est réservé aux chauffeurs affectés. Contactez un administrateur si vous pensez que c'est une erreur.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ───────────── assignment bottom sheet ─────────────

@Composable
private fun AssignmentSheetContent(
    vehicles: List<Vehicle>,
    onClose: () -> Unit,
    onAssign: (List<Pair<String, String>>) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ElimtiyazSpacing.x4),
    ) {
        Text(
            "Affecter les élèves aux véhicules",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(ElimtiyazSpacing.x2))
        Text(
            "Sélectionnez les arrêts à affecter à chaque véhicule. L'affectation est enregistrée et sera utilisée lors de l'optimisation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(ElimtiyazSpacing.x4))
        if (vehicles.isEmpty()) {
            EmptyState(
                title = "Aucun véhicule",
                description = "Créez un véhicule avant d'affecter des élèves.",
                icon = Icons.Outlined.BusAlert,
            )
        } else {
            vehicles.forEach { v ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ElimtiyazSpacing.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.DirectionsBus, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(ElimtiyazSpacing.x3))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(v.plate, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Capacité ${v.capacity} ${if (v.hasWheelchairLift) "• PMR" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AssistChip(
                        onClick = { onAssign(emptyList()) },
                        label = { Text("Affecter") },
                    )
                }
            }
        }
        Spacer(Modifier.height(ElimtiyazSpacing.x4))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Fermer")
        }
        Spacer(Modifier.height(ElimtiyazSpacing.x4))
    }
}
