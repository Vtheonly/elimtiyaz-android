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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.AcademicLevel
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.TenancyTier
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.ElImtiyazCard

/**
 * BatchRegistrationScreen — 4-step atomic registration wizard (Route.BatchRegistration).
 *
 *   Step 1 → Parent info form
 *   Step 2 → Students list (add/remove child forms)
 *   Step 3 → Review summary
 *   Step 4 → Submit (atomic batch — master plan §04.03)
 *
 * On success the wizard navigates to Route.ParentDetail of the newly-created parent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchRegistrationScreen(
    nav: NavController,
    vm: BatchRegistrationViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()

    val canCreate = session?.can(com.elimtiyaz.core.common.Permission.CreateParent) ?: false ||
        session?.can(com.elimtiyaz.core.common.Permission.CreateStudent) ?: false

    // If the user lacks both create permissions, show a denial screen.
    if (!canCreate) {
        PermissionDeniedScreen(onBack = { nav.popBackStack() })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nouvelle famille") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            WizardStepper(currentStep = state.step, onJump = vm::jumpTo)
            HorizontalDivider()

            Box(modifier = Modifier.weight(1f)) {
                when (state.step) {
                    BatchStep.ParentInfo -> ParentInfoStep(
                        form = state.parentForm,
                        error = state.formError,
                        onUpdate = vm::updateParentForm,
                    )
                    BatchStep.Students -> StudentsStep(
                        forms = state.studentForms,
                        error = state.formError,
                        onAdd = vm::addStudent,
                        onRemove = vm::removeStudent,
                        onUpdate = vm::updateStudent,
                    )
                    BatchStep.Review -> ReviewStep(
                        parentForm = state.parentForm,
                        studentForms = state.studentForms,
                        error = state.formError,
                    )
                    BatchStep.Submit -> SubmitStep(
                        isSubmitting = state.isSubmitting,
                        error = state.formError,
                        result = state.result,
                    )
                }
            }

            // Bottom navigation bar with Prev/Next/Submit.
            WizardBottomBar(
                step = state.step,
                isSubmitting = state.isSubmitting,
                canGoNext = true,
                onPrev = vm::goToPrevious,
                onNext = { vm.goToNext() },
                onSubmit = {
                    vm.submit { ok, msg, parentId ->
                        if (ok && parentId != null) {
                            nav.navigate(Route.ParentDetail.build(parentId)) {
                                popUpTo(Route.Roster.route) { this@popUpTo.inclusive = false }
                            }
                        }
                        // On failure the error is surfaced via state.formError, picked up by SubmitStep.
                    }
                },
            )
        }
    }
}

@Composable
private fun PermissionDeniedScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(ElimtiyazSpacing.x6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Vous n'avez pas l'autorisation de créer un parent ou un élève.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(ElimtiyazSpacing.x4))
        Button(onClick = onBack) { Text("Retour") }
    }
}

@Composable
private fun WizardStepper(currentStep: BatchStep, onJump: (BatchStep) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ElimtiyazSpacing.x4),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BatchStep.values().forEach { step ->
            val isCurrent = step == currentStep
            val isPast = step.index < currentStep.index
            val color = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isPast -> ElimtiyazColors.SuccessGreen
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = color,
                    onClick = { if (step.index <= currentStep.index) onJump(step) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isPast) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        } else {
                            Text(
                                "${step.index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(ElimtiyazSpacing.x1))
                Text(
                    step.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WizardBottomBar(
    step: BatchStep,
    isSubmitting: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ElimtiyazSpacing.x4),
            horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
        ) {
            if (step != BatchStep.ParentInfo) {
                OutlinedButton(onClick = onPrev, modifier = Modifier.weight(1f)) {
                    Text("Précédent")
                }
            }
            if (step == BatchStep.Submit) {
                Button(
                    onClick = onSubmit,
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text("Valider l'inscription")
                    }
                }
            } else {
                Button(
                    onClick = onNext,
                    enabled = canGoNext && !isSubmitting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Suivant")
                    Spacer(Modifier.width(ElimtiyazSpacing.x2))
                    Icon(Icons.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ---------- Step 1: Parent info ----------

@Composable
private fun ParentInfoStep(
    form: ParentForm,
    error: String?,
    onUpdate: ((ParentForm) -> ParentForm) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(ElimtiyazSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
    ) {
        item { SectionTitle("Informations du parent") }
        item {
            ElImtiyazCard {
                Column(modifier = Modifier.padding(ElimtiyazSpacing.x4), verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3)) {
                        OutlinedTextField(
                            value = form.firstName,
                            onValueChange = { v -> onUpdate { it.copy(firstName = v) } },
                            label = { Text("Prénom *") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = form.lastName,
                            onValueChange = { v -> onUpdate { it.copy(lastName = v) } },
                            label = { Text("Nom *") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3)) {
                        OutlinedTextField(
                            value = form.phone,
                            onValueChange = { v -> onUpdate { it.copy(phone = v) } },
                            label = { Text("Téléphone *") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = form.whatsapp ?: "",
                            onValueChange = { v -> onUpdate { it.copy(whatsapp = v) } },
                            label = { Text("WhatsApp") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = form.email,
                        onValueChange = { v -> onUpdate { it.copy(email = v) } },
                        label = { Text("E-mail") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.occupation ?: "",
                        onValueChange = { v -> onUpdate { it.copy(occupation = v) } },
                        label = { Text("Profession") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.address ?: "",
                        onValueChange = { v -> onUpdate { it.copy(address = v) } },
                        label = { Text("Adresse") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3)) {
                        CityTierDropdown(
                            selected = form.cityTier,
                            onSelect = { v -> onUpdate { it.copy(cityTier = v) } },
                            modifier = Modifier.weight(1f),
                        )
                        LanguageDropdown(
                            selected = form.preferredLanguage,
                            onSelect = { v -> onUpdate { it.copy(preferredLanguage = v) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        error?.let {
            item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ---------- Step 2: Students ----------

@Composable
private fun StudentsStep(
    forms: List<StudentForm>,
    error: String?,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onUpdate: (Int, (StudentForm) -> StudentForm) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(ElimtiyazSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("Élèves (${forms.size})")
                OutlinedButton(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(ElimtiyazSpacing.x2))
                    Text("Ajouter un élève")
                }
            }
        }
        if (forms.isEmpty()) {
            item {
                EmptyInline("Aucun élève ajouté. Touchez « Ajouter un élève » pour commencer.")
            }
        } else {
            items(forms.size, key = { it }) { index ->
                StudentFormCard(
                    index = index,
                    form = forms[index],
                    onRemove = { onRemove(index) },
                    onUpdate = { transform -> onUpdate(index, transform) },
                )
            }
        }
        error?.let {
            item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun StudentFormCard(
    index: Int,
    form: StudentForm,
    onRemove: () -> Unit,
    onUpdate: ((StudentForm) -> StudentForm) -> Unit,
) {
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4), verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Élève #${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3)) {
                OutlinedTextField(
                    value = form.firstName,
                    onValueChange = { v -> onUpdate { it.copy(firstName = v) } },
                    label = { Text("Prénom *") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.lastName,
                    onValueChange = { v -> onUpdate { it.copy(lastName = v) } },
                    label = { Text("Nom *") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = form.birthDate,
                onValueChange = { v -> onUpdate { it.copy(birthDate = v) } },
                label = { Text("Date de naissance (AAAA-MM-JJ) *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3)) {
                LevelDropdown(
                    selected = form.level,
                    onSelect = { v -> onUpdate { it.copy(level = v) } },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = if (form.gradeYear > 0) form.gradeYear.toString() else "",
                    onValueChange = { v -> onUpdate { it.copy(gradeYear = v.toIntOrNull() ?: 0) } },
                    label = { Text("Année *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3)) {
                OutlinedTextField(
                    value = form.classId ?: "",
                    onValueChange = { v -> onUpdate { it.copy(classId = v.ifBlank { null }) } },
                    label = { Text("Classe") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                CityTierDropdown(
                    selected = form.transportTier,
                    onSelect = { v -> onUpdate { it.copy(transportTier = v) } },
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = form.medicalNotes ?: "",
                onValueChange = { v -> onUpdate { it.copy(medicalNotes = v) } },
                label = { Text("Notes médicales") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------- Step 3: Review ----------

@Composable
private fun ReviewStep(
    parentForm: ParentForm,
    studentForms: List<StudentForm>,
    error: String?,
) {
    LazyColumn(
        contentPadding = PaddingValues(ElimtiyazSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
    ) {
        item { SectionTitle("Révision avant validation") }
        item {
            ElImtiyazCard {
                Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
                    Text("Parent", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(ElimtiyazSpacing.x2))
                    ReviewLine("Nom", Formatters.fullName(parentForm.firstName, parentForm.lastName))
                    ReviewLine("Téléphone", parentForm.phone)
                    ReviewLine("WhatsApp", parentForm.whatsapp ?: "—")
                    ReviewLine("E-mail", parentForm.email.ifBlank { "—" })
                    ReviewLine("Profession", parentForm.occupation ?: "—")
                    ReviewLine("Adresse", parentForm.address ?: "—")
                    ReviewLine("Zone", TenancyTier.from(parentForm.cityTier)?.displayFr ?: "—")
                    ReviewLine("Langue", parentForm.preferredLanguage)
                }
            }
        }
        item { SectionTitle("Élèves (${studentForms.size})") }
        if (studentForms.isEmpty()) {
            item { EmptyInline("Aucun élève à inscrire.") }
        } else {
            items(studentForms.size, key = { it }) { i ->
                val s = studentForms[i]
                ElImtiyazCard {
                    Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
                        Text(
                            "Élève #${i + 1}: ${Formatters.fullName(s.firstName, s.lastName)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(ElimtiyazSpacing.x2))
                        ReviewLine("Naissance", s.birthDate)
                        ReviewLine("Niveau", "${AcademicLevel.from(s.level)?.displayFr ?: s.level} — Année ${s.gradeYear}")
                        ReviewLine("Classe", s.classId ?: "—")
                        ReviewLine("Transport", TenancyTier.from(s.transportTier)?.displayFr ?: "—")
                        s.medicalNotes?.let { ReviewLine("Médical", it) }
                    }
                }
            }
        }
        error?.let {
            item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ReviewLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ElimtiyazSpacing.x1),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

// ---------- Step 4: Submit ----------

@Composable
private fun SubmitStep(
    isSubmitting: Boolean,
    error: String?,
    result: com.elimtiyaz.domain.model.BatchRegistrationResult?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ElimtiyazSpacing.x6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
            Text("Enregistrement en cours…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (result != null) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = ElimtiyazColors.SuccessGreen, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Text("Inscription réussie !", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(
                "Parent ${result.parent.code} créé avec ${result.students.size} élève(s).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Touchez « Valider l'inscription » pour enregistrer cette famille.\nL'opération est atomique : aucune donnée partielle ne sera persistée en cas d'erreur.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            error?.let {
                Spacer(Modifier.height(ElimtiyazSpacing.x3))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------- Shared small components ----------

@Composable
private fun CityTierDropdown(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(null to "—") + TenancyTier.values().map { it.key to it.displayFr }
    DropdownSelector(
        label = "Zone",
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
private fun LanguageDropdown(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf("fr" to "Français", "ar" to "العربية")
    DropdownSelector(
        label = "Langue",
        options = options,
        selected = selected,
        onSelect = { onSelect(it ?: "fr") },
        modifier = modifier,
    )
}

@Composable
private fun LevelDropdown(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = AcademicLevel.values().map { it.key to it.displayFr }
    DropdownSelector(
        label = "Niveau",
        options = options,
        selected = selected,
        onSelect = { onSelect(it ?: "primaire") },
        modifier = modifier,
    )
}

@Composable
private fun DropdownSelector(
    label: String,
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "—"
    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = ElimtiyazSpacing.x3, vertical = ElimtiyazSpacing.x2)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    selectedLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, name) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun EmptyInline(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = ElimtiyazSpacing.x3),
    )
}
