package com.elimtiyaz.feature.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.core.common.AcademicLevel
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AsyncContent
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.Subject

/**
 * Flat, searchable alternative to the Matières tab on the Academics hub.
 *
 * Provides a quick lookup of subjects across all levels with the level filter
 * exposed as chips at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsDirectoryScreen(
    nav: NavController,
    vm: AcademicsHubViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Annuaire des matières") },
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
                placeholder = { Text("Rechercher par nom ou code…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ElimtiyazSpacing.x4),
                horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
            ) {
                item {
                    FilterChip(
                        selected = state.levelFilter == null,
                        onClick = { vm.onLevelFilter(null) },
                        label = { Text("Tous niveaux") },
                    )
                }
                items(AcademicLevel.values().toList()) { lvl ->
                    FilterChip(
                        selected = state.levelFilter == lvl,
                        onClick = { vm.onLevelFilter(lvl) },
                        label = { Text(lvl.displayFr) },
                    )
                }
            }
            val filtered = state.filteredSubjects.let { all ->
                val q = query.trim().lowercase()
                if (q.isBlank()) all
                else all.filter { it.name.lowercase().contains(q) || it.code.lowercase().contains(q) }
            }
            AsyncContent(
                isLoading = state.isLoading,
                error = state.error,
                items = filtered,
                emptyTitle = "Aucune matière",
                emptyDescription = "Aucune matière ne correspond à votre recherche.",
                emptyIcon = Icons.Outlined.MenuBook,
                onRetry = { vm.load() },
            ) { subjects ->
                LazyColumn(
                    contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                    verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
                ) {
                    items(subjects, key = { it.id }) { s -> DirectorySubjectRow(s) }
                }
            }
        }
    }
}

@Composable
private fun DirectorySubjectRow(s: Subject) {
    ElImtiyazCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s.code.take(2).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(ElimtiyazSpacing.x3))
            Column(modifier = Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${AcademicLevel.from(s.level)?.displayFr ?: s.level} · Coef ${s.coefficient}${if (s.isExtracurricular) " · Hors programme" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (s.isExtracurricular) {
                StatusChip(label = "Club", tone = StatusTone.Warning)
            } else {
                StatusChip(label = "Coef ${s.coefficient}", tone = StatusTone.Neutral)
            }
        }
    }
}
