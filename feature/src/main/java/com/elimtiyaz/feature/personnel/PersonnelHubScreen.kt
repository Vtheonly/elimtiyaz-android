package com.elimtiyaz.feature.personnel

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AsyncContent
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.EmptyState
import com.elimtiyaz.core.ui.ListRow
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.AuditEntry
import com.elimtiyaz.domain.model.Personnel
import com.elimtiyaz.domain.model.PersonnelStatus
import com.elimtiyaz.domain.model.StaffCategory

/**
 * Personnel hub — root of the Personnel tab (Route.Personnel).
 *
 * TopAppBar with avatar + 4-tab [TabRow] (Annuaire / Relevé / Audit /
 * Workflows). The FAB "+ Personnel" is gated by [Permission.ManagePersonnel].
 * Filter chips by [StaffCategory] appear on the Annuaire and Relevé tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonnelHubScreen(
    nav: NavController,
    vm: PersonnelHubViewModel = hiltViewModel(),
    workflowVm: WorkflowMonitorViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val workflowState by workflowVm.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val canManage = session?.can(Permission.ManagePersonnel) == true
    val canViewAudit = session?.can(Permission.ViewAuditLog) == true
    val canViewReleve = session?.can(Permission.ViewReleve) == true
    val canAccessDriver = session?.can(Permission.AccessDriverMode) == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personnel", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { nav.navigate(Route.Profile.route) }) {
                        AvatarCircle(
                            initial = session?.displayName?.firstOrNull()?.toString() ?: "?",
                            size = 32,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (canManage) {
                ExtendedFloatingActionButton(
                    onClick = {
                        // Personnel create flow is out-of-scope for v1 per master plan §09.04;
                        // the directory mock already seeds realistic data.
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Personnel") },
                )
            }
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            TabRow(selectedTabIndex = selectedTab) {
                val tabs = listOf("Annuaire", "Relevé", "Audit", "Workflows")
                tabs.forEachIndexed { i, label ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(label) },
                    )
                }
            }
            when (selectedTab) {
                0 -> AnnuaireTab(
                    state = state,
                    onCategoryFilter = vm::filterByCategory,
                    onPersonnelClick = { p -> nav.navigate(Route.PersonnelDetail.build(p.id)) },
                    canAccessDriver = canAccessDriver,
                    onOpenDriverMode = { nav.navigate(Route.Routing.route) },
                    onRetry = vm::loadPersonnel,
                )
                1 -> if (canViewReleve) {
                    ReleveTab(
                        state = state,
                        onCategoryFilter = vm::filterByCategory,
                        onLogClick = { p -> nav.navigate(Route.Releve.build(p.id)) },
                        onPersonnelClick = { p -> nav.navigate(Route.PersonnelDetail.build(p.id)) },
                        onRetry = vm::loadPersonnel,
                    )
                } else {
                    PermissionGate(message = "Vous n'avez pas la permission de consulter les relevés d'heures.")
                }
                2 -> if (canViewAudit) {
                    AuditPreviewTab(
                        state = state,
                        onSeeAll = { nav.navigate(Route.AuditLog.route) },
                        onRetry = vm::loadAuditPreview,
                    )
                } else {
                    PermissionGate(message = "Vous n'avez pas la permission de consulter le journal d'audit.")
                }
                3 -> WorkflowsTab(
                    state = workflowState,
                    onSeeAll = { nav.navigate(Route.WorkflowMonitor.route) },
                )
            }
        }
    }
}

// ----- Annuaire tab --------------------------------------------------------

@Composable
private fun AnnuaireTab(
    state: PersonnelHubUiState,
    onCategoryFilter: (StaffCategory?) -> Unit,
    onPersonnelClick: (Personnel) -> Unit,
    canAccessDriver: Boolean,
    onOpenDriverMode: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
            horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
        ) {
            item {
                FilterChip(
                    selected = state.categoryFilter == null,
                    onClick = { onCategoryFilter(null) },
                    label = { Text("Tous") },
                )
            }
            items(StaffCategory.values().toList()) { cat ->
                FilterChip(
                    selected = state.categoryFilter == cat,
                    onClick = { onCategoryFilter(if (state.categoryFilter == cat) null else cat) },
                    label = { Text(cat.displayFr) },
                )
            }
        }
        AsyncContent(
            isLoading = state.personnelLoading,
            error = state.personnelError,
            items = state.filteredPersonnel,
            onRetry = onRetry,
            emptyTitle = "Aucun personnel",
            emptyDescription = "Aucun membre du personnel dans cette catégorie.",
            emptyIcon = Icons.Outlined.Badge,
        ) { list ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
            ) {
                items(list, key = { it.id }) { p ->
                    PersonnelDirectoryRow(
                        personnel = p,
                        canAccessDriver = canAccessDriver,
                        onOpen = { onPersonnelClick(p) },
                        onCall = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.phone}"))
                            context.startActivity(intent)
                        },
                        onOpenDriverMode = onOpenDriverMode,
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonnelDirectoryRow(
    personnel: Personnel,
    canAccessDriver: Boolean,
    onOpen: () -> Unit,
    onCall: () -> Unit,
    onOpenDriverMode: () -> Unit,
) {
    ElImtiyazCard(onClick = onOpen) {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(
                    initial = Formatters.initials(personnel.firstName, personnel.lastName),
                    size = 40,
                    backgroundColor = categoryColor(personnel.staffCategory),
                )
                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Formatters.fullName(personnel.firstName, personnel.lastName),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = personnel.phone.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(
                    label = personnel.staffCategory.displayFr,
                    tone = categoryTone(personnel.staffCategory),
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            // Weekly hours progress bar
            val target = personnel.weeklyHoursTarget.coerceAtLeast(1)
            val logged = personnel.weeklyHoursLogged.coerceAtLeast(0)
            val ratio = (logged.toFloat() / target.toFloat()).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Heures sem.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$logged / ${personnel.weeklyHoursTarget} h",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x1))
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                color = progressColor(ratio),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    label = personnelStatusFr(personnel.status),
                    tone = personnelStatusTone(personnel.status),
                )
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                if (personnel.staffCategory == StaffCategory.Driver && canAccessDriver) {
                    AssistChip(
                        onClick = onOpenDriverMode,
                        label = { Text("Mode chauffeur") },
                        leadingIcon = { Icon(Icons.Outlined.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCall) {
                    Icon(Icons.Outlined.Call, contentDescription = "Appeler")
                }
            }
        }
    }
}

// ----- Relevé tab ----------------------------------------------------------

@Composable
private fun ReleveTab(
    state: PersonnelHubUiState,
    onCategoryFilter: (StaffCategory?) -> Unit,
    onLogClick: (Personnel) -> Unit,
    onPersonnelClick: (Personnel) -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Weekly hours totals header
        ElImtiyazCard(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x4),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Heures cette semaine",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${state.weeklyHoursTotalLogged} / ${state.weeklyHoursTotalTarget} h",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = ElimtiyazColors.PrimaryBlue)
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ElimtiyazSpacing.x4),
            horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
        ) {
            item {
                FilterChip(
                    selected = state.categoryFilter == null,
                    onClick = { onCategoryFilter(null) },
                    label = { Text("Tous") },
                )
            }
            items(StaffCategory.values().toList()) { cat ->
                FilterChip(
                    selected = state.categoryFilter == cat,
                    onClick = { onCategoryFilter(if (state.categoryFilter == cat) null else cat) },
                    label = { Text(cat.displayFr) },
                )
            }
        }
        AsyncContent(
            isLoading = state.personnelLoading,
            error = state.personnelError,
            items = state.filteredPersonnel,
            onRetry = onRetry,
            emptyTitle = "Aucun personnel",
            emptyDescription = "Aucun membre du personnel dans cette catégorie.",
            emptyIcon = Icons.Outlined.Schedule,
        ) { list ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
            ) {
                items(list, key = { it.id }) { p ->
                    ReleveLedgerRow(
                        personnel = p,
                        onLog = { onLogClick(p) },
                        onOpen = { onPersonnelClick(p) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleveLedgerRow(
    personnel: Personnel,
    onLog: () -> Unit,
    onOpen: () -> Unit,
) {
    val target = personnel.weeklyHoursTarget.coerceAtLeast(1)
    val logged = personnel.weeklyHoursLogged.coerceAtLeast(0)
    val ratio = (logged.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    ElImtiyazCard(onClick = onOpen) {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            ListRow(
                leading = {
                    AvatarCircle(
                        initial = Formatters.initials(personnel.firstName, personnel.lastName),
                        size = 36,
                        backgroundColor = categoryColor(personnel.staffCategory),
                    )
                },
                title = Formatters.fullName(personnel.firstName, personnel.lastName),
                subtitle = "${personnel.staffCategory.displayFr} • $logged / ${personnel.weeklyHoursTarget} h",
                trailing = {
                    StatusChip(
                        label = "${(ratio * 100).toInt()}%",
                        tone = progressTone(ratio),
                    )
                },
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                color = progressColor(ratio),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AssistChip(
                    onClick = onLog,
                    label = { Text("Logger") },
                    leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
}

// ----- Audit preview tab ---------------------------------------------------

@Composable
private fun AuditPreviewTab(
    state: PersonnelHubUiState,
    onSeeAll: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Derniers événements",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            AssistChip(
                onClick = onSeeAll,
                label = { Text("Tout voir") },
                leadingIcon = { Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
        AsyncContent(
            isLoading = state.auditLoading,
            error = state.auditError,
            items = state.auditPreview,
            onRetry = onRetry,
            emptyTitle = "Aucun événement",
            emptyDescription = "Les actions sensibles seront enregistrées ici.",
            emptyIcon = Icons.Outlined.History,
        ) { list ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
            ) {
                items(list, key = { it.id }) { e -> AuditPreviewRow(e) }
                item {
                    HorizontalDivider()
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x4),
                        contentAlignment = Alignment.Center,
                    ) {
                        AssistChip(
                            onClick = onSeeAll,
                            label = { Text("Ouvrir le journal complet") },
                            leadingIcon = { Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditPreviewRow(entry: AuditEntry) {
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text(
                    text = entry.action,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(label = entry.entityType, tone = StatusTone.Info)
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(
                text = entry.actorName.ifBlank { entry.actorId },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${entry.entityType} #${entry.entityId.take(8)} • ${Formatters.dateTime(entry.at)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.diff?.takeIf { it.isNotBlank() }?.let { d ->
                Spacer(Modifier.height(ElimtiyazSpacing.x1))
                Text(
                    text = d.take(120) + if (d.length > 120) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ----- Workflows tab -------------------------------------------------------

@Composable
private fun WorkflowsTab(
    state: WorkflowMonitorUiState,
    onSeeAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Exécutions récentes",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            AssistChip(
                onClick = onSeeAll,
                label = { Text("Moniteur") },
                leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
        if (state.runs.isEmpty()) {
            EmptyState(
                title = "Aucune exécution",
                description = "Les exécutions de workflows Edge Functions apparaîtront ici.",
                icon = Icons.Outlined.PlayArrow,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
            ) {
                items(state.runs, key = { it.id }) { r -> WorkflowRunRow(r) }
            }
        }
    }
}

@Composable
private fun WorkflowRunRow(run: WorkflowRun) {
    val tone = when (run.status) {
        WorkflowStatus.Running -> StatusTone.Info
        WorkflowStatus.Success -> StatusTone.Success
        WorkflowStatus.Failed -> StatusTone.Danger
        WorkflowStatus.Cancelled -> StatusTone.Neutral
    }
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = run.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(label = run.status.displayFr, tone = tone)
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x1))
            Text(
                text = "${run.trigger.displayFr} • ${Formatters.dateTime(run.startedAt)} • ${run.durationMs} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(
                text = run.outputPreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ----- shared helpers ------------------------------------------------------

/** Permission-denied placeholder shown when the user lacks the tab's permission. */
@Composable
private fun PermissionGate(message: String) {
    EmptyState(
        title = "Accès refusé",
        description = message,
        icon = Icons.Outlined.Lock,
    )
}

