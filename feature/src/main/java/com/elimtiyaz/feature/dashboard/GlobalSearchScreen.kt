package com.elimtiyaz.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.ExpenseStatus
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.PaymentStatus
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.getOrDefault
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.EmptyState
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.Expense
import com.elimtiyaz.domain.model.Parent
import com.elimtiyaz.domain.model.Payment
import com.elimtiyaz.domain.model.Student
import com.elimtiyaz.domain.repository.ExpenseRepository
import com.elimtiyaz.domain.repository.ParentRepository
import com.elimtiyaz.domain.repository.PaymentRepository
import com.elimtiyaz.domain.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Global search — typed query is fanned out to four repos in parallel:
 *  - [ParentRepository.search] (server-side, returns parents matching name/code/phone)
 *  - [StudentRepository.search] (server-side, returns students matching name/code)
 *  - [PaymentRepository.payments] + client-side filter on receipt number / amount / notes
 *  - [ExpenseRepository.expenses] + client-side filter on title / requestCode / payee
 *
 * The query is debounced 250ms to avoid hammering the backend on every keystroke.
 * Recent (non-blank) queries are kept in memory as a stub for "recent searches".
 *
 * Per master plan §13, XLSX/CSV report generation is desktop-only — this screen
 * is a navigation entry point, not a report generator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    nav: NavController,
    vm: GlobalSearchViewModel = hiltViewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recherche") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Search field
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
                placeholder = { Text("Rechercher un parent, élève, paiement…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = vm::clear) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true,
            )

            if (query.isBlank()) {
                if (recents.isEmpty()) {
                    EmptyState(
                        title = "Recherche globale",
                        description = "Saisissez un nom, un code, un numéro de reçu…",
                        icon = Icons.Outlined.Search,
                    )
                } else {
                    RecentSearches(recents = recents, onSelect = vm::onQueryChange, onClear = vm::clearRecents)
                }
            } else if (results.isLoading) {
                LoadingState(message = "Recherche…")
            } else if (results.totalCount == 0) {
                EmptyState(
                    title = "Aucun résultat",
                    description = "Aucun parent, élève, paiement ou dépense ne correspond à « $query ».",
                    icon = Icons.Outlined.Search,
                )
            } else {
                SearchResultsList(results = results, nav = nav)
            }
        }
    }
}

/** Recent searches — horizontal chips with a clear-all button. */
@Composable
private fun RecentSearches(
    recents: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Recherches récentes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onClear) {
                Icon(Icons.Outlined.Close, contentDescription = "Effacer l'historique")
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
        ) {
            items(recents) { r ->
                AssistChip(
                    onClick = { onSelect(r) },
                    label = { Text(r) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
}

/** Sectioned list of search results — parents, students, payments, expenses. */
@Composable
private fun SearchResultsList(results: SearchResults, nav: NavController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = ElimtiyazSpacing.x4,
            vertical = ElimtiyazSpacing.x2,
        ),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
    ) {
        if (results.parents.isNotEmpty()) {
            item {
                SectionTitle(icon = Icons.Outlined.Group, title = "Parents (${results.parents.size})")
            }
            items(results.parents) { parent ->
                ParentResultRow(parent = parent, onClick = { nav.navigate(Route.ParentDetail.build(parent.id)) })
            }
        }
        if (results.students.isNotEmpty()) {
            item {
                SectionTitle(icon = Icons.Outlined.School, title = "Élèves (${results.students.size})")
            }
            items(results.students) { student ->
                StudentResultRow(student = student, onClick = { nav.navigate(Route.StudentDetail.build(student.id)) })
            }
        }
        if (results.payments.isNotEmpty()) {
            item {
                SectionTitle(icon = Icons.Outlined.Payments, title = "Paiements (${results.payments.size})")
            }
            items(results.payments) { payment ->
                PaymentResultRow(payment = payment, onClick = { nav.navigate(Route.PaymentDetail.build(payment.id)) })
            }
        }
        if (results.expenses.isNotEmpty()) {
            item {
                SectionTitle(icon = Icons.Outlined.Assessment, title = "Dépenses (${results.expenses.size})")
            }
            items(results.expenses) { expense ->
                ExpenseResultRow(expense = expense, onClick = { nav.navigate(Route.ExpenseDetail.build(expense.id)) })
            }
        }
    }
}

/** Section header with leading icon. */
@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ElimtiyazSpacing.x3, bottom = ElimtiyazSpacing.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(ElimtiyazSpacing.x2))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Parent search result row — avatar + name + code + phone. */
@Composable
private fun ParentResultRow(parent: Parent, onClick: () -> Unit) {
    ResultRow(
        initial = Formatters.initials(parent.firstName, parent.lastName),
        title = Formatters.fullName(parent.firstName, parent.lastName),
        subtitle = "${parent.code} · ${parent.phone}",
        onClick = onClick,
    )
}

/** Student search result row — avatar + name + code + level. */
@Composable
private fun StudentResultRow(student: Student, onClick: () -> Unit) {
    ResultRow(
        initial = Formatters.initials(student.firstName, student.lastName),
        title = Formatters.fullName(student.firstName, student.lastName),
        subtitle = "${student.code} · ${student.level} (${student.gradeYear})",
        onClick = onClick,
    )
}

