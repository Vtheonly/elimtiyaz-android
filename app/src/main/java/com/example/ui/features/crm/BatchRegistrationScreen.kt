package com.example.ui.features.crm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.GRADE_LEVEL_CODES
import com.example.core.academicLevelForGradeCode
import com.example.domain.repository.CreateParentInput
import com.example.domain.repository.CreateStudentInput
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElDropdown
import com.example.ui.components.ElFab
import com.example.ui.components.ElIconButton
import com.example.ui.components.ElScaffold
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTextField
import com.example.ui.components.ElTopBar
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Vault §04.03 — Relationship values (Father / Mother / Guardian). */
private val RELATIONSHIPS = listOf("Père", "Mère", "Tuteur")

/** Vault §06 (Assessment/billing) — canonical payment plans. */
private val PAYMENT_PLANS = listOf("tranches" to "Tranches (3 échéances)", "full_annual" to "Paiement annuel intégral")

private val GENDERS = listOf("M" to "Masculin", "F" to "Féminin")

@Composable
fun BatchRegistrationScreen(
    onSuccess: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: BatchRegistrationViewModel = hiltViewModel(),
) {
    // ── Step 1: Parent master info (vault §04.03) ─────────────────────────
    var parentFirstName by remember { mutableStateOf("") }
    var parentLastName by remember { mutableStateOf("") }
    var parentPhone by remember { mutableStateOf("") }
    var parentSecondaryPhone by remember { mutableStateOf("") }
    var parentEmail by remember { mutableStateOf("") }
    var parentOccupation by remember { mutableStateOf("") }
    var parentAddress by remember { mutableStateOf("") }
    var parentNationalId by remember { mutableStateOf("") }
    var parentRelationship by remember { mutableStateOf(RELATIONSHIPS.first()) }
    var parentTransportDestination by remember { mutableStateOf("") }

    val children = remember { mutableStateListOf(ChildFormState()) }
    val classes by viewModel.classes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val activationCode by viewModel.activationCode.collectAsState()

    // FIX (no form reset): clear the form after a successful registration so
    // a second registration doesn't silently re-submit the previous family's
    // pre-filled values.
    LaunchedEffect(activationCode) {
        if (activationCode != null) {
            parentFirstName = ""
            parentLastName = ""
            parentPhone = ""
            parentSecondaryPhone = ""
            parentEmail = ""
            parentOccupation = ""
            parentAddress = ""
            parentNationalId = ""
            parentRelationship = RELATIONSHIPS.first()
            parentTransportDestination = ""
            children.clear()
            children.add(ChildFormState())
        }
    }

    ElScaffold(
        // FIX (no back affordance): the standalone route had no way back.
        topBar = {
            ElTopBar(
                title = "Inscription famille",
                onBack = onBack,
            )
        },
        floatingActionButton = {
            // Vault §04.02 — "Add Another Child" with NO upper bound (the
            // earlier 4-child cap is removed; the list is fully dynamic).
            ElFab(
                icon = Icons.Default.Add,
                onClick = { children.add(ChildFormState()) },
                contentDescription = "Ajouter un enfant",
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ElSectionHeader(title = "Étape 1 — Parent / Tuteur")
                    ElTextField(value = parentFirstName, onValueChange = { parentFirstName = it }, label = "Prénom *", modifier = Modifier.fillMaxWidth())
                    ElTextField(value = parentLastName, onValueChange = { parentLastName = it }, label = "Nom *", modifier = Modifier.fillMaxWidth())
                    ElTextField(value = parentPhone, onValueChange = { parentPhone = it }, label = "Téléphone principal *", modifier = Modifier.fillMaxWidth())
                    ElTextField(value = parentSecondaryPhone, onValueChange = { parentSecondaryPhone = it }, label = "Téléphone secondaire (WhatsApp)", modifier = Modifier.fillMaxWidth())
                    ElTextField(value = parentEmail, onValueChange = { parentEmail = it }, label = "Email (optionnel)", modifier = Modifier.fillMaxWidth())
                    ElTextField(value = parentNationalId, onValueChange = { parentNationalId = it }, label = "N° pièce d'identité (optionnel)", modifier = Modifier.fillMaxWidth())
                    ElTextField(value = parentOccupation, onValueChange = { parentOccupation = it }, label = "Profession (optionnel)", modifier = Modifier.fillMaxWidth())
                    ElTextField(value = parentAddress, onValueChange = { parentAddress = it }, label = "Adresse (optionnel)", modifier = Modifier.fillMaxWidth(), singleLine = false)
                    ElDropdown(
                        label = "Lien de parenté",
                        selectedValue = parentRelationship,
                        options = RELATIONSHIPS,
                        onSelected = { parentRelationship = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ElTextField(
                        value = parentTransportDestination,
                        onValueChange = { parentTransportDestination = it },
                        label = "Destination transport (optionnel — ex: ville_boumerdes)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "La destination de transport déclenche la facturation transport automatique (moteur canonique).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Step 2: Dynamic children blocks (1..N, vault §04.02/§04.03) ──
            Text(
                "Étape 2 — Enfants (${children.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            children.forEachIndexed { index, child ->
                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Enfant ${index + 1}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), modifier = Modifier.weight(1f))
                            if (children.size > 1) {
                                ElIconButton(
                                    icon = Icons.Default.Delete,
                                    onClick = { children.removeAt(index) },
                                    contentDescription = "Supprimer",
                                    size = 36,
                                    background = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        ElTextField(value = child.firstName, onValueChange = { children[index] = child.copy(firstName = it) }, label = "Prénom *", modifier = Modifier.fillMaxWidth())
                        ElTextField(value = child.lastName, onValueChange = { children[index] = child.copy(lastName = it) }, label = "Nom", modifier = Modifier.fillMaxWidth())
                        ElTextField(value = child.birthDate, onValueChange = { children[index] = child.copy(birthDate = it) }, label = "Date de naissance (AAAA-MM-JJ) *", modifier = Modifier.fillMaxWidth())
                        ElDropdown(
                            label = "Sexe",
                            selectedValue = GENDERS.firstOrNull { it.first == child.gender }?.second ?: "Non précisé",
                            options = GENDERS.map { it.second },
                            onSelected = { label ->
                                children[index] = child.copy(gender = GENDERS.first { it.second == label }.first)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // FIX (broken level derivation): free-text level input
                        // with string-surgery classification misclassified
                        // lycée ("1ere_annee") and uppercase ("2AM") codes as
                        // "primaire". Replaced with a canonical dropdown +
                        // `academicLevelForGradeCode`.
                        ElDropdown(
                            label = "Niveau scolaire",
                            selectedValue = child.gradeLevel,
                            options = GRADE_LEVEL_CODES,
                            onSelected = { children[index] = child.copy(gradeLevel = it, classId = null) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Vault §04.03 — "Assigned Academic Level & Class": the
                        // class dropdown only offers classes of the chosen
                        // grade's cycle (grouped by cycle in UI selectors,
                        // vault §05.02 rule).
                        val cycle = academicLevelForGradeCode(child.gradeLevel)
                        val cycleClasses = classes.filter { it.level == cycle }
                        if (child.gradeLevel.isNotBlank() && cycleClasses.isNotEmpty()) {
                            val selectedClass = cycleClasses.firstOrNull { it.id == child.classId }
                            ElDropdown(
                                label = "Classe (optionnel)",
                                selectedValue = selectedClass?.name ?: "Aucune",
                                options = listOf("Aucune") + cycleClasses.map { it.name },
                                onSelected = { name ->
                                    children[index] = child.copy(
                                        classId = cycleClasses.firstOrNull { it.name == name }?.id,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        // ── Step 3 (per-child billing): payment plan drives the
                        // canonical discount engine + tranche split in
                        // batchRegister (CANONICAL-FINANCIAL-LOGIC.md §5-§6).
                        ElDropdown(
                            label = "Modalité de paiement",
                            selectedValue = PAYMENT_PLANS.first { it.first == child.paymentPlan }.second,
                            options = PAYMENT_PLANS.map { it.second },
                            onSelected = { label ->
                                children[index] = child.copy(paymentPlan = PAYMENT_PLANS.first { it.second == label }.first)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ElTextField(
                            value = child.medicalNotes,
                            onValueChange = { children[index] = child.copy(medicalNotes = it) },
                            label = "Notes médicales (optionnel)",
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                        )
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            activationCode?.let { code ->
                ElCard(modifier = Modifier.fillMaxWidth(), accent = SuccessGreen) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Inscription réussie!", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = SuccessGreen)
                        Text("Code d'activation: $code", style = MaterialTheme.typography.bodyMedium)
                        Text("Donnez ce code au parent pour qu'il puisse se connecter au portail web.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text(
                "Étape 4 — Validation atomique : le parent et les ${children.size} enfant(s) seront créés en une seule transaction (tout ou rien).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ElButton(
                text = if (isLoading) "Inscription..." else "Inscrire la famille",
                onClick = {
                    val parent = CreateParentInput(
                        firstName = parentFirstName, lastName = parentLastName, phone = parentPhone,
                        email = parentEmail.ifBlank { null }, occupation = parentOccupation.ifBlank { null },
                        address = parentAddress.ifBlank { null },
                        secondaryPhone = parentSecondaryPhone.ifBlank { null },
                        nationalId = parentNationalId.ifBlank { null },
                        relationship = when (parentRelationship) {
                            "Père" -> "father"
                            "Mère" -> "mother"
                            else -> "guardian"
                        },
                        transportDestination = parentTransportDestination.ifBlank { null },
                    )
                    val students = children.map { c ->
                        CreateStudentInput(
                            firstName = c.firstName, lastName = c.lastName,
                            gender = c.gender.ifBlank { "unspecified" }, birthDate = c.birthDate,
                            level = academicLevelForGradeCode(c.gradeLevel),
                            gradeLevel = c.gradeLevel,
                            classId = c.classId,
                            medicalNotes = c.medicalNotes.ifBlank { null },
                            paymentPlan = c.paymentPlan,
                        )
                    }
                    viewModel.register(parent, students, onSuccess)
                },
                enabled = !isLoading,
                fullWidth = true,
                loading = isLoading,
            )
        }
    }
}
