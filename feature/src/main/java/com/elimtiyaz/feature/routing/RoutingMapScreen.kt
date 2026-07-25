package com.elimtiyaz.feature.routing

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.EmptyState
import com.elimtiyaz.core.ui.ErrorState
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.domain.model.GeoPoint
import com.elimtiyaz.domain.model.RoutingStop
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Full-screen live navigation map.
 *
 * Renders an osmdroid [MapView] with:
 *  - The vehicle's current location (bus marker, follows [RoutingForegroundService.liveLocation]).
 *  - Each stop as a numbered marker with the student's initial.
 *  - A polyline connecting stops (OSRM road geometry if available, straight-line
 *    Haversine fallback otherwise).
 *
 * Overlays:
 *  - Top card: current stop name, next stop name, ETA, distance remaining.
 *  - Bottom action bar: "Arrivé à l'arrêt" + "Terminer la tournée".
 *  - Side drawer: list of stops with drag-to-reorder handles (long-press and
 *    drag to move; the new order is persisted via [RoutingMapViewModel.reorderStops]).
 *
 * Lifecycle:
 *  - On entry: asks for ACCESS_FINE_LOCATION via accompanist, starts
 *    [RoutingForegroundService] (skipped in preview mode), triggers [RoutingMapViewModel.load].
 *  - On exit: stops [RoutingForegroundService] via [RoutingForegroundService.stopTracking].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RoutingMapScreen(
    vehicleId: String,
    nav: NavController,
    preview: Boolean = false,
    vm: RoutingMapViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)

    val canAccess = session?.can(Permission.AccessDriverMode) == true
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // Bootstrap the VM once with the vehicleId + preview flag.
    LaunchedEffect(vehicleId, preview) {
        vm.load(vehicleId, preview = preview)
    }

    // Request location permission on entry (live mode only).
    LaunchedEffect(preview, canAccess) {
        if (canAccess && !preview && !locationPermission.status.isGranted) {
            locationPermission.launchPermissionRequest()
        }
    }

    // Start / stop the foreground service around the screen's lifecycle.
    // The keys include `locationPermission.status.isGranted` so the service
    // starts as soon as the user grants ACCESS_FINE_LOCATION.
    val hasLocationGranted = locationPermission.status.isGranted
    DisposableEffect(preview, canAccess, hasLocationGranted) {
        if (canAccess && !preview && hasLocationGranted && vm.hasLocationPermission()) {
            val label = "Tournée — ${state.vehicle?.driverName ?: "Driver"}"
            RoutingForegroundService.startTracking(
                context = context,
                tripLabel = label,
                stopIndex = state.currentStopIndex,
                stopTotal = state.stops.size,
            )
        }
        onDispose {
            if (!preview) {
                RoutingForegroundService.stopTracking(context)
            }
        }
    }

    if (!canAccess) {
        AccessDeniedMap(modifier = Modifier.fillMaxSize(), onBack = nav::popBackStack)
        return
    }

    var showEndDialog by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            StopsDrawer(
                state = state,
                onReorder = vm::reorderStops,
                onAdvance = vm::advanceStop,
                onClose = { scope.launch { drawerState.close() } },
            )
        },
    ) {
        Scaffold(
            topBar = {
                androidx.compose.material3.TopAppBar(
                    title = {
                        Column {
                            Text(
                                state.vehicle?.plate ?: "Tournée",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (preview) "Aperçu" else "Tournée en cours",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = nav::popBackStack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.DragHandle, contentDescription = "Liste des arrêts")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { inner ->
            Box(modifier = Modifier.fillMaxSize().padding(inner)) {
                when {
                    state.isLoading && state.stops.isEmpty() -> LoadingState(message = "Chargement de l'itinéraire…")
                    state.error != null && state.stops.isEmpty() -> ErrorState(
                        error = state.error!!,
                        onRetry = { vm.load(vehicleId, preview = preview) },
                    )
                    state.stops.isEmpty() -> EmptyState(
                        title = "Aucun arrêt",
                        description = "Aucun arrêt n'est affecté à ce véhicule pour la shift sélectionnée.",
                        icon = Icons.Outlined.LocationOn,
                    )
                    else -> MapContent(
                        state = state,
                        preview = preview,
                        onAdvance = vm::advanceStop,
                        onEnd = { showEndDialog = true },
                    )
                }
            }
        }
    }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("Terminer la tournée ?") },
            text = {
                Text(
                    "Vous avez ramassé ${state.stopsPickedUp} élève(s) sur ${state.stops.size}. " +
                        "Distance parcourue : ${String.format(java.util.Locale.FRANCE, "%.1f", state.distanceCoveredKm)} km.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEndDialog = false
                    vm.endTrip { ok ->
                        if (ok) nav.popBackStack()
                        else scope.launch { snackbarHostState.showSnackbar("Échec de la clôture.") }
                    }
                }) { Text("Terminer") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) { Text("Annuler") }
            },
        )
    }

    // Surface inline errors via snackbar.
    val err = state.error
    LaunchedEffect(err) {
        if (err != null && state.stops.isNotEmpty()) {
            snackbarHostState.showSnackbar(err.userMessage)
            vm.clearError()
        }
    }
}

