package com.elimtiyaz.feature.academics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Room
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.AcademicLevel
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AsyncContent
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone

/**
 * Flat, searchable alternative to the grouped Classes tab on the Academics hub.
 *
 * Useful when the user wants to scan all classes regardless of level. Not wired
 * into the bottom-nav graph; reached from the hub's overflow menu or a deep link.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassesDirectoryScreen(
    nav: NavController,
    vm: AcademicsHubViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Annuaire des classes") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            OutlinedTextField(
                value = query,
                onValueChange = { q ->
                    query = q
                    vm.onSearch(q)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
                placeholder = { Text("Rechercher par nom, titulaire, salle…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )
            val filtered = state.filteredClasses()
            AsyncContent(
                isLoading = state.isLoading,
                error = state.error,
                items = filtered,
                emptyTitle = "Aucune classe",
                emptyDescription = "Aucune classe ne correspond à votre recherche.",
                onRetry = { vm.load() },
            ) { classes ->
                LazyColumn(
                    contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                    verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
                ) {
                    items(classes, key = { it.id }) { c ->
                        DirectoryClassRow(c, onClick = { nav.navigate(Route.ClassDetail.build(c.id)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryClassRow(c: com.elimtiyaz.domain.model.AcademicClass, onClick: () -> Unit) {
    ElImtiyazCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarCircle(initial = c.name.firstOrNull()?.toString() ?: "?", size = 36)
            Spacer(Modifier.width(ElimtiyazSpacing.x3))
            Column(modifier = Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(ElimtiyazSpacing.x1))
                    Text(
                        c.homeroomTeacherName ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(ElimtiyazSpacing.x3))
                    Icon(Icons.Outlined.Room, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(ElimtiyazSpacing.x1))
                    Text(
                        c.room ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatusChip(
                label = AcademicLevel.from(c.level)?.displayFr ?: c.level,
                tone = StatusTone.Info,
            )
        }
    }
}
