package com.elimtiyaz.feature.crm

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.AcademicLevel
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.TenancyTier
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AsyncContent
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.EmptyState
import com.elimtiyaz.core.ui.ListRow
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.Parent
import com.elimtiyaz.domain.model.Student

/**
 * RosterScreen — root of the CRM tab (Route.Roster).
 *
 * Two-tab layout (Parents | Élèves). When the user activates the search bar,
 * the tabs collapse into a single mixed results list of parents + students.
 * A FAB opens the Batch Registration wizard — gated on [Permission.CreateParent].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RosterScreen(
    nav: NavController,
    vm: RosterViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val levelFilter by vm.levelFilter.collectAsStateWithLifecycle()

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val canCreate = session?.can(Permission.CreateParent) ?: false
    val searching = query.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (searching) "Résultats" else "Élèves") },
                actions = {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) vm.onQueryChange("")
                    }) {
                        Icon(
                            if (searchOpen) Icons.Outlined.Person else Icons.Outlined.Search,
                            contentDescription = "Rechercher",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (canCreate) {
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate(Route.BatchRegistration.route) },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Nouveau parent") },
                )
            }
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            // Search bar (toggled by the search icon).
            if (searchOpen) {
                TextField(
                    value = query,
                    onValueChange = vm::onQueryChange,
                    placeholder = { Text("Nom, code, téléphone…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
                )
            }

            // Level filter chips — only on the students tab when not searching.
            if (!searching && selectedTab == 1) {
                LevelFilterRow(
                    selected = levelFilter,
                    onSelect = vm::onLevelFilterChange,
                )
            }

            // Tabs collapse when searching.
            if (!searching) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Parents (${state.parents.size})") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Élèves (${state.students.size})") },
                    )
                }
            }

            // Content area.
            when {
                searching -> SearchResultsList(
                    parents = state.filteredParents,
                    students = state.filteredStudents,
                    isLoading = state.isLoading,
                    onParentClick = { nav.navigate(Route.ParentDetail.build(it)) },
                    onStudentClick = { nav.navigate(Route.StudentDetail.build(it)) },
                )
                selectedTab == 0 -> ParentsList(
                    items = state.filteredParents,
                    isLoading = state.parentsLoading,
                    error = state.parentsError,
                    onRetry = vm::reload,
                    onParentClick = { nav.navigate(Route.ParentDetail.build(it)) },
                )
                else -> StudentsList(
                    items = state.filteredStudents,
                    isLoading = state.studentsLoading,
                    error = state.studentsError,
                    onRetry = vm::reload,
                    onStudentClick = { nav.navigate(Route.StudentDetail.build(it)) },
                )
            }
        }
    }
}

@Composable
private fun LevelFilterRow(
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
        horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
    ) {
        FilterChip("Tous", selected == null) { onSelect(null) }
        AcademicLevel.values().forEach { lvl ->
            FilterChip(lvl.displayFr, selected == lvl.key) { onSelect(lvl.key) }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(height = 36.dp, width = 120.dp)
            .padding(0.dp),
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(999.dp),
            color = bg,
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ParentsList(
    items: List<Parent>,
    isLoading: Boolean,
    error: com.elimtiyaz.core.common.AppError?,
    onRetry: () -> Unit,
    onParentClick: (String) -> Unit,
) {
    AsyncContent(
        isLoading = isLoading,
        error = error,
        items = items,
        onRetry = onRetry,
        emptyTitle = "Aucun parent",
        emptyDescription = "Le roster est vide. Touchez « Nouveau parent » pour enregistrer une famille.",
    ) { list ->
        LazyColumn(contentPadding = PaddingValues(ElimtiyazSpacing.x4)) {
            items(list, key = { it.id }) { parent ->
                ParentRow(parent = parent, onClick = { onParentClick(parent.id) })
                Spacer(Modifier.height(ElimtiyazSpacing.x2))
            }
        }
    }
}

@Composable
private fun StudentsList(
    items: List<Student>,
    isLoading: Boolean,
    error: com.elimtiyaz.core.common.AppError?,
    onRetry: () -> Unit,
    onStudentClick: (String) -> Unit,
) {
    AsyncContent(
        isLoading = isLoading,
        error = error,
        items = items,
        onRetry = onRetry,
        emptyTitle = "Aucun élève",
        emptyDescription = "Aucun élève ne correspond à ce filtre.",
    ) { list ->
        LazyColumn(contentPadding = PaddingValues(ElimtiyazSpacing.x4)) {
            items(list, key = { it.id }) { student ->
                StudentRow(student = student, onClick = { onStudentClick(student.id) })
                Spacer(Modifier.height(ElimtiyazSpacing.x2))
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    parents: List<Parent>,
    students: List<Student>,
    isLoading: Boolean,
    onParentClick: (String) -> Unit,
    onStudentClick: (String) -> Unit,
) {
    if (isLoading) {
        com.elimtiyaz.core.ui.LoadingState()
        return
    }
    if (parents.isEmpty() && students.isEmpty()) {
        EmptyState(
            title = "Aucun résultat",
            description = "Aucun parent ni élève ne correspond à votre recherche.",
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(ElimtiyazSpacing.x4)) {
        if (parents.isNotEmpty()) {
            item {
                SectionHeader("Parents")
            }
            items(parents, key = { "p-${it.id}" }) { parent ->
                ParentRow(parent = parent, onClick = { onParentClick(parent.id) })
                Spacer(Modifier.height(ElimtiyazSpacing.x2))
            }
        }
        if (students.isNotEmpty()) {
            item {
                SectionHeader("Élèves")
            }
            items(students, key = { "s-${it.id}" }) { student ->
                StudentRow(student = student, onClick = { onStudentClick(student.id) })
                Spacer(Modifier.height(ElimtiyazSpacing.x2))
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = ElimtiyazSpacing.x2),
    )
}

@Composable
private fun ParentRow(parent: Parent, onClick: () -> Unit) {
    ElImtiyazCard(onClick = onClick) {
        ListRow(
            leading = {
                AvatarCircle(
                    initial = Formatters.initials(parent.firstName, parent.lastName),
                    backgroundColor = ElimtiyazColors.PrimaryBlue,
                )
            },
            title = Formatters.fullName(parent.firstName, parent.lastName),
            subtitle = "${parent.code} • ${parent.phone}",
            trailing = {
                StudentCountBadge(count = parent.students.size)
            },
        )
    }
}

@Composable
private fun StudentRow(student: Student, onClick: () -> Unit) {
    val level = AcademicLevel.from(student.level)
    val tier = TenancyTier.from(student.transportTier)
    ElImtiyazCard(onClick = onClick) {
        ListRow(
            leading = {
                AvatarCircle(
                    initial = Formatters.initials(student.firstName, student.lastName),
                    backgroundColor = ElimtiyazColors.DeepBlue,
                )
            },
            title = Formatters.fullName(student.firstName, student.lastName),
            subtitle = "${student.code} • ${level?.displayFr ?: student.level} ${student.gradeYear}" +
                (tier?.let { " • ${it.displayFr}" } ?: ""),
        )
    }
}

@Composable
private fun StudentCountBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 24.dp)
            .padding(0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Group,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(ElimtiyazSpacing.x1))
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