// ───────────── map content ─────────────

@Composable
private fun MapContent(
    state: RoutingMapUiState,
    preview: Boolean,
    onAdvance: () -> Unit,
    onEnd: () -> Unit,
) {
    val context = LocalContext.current
    // Ensure osmdroid has a user-agent (required for tile downloads).
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setUseDataConnection(true)
                    controller.setZoom(13.0)
                }
            },
            update = { map ->
                map.overlays.clear()
                // 1) Polyline (OSRM road or straight-line fallback).
                if (state.polyline.isNotEmpty()) {
                    val polyline = Polyline().apply {
                        outlinePaint.color = ElimtiyazColors.PrimaryBlue.toArgb()
                        outlinePaint.strokeWidth = 8f
                        setPoints(state.polyline.map { OsmGeoPoint(it.lat, it.lng) })
                    }
                    map.overlays.add(polyline)
                }
                // 2) Stop markers — numbered, with student initial.
                state.stops.forEachIndexed { idx, stop ->
                    val marker = Marker(map).apply {
                        position = OsmGeoPoint(stop.lat, stop.lng)
                        title = "${idx + 1}. ${stop.studentName}"
                        snippet = stop.address
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = makeNumberedMarker(
                            context = ctx,
                            number = idx + 1,
                            label = stop.studentName.firstOrNull()?.toString() ?: "?",
                            done = idx < state.currentStopIndex,
                            current = idx == state.currentStopIndex,
                        )
                    }
                    map.overlays.add(marker)
                }
                // 3) Vehicle current-location marker (bus).
                state.currentLocation?.let { loc ->
                    val bus = Marker(map).apply {
                        position = OsmGeoPoint(loc.lat, loc.lng)
                        title = "Bus — ${state.vehicle?.plate ?: ""}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = makeBusMarker(ctx)
                    }
                    map.overlays.add(bus)
                    // Centre the map on the vehicle the first time we have a fix.
                    if (map.mapCenter.latitude == 0.0 && map.mapCenter.longitude == 0.0) {
                        map.controller.setCenter(OsmGeoPoint(loc.lat, loc.lng))
                    }
                } ?: run {
                    // Centre on the first stop while we wait for a GPS fix.
                    state.stops.firstOrNull()?.let { s ->
                        if (map.mapCenter.latitude == 0.0 && map.mapCenter.longitude == 0.0) {
                            map.controller.setCenter(OsmGeoPoint(s.lat, s.lng))
                        }
                    }
                }
                map.invalidate()
            },
        )

        // Top overlay card — current stop / next stop / ETA / distance.
        TripOverlayCard(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(ElimtiyazSpacing.x4),
        )

        // Bottom action bar.
        TripActionBar(
            state = state,
            preview = preview,
            onAdvance = onAdvance,
            onEnd = onEnd,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(ElimtiyazSpacing.x4),
        )
    }
}

// ───────────── overlay card ─────────────

