package com.example.ui.features.personnel

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Permission
import com.example.core.Role
import com.example.core.formatDzd
import com.example.domain.model.Personnel
import com.example.domain.model.ReleveEntry
import com.example.domain.repository.PersonnelRepository
import com.example.domain.repository.ReleveRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import java.time.DayOfWeek

/**
 * Personnel detail ViewModel.
 *
 * Restored behavior (commit a34333a):
 *  - Loads personnel + current-week (Mon→Sun) Relevé entries.
 *  - Computes `hoursLoggedThisWeek` from actual Relevé entries (NOT from
 *    `Personnel.weeklyHoursLogged` which the Supabase DTO hardcodes to 0).
 *  - Per-day breakdown (Mon→Sun) of hours.
 *  - Salary visible only to SUPER_ADMIN / FINANCIAL_OFFICER (per desktop §09.04).
 */
@HiltViewModel
class PersonnelDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val personnelRepository: PersonnelRepository,
    private val releveRepository: ReleveRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val personnelId: String = savedStateHandle["personnelId"] ?: ""

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val personnel: StateFlow<Personnel?> = personnelRepository.observeById(personnelId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _weekEntries = MutableStateFlow<List<ReleveEntry>>(emptyList())
    val weekEntries: StateFlow<List<ReleveEntry>> = _weekEntries.asStateFlow()

    val canViewSalary: Boolean
        get() = sessionManager.current()?.let { session ->
            session.hasRole(Role.SUPER_ADMIN) || session.hasRole(Role.FINANCIAL_OFFICER) ||
                session.can(Permission.VIEW_SALARY)
        } ?: false

    init {
        loadWeekReleve()
    }

    fun loadWeekReleve() {
        viewModelScope.launch {
            try {
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                // dayOfWeek.value: 1=Monday..7=Sunday (ISO). Move back to Monday.
                val monday = today.minus(today.dayOfWeek.value - 1, DateTimeUnit.DAY)
                val sunday = monday.plus(6, DateTimeUnit.DAY)
                _isLoading.value = true
                releveRepository.observeByPersonnel(personnelId, monday.toString(), sunday.toString())
                    .collect { result ->
                        val entries = (result as? com.example.core.Result.Ok)?.value ?: emptyList()
                        _weekEntries.value = entries.sortedByDescending { it.recordedAt }
                        _isLoading.value = false
                    }
            } catch (t: Throwable) {
                _error.value = t.message ?: "Erreur de chargement du relevé."
                _isLoading.value = false
            }
        }
    }

    /** Sum of (hoursOut - hoursIn) for the current week, in hours. */
    val hoursLoggedThisWeek: StateFlow<Double> = _weekEntries.asStateFlow().let { sf ->
        sf.map { entries ->
            entries.sumOf { it.durationMinutes?.toDouble()?.div(60.0) ?: 0.0 }
        }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
    }

    /** Per-day breakdown: Mon→Sun hours. */
    val perDayBreakdown: StateFlow<Map<DayOfWeek, Double>> = _weekEntries.asStateFlow().let { sf ->
        sf.map { entries ->
            val map = mutableMapOf<DayOfWeek, Double>()
            entries.forEach { e ->
                val day = try {
                    kotlinx.datetime.LocalDate.parse(e.date).dayOfWeek
                } catch (_: Throwable) { return@forEach }
                val hours = e.durationMinutes?.toDouble()?.div(60.0) ?: 0.0
                map[day] = (map[day] ?: 0.0) + hours
            }
            map
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    }

    val hoursTarget: StateFlow<Int> = personnel
        .map { p -> p?.weeklyHoursTarget ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val recentEntries: StateFlow<List<ReleveEntry>> = _weekEntries.asStateFlow().let { sf ->
        sf.map { entries ->
            entries.take(10)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonnelDetailScreen(
    onBack: () -> Unit,
    onNavigateToReleve: (String) -> Unit,
    viewModel: PersonnelDetailViewModel = hiltViewModel(),
) {
    val personnel by viewModel.personnel.collectAsState()
    val weekEntries by viewModel.weekEntries.collectAsState()
    val recentEntries by viewModel.recentEntries.collectAsState()
    val hoursLogged by viewModel.hoursLoggedThisWeek.collectAsState()
    val hoursTarget by viewModel.hoursTarget.collectAsState()
    val perDay by viewModel.perDayBreakdown.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(personnel?.fullName ?: "Personnel") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
                actions = {
                    IconButton(onClick = {
                        personnel?.phone?.let { phone ->
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(Intent.createChooser(intent, "Appeler"))
                        }
                    }) { Icon(Icons.Default.Call, contentDescription = "Appeler") }
                    personnel?.email?.let { email ->
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                            context.startActivity(Intent.createChooser(intent, "Email"))
                        }) { Icon(Icons.Default.Email, contentDescription = "Email") }
                    }
                },
            )
        },
    ) { padding ->
        if (isLoading && personnel == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Chargement…")
            }
            return@Scaffold
        }
        val p = personnel ?: run {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error ?: "Personnel introuvable.", color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null)
                            }
                            Spacer(Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(p.position, style = MaterialTheme.typography.bodySmall)
                                Text("Catégorie: ${p.staffCategory}", style = MaterialTheme.typography.labelSmall)
                                Text("Statut: ${p.status}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        InfoRow("Téléphone", p.phone)
                        p.email?.let { InfoRow("Email", it) }
                        InfoRow("Date d'embauche", p.hireDate)
                        p.terminationDate?.let { InfoRow("Date de fin", it) }
                        if (viewModel.canViewSalary && p.salary != null) {
                            InfoRow("Salaire", "${(p.salary / 100).formatDzd()} DZD")
                        } else if (!viewModel.canViewSalary && p.salary != null) {
                            Text("Salaire masqué (permission requise)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(8.dp))
                            Text("Heures cette semaine", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        val target = hoursTarget.toDouble().coerceAtLeast(1.0)
                        val pct = (hoursLogged / target).coerceIn(0.0, 1.0)
                        LinearProgressIndicator(
                            progress = { pct.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (pct < 0.5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "%.1f h / %d h".format(hoursLogged, hoursTarget),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (hoursTarget == 0) {
                            Text("Cible hebdomadaire non configurée.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("Répartition par jour", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        DayBarChart(perDay = perDay)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Relevé récent", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onNavigateToReleve(p.id) }) { Text("Saisir") }
                }
            }

            items(recentEntries) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.activity.displayFr, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(entry.date, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${entry.hoursIn}${entry.hoursOut?.let { " → $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                        entry.durationMinutes?.let { min ->
                            Text("%.1f h".format(min / 60.0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DayBarChart(perDay: Map<DayOfWeek, Double>) {
    val days = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
    )
    val labels = listOf("L", "M", "M", "J", "V", "S", "D")
    val maxHours = (perDay.values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { idx, day ->
            val hours = perDay[day] ?: 0.0
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .height((hours / maxHours * 60).coerceAtLeast(2.0).dp)
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.height(4.dp))
                Text(labels[idx], style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
