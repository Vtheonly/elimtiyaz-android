package com.example.ui.features.personnel

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
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.core.Session
import com.example.domain.model.AuditLog
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElGradientStatCard
import com.example.ui.components.ElProgressBar
import com.example.ui.components.ElScrollableTabRow
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ModernSecondaryTabRow
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen

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
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.TopStart) {
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

data class StaffMember(
    val name: String,
    val role: String,
    val category: String,
    val phone: String,
    val email: String,
    val assignedInfo: String,
)

val SAMPLE_STAFF = listOf(
    StaffMember("Dr. Karim Bencherif", "Directeur Général", "Administratif", "+213 550 12 34 56", "k.bencherif@el-imtiyaz.dz", "Super Admin"),
    StaffMember("Mme. Samia Amrani", "Professeure Principale", "Enseignants", "+213 661 98 76 54", "s.amrani@el-imtiyaz.dz", "Mathématiques • CP A, CE1 B"),
    StaffMember("M. Redouane Saidi", "Professeur", "Enseignants", "+213 770 45 67 89", "r.saidi@el-imtiyaz.dz", "Physique-Chimie • 3AS S"),
    StaffMember("Mme. Amina Ziani", "Orthophoniste", "Soin & Médical", "+213 552 11 22 33", "a.ziani@el-imtiyaz.dz", "Cabinet Spécialisé • 12 Suivis"),
    StaffMember("M. Mourad Khelil", "Support Informatique", "Support & Logistique", "+213 662 33 44 55", "m.khelil@el-imtiyaz.dz", "Infrastructures & Réseau"),
)

@Composable
fun EmployeeDirectoryScreen(session: Session) {
    var selectedCategoryTab by remember { mutableIntStateOf(0) }
    val categories = listOf("Tous", "Administratif", "Enseignants", "Soin & Médical", "Support & Logistique")

    val filteredStaff = remember(selectedCategoryTab) {
        if (selectedCategoryTab == 0) SAMPLE_STAFF
        else SAMPLE_STAFF.filter { it.category == categories[selectedCategoryTab] }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElSectionHeader(title = "Registre du Personnel")

        ElScrollableTabRow(
            tabs = categories,
            selectedTabIndex = selectedCategoryTab,
            onTabSelected = { selectedCategoryTab = it },
        )

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
                                ElAvatar(initials = staff.name, size = 44)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(staff.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp))
                                    Text(staff.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            ElTag(text = staff.category, color = PrimaryBlue)
                        }

                        Spacer(Modifier.height(10.dp))
                        Text("Affectation: ${staff.assignedInfo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ElButton(
                                text = "Appeler",
                                onClick = {},
                                style = ElButtonStyle.Secondary,
                                icon = Icons.Default.Phone,
                                modifier = Modifier.weight(1f),
                            )
                            ElButton(
                                text = "Email",
                                onClick = {},
                                style = ElButtonStyle.Secondary,
                                icon = Icons.Default.Email,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 2. Teacher Activity Ledger (Relevé) ───────────────────────────────────

@Composable
fun ReleveScreen(session: Session) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElGradientStatCard(
            title = "Relevé d'Activité Enseignants",
            value = "Suivi Ponctualité",
            subtitle = "Appels, saisies de notes et devoirs",
            modifier = Modifier.fillMaxWidth(),
        )

        listOf(
            Triple("Mme. Samia Amrani (Maths)", 98, "28 / 28 Heures Effectuées"),
            Triple("M. Redouane Saidi (Physique)", 92, "24 / 26 Heures Effectuées"),
            Triple("Mme. Fatma Zohra (Arabe)", 100, "30 / 30 Heures Effectuées"),
        ).forEach { (name, compliance, hours) ->
            ElCard(modifier = Modifier.fillMaxWidth(), compact = true) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp))
                    Spacer(Modifier.height(4.dp))
                    Text(hours, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Conformité", style = MaterialTheme.typography.labelSmall)
                        Text("$compliance%", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SuccessGreen)
                    }
                    Spacer(Modifier.height(6.dp))
                    ElProgressBar(progress = compliance / 100f)
                }
            }
        }
    }
}

// ── 3. Live Contextual Audit Log Stream ────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditStreamScreen(session: Session, onNavigateToAuditLog: () -> Unit) {
    var selectedAuditLog by remember { mutableStateOf<AuditLog?>(null) }
    val sheetState = rememberModalBottomSheetState()

    val sampleLogs = listOf(
        AuditLog(
            id = "AUD-1001", tenantId = "dev_tenant", action = "payment.recorded",
            entityType = "payment_receipts", entityId = "REC-8821",
            actorId = "USR-001", actorName = "M. Khelil", actorRole = "receptionist",
            beforeJson = null, afterJson = """{"amount":25000}""",
            note = "Paiement 25,000 DZD (Tranche 2) enregistré pour Élève STU-001 (Amine Benali). Reçu B11-042.",
            ipAddress = "192.168.1.50", userAgent = "Android App", occurredAt = "2026-07-31T10:15:30Z"
        ),
        AuditLog(
            id = "AUD-1002", tenantId = "dev_tenant", action = "grade.modified",
            entityType = "grade_entries", entityId = "GRD-3302",
            actorId = "USR-002", actorName = "Mme. Amrani", actorRole = "teacher",
            beforeJson = """{"devoir1":12.0}""", afterJson = """{"devoir1":14.5}""",
            note = "Modification note Devoir 1 Mathématiques de 12.0 à 14.5.",
            ipAddress = "192.168.1.52", userAgent = "Android App", occurredAt = "2026-07-31T09:42:00Z"
        ),
        AuditLog(
            id = "AUD-1003", tenantId = "dev_tenant", action = "expense.approved",
            entityType = "expenses", entityId = "EXP-004",
            actorId = "USR-003", actorName = "Dr. Bencherif", actorRole = "admin",
            beforeJson = """{"status":"pending"}""", afterJson = """{"status":"approved"}""",
            note = "Approbation dépense Tier 2 #EXP-004 (Fournitures Informatiques: 45,000 DZD).",
            ipAddress = "192.168.1.10", userAgent = "Android App", occurredAt = "2026-07-31T08:12:10Z"
        ),
    )

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElSectionHeader(
            title = "Journal d'Audit",
            actionText = "Journal complet",
            onAction = onNavigateToAuditLog,
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(sampleLogs) { log ->
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { selectedAuditLog = log },
                    compact = true,
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(log.action, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = PrimaryBlue, fontSize = 14.sp))
                            Text(log.occurredAt.take(19).replace("T", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("${log.actorName} • ${log.entityType}/${log.entityId}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                        log.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

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
                Text("Inspecteur JSON (${log.action})", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
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
                              "note": "${log.note}"
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
                    onClick = { selectedAuditLog = null },
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
                Text("Rôle: ${session.role.code}", style = MaterialTheme.typography.bodyMedium.copy(color = PrimaryBlue, fontWeight = FontWeight.Medium))
                Text("Permissions: ${session.permissions.size} actives", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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