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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Room
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.AcademicLevel
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AsyncContent
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.ListRow
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.AcademicClass
import com.elimtiyaz.domain.model.Homework
import com.elimtiyaz.domain.model.Subject

/**
 * Root Academics hub tab. Shows three sections (Classes / Matières / Devoirs)
 * behind a [SecondaryTabRow]. Search + level filter apply to the visible tab.
 *
 * FAB is "+ Classe" (Classes tab) or "+ Devoir" (Devoirs tab), gated by
 * [Permission.ManageClasses] and [Permission.AssignHomework] respectively.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicsHubScreen(
    nav: NavController,
    vm: AcademicsHubViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val canManageClasses = session?.can(Permission.ManageClasses) == true
    val canPushHomework = session?.can(Permission.AssignHomework) == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pédagogie") },
            )
        },
        floatingActionButton = {
            when (tab) {
                0 -> if (canManageClasses) {
                    FloatingActionButton(onClick = { /* create-class flow not in v1 scope */ }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Nouvelle classe")
                    }
                }
                2 -> if (canPushHomework) {
                    FloatingActionButton(onClick = {
                        // Pick the first class as default — the hub doesn't know which
                        // class to target, so the user opens homework-push from class detail.
                        state.allClasses.firstOrNull()?.let { c ->
                            nav.navigate(Route.HomeworkPush.build(c.id))
                        }
                    }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Nouveau devoir")
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            // Search row — applies to every tab.
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = vm::onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
                placeholder = { Text("Rechercher une classe, matière, devoir…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )
            SecondaryTabRow(selectedTabIndex = tab) {
                listOf("Classes", "Matières", "Devoirs").forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(label) },
                    )
                }
            }
            when (tab) {
                0 -> ClassesTab(state, onClassClick = { c -> nav.navigate(Route.ClassDetail.build(c.id)) })
                1 -> SubjectsTab(
                    state = state,
                    onLevelFilter = vm::onLevelFilter,
                )
                2 -> HomeworkTab(
                    state = state,
                    onHomeworkClick = { h -> nav.navigate(Route.HomeworkPush.build(h.classId)) },
                )
            }
        }
    }
}

// ----- Classes tab ----------------------------------------------------------

@Composable
private fun ClassesTab(
    state: AcademicsHubUiState,
    onClassClick: (AcademicClass) -> Unit,
) {
    val q = state.searchQuery.trim().lowercase()
    AsyncContent(
        isLoading = state.isLoading,
        error = state.error,
        items = state.allClasses,
        emptyTitle = "Aucune classe",
        emptyDescription = "Créez une classe depuis le bouton + pour commencer.",
        emptyIcon = Icons.Outlined.Class,
        onRetry = { /* reload via VM is automatic on collect */ },
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ElimtiyazSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x4),
        ) {
            if (q.isBlank()) {
                // Default view — grouped horizontal rows per level.
                item { ClassLevelSection("Primaire", state.primaireClasses, onClassClick) }
                item { ClassLevelSection("CEM", state.cemClasses, onClassClick) }
                item { ClassLevelSection("Lycée", state.lyceeClasses, onClassClick) }
            } else {
                // Search view — flat list of matches.
                val matches = state.filteredClasses()
                item {
                    Text(
                        "${matches.size} résultat(s)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(matches, key = { it.id }) { c -> ClassCard(c, onClick = { onClassClick(c) }) }
            }
        }
    }
}

