package com.example.ui.features.crm

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.GRADE_LEVEL_CODES
import com.example.core.academicLevelForGradeCode
import com.example.domain.repository.CreateParentInput
import com.example.domain.repository.CreateStudentInput
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.display.ElInfoRow
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.input.ElDropdown
import com.example.ui.designsystem.components.input.ElDropdownOption
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.foundation.elMoneyFormat
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.util.PhoneUtils

private val RELATIONSHIPS = listOf("Père", "Mère", "Tuteur légal")
private val LANGUAGES = listOf("Français" to "fr", "Arabe" to "ar", "Anglais" to "en")
private val GENDERS = listOf(
    "Non spécifié" to "unspecified",
    "Masculin" to "M",
    "Féminin" to "F",
)
private val PAYMENT_PLANS = listOf(
    "tranches" to "3 tranches (40 / 30 / 30)",
    "full_annual" to "Paiement annuel intégral (−5% scolarité si avant le 30 juin)",
)

/** Prettifies a canonical transport zone code for display ("ville_boumerdes" → "Ville boumerdes"). */
private fun zoneLabel(code: String): String =
    code.replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Plain-string option list helper (value == label). */
private fun stringOptions(values: List<String>): List<ElDropdownOption> =
    values.map { ElDropdownOption(value = it, label = it) }

/** Label/value option list helper. */
private fun pairOptions(values: List<Pair<String, String>>): List<ElDropdownOption> =
    values.map { ElDropdownOption(value = it.second, label = it.first) }

/**
 * T-320 (55th session, UI-310) — the 4-step family-registration wizard:
 * Parent → Élèves → Facturation → Validation, with per-step validation,
 * a live billing simulation (REAL engine + REAL transport fees), an
 * atomic-transaction recap, and a full success screen (codes + copy +
 * WhatsApp share).
 *
 * Preserved from the previous implementation: the ViewModel's validation
 * contract (Vault §04.01), the `register()` input mapping (relationship
 * fr→en, level derivation, payment-plan passthrough), and the exact
 * `batchRegister` data path. UI state is now saveable and the wizard owns
 * back-navigation.
 */
