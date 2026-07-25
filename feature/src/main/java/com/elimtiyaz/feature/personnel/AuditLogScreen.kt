package com.elimtiyaz.feature.personnel

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AsyncContent
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.AuditEntry

/**
 * AuditLogScreen — full paginated audit log (Route.AuditLog).
 *
 * Filter bar at the top (action dropdown, entity dropdown, actor search,
 * optional date range). Each row shows timestamp, action chip, entity + id,
 * actor name, diff preview. Tap a row to expand the full JSON diff in a Mono
 * font. "Charger plus" button at the bottom reveals the next 50 rows.
 *
 * Gated upstream by [com.elimtiyaz.core.common.Permission.ViewAuditLog]; the
 * hub's Audit tab is hidden when the user lacks the permission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    nav: NavController,
    vm: AuditLogViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journal d'audit", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            AuditFilterBar(state = state, vm = vm)
            HorizontalDivider()
            AsyncContent(
                isLoading = state.isLoading,
                error = state.error,
                items = state.visibleEntries,
                onRetry = vm::load,
                emptyTitle = "Aucun événement",
                emptyDescription = if (state.filteredEntries.isEmpty() && state.allEntries.isNotEmpty())
                    "Aucun événement ne correspond aux filtres."
                else "Les actions sensibles seront enregistrées ici.",
                emptyIcon = Icons.Outlined.History,
            ) { list ->
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                    verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
                ) {
                    items(list, key = { it.id }) { e ->
                        AuditEntryCard(entry = e)
                    }
                    if (state.canLoadMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x4),
                                contentAlignment = Alignment.Center,
                            ) {
                                Button(onClick = vm::loadMore) {
                                    Text("Charger plus (${state.filteredEntries.size - state.visibleEntries.size} restants)")
                                }
                            }
                        }
                    } else if (state.filteredEntries.isNotEmpty()) {
                        item {
                            Text(
                                text = "${state.filteredEntries.size} événement(s) au total",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x4),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditFilterBar(
    state: AuditLogUiState,
    vm: AuditLogViewModel,
) {
    var actionExpanded by remember { mutableStateOf(false) }
    var entityExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ElimtiyazSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
        ) {
            // Action dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = state.actionFilter ?: "Toutes actions",
                    onValueChange = { },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = actionExpanded,
                    onDismissRequest = { actionExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Toutes actions") },
                        onClick = { vm.filterByAction(null); actionExpanded = false },
                    )
                    state.availableActions.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(a, fontFamily = FontFamily.Monospace) },
                            onClick = { vm.filterByAction(a); actionExpanded = false },
                        )
                    }
                }
            }
            // Entity dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = state.entityFilter ?: "Toutes entités",
                    onValueChange = { },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = entityExpanded,
                    onDismissRequest = { entityExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Toutes entités") },
                        onClick = { vm.filterByEntity(null); entityExpanded = false },
                    )
                    state.availableEntityTypes.forEach { e ->
                        DropdownMenuItem(
                            text = { Text(e) },
                            onClick = { vm.filterByEntity(e); entityExpanded = false },
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.actorQuery,
                onValueChange = vm::filterByActor,
                label = { Text("Acteur") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.dateFrom.orEmpty(),
                onValueChange = { vm.filterByDateRange(it.ifBlank { null }, state.dateTo) },
                label = { Text("De (AAAA-MM-JJ)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.dateTo.orEmpty(),
                onValueChange = { vm.filterByDateRange(state.dateFrom, it.ifBlank { null }) },
                label = { Text("À (AAAA-MM-JJ)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (state.actionFilter != null || state.entityFilter != null ||
            state.actorQuery.isNotBlank() || state.dateFrom != null || state.dateTo != null
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                AssistChip(
                    onClick = vm::clearFilters,
                    label = { Text("Réinitialiser") },
                    leadingIcon = { Icon(Icons.Outlined.Clear, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
}

@Composable
private fun AuditEntryCard(entry: AuditEntry) {
    var expanded by remember { mutableStateOf(false) }
    ElImtiyazCard(onClick = { expanded = !expanded }) {
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
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Réduire" else "Développer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(
                text = entry.actorName.ifBlank { entry.actorId },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${entry.entityType} #${entry.entityId.take(12)} • ${Formatters.dateTime(entry.at)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
            entry.note?.takeIf { it.isNotBlank() }?.let { n ->
                Spacer(Modifier.height(ElimtiyazSpacing.x1))
                Text(
                    text = n,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Diff preview (collapsed) or full diff (expanded).
            entry.diff?.takeIf { it.isNotBlank() }?.let { d ->
                Spacer(Modifier.height(ElimtiyazSpacing.x2))
                if (expanded) {
                    HorizontalDivider()
                    Spacer(Modifier.height(ElimtiyazSpacing.x2))
                    Text(
                        text = "Diff JSON",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(ElimtiyazSpacing.x1))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(ElimtiyazSpacing.x3),
                    ) {
                        Text(
                            text = d,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                } else {
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
            // Expand/collapse affordance.
            AnimatedVisibility(visible = expanded) {
                if (entry.ipAddress != null || entry.userAgent != null) {
                    Spacer(Modifier.height(ElimtiyazSpacing.x2))
                    entry.ipAddress?.let { ip ->
                        Text(
                            text = "IP: $ip",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    entry.userAgent?.let { ua ->
                        Text(
                            text = "UA: ${ua.take(80)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