@Composable
private fun ClassLevelSection(
    label: String,
    classes: List<AcademicClass>,
    onClick: (AcademicClass) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = ElimtiyazSpacing.x2),
        )
        if (classes.isEmpty()) {
            Text(
                "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
                contentPadding = PaddingValues(vertical = ElimtiyazSpacing.x2),
            ) {
                items(classes, key = { it.id }) { c ->
                    Box(modifier = Modifier.width(260.dp)) {
                        ClassCard(c, onClick = { onClick(c) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassCard(c: AcademicClass, onClick: () -> Unit) {
    ElImtiyazCard(onClick = onClick) {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(initial = c.name.firstOrNull()?.toString() ?: "?", size = 36)
                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Année ${c.academicYear}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(
                    label = AcademicLevel.from(c.level)?.displayFr ?: c.level,
                    tone = StatusTone.Info,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text(
                    c.homeroomTeacherName ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Outlined.Room, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text(
                    c.room ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${c.enrolledCount}/${c.capacity} élèves",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val fillRatio = if (c.capacity == 0) 0f else c.enrolledCount.toFloat() / c.capacity
                val tone = when {
                    fillRatio >= 1f -> StatusTone.Danger
                    fillRatio >= 0.8f -> StatusTone.Warning
                    else -> StatusTone.Success
                }
                StatusChip(
                    label = "${(fillRatio * 100).toInt()}%",
                    tone = tone,
                )
            }
        }
    }
}

// ----- Subjects tab ---------------------------------------------------------

@Composable
private fun SubjectsTab(
    state: AcademicsHubUiState,
    onLevelFilter: (AcademicLevel?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
            horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
        ) {
            item {
                FilterChip(
                    selected = state.levelFilter == null,
                    onClick = { onLevelFilter(null) },
                    label = { Text("Tous niveaux") },
                )
            }
            items(AcademicLevel.values().toList()) { lvl ->
                FilterChip(
                    selected = state.levelFilter == lvl,
                    onClick = { onLevelFilter(lvl) },
                    label = { Text(lvl.displayFr) },
                )
            }
        }
        val filtered = state.filteredSubjects.let { all ->
            val q = state.searchQuery.trim().lowercase()
            if (q.isBlank()) all
            else all.filter { it.name.lowercase().contains(q) || it.code.lowercase().contains(q) }
        }
        AsyncContent(
            isLoading = state.isLoading,
            error = state.error,
            items = filtered,
            emptyTitle = "Aucune matière",
            emptyDescription = "Aucune matière pour ce niveau.",
            emptyIcon = Icons.Outlined.MenuBook,
        ) { subjects ->
            LazyColumn(
                contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
            ) {
                items(subjects, key = { it.id }) { s -> SubjectRow(s) }
            }
        }
    }
}

@Composable
private fun SubjectRow(s: Subject) {
    ElImtiyazCard {
        ListRow(
            leading = {
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
            },
            title = s.name,
            subtitle = buildString {
                append(AcademicLevel.from(s.level)?.displayFr ?: s.level)
                append(" · Coef. ")
                append(s.coefficient)
                if (s.isExtracurricular) append(" · Hors programme")
            },
            trailing = {
                if (s.isExtracurricular) {
                    StatusChip(label = "Club", tone = StatusTone.Warning)
                } else {
                    StatusChip(label = "Coef ${s.coefficient}", tone = StatusTone.Neutral)
                }
            },
        )
    }
}

// ----- Homework tab ---------------------------------------------------------

@Composable
private fun HomeworkTab(
    state: AcademicsHubUiState,
    onHomeworkClick: (Homework) -> Unit,
) {
    val filtered = state.filteredHomework()
    AsyncContent(
        isLoading = state.isLoading,
        error = state.error,
        items = filtered,
        emptyTitle = "Aucun devoir",
        emptyDescription = "Les devoirs assignés apparaîtront ici.",
        emptyIcon = Icons.Outlined.Assignment,
    ) { list ->
        LazyColumn(
            contentPadding = PaddingValues(ElimtiyazSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
        ) {
            items(list, key = { it.id }) { h -> HomeworkCard(h, onClick = { onHomeworkClick(h) }) }
        }
    }
}

@Composable
private fun HomeworkCard(h: Homework, onClick: () -> Unit) {
    ElImtiyazCard(onClick = onClick) {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text(h.subjectName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                StatusChip(label = "${h.acknowledgedCount} confirmés", tone = StatusTone.Info)
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(h.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (h.description.isNotBlank()) {
                Spacer(Modifier.height(ElimtiyazSpacing.x1))
                Text(
                    h.description.take(140) + if (h.description.length > 140) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text(
                    "Échéance ${Formatters.date(h.dueDate)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "par ${h.teacherName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