@Composable
fun BatchRegistrationScreen(
    onSuccess: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: BatchRegistrationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val c = ElTheme.colors

    val currentStep by viewModel.currentStep.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val activationCode by viewModel.activationCode.collectAsState()
    val generatedParentCode by viewModel.generatedParentCode.collectAsState()
    val registeredStudentCodes by viewModel.registeredStudentCodes.collectAsState()
    val classes by viewModel.classes.collectAsState()
    val children by viewModel.children.collectAsState()
    val selectedChildIndex by viewModel.selectedChildIndex.collectAsState()
    val gradeTuitions by viewModel.gradeTuitions.collectAsState()
    val transportPricing by viewModel.transportPricing.collectAsState()

    // ── Step-1 parent fields (saveable — rotation/process-death safe) ──────
    var parentFirstName by rememberSaveable { mutableStateOf("") }
    var parentLastName by rememberSaveable { mutableStateOf("") }
    var parentPhone by rememberSaveable { mutableStateOf("") }
    var parentWhatsapp by rememberSaveable { mutableStateOf("") }
    var parentEmail by rememberSaveable { mutableStateOf("") }
    var parentNationalId by rememberSaveable { mutableStateOf("") }
    var parentOccupation by rememberSaveable { mutableStateOf("") }
    var parentAddress by rememberSaveable { mutableStateOf("") }
    var parentRelationship by rememberSaveable { mutableStateOf(RELATIONSHIPS.first()) }
    var parentTransportZone by rememberSaveable { mutableStateOf("") } // "" = Sans transport
    var parentLanguage by rememberSaveable { mutableStateOf(LANGUAGES.first().second) }

    // Per-step validation errors (screen-owned, transient by design)
    var stepError by remember { mutableStateOf<String?>(null) }

    // Live simulation (pure function of the wizard inputs + real pricing rows)
    val simulation = remember(currentStep, parentTransportZone, children, gradeTuitions, transportPricing) {
        if (currentStep >= 3) {
            viewModel.computeSimulation(
                parentTransportZone.takeIf { it.isNotBlank() },
                children,
            )
        } else {
            null
        }
    }

    fun resetAllFields() {
        parentFirstName = ""
        parentLastName = ""
        parentPhone = ""
        parentWhatsapp = ""
        parentEmail = ""
        parentNationalId = ""
        parentOccupation = ""
        parentAddress = ""
        parentRelationship = RELATIONSHIPS.first()
        parentTransportZone = ""
        parentLanguage = LANGUAGES.first().second
        stepError = null
    }

    val isSuccess = activationCode != null

    // Wizard back-navigation: step back inside the wizard before leaving.
    BackHandler(enabled = !isSuccess && currentStep > 1) { viewModel.prevStep() }

    // Per-step validation gate on "Suivant" (defence in depth with the VM).
    fun validateCurrentStep(): String? = when (currentStep) {
        1 -> when {
            parentFirstName.isBlank() || parentLastName.isBlank() ->
                "Le prénom et le nom du parent sont requis"
            parentPhone.isBlank() ->
                "Le téléphone principal du parent est requis"
            else -> null
        }
        2 -> children.indexOfFirst { child ->
            child.firstName.isBlank() || child.birthDate.isBlank() || child.gradeLevel.isBlank()
        }.takeIf { it >= 0 }?.let { index ->
            val child = children[index]
            when {
                child.firstName.isBlank() -> "Élève ${index + 1} : le prénom est requis"
                child.birthDate.isBlank() -> "Élève ${index + 1} : la date de naissance est requise"
                else -> "Élève ${index + 1} : le niveau scolaire est requis (sinon aucun frais ne peut être facturé)"
            }
        }
        else -> null
    }

    fun goToNextStep() {
        val problem = validateCurrentStep()
        if (problem != null) {
            stepError = problem
            return
        }
        stepError = null
        viewModel.nextStep()
    }

    fun submit() {
        val problem = validateCurrentStep()
        if (problem != null) {
            stepError = problem
            viewModel.setStep(1)
            return
        }
        stepError = null
        val parent = CreateParentInput(
            firstName = parentFirstName.trim(),
            lastName = parentLastName.trim(),
            phone = parentPhone.trim(),
            email = parentEmail.trim().ifBlank { null },
            occupation = parentOccupation.trim().ifBlank { null },
            address = parentAddress.trim().ifBlank { null },
            secondaryPhone = parentWhatsapp.trim().ifBlank { null },
            nationalId = parentNationalId.trim().ifBlank { null },
            relationship = when (parentRelationship) {
                "Père" -> "father"
                "Mère" -> "mother"
                else -> "guardian"
            },
            transportDestination = parentTransportZone.takeIf { it.isNotBlank() },
            preferredLanguage = parentLanguage,
        )
        val students = children.map { child ->
            CreateStudentInput(
                firstName = child.firstName.trim(),
                lastName = child.lastName.trim().ifBlank { parentLastName.trim() },
                gender = child.gender.ifBlank { "unspecified" },
                birthDate = child.birthDate.trim(),
                level = academicLevelForGradeCode(child.gradeLevel),
                gradeLevel = child.gradeLevel,
                classId = child.classId,
                medicalNotes = child.medicalNotes.trim().ifBlank { null },
                paymentPlan = child.paymentPlan,
            )
        }
        viewModel.register(parent, students) { /* success stays in-wizard; "Terminer" calls onSuccess */ }
    }

    ElScaffold(
        topBar = {
            if (!isSuccess) {
                ElTopBar(
                    title = "Inscription famille",
                    subtitle = "Étape $currentStep sur 4",
                    onBack = onBack,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (isSuccess) {
                RegistrationSuccessCard(
                    parentName = "${parentFirstName.trim()} ${parentLastName.trim()}".trim(),
                    parentCode = generatedParentCode ?: "—",
                    activationCode = activationCode!!,
                    studentCodes = registeredStudentCodes,
                    parentPhone = parentPhone,
                    whatsappPhone = parentWhatsapp.ifBlank { parentPhone },
                    onCopyCode = {
                        clipboardManager.setText(AnnotatedString(activationCode!!))
                        Toast.makeText(context, "Code copié dans le presse-papier !", Toast.LENGTH_SHORT).show()
                    },
                    onShareWhatsApp = {
                        PhoneUtils.openWhatsApp(
                            context,
                            parentWhatsapp.ifBlank { parentPhone },
                            activationShareMessage(parentFirstName.trim(), activationCode!!),
                        )
                    },
                    onNewRegistration = {
                        viewModel.resetWizard()
                        resetAllFields()
                    },
                    onDone = onSuccess,
                )
            } else {
                RegistrationStepProgress(
                    currentStep = currentStep,
                    onStepClick = { viewModel.setStep(it) },
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    error?.let {
                        ElAlertBanner(
                            title = "Erreur d'enregistrement",
                            message = it,
                            severity = ElAlertSeverity.DANGER,
                            onDismiss = viewModel::clearError,
                        )
                    }
                    stepError?.let {
                        ElAlertBanner(
                            title = "Étape incomplète",
                            message = it,
                            severity = ElAlertSeverity.WARNING,
                            onDismiss = { stepError = null },
                        )
                    }

                    when (currentStep) {
                        1 -> Step1Parent(
                            parentFirstName = parentFirstName,
                            onParentFirstNameChange = { parentFirstName = it },
                            parentLastName = parentLastName,
                            onParentLastNameChange = {
                                parentLastName = it
                                viewModel.fillChildrenLastNameIfBlank(it.trim())
                            },
                            parentPhone = parentPhone,
                            onParentPhoneChange = { parentPhone = it },
                            parentWhatsapp = parentWhatsapp,
                            onParentWhatsappChange = { parentWhatsapp = it },
                            parentEmail = parentEmail,
                            onParentEmailChange = { parentEmail = it },
                            parentNationalId = parentNationalId,
                            onParentNationalIdChange = { parentNationalId = it },
                            parentOccupation = parentOccupation,
                            onParentOccupationChange = { parentOccupation = it },
                            parentAddress = parentAddress,
                            onParentAddressChange = { parentAddress = it },
                            parentRelationship = parentRelationship,
                            onParentRelationshipChange = { parentRelationship = it },
                            transportZones = transportPricing.map { it.destination },
                            parentTransportZone = parentTransportZone,
                            onParentTransportZoneChange = { parentTransportZone = it },
                            parentLanguage = parentLanguage,
                            onParentLanguageChange = { parentLanguage = it },
                        )
                        2 -> Step2Children(
                            children = children,
                            selectedChildIndex = selectedChildIndex,
                            classes = classes,
                            onSelectChild = viewModel::selectChild,
                            onAddChild = { viewModel.addChild(parentLastName.trim()) },
                            onRemoveChild = viewModel::removeChild,
                            onUpdateChild = viewModel::updateChild,
                        )
                        3 -> Step3Simulation(
                            simulation = simulation,
                            transportZoneLabel = parentTransportZone.takeIf { it.isNotBlank() }
                                ?.let { zoneLabel(it) },
                            isLoading = gradeTuitions.isEmpty(),
                        )
                        4 -> Step4Validation(
                            parentFirstName = parentFirstName.trim(),
                            parentLastName = parentLastName.trim(),
                            parentPhone = parentPhone.trim(),
                            parentWhatsapp = parentWhatsapp.trim(),
                            parentTransportZoneLabel = parentTransportZone.takeIf { it.isNotBlank() }
                                ?.let { zoneLabel(it) },
                            children = children,
                            simulation = simulation,
                        )
                    }

                    // Wizard controls
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (currentStep > 1) {
                            ElButton(
                                text = "Précédent",
                                onClick = { viewModel.prevStep() },
                                variant = ElButtonVariant.SECONDARY,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        when (currentStep) {
                            1, 2 -> ElButton(
                                text = "Suivant",
                                onClick = { goToNextStep() },
                                variant = ElButtonVariant.PRIMARY,
                                modifier = Modifier.weight(1f),
                            )
                            3 -> ElButton(
                                text = "Vérifier le récapitulatif",
                                onClick = { goToNextStep() },
                                variant = ElButtonVariant.PRIMARY,
                                modifier = Modifier.weight(1f),
                            )
                            4 -> ElButton(
                                text = if (isLoading) "Inscription en cours…" else "Confirmer & inscrire",
                                onClick = { submit() },
                                variant = ElButtonVariant.PRIMARY,
                                loading = isLoading,
                                enabled = !isLoading,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ── Étape 1 — Parent / Tuteur ───────────────────────────────────────────────

@Composable
private fun Step1Parent(
    parentFirstName: String,
    onParentFirstNameChange: (String) -> Unit,
    parentLastName: String,
    onParentLastNameChange: (String) -> Unit,
    parentPhone: String,
    onParentPhoneChange: (String) -> Unit,
    parentWhatsapp: String,
    onParentWhatsappChange: (String) -> Unit,
    parentEmail: String,
    onParentEmailChange: (String) -> Unit,
    parentNationalId: String,
    onParentNationalIdChange: (String) -> Unit,
    parentOccupation: String,
    onParentOccupationChange: (String) -> Unit,
    parentAddress: String,
    onParentAddressChange: (String) -> Unit,
    parentRelationship: String,
    onParentRelationshipChange: (String) -> Unit,
    transportZones: List<String>,
    parentTransportZone: String,
    onParentTransportZoneChange: (String) -> Unit,
    parentLanguage: String,
    onParentLanguageChange: (String) -> Unit,
) {
    ElCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ElSectionHeader(
                title = "Renseignements du parent",
                subtitle = "Identité du tuteur légal et coordonnées",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ElTextField(
                    value = parentFirstName,
                    onValueChange = onParentFirstNameChange,
                    label = "Prénom *",
                    placeholder = "Ex : Karim",
                    modifier = Modifier.weight(1f),
                )
                ElTextField(
                    value = parentLastName,
                    onValueChange = onParentLastNameChange,
                    label = "Nom de famille *",
                    placeholder = "Ex : Benali",
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ElDropdown(
                    options = stringOptions(RELATIONSHIPS),
                    selectedValue = parentRelationship,
                    onSelected = { option -> onParentRelationshipChange(option.value) },
                    label = "Lien de parenté *",
                    modifier = Modifier.weight(1f),
                )
                ElDropdown(
                    options = pairOptions(LANGUAGES),
                    selectedValue = parentLanguage,
                    onSelected = { option -> onParentLanguageChange(option.value) },
                    label = "Langue préférée",
                    modifier = Modifier.weight(1f),
                )
            }

            ElTextField(
                value = parentPhone,
                onValueChange = onParentPhoneChange,
                label = "Téléphone principal *",
                placeholder = "Ex : 0555 12 34 56",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )

            ElTextField(
                value = parentWhatsapp,
                onValueChange = onParentWhatsappChange,
                label = "Numéro WhatsApp / Secondaire (optionnel)",
                placeholder = "Ex : 0661 23 45 67",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )

            ElTextField(
                value = parentEmail,
                onValueChange = onParentEmailChange,
                label = "Adresse email (optionnel)",
                placeholder = "karim.benali@example.dz",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            ElTextField(
                value = parentNationalId,
                onValueChange = onParentNationalIdChange,
                label = "N° pièce d'identité (optionnel)",
            )

            ElTextField(
                value = parentOccupation,
                onValueChange = onParentOccupationChange,
                label = "Profession (optionnel)",
            )

            ElDropdown(
                options = pairOptions(
                    listOf("Sans transport" to "") + transportZones.map { zoneLabel(it) to it },
                ),
                selectedValue = parentTransportZone,
                onSelected = { option -> onParentTransportZoneChange(option.value) },
                label = "Zone de transport scolaire",
                modifier = Modifier.fillMaxWidth(),
            )

            ElTextField(
                value = parentAddress,
                onValueChange = onParentAddressChange,
                label = "Adresse de résidence (optionnel)",
                placeholder = "12 Rue des Frères Bouadou, Boumerdès",
                singleLine = false,
            )
        }
    }
}

// ── Étape 2 — Élèves (tabbed child manager) ─────────────────────────────────

@Composable
private fun Step2Children(
    children: List<ChildFormState>,
    selectedChildIndex: Int,
    classes: List<com.example.domain.model.AcademicClass>,
    onSelectChild: (Int) -> Unit,
    onAddChild: () -> Unit,
    onRemoveChild: (Int) -> Unit,
    onUpdateChild: (Int, ChildFormState) -> Unit,
) {
    val c = ElTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Enfants à inscrire (${children.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
            )
            ElButton(
                text = "Ajouter",
                onClick = onAddChild,
                variant = ElButtonVariant.SECONDARY,
                icon = Icons.Default.Add,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            children.forEachIndexed { index, child ->
                ElChip(
                    text = child.firstName.ifBlank { "Élève ${index + 1}" },
                    selected = index == selectedChildIndex,
                    onClick = { onSelectChild(index) },
                )
            }
        }

        if (selectedChildIndex in children.indices) {
            val index = selectedChildIndex
            val child = children[index]
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Fiche élève #${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = c.primary,
                        )
                        if (children.size > 1) {
                            ElButton(
                                text = "Supprimer",
                                onClick = { onRemoveChild(index) },
                                variant = ElButtonVariant.DANGER,
                                icon = Icons.Default.DeleteOutline,
                            )
                        }
                    }

                    fun update(transform: (ChildFormState) -> ChildFormState) = onUpdateChild(index, transform(child))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ElTextField(
                            value = child.firstName,
                            onValueChange = { v -> update { it.copy(firstName = v) } },
                            label = "Prénom *",
                            placeholder = "Ex : Yacine",
                            modifier = Modifier.weight(1f),
                        )
                        ElTextField(
                            value = child.lastName,
                            onValueChange = { v -> update { it.copy(lastName = v) } },
                            label = "Nom *",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ElTextField(
                            value = child.birthDate,
                            onValueChange = { v -> update { it.copy(birthDate = v) } },
                            label = "Date de naissance *",
                            placeholder = "AAAA-MM-JJ",
                            modifier = Modifier.weight(1f),
                        )
                        ElDropdown(
                            options = pairOptions(GENDERS),
                            selectedValue = child.gender.ifBlank { "unspecified" },
                            onSelected = { option -> update { it.copy(gender = option.value) } },
                            label = "Sexe",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    ElDropdown(
                        options = stringOptions(GRADE_LEVEL_CODES),
                        selectedValue = child.gradeLevel,
                        onSelected = { option -> update { it.copy(gradeLevel = option.value, classId = null) } },
                        label = "Niveau scolaire *",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    val cycle = academicLevelForGradeCode(child.gradeLevel)
                    val cycleClasses = classes.filter { it.level == cycle }
                    ElDropdown(
                        options = listOf(
                            ElDropdownOption(value = "", label = "Non assignée"),
                        ) + cycleClasses.map { ElDropdownOption(value = it.id, label = it.name) },
                        selectedValue = child.classId ?: "",
                        onSelected = { option -> update { it.copy(classId = option.value.ifBlank { null }) } },
                        label = if (cycleClasses.isEmpty()) "Classe (aucune classe $cycle disponible)" else "Classe (optionnel)",
                        enabled = cycleClasses.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ElDropdown(
                        options = pairOptions(PAYMENT_PLANS),
                        selectedValue = child.paymentPlan,
                        onSelected = { option -> update { it.copy(paymentPlan = option.value) } },
                        label = "Modalité de paiement",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ElTextField(
                        value = child.medicalNotes,
                        onValueChange = { v -> update { it.copy(medicalNotes = v) } },
                        label = "Notes médicales (optionnel)",
                        singleLine = false,
                    )
                }
            }
        }
    }
}

// ── Étape 3 — Facturation & simulation en direct ────────────────────────────

@Composable
private fun Step3Simulation(
    simulation: BillingSimulation?,
    transportZoneLabel: String?,
    isLoading: Boolean,
) {
    val c = ElTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElSectionHeader(
            title = "Facturation & simulation en direct",
            subtitle = "Calculée avec les tarifs officiels de l'établissement" +
                (transportZoneLabel?.let { " — transport : $it" } ?: ""),
        )
        when {
            isLoading -> Text(
                "Chargement des tarifs…",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            simulation == null -> Text(
                "Aucune donnée de simulation.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            else -> {
                SimulationGrandTotalCard(
                    simulation = simulation,
                    childCount = simulation.children.size,
                )
                if (simulation.discountsTotal < 0L) {
                    ElAlertBanner(
                        title = "Réductions appliquées",
                        message = "Réduction totale : ${elMoneyFormat(simulation.discountsTotal)}",
                        severity = ElAlertSeverity.SUCCESS,
                    )
                }
                simulation.children.forEach { billing ->
                    SimulationChildCard(billing = billing)
                }
            }
        }
    }
}

// ── Étape 4 — Validation & envoi ────────────────────────────────────────────

@Composable
private fun Step4Validation(
    parentFirstName: String,
    parentLastName: String,
    parentPhone: String,
    parentWhatsapp: String,
    parentTransportZoneLabel: String?,
    children: List<ChildFormState>,
    simulation: BillingSimulation?,
) {
    val c = ElTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElSectionHeader(
            title = "Validation & envoi",
            subtitle = "Vérifiez le récapitulatif avant l'enregistrement définitif",
        )

        AtomicTransactionBanner()

        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Parent / Tuteur",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                ElInfoRow(
                    label = "Nom",
                    value = "$parentFirstName $parentLastName".ifBlank { "—" },
                    valueTint = c.textPrimary,
                )
                ElInfoRow(
                    label = "Téléphone",
                    value = parentPhone,
                    valueTint = c.textPrimary,
                )
                if (parentWhatsapp.isNotBlank()) {
                    ElInfoRow(
                        label = "WhatsApp",
                        value = parentWhatsapp,
                        valueTint = c.textPrimary,
                    )
                }
                ElInfoRow(
                    label = "Transport",
                    value = parentTransportZoneLabel ?: "Sans transport",
                    valueTint = c.textPrimary,
                )
            }
        }

        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Élèves (${children.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                children.forEachIndexed { index, child ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                listOf(child.firstName, child.lastName).filter { it.isNotBlank() }
                                    .joinToString(" ").ifBlank { "Élève ${index + 1}" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textPrimary,
                            )
                            if (child.birthDate.isNotBlank()) {
                                Text(
                                    "Né(e) le ${child.birthDate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                )
                            }
                        }
                        if (child.gradeLevel.isNotBlank()) {
                            ElTag(
                                text = child.gradeLevel.uppercase(),
                                tone = ElTagTone.INFO,
                            )
                        }
                    }
                }
            }
        }

        if (simulation != null) {
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Facturation globale",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    ElInfoRow(
                        label = "Scolarité nette totale",
                        value = elMoneyFormat(simulation.tuitionNetTotal),
                    )
                    if (simulation.discountsTotal < 0L) {
                        ElInfoRow(
                            label = "Réductions",
                            value = elMoneyFormat(simulation.discountsTotal),
                            valueTint = c.success,
                        )
                    }
                    if (simulation.transportTotal > 0L) {
                        ElInfoRow(
                            label = "Transport (${children.size} × zone)",
                            value = elMoneyFormat(simulation.transportTotal),
                        )
                    }
                    ElInfoRow(
                        label = "TOTAL ANNUEL PRÉVU",
                        value = elMoneyFormat(simulation.grandTotal),
                        valueTint = c.primary,
                    )
                }
            }
        }
    }
}
