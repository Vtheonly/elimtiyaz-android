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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.domain.repository.UpdatePersonnelInput
import com.example.session.SessionManager
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.components.feedback.ElLoadingBlock
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.overlays.ElDialogShell
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.util.PhoneUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

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

    val canManage: Boolean
        get() = sessionManager.current()?.can(Permission.MANAGE_PERSONNEL) == true ||
            sessionManager.current()?.let { session ->
                session.hasRole(Role.SUPER_ADMIN) || session.hasRole(Role.MANAGER)
            } == true

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun updatePersonnel(id: String, input: UpdatePersonnelInput) {
        viewModelScope.launch {
            _busy.value = true
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = personnelRepository.updatePersonnel(id, input, actorId, actorName)) {
                is com.example.core.Result.Ok -> _message.value = "Employé mis à jour."
                is com.example.core.Result.Err -> _error.value = result.error.userMessage
            }
            _busy.value = false
        }
    }

    fun deletePersonnel(id: String) {
        viewModelScope.launch {
            _busy.value = true
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = personnelRepository.deletePersonnel(id, actorId, actorName)) {
                is com.example.core.Result.Ok -> _message.value = "Employé marqué comme terminé."
                is com.example.core.Result.Err -> _error.value = result.error.userMessage
            }
            _busy.value = false
        }
    }

    fun clearMessages() {
        _error.value = null
        _message.value = null
    }

    init {
        loadWeekReleve()
    }

    fun loadWeekReleve() {
        viewModelScope.launch {
            try {
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
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

    val hoursLoggedThisWeek: StateFlow<Double> = _weekEntries.asStateFlow().let { sf ->
        sf.map { entries ->
            entries.sumOf { it.durationMinutes?.toDouble()?.div(60.0) ?: 0.0 }
        }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
    }

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
        sf.map { entries -> entries.take(10) }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }
}

/**
 * T-460 pass G-b (issue #3 F-06): the raw-M3 PersonnelDetail screen → the
 * design system. ElScaffold/ElTopBar chrome with the manage actions as
 * ElIconButtons; the loading gate on ElLoadingBlock (was a bare "Chargement…"
 * Text); ElAvatar + ElTag status; ElLinearProgress for the hours target;
 * edit/delete dialogs on ElDialogShell + ElTextField. All VM logic, the RBAC
 * gates (canViewSalary/canManage), the T-324 call-affordance rule and the
 * relevé flow are preserved verbatim.
 */
@Composable
fun PersonnelDetailScreen(
    onBack: () -> Unit,
    onNavigateToReleve: (String) -> Unit,
    viewModel: PersonnelDetailViewModel = hiltViewModel(),
) {
    val c = ElTheme.colors
    val context = LocalContext.current
    val personnel by viewModel.personnel.collectAsState()
    val recentEntries by viewModel.recentEntries.collectAsState()
    val hoursLogged by viewModel.hoursLoggedThisWeek.collectAsState()
    val hoursTarget by viewModel.hoursTarget.collectAsState()
    val perDay by viewModel.perDayBreakdown.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    ElScaffold(
        topBar = {
            ElTopBar(
                title = personnel?.fullName ?: "Personnel",
                onBack = onBack,
                actions = {
                    if (viewModel.canManage && personnel != null) {
                        ElIconButton(
                            icon = Icons.Default.Edit,
                            onClick = { showEditDialog = true },
                            contentDescription = "Modifier l'employé",
                            tint = c.primary,
                            background = androidx.compose.ui.graphics.Color.Transparent,
                        )
                        ElIconButton(
                            icon = Icons.Default.Delete,
                            onClick = { showDeleteDialog = true },
                            contentDescription = "Retirer l'employé",
                            tint = c.danger,
                            background = androidx.compose.ui.graphics.Color.Transparent,
                        )
                    }
                    // T-324: the call affordance is only tappable with a real
                    // number (it used to toast a confusing error on blank).
                    if (!personnel?.phone.isNullOrBlank()) {
                        ElIconButton(
                            icon = Icons.Default.Call,
                            onClick = {
                                personnel?.phone?.let { PhoneUtils.dial(context, it) }
                            },
                            contentDescription = "Appeler",
                            tint = c.primary,
                            background = androidx.compose.ui.graphics.Color.Transparent,
                        )
                    }
                    personnel?.email?.let { email ->
                        ElIconButton(
                            icon = Icons.Default.Email,
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                                context.startActivity(Intent.createChooser(intent, "Email"))
                            },
                            contentDescription = "Email",
                            tint = c.primary,
                            background = androidx.compose.ui.graphics.Color.Transparent,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (isLoading && personnel == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                ElLoadingBlock(message = "Chargement de la fiche employé…")
            }
            return@ElScaffold
        }
        val p = personnel ?: run {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error ?: "Personnel introuvable.", color = c.danger, style = ElTheme.typography.bodyMedium)
            }
            return@ElScaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = ElCardSize.STANDARD,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ElAvatar(
                                initials = p.fullName.split(" ").filter { it.isNotBlank() }.take(2)
                                    .map { it.first().uppercase() }.joinToString(""),
                                size = ElAvatarSize.L,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.fullName, style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                                Text(p.position, style = ElTheme.typography.bodySmall, color = c.textSecondary)
                                Text("Catégorie : ${p.staffCategory}", style = ElTheme.typography.labelSmall, color = c.textSecondary)
                                // T-324: humanized status label (was the raw code).
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Statut :", style = ElTheme.typography.labelSmall, color = c.textSecondary)
                                    ElTag(
                                        text = personnelStatusLabel(p.status),
                                        tone = if (p.status == "active") ElTagTone.SUCCESS else ElTagTone.DANGER,
                                        size = ElTagSize.SM,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        InfoRow("Téléphone", p.phone)
                        p.email?.let { InfoRow("Email", it) }
                        InfoRow("Date d'embauche", p.hireDate)
                        p.terminationDate?.let { InfoRow("Date de fin", it) }
                        if (viewModel.canViewSalary && p.salary != null) {
                            InfoRow("Salaire", "${(p.salary / 100).formatDzd()} DZD")
                        }
                        message?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = c.primary, style = ElTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = ElCardSize.STANDARD,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
                            Text("Heures cette semaine", style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                        }
                        Spacer(Modifier.height(8.dp))
                        val target = hoursTarget.toDouble().coerceAtLeast(1.0)
                        val pct = (hoursLogged / target).coerceIn(0.0, 1.0)
                        ElLinearProgress(progress = pct.toFloat())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "%.1f h / %d h".format(hoursLogged, hoursTarget),
                            style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = c.textPrimary,
                        )

                        Spacer(Modifier.height(12.dp))
                        Text("Répartition par jour", style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
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
                    Text("Relevé récent", style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                    ElButton(
                        text = "Saisir",
                        onClick = { onNavigateToReleve(p.id) },
                        variant = ElButtonVariant.GHOST,
                        size = com.example.ui.designsystem.components.button.ElButtonSize.SMALL,
                    )
                }
            }

            items(recentEntries) { entry ->
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = ElCardSize.COMPACT,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.activity.displayFr, style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = c.textPrimary, modifier = Modifier.weight(1f))
                            Text(entry.date, style = ElTheme.typography.labelSmall, color = c.textSecondary)
                        }
                        Text("${entry.hoursIn}${entry.hoursOut?.let { " → $it" } ?: ""}", style = ElTheme.typography.bodySmall, color = c.textSecondary)
                        entry.durationMinutes?.let { min ->
                            Text("%.1f h".format(min / 60.0), style = ElTheme.typography.bodySmall, color = c.primary)
                        }
                    }
                }
            }

            if (recentEntries.isEmpty()) {
                // T-324: empty state for the relevé list (was a silent blank).
                item {
                    Text(
                        "Aucun relevé cette semaine. Utilisez « Saisir » pour enregistrer les heures travaillées.",
                        style = ElTheme.typography.bodySmall,
                        color = c.textSecondary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (showEditDialog && personnel != null) {
        val p = personnel!!
        var phone by remember { mutableStateOf(p.phone) }
        var email by remember { mutableStateOf(p.email ?: "") }
        var position by remember { mutableStateOf(p.position) }
        var salaryDzd by remember { mutableStateOf(p.salary?.let { (it / 100).toString() } ?: "") }
        var status by remember { mutableStateOf(if (p.status == "terminated") "terminated" else "active") }
        val salaryCentimes = salaryDzd.replace(" ", "").toLongOrNull()?.let { it * 100L }

        ElDialogShell(onDismissRequest = { showEditDialog = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Modifier l'employé",
                    style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                )
                ElTextField(value = phone, onValueChange = { phone = it }, label = "Téléphone", singleLine = true, modifier = Modifier.fillMaxWidth())
                ElTextField(value = email, onValueChange = { email = it }, label = "Email", singleLine = true, modifier = Modifier.fillMaxWidth())
                ElTextField(value = position, onValueChange = { position = it }, label = "Poste", singleLine = true, modifier = Modifier.fillMaxWidth())
                ElTextField(
                    value = salaryDzd,
                    onValueChange = { raw -> salaryDzd = raw.filter { it.isDigit() }.take(12) },
                    label = "Salaire mensuel (DZD)",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ElButton(
                        text = "Annuler",
                        onClick = { showEditDialog = false },
                        variant = ElButtonVariant.GHOST,
                        modifier = Modifier.weight(1f),
                    )
                    ElButton(
                        text = "Enregistrer",
                        onClick = {
                            viewModel.updatePersonnel(
                                p.id,
                                UpdatePersonnelInput(
                                    position = position.trim().ifBlank { null },
                                    phone = phone.trim().ifBlank { null },
                                    email = email.trim().ifBlank { null },
                                    salary = salaryCentimes,
                                    status = status,
                                ),
                            )
                            showEditDialog = false
                        },
                        enabled = !busy && phone.isNotBlank(),
                        variant = ElButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (showDeleteDialog && personnel != null) {
        val p = personnel!!
        ElDialogShell(onDismissRequest = { showDeleteDialog = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Retirer ${p.fullName} ?",
                    style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                )
                Text(
                    "L'employé sera marqué comme « terminé » et retiré du registre actif.",
                    style = ElTheme.typography.bodyMedium,
                    color = c.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ElButton(
                        text = "Annuler",
                        onClick = { showDeleteDialog = false },
                        variant = ElButtonVariant.GHOST,
                        modifier = Modifier.weight(1f),
                    )
                    ElButton(
                        text = "Confirmer",
                        onClick = {
                            viewModel.deletePersonnel(p.id)
                            showDeleteDialog = false
                        },
                        enabled = !busy,
                        variant = ElButtonVariant.DANGER,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = ElTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = ElTheme.typography.labelSmall, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = ElTheme.typography.bodySmall, color = c.textPrimary)
    }
}

@Composable
private fun DayBarChart(perDay: Map<DayOfWeek, Double>) {
    val c = ElTheme.colors
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
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .background(c.primary),
                )
                Spacer(Modifier.height(4.dp))
                Text(labels[idx], style = ElTheme.typography.labelSmall, color = c.textSecondary)
            }
        }
    }
}
/** T-324: humanized personnel status (raw codes no longer leak to the card). */
internal fun personnelStatusLabel(status: String): String = when (status) {
    "active" -> "En poste"
    "terminated" -> "Terminé"
    "suspended" -> "Suspendu"
    else -> status
}
