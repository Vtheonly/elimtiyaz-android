package com.elimtiyaz.feature.personnel

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.elimtiyaz.core.common.Role
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.EmptyState
import com.elimtiyaz.core.ui.ErrorState
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.Personnel
import com.elimtiyaz.domain.model.PersonnelStatus
import com.elimtiyaz.domain.model.ReleveEntry
import com.elimtiyaz.domain.model.StaffCategory
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

/**
 * PersonnelDetailScreen — full profile of a personnel member (Route.PersonnelDetail).
 *
 * Layout: TopAppBar (back / edit) → scrollable LazyColumn with header card
 * (avatar, name, category, status, phone, email, hire date, salary),
 * "Heures cette semaine" card (progress bar + per-day breakdown),
 * "Relevé récent" section (last 10 entries), and quick actions (Appeler,
 * E-mail, Logger heures).
 *
 * The edit action is gated by [Permission.ManagePersonnel]; salary is admin-only
 * (SuperAdmin or FinancialOfficer). A floating "Logger heures" button jumps to
 * [Route.Releve].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonnelDetailScreen(
    nav: NavController,
    vm: PersonnelDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val canManage = session?.can(Permission.ManagePersonnel) == true
    val canAccessDriver = session?.can(Permission.AccessDriverMode) == true
    val isAdmin = session?.hasAnyRole(Role.SuperAdmin, Role.FinancialOfficer) == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.personnel?.let { Formatters.fullName(it.firstName, it.lastName) } ?: "Personnel") },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (canManage && state.personnel != null) {
                        IconButton(onClick = {
                            scope.launch {
                                snackbar.showSnackbar("La modification du personnel sera disponible dans une prochaine version.")
                            }
                        }) { Icon(Icons.Outlined.Edit, contentDescription = "Modifier") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.personnel != null) {
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate(Route.Releve.build(state.personnel!!.id)) },
                    icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                    text = { Text("Logger heures") },
                )
            }
        },
    ) { inner ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(inner))
            state.error != null -> ErrorState(state.error!!, onRetry = vm::reload, modifier = Modifier.padding(inner))
            state.personnel == null -> EmptyState(
                title = "Personnel introuvable",
                description = "Ce membre du personnel n'existe plus ou l'identifiant est invalide.",
                modifier = Modifier.padding(inner),
            )
            else -> PersonnelDetailContent(
                personnel = state.personnel!!,
                hoursLogged = state.hoursLoggedThisWeek,
                hoursTarget = state.hoursTarget,
                perDayBreakdown = state.perDayBreakdown,
                recentEntries = state.recentEntries,
                isAdmin = isAdmin,
                canAccessDriver = canAccessDriver,
                contentPadding = inner,
                onCall = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${state.personnel!!.phone}"))
                    context.startActivity(intent)
                },
                onEmail = {
                    val email = state.personnel!!.email ?: return@PersonnelDetailContent
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                    context.startActivity(intent)
                },
                onLogHours = { nav.navigate(Route.Releve.build(state.personnel!!.id)) },
                onOpenDriverMode = { nav.navigate(Route.Routing.route) },
            )
        }
    }
}

@Composable
private fun PersonnelDetailContent(
    personnel: Personnel,
    hoursLogged: Double,
    hoursTarget: Int,
    perDayBreakdown: Map<DayOfWeek, Double>,
    recentEntries: List<ReleveEntry>,
    isAdmin: Boolean,
    canAccessDriver: Boolean,
    contentPadding: PaddingValues,
    onCall: () -> Unit,
    onEmail: () -> Unit,
    onLogHours: () -> Unit,
    onOpenDriverMode: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ElimtiyazSpacing.x4,
            end = ElimtiyazSpacing.x4,
            top = contentPadding.calculateTopPadding() + ElimtiyazSpacing.x4,
            bottom = contentPadding.calculateBottomPadding() + ElimtiyazSpacing.x12,
        ),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x4),
    ) {
        item { PersonnelHeaderCard(personnel = personnel, isAdmin = isAdmin) }
        item {
            QuickActionsRow(
                phone = personnel.phone,
                email = personnel.email,
                onCall = onCall,
                onEmail = onEmail,
                onLogHours = onLogHours,
                canAccessDriver = canAccessDriver && personnel.staffCategory == StaffCategory.Driver,
                onOpenDriverMode = onOpenDriverMode,
            )
        }
        item { HorizontalDivider() }
        item {
            WeeklyHoursCard(
                logged = hoursLogged,
                target = hoursTarget,
                perDayBreakdown = perDayBreakdown,
            )
        }
        item { HorizontalDivider() }
        item { RecentReleveSection(entries = recentEntries) }
    }
}

@Composable
private fun PersonnelHeaderCard(personnel: Personnel, isAdmin: Boolean) {
    ElImtiyazCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvatarCircle(
                initial = Formatters.initials(personnel.firstName, personnel.lastName),
                size = 80,
                backgroundColor = when (personnel.staffCategory) {
                    StaffCategory.Teacher -> ElimtiyazColors.PrimaryBlue
                    StaffCategory.Administration -> ElimtiyazColors.DeepBlue
                    StaffCategory.Support -> ElimtiyazColors.WarmGold
                    StaffCategory.Maintenance -> ElimtiyazColors.MutedBrown
                    StaffCategory.Driver -> ElimtiyazColors.LightBlue
                },
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Text(
                text = Formatters.fullName(personnel.firstName, personnel.lastName),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Row(
                horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(
                    label = personnel.staffCategory.displayFr,
                    tone = when (personnel.staffCategory) {
                        StaffCategory.Teacher -> StatusTone.Info
                        StaffCategory.Administration -> StatusTone.Success
                        StaffCategory.Support -> StatusTone.Warning
                        StaffCategory.Maintenance -> StatusTone.Neutral
                        StaffCategory.Driver -> StatusTone.Info
                    },
                )
                StatusChip(
                    label = personnelStatusFr(personnel.status),
                    tone = when (personnel.status) {
                        PersonnelStatus.Active -> StatusTone.Success
                        PersonnelStatus.OnLeave -> StatusTone.Warning
                        PersonnelStatus.Suspended -> StatusTone.Danger
                        PersonnelStatus.Terminated -> StatusTone.Neutral
                    },
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
            InfoRow("Téléphone", personnel.phone.ifBlank { "—" })
            InfoRow("E-mail", personnel.email ?: "—")
            InfoRow("Date d'embauche", runCatching { Formatters.date(personnel.hireDate) }.getOrDefault(personnel.hireDate))
            if (isAdmin) {
                InfoRow("Salaire", personnel.salary?.let { Formatters.currency(it) } ?: "—")
            }
            InfoRow("Heures hebdo. cibles", "${personnel.weeklyHoursTarget} h")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ElimtiyazSpacing.x1),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun QuickActionsRow(
    phone: String,
    email: String?,
    onCall: () -> Unit,
    onEmail: () -> Unit,
    onLogHours: () -> Unit,
    canAccessDriver: Boolean,
    onOpenDriverMode: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        QuickAction(Icons.Outlined.Call, "Appeler", onCall, enabled = phone.isNotBlank())
        QuickAction(Icons.Outlined.Mail, "E-mail", onEmail, enabled = !email.isNullOrBlank())
        QuickAction(Icons.Outlined.Schedule, "Logger", onLogHours, enabled = true)
        if (canAccessDriver) {
            QuickAction(Icons.Outlined.LocalShipping, "Chauffeur", onOpenDriverMode, enabled = true)
        }
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            onClick = { if (enabled) onClick() },
            modifier = Modifier.size(56.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(ElimtiyazSpacing.x2))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeeklyHoursCard(
    logged: Double,
    target: Int,
    perDayBreakdown: Map<DayOfWeek, Double>,
) {
    ElImtiyazCard {
        Column(modifier = Modifier.fillMaxWidth().padding(ElimtiyazSpacing.x4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Heures cette semaine",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusChip(
                    label = "${logged.toInt()} / ${target} h",
                    tone = when {
                        target > 0 && logged >= target -> StatusTone.Danger
                        target > 0 && logged >= target * 0.8 -> StatusTone.Warning
                        else -> StatusTone.Success
                    },
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            val ratio = if (target <= 0) 0f else (logged.toFloat() / target.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)),
                color = when {
                    ratio >= 1f -> ElimtiyazColors.DangerRed
                    ratio >= 0.8f -> ElimtiyazColors.WarmGold
                    else -> ElimtiyazColors.SuccessGreen
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
            // Per-day breakdown — 7 columns Mon→Sun
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DayOfWeek.values().forEach { d ->
                    val hours = perDayBreakdown[d] ?: 0.0
                    val maxHours = (perDayBreakdown.values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
                    val h = (hours / maxHours * 56.0).coerceAtLeast(2.0)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(h.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (hours > 0) ElimtiyazColors.PrimaryBlue
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                        )
                        Spacer(Modifier.height(ElimtiyazSpacing.x1))
                        Text(
                            text = dayLabelShort(d),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${hours.toInt()}h",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentReleveSection(entries: List<ReleveEntry>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Relevé récent",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(ElimtiyazSpacing.x2))
        if (entries.isEmpty()) {
            Text(
                text = "Aucun relevé enregistré cette semaine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.forEach { e -> ReleveRow(e) }
        }
    }
}

@Composable
private fun ReleveRow(entry: ReleveEntry) {
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.activity,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(
                    label = "${entry.hoursIn.toInt()}h" + (entry.hoursOut?.let { "→${it.toInt()}h" } ?: ""),
                    tone = StatusTone.Info,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x1))
            Text(
                text = "${runCatching { Formatters.date(entry.date) }.getOrDefault(entry.date)} • ${runCatching { Formatters.dateTime(entry.recordedAt) }.getOrDefault(entry.recordedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.classId?.let { cid ->
                Text(
                    text = "Classe: ${cid.take(8)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            entry.subjectId?.let { sid ->
                Text(
                    text = "Matière: ${sid.take(8)}",
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

/** Short French label for a [DayOfWeek] (Lun / Mar / Mer / Jeu / Ven / Sam / Dim). */
private fun dayLabelShort(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "Lun"
    DayOfWeek.TUESDAY -> "Mar"
    DayOfWeek.WEDNESDAY -> "Mer"
    DayOfWeek.THURSDAY -> "Jeu"
    DayOfWeek.FRIDAY -> "Ven"
    DayOfWeek.SATURDAY -> "Sam"
    DayOfWeek.SUNDAY -> "Dim"
}

/** French label for a [PersonnelStatus]. */
private fun personnelStatusFr(status: PersonnelStatus): String = when (status) {
    PersonnelStatus.Active -> "Actif"
    PersonnelStatus.OnLeave -> "En congé"
    PersonnelStatus.Suspended -> "Suspendu"
    PersonnelStatus.Terminated -> "Licencié"
}
