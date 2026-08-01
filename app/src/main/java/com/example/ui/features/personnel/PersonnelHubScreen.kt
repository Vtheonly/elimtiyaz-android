package com.example.ui.features.personnel

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Session
import com.example.domain.model.AuditLog
import com.example.domain.model.Personnel
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.PersonnelRepository
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElGradientStatCard
import com.example.ui.components.ElProgressBar
import com.example.ui.components.ElScrollableTabRow
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ModernSecondaryTabRow
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Root Personnel hub — 4 tabs:
 *   0. Employés    → [EmployeeDirectoryScreen] (PersonnelRepository)
 *   1. Activité    → [ReleveScreen]            (PersonnelRepository — derived compliance)
 *   2. Audit       → [AuditStreamScreen]       (AuditRepository)
 *   3. Déconnexion → [SignOutScreen]
 *
 * BUGFIX (iter 2): previously tabs 0–2 rendered hardcoded sample data.
 * Now each tab has a real Hilt ViewModel that calls the corresponding
 * repository, mirroring the desktop `personnel-page` implementation.
 */
@Composable
fun PersonnelHubScreen(
    session: Session,
    onNavigateToAuditLog: () -> Unit,
    onSignOut: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Employés", "Activité", "Audit", "Déconnexion")

    Column(modifier = Modifier.fillMaxSize()) {
        ModernSecondaryTabRow(
            tabs = tabs,
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
        )
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            when (selectedTab) {
                0 -> EmployeeDirectoryScreen(session)
                1 -> ReleveScreen(session)
                2 -> AuditStreamScreen(session, onNavigateToAuditLog = onNavigateToAuditLog)
                3 -> SignOutScreen(session, onSignOut = onSignOut)
            }
        }
    }
}

// ── 1. Personnel Directory ──────────────────────────────────────────────────

