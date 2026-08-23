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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Permission
import com.example.core.Session
import com.example.domain.model.Personnel
import com.example.domain.repository.CreatePersonnelInput
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElFab
import com.example.ui.components.ElScrollableTabRow
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.theme.PrimaryBlue
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

private fun roleDisplayLabel(code: String): String = when (code.lowercase()) {
    "teacher" -> "Enseignants"
    "super_admin" -> "Direction"
    "financial_officer" -> "Finances"
    "support_staff" -> "Support"
    "driver" -> "Chauffeurs"
    "buyer" -> "Achats"
    "warehouse_worker" -> "Magasin"
    "worker" -> "Services"
    else -> code.replace("_", " ").replaceFirstChar { it.uppercase() }
}

/** Staff role options for the create-employee form (code -> singular FR label). */
private val STAFF_ROLE_OPTIONS: List<Pair<String, String>> = listOf(
    "teacher" to "Enseignant",
    "super_admin" to "Direction",
    "financial_officer" to "Finances",
    "support_staff" to "Support",
    "manager" to "Management",
    "driver" to "Chauffeur",
    "buyer" to "Achats",
    "warehouse_worker" to "Magasin",
    "worker" to "Services",
)

@Composable
fun EmployeeDirectoryScreen(
    session: Session,
    onNavigateToPersonnelDetail: (String) -> Unit = {},
    viewModel: EmployeeDirectoryViewModel = hiltViewModel(),
) {
    val personnel by viewModel.personnel.collectAsState()
    val departments by viewModel.departments.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val context = LocalContext.current

    var selectedCategoryTab by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val rawCategories = remember(personnel) {
        val distinct = personnel.map { it.staffCategory }.distinct().sorted()
        listOf("all") + distinct
    }
    val tabLabels = remember(rawCategories) {
        rawCategories.map { if (it == "all") "Tous" else roleDisplayLabel(it) }
    }
    val filteredStaff = remember(selectedCategoryTab, personnel) {
        if (selectedCategoryTab == 0) personnel
        else personnel.filter { it.staffCategory == rawCategories[selectedCategoryTab] }
    }

    // Create-employee FAB — visible only with the MANAGE_PERSONNEL permission
    // (the repository layer already exposes createPersonnel; this is its UI).
    val canManage = session.can(Permission.MANAGE_PERSONNEL) || viewModel.canManage

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ElSectionHeader(title = "Registre du Personnel (${personnel.size})")

            message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (tabLabels.size > 1) {
                ElScrollableTabRow(
                    tabs = tabLabels,
                    selectedTabIndex = selectedCategoryTab,
                    onTabSelected = { selectedCategoryTab = it },
                )
            }

            if (filteredStaff.isEmpty()) {
                ElEmptyState(
                    icon = Icons.Default.Phone,
                    title = "Aucun personnel",
                    message = "Aucun employé dans cette catégorie.",
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(filteredStaff) { staff ->
                        EmployeeCard(
                            staff = staff,
                            onClick = { onNavigateToPersonnelDetail(staff.id) },
                            context = context,
                        )
                    }
                }
            }
        }

        if (canManage) {
            ElFab(
                icon = Icons.Default.PersonAdd,
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                contentDescription = "Ajouter un employé",
            )
        }
    }

    if (showCreateDialog) {
        CreateEmployeeDialog(
            departments = departments.map { it.id to it.name },
            busy = busy,
            onConfirm = { input ->
                viewModel.createPersonnel(input)
                showCreateDialog = false
            },
            onDismiss = {
                showCreateDialog = false
                viewModel.clearMessages()
            },
        )
    }
}

@Composable
private fun EmployeeCard(
    staff: Personnel,
    onClick: () -> Unit,
    context: android.content.Context,
) {
    ElCard(modifier = Modifier.fillMaxWidth(), compact = true, onClick = onClick) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ElAvatar(initials = staff.fullName, size = 42)
                    Spacer(Modifier.width(10.dp))
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
                ElTag(text = roleDisplayLabel(staff.staffCategory), color = PrimaryBlue)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = if (staff.phone.isNotBlank()) "Tél: ${staff.phone} • Embauché: ${staff.hireDate.take(10)}"
                       else "Embauché: ${staff.hireDate.take(10)}",
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
                    enabled = staff.phone.isNotBlank(),
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

/**
 * Create-employee dialog — UI entry for [com.example.domain.repository.PersonnelRepository.createPersonnel].
 * Fields mirror [CreatePersonnelInput]: names, role, department, phone,
 * email, position, hire date, and salary (all-French labels).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CreateEmployeeDialog(
    departments: List<Pair<String, String>>,
    busy: Boolean,
    onConfirm: (CreatePersonnelInput) -> Unit,
    onDismiss: () -> Unit,
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var roleCode by remember { mutableStateOf(STAFF_ROLE_OPTIONS.first().first) }
    var position by remember { mutableStateOf(STAFF_ROLE_OPTIONS.first().second) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var hireDate by remember {
        mutableStateOf(Clock.System.todayIn(TimeZone.currentSystemDefault()).toString())
    }
    var salaryDzd by remember { mutableStateOf("") }
    var departmentId by remember { mutableStateOf<String?>(null) }

    val salaryCentimes = salaryDzd.replace(" ", "").toLongOrNull()?.let { it * 100L }
    val valid = firstName.isNotBlank() && lastName.isNotBlank() && phone.isNotBlank() && hireDate.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un employé") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Prénom *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Nom *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Rôle *", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    STAFF_ROLE_OPTIONS.forEach { (code, label) ->
                        ElTag(
                            text = label,
                            selected = roleCode == code,
                            color = PrimaryBlue,
                            onClick = {
                                roleCode = code
                                position = label
                            },
                        )
                    }
                }

                if (departments.isNotEmpty()) {
                    Text("Département", style = MaterialTheme.typography.labelMedium)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ElTag(
                            text = "Aucun",
                            selected = departmentId == null,
                            color = PrimaryBlue,
                            onClick = { departmentId = null },
                        )
                        departments.forEach { (id, name) ->
                            ElTag(
                                text = name,
                                selected = departmentId == id,
                                color = PrimaryBlue,
                                onClick = { departmentId = id },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it },
                    label = { Text("Poste") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Téléphone *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = hireDate,
                    onValueChange = { hireDate = it },
                    label = { Text("Date d'embauche (AAAA-MM-JJ) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = salaryDzd,
                    onValueChange = { raw -> salaryDzd = raw.filter { it.isDigit() }.take(12) },
                    label = { Text("Salaire mensuel (DZD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Un identifiant PER-XXX sera généré automatiquement.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        CreatePersonnelInput(
                            firstName = firstName.trim(),
                            lastName = lastName.trim(),
                            staffCategory = roleCode,
                            roleId = roleCode,
                            departmentId = departmentId,
                            position = position.trim().ifBlank { roleCode },
                            phone = phone.trim(),
                            email = email.trim().ifBlank { null },
                            hireDate = hireDate.trim(),
                            salary = salaryCentimes,
                        ),
                    )
                },
                enabled = !busy && valid,
            ) { Text("Ajouter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