/** Payment search result row — receipt icon + receipt number + amount + status chip. */
@Composable
private fun PaymentResultRow(payment: Payment, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(ElimtiyazSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(ElimtiyazSpacing.x3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = payment.receiptNumber,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = Formatters.currency(payment.amount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PaymentStatus.from(payment.status)?.let {
            StatusChip(label = it.displayFr, tone = paymentTone(it))
        }
    }
}


/** Expense search result row — assessment icon + requestCode + title + status chip. */
@Composable
private fun ExpenseResultRow(expense: Expense, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(ElimtiyazSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(ElimtiyazSpacing.x3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = expense.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${expense.requestCode} · ${Formatters.currency(expense.amount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExpenseStatus.from(expense.status)?.let {
            StatusChip(label = it.displayFr, tone = expenseTone(it))
        }
    }
}

/** Generic search result row with a circular avatar leading. */
@Composable
private fun ResultRow(
    initial: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(ElimtiyazSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarCircle(initial = initial, size = 36)
        Spacer(Modifier.width(ElimtiyazSpacing.x3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Maps a payment status to its status-chip tone. */
private fun paymentTone(status: PaymentStatus): StatusTone = when (status) {
    PaymentStatus.Paid     -> StatusTone.Success
    PaymentStatus.Partial  -> StatusTone.Warning
    PaymentStatus.Pending  -> StatusTone.Warning
    PaymentStatus.Overdue  -> StatusTone.Danger
    PaymentStatus.Refunded -> StatusTone.Neutral
    PaymentStatus.Cancelled-> StatusTone.Neutral
}

/** Maps an expense status to its status-chip tone. */
private fun expenseTone(status: ExpenseStatus): StatusTone = when (status) {
    ExpenseStatus.Draft     -> StatusTone.Neutral
    ExpenseStatus.Submitted -> StatusTone.Warning
    ExpenseStatus.Approved  -> StatusTone.Info
    ExpenseStatus.Rejected  -> StatusTone.Danger
    ExpenseStatus.Disbursed -> StatusTone.Warning
    ExpenseStatus.Settled   -> StatusTone.Success
    ExpenseStatus.Anomaly   -> StatusTone.Danger
}

/**
 * In-memory view-model backing [GlobalSearchScreen]. Holds the current query,
 * recent searches (stub for DataStore-backed persistence), and a combined
 * [SearchResults] flow that fans the query out to four repos in parallel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val parentRepo: ParentRepository,
    private val studentRepo: StudentRepository,
    private val paymentRepo: PaymentRepository,
    private val expenseRepo: ExpenseRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _recents = MutableStateFlow<List<String>>(emptyList())
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    /** Parent matches — uses [ParentRepository.search] (server-side filter). */
    private val parentResults: StateFlow<List<Parent>> = _query
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList())
            else parentRepo.search(q).map { it.getOrDefault(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Student matches — uses [StudentRepository.search]. */
    private val studentResults: StateFlow<List<Student>> = _query
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList())
            else studentRepo.search(q).map { it.getOrDefault(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Payment matches — client-side filter on the full list (no repo search method). */
    private val paymentResults: StateFlow<List<Payment>> = combine(_query, paymentRepo.payments()) { q, r ->
        val list = r.getOrDefault(emptyList())
        if (q.isBlank()) emptyList() else list.filter { matchesPayment(it, q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Expense matches — client-side filter on the full list. */
    private val expenseResults: StateFlow<List<Expense>> = combine(_query, expenseRepo.expenses()) { q, r ->
        val list = r.getOrDefault(emptyList())
        if (q.isBlank()) emptyList() else list.filter { matchesExpense(it, q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Combined results — empty `isEmpty` flag true while waiting for the first emission. */
    val results: StateFlow<SearchResults> = combine(
        parentResults, studentResults, paymentResults, expenseResults,
    ) { p, s, pa, e ->
        SearchResults(parents = p, students = s, payments = pa, expenses = e)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults(isLoading = true))

    /** Update the query and refresh the recent-searches stub. */
    fun onQueryChange(q: String) {
        _query.value = q
    }

    /** Clear the query field. */
    fun clear() {
        _query.value = ""
    }

    /** Persist a successful query to the in-memory recent-searches list (max 8). */
    fun rememberQuery() {
        val q = _query.value.trim()
        if (q.isBlank()) return
        _recents.update { (listOf(q) + it.filter { it != q }).take(8) }
    }

    /** Clear the recent-searches list. */
    fun clearRecents() {
        _recents.value = emptyList()
    }

    /** Payment matching — receipt number, notes, amount string contains the query. */
    private fun matchesPayment(p: Payment, q: String): Boolean {
        val needle = q.lowercase()
        return p.receiptNumber.lowercase().contains(needle) ||
            (p.notes?.lowercase()?.contains(needle) == true) ||
            Formatters.currency(p.amount).lowercase().contains(needle)
    }

    /** Expense matching — title, requestCode, payee, amount string contains the query. */
    private fun matchesExpense(e: Expense, q: String): Boolean {
        val needle = q.lowercase()
        return e.title.lowercase().contains(needle) ||
            e.requestCode.lowercase().contains(needle) ||
            e.payee.lowercase().contains(needle) ||
            Formatters.currency(e.amount).lowercase().contains(needle)
    }
}

/** Combined search results across all four entity types. */
data class SearchResults(
    val parents: List<Parent> = emptyList(),
    val students: List<Student> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val isLoading: Boolean = false,
) {
    val totalCount: Int get() = parents.size + students.size + payments.size + expenses.size
}