/** Returns the design-system accent color for a [StaffCategory]. */
private fun categoryColor(category: StaffCategory) = when (category) {
    StaffCategory.Teacher -> ElimtiyazColors.PrimaryBlue
    StaffCategory.Administration -> ElimtiyazColors.DeepBlue
    StaffCategory.Support -> ElimtiyazColors.WarmGold
    StaffCategory.Maintenance -> ElimtiyazColors.MutedBrown
    StaffCategory.Driver -> ElimtiyazColors.LightBlue
}

/** StatusChip tone for a [StaffCategory]. */
private fun categoryTone(category: StaffCategory): StatusTone = when (category) {
    StaffCategory.Teacher -> StatusTone.Info
    StaffCategory.Administration -> StatusTone.Success
    StaffCategory.Support -> StatusTone.Warning
    StaffCategory.Maintenance -> StatusTone.Neutral
    StaffCategory.Driver -> StatusTone.Info
}

/** French label for a [PersonnelStatus]. */
private fun personnelStatusFr(status: PersonnelStatus): String = when (status) {
    PersonnelStatus.Active -> "Actif"
    PersonnelStatus.OnLeave -> "En congé"
    PersonnelStatus.Suspended -> "Suspendu"
    PersonnelStatus.Terminated -> "Licencié"
}

/** StatusChip tone for a [PersonnelStatus]. */
private fun personnelStatusTone(status: PersonnelStatus): StatusTone = when (status) {
    PersonnelStatus.Active -> StatusTone.Success
    PersonnelStatus.OnLeave -> StatusTone.Warning
    PersonnelStatus.Suspended -> StatusTone.Danger
    PersonnelStatus.Terminated -> StatusTone.Neutral
}

/** Progress bar color based on logged/target ratio. */
private fun progressColor(ratio: Float) = when {
    ratio >= 1f -> ElimtiyazColors.DangerRed
    ratio >= 0.8f -> ElimtiyazColors.WarmGold
    else -> ElimtiyazColors.SuccessGreen
}

/** StatusChip tone for the progress percentage. */
private fun progressTone(ratio: Float): StatusTone = when {
    ratio >= 1f -> StatusTone.Danger
    ratio >= 0.8f -> StatusTone.Warning
    else -> StatusTone.Success
}