@Composable
private fun TripOverlayCard(
    state: RoutingMapUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = ElimtiyazColors.PrimaryBlue,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text(
                    "Arrêt actuel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x1))
            Text(
                state.currentStop?.let { "${state.currentStopIndex + 1}. ${it.studentName}" } ?: "Tournée terminée",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            state.currentStop?.let {
                Text(
                    it.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Prochain arrêt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        state.nextStop?.studentName ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(ElimtiyazSpacing.x1))
                        Text(
                            "ETA ${state.etaMin.toInt()} min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        "Reste ${String.format(java.util.Locale.FRANCE, "%.1f", state.distanceRemainingKm)} km",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${state.stopsPickedUp}/${state.stops.size} élèves ramassés",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    state.polylineSource.displayFr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ───────────── action bar ─────────────

@Composable
private fun TripActionBar(
    state: RoutingMapUiState,
    preview: Boolean,
    onAdvance: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
    ) {
        Button(
            onClick = onAdvance,
            enabled = !preview && state.currentStop != null,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(ElimtiyazSpacing.x2))
            Text(if (state.isAtLastStop) "Dernier arrêt" else "Arrivé à l'arrêt")
        }
        OutlinedButton(
            onClick = onEnd,
            enabled = !preview,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(ElimtiyazSpacing.x2))
            Text("Terminer")
        }
    }
}

// ───────────── side drawer with stops + drag-to-reorder ─────────────

@Composable
private fun StopsDrawer(
    state: RoutingMapUiState,
    onReorder: (Int, Int) -> Unit,
    onAdvance: () -> Unit,
    onClose: () -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.fillMaxHeight(0.85f)) {
        Column(modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Itinéraire (${state.stops.size} arrêts)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Fermer")
                }
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(
                "Glissez un arrêt par la poignée pour le réordonner. L'ordre est persisté à chaque déplacement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            LazyColumn(
                state = rememberLazyListState(),
                verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
            ) {
                items(state.stops, key = { it.id }) { stop ->
                    val index = state.stops.indexOf(stop)
                    StopReorderRow(
                        stop = stop,
                        index = index,
                        isCurrent = index == state.currentStopIndex,
                        isDone = index < state.currentStopIndex,
                        onMoveUp = { if (index > 0) onReorder(index, index - 1) },
                        onMoveDown = { if (index < state.stops.size - 1) onReorder(index, index + 1) },
                        onDragTo = { target -> onReorder(index, target) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StopReorderRow(
    stop: RoutingStop,
    index: Int,
    isCurrent: Boolean,
    isDone: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragTo: (Int) -> Unit,
) {
    var dragOffsetY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val containerColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        isDone -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(stop.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragOffsetY = 0f },
                    onDragEnd = { dragOffsetY = 0f },
                    onDragCancel = { dragOffsetY = 0f },
                ) { _, drag ->
                    dragOffsetY += drag.y
                    // Each row is ~64dp tall — when drag exceeds half, swap.
                    val rowHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
                    val moves = (dragOffsetY / rowHeightPx).toInt()
                    if (moves != 0) {
                        onDragTo((index + moves).coerceIn(0, Int.MAX_VALUE))
                        dragOffsetY = 0f
                    }
                }
            },
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isCurrent) ElimtiyazColors.PrimaryBlue
                        else if (isDone) ElimtiyazColors.SuccessGreen
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCurrent || isDone) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(ElimtiyazSpacing.x3))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stop.studentName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stop.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onMoveUp, enabled = index > 0) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = "Monter")
            }
            IconButton(onClick = onMoveDown, enabled = index < Int.MAX_VALUE) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = "Descendre")
            }
        }
    }
}

// ───────────── access-denied (map variant) ─────────────

@Composable
private fun AccessDeniedMap(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(
        modifier = modifier.padding(ElimtiyazSpacing.x6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = ElimtiyazColors.DangerRed, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(ElimtiyazSpacing.x4))
        Text("Accès refusé", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(ElimtiyazSpacing.x2))
        Text(
            "Le mode conducteur est réservé aux chauffeurs affectés.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(ElimtiyazSpacing.x4))
        Button(onClick = onBack) { Text("Retour") }
    }
}

// ───────────── marker bitmap factories ─────────────

/** Build a numbered, coloured teardrop marker for a stop. */
private fun makeNumberedMarker(
    context: Context,
    number: Int,
    label: String,
    done: Boolean,
    current: Boolean,
): BitmapDrawable {
    val size = 96
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = when {
        current -> ElimtiyazColors.PrimaryBlue.toArgb()
        done -> ElimtiyazColors.SuccessGreen.toArgb()
        else -> ElimtiyazColors.WarmGold.toArgb()
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    // Draw a teardrop shape.
    val cx = size / 2f
    val cy = size / 2f - 8f
    val r = 28f
    canvas.drawCircle(cx, cy, r, fillPaint)
    canvas.drawCircle(cx, cy, r, strokePaint)
    // Pointer triangle.
    val path = android.graphics.Path().apply {
        moveTo(cx - r / 2, cy + r / 1.6f)
        lineTo(cx + r / 2, cy + r / 1.6f)
        lineTo(cx, size - 12f)
        close()
    }
    canvas.drawPath(path, fillPaint)
    canvas.drawPath(path, strokePaint)
    // Number + label text.
    val text = if (current) label else number.toString()
    val bounds = Rect()
    textPaint.getTextBounds(text, 0, text.length, bounds)
    canvas.drawText(text, cx, cy + bounds.height() / 2f, textPaint)
    return BitmapDrawable(context.resources, bmp)
}

/** Build a simple circular bus marker for the vehicle's current position. */
private fun makeBusMarker(context: Context): BitmapDrawable {
    val size = 80
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ElimtiyazColors.DangerRed.toArgb()
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 36f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val cx = size / 2f
    val cy = size / 2f
    canvas.drawCircle(cx, cy, size / 2f - 4f, fillPaint)
    canvas.drawCircle(cx, cy, size / 2f - 4f, strokePaint)
    val bounds = Rect()
    textPaint.getTextBounds("B", 0, 1, bounds)
    canvas.drawText("B", cx, cy + bounds.height() / 2f, textPaint)
    return BitmapDrawable(context.resources, bmp)
}