@HiltViewModel
class EmployeeDirectoryViewModel @Inject constructor(
    private val personnelRepository: PersonnelRepository,
) : ViewModel() {
    val personnel: StateFlow<List<Personnel>> = personnelRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@Composable
fun EmployeeDirectoryScreen(
    session: Session,
    viewModel: EmployeeDirectoryViewModel = hiltViewModel(),
) {
    val personnel by viewModel.personnel.collectAsState()
    val context = LocalContext.current

    var selectedCategoryTab by remember { mutableIntStateOf(0) }
    val categories = remember(personnel) {
        val distinct = personnel.map { it.staffCategory }.distinct().sorted()
        listOf("Tous") + distinct
    }
    val filteredStaff = remember(selectedCategoryTab, personnel) {
        if (selectedCategoryTab == 0) personnel
        else personnel.filter { it.staffCategory == categories[selectedCategoryTab] }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElSectionHeader(title = "Registre du Personnel (${personnel.size})")

        if (categories.size > 1) {
            ElScrollableTabRow(
                tabs = categories,
                selectedTabIndex = selectedCategoryTab,
                onTabSelected = { selectedCategoryTab = it },
            )
        }

        if (filteredStaff.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Phone,
                title = "Aucun personnel",
                message = "Aucun employé enregistré. Ajoutez-en depuis les paramètres ou contactez un administrateur.",
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(filteredStaff) { staff ->
                ElCard(modifier = Modifier.fillMaxWidth(), compact = true) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ElAvatar(initials = staff.fullName, size = 44)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        staff.fullName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                        ),
                                    )
                                    Text(
                                        staff.position,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            ElTag(text = staff.staffCategory, color = PrimaryBlue)
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Téléphone: ${staff.phone} · Embauché: ${staff.hireDate.take(10)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ElButton(
                                text = "Appeler",
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${staff.phone}"))
                                    runCatching { context.startActivity(intent) }
                                },
                                style = ElButtonStyle.Secondary,
                                icon = Icons.Default.Phone,
                                modifier = Modifier.weight(1f),
                            )
                            ElButton(
                                text = "Email",
                                onClick = {
                                    staff.email?.let { email ->
                                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                                        runCatching { context.startActivity(intent) }
                                    }
                                },
                                style = ElButtonStyle.Secondary,
                                icon = Icons.Default.Email,
                                modifier = Modifier.weight(1f),
                                enabled = !staff.email.isNullOrBlank(),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 2. Teacher Activity Ledger (Relevé) ───────────────────────────────────

/**
 * Activity / compliance view derived from [PersonnelRepository].
 *
 * Mirrors desktop `releve-tab` — shows each personnel's weekly hours
 * compliance (logged / target). The desktop also tracks `ReleveEntry`
 * events (course, meeting, supervision, …) but the mobile repo doesn't
 * expose them yet, so we derive compliance from the `weeklyHoursLogged`
 * vs `weeklyHoursTarget` fields on the Personnel entity.
 */
@HiltViewModel
class ReleveViewModel @Inject constructor(
    private val personnelRepository: PersonnelRepository,
) : ViewModel() {
    val personnel: StateFlow<List<Personnel>> = personnelRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@Composable
fun ReleveScreen(
    session: Session,
    viewModel: ReleveViewModel = hiltViewModel(),
) {
    val personnel by viewModel.personnel.collectAsState()
    val teachers = personnel.filter { it.staffCategory == "teacher" || it.weeklyHoursTarget > 0 }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElGradientStatCard(
            title = "Relevé d'Activité",
            value = "${teachers.size} Enseignants",
            subtitle = "Suivi hebdomadaire des heures",
            modifier = Modifier.fillMaxWidth(),
        )

        if (teachers.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Code,
                title = "Aucune donnée d'activité",
                message = "Aucun personnel avec objectif horaire défini.",
            )
            return@Column
        }

        teachers.forEach { staff ->
            val target = staff.weeklyHoursTarget.coerceAtLeast(1)
            val logged = staff.weeklyHoursLogged
            val compliance = (logged.toFloat() / target * 100).toInt().coerceIn(0, 100)
            val complianceColor = when {
                compliance >= 95 -> SuccessGreen
                compliance >= 80 -> WarmGold
                else -> PrimaryBlue
            }

            ElCard(modifier = Modifier.fillMaxWidth(), compact = true) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        staff.fullName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$logged / $target Heures Effectuées",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Conformité", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "$compliance%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = complianceColor,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    ElProgressBar(progress = compliance / 100f)
                }
            }
        }
    }
}

// ── 3. Live Audit Stream ────────────────────────────────────────────────────

@HiltViewModel
class AuditStreamViewModel @Inject constructor(
    private val auditRepository: AuditRepository,
) : ViewModel() {
    val logs: StateFlow<List<AuditLog>> = auditRepository.observe(limit = 50)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditStreamScreen(
    session: Session,
    onNavigateToAuditLog: () -> Unit,
    viewModel: AuditStreamViewModel = hiltViewModel(),
) {
    val logs by viewModel.logs.collectAsState()
    var selectedAuditLog by remember { mutableStateOf<AuditLog?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElSectionHeader(
            title = "Journal d'Audit (${logs.size})",
            actionText = "Journal complet",
            onAction = onNavigateToAuditLog,
        )

        if (logs.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Code,
                title = "Aucun événement",
                message = "Aucune entrée d'audit récente. Les actions des utilisateurs apparaîtront ici.",
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(logs) { log ->
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { selectedAuditLog = log },
                    compact = true,
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                log.action,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryBlue,
                                    fontSize = 14.sp,
                                ),
                            )
                            Text(
                                log.occurredAt.take(19).replace("T", " "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${log.actorName} • ${log.entityType}/${log.entityId}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        )
                        log.note?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Inspecter le delta JSON", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                        }
                    }
                }
            }
        }
    }

    selectedAuditLog?.let { log ->
        ModalBottomSheet(
            onDismissRequest = { selectedAuditLog = null },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Inspecteur JSON (${log.action})",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text("Entité: ${log.entityType} ID: ${log.entityId}", style = MaterialTheme.typography.bodyMedium)

                ElCard(modifier = Modifier.fillMaxWidth(), gradient = false) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Payload Audit Event:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            """
                            {
                              "audit_id": "${log.id}",
                              "action": "${log.action}",
                              "actor": "${log.actorName}",
                              "entity": "${log.entityType}",
                              "entity_id": "${log.entityId}",
                              "timestamp": "${log.occurredAt}",
                              "note": "${log.note ?: ""}"
                            }
                            """.trimIndent(),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ElButton(
                    text = "Fermer",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { selectedAuditLog = null }
                    },
                    style = ElButtonStyle.Secondary,
                    fullWidth = true,
                )
            }
        }
    }
}

@Composable
fun SignOutScreen(session: Session, onSignOut: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElGradientStatCard(
            title = "Session Utilisateur",
            value = session.displayName,
            subtitle = "Gérez votre session et déconnexion",
            modifier = Modifier.fillMaxWidth(),
        )

        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ElSectionHeader(title = "Informations")
                Text("Email: ${session.email}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Rôle: ${session.role.code}",
                    style = MaterialTheme.typography.bodyMedium.copy(color = PrimaryBlue, fontWeight = FontWeight.Medium),
                )
                Text(
                    "Permissions: ${session.permissions.size} actives",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ElButton(
            text = "Se déconnecter",
            onClick = onSignOut,
            style = ElButtonStyle.Danger,
            fullWidth = true,
        )
    }
}
