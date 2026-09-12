package com.example.ui.features.financials

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.formatDzd
import com.example.domain.model.Parent
import com.example.domain.model.Student
import com.example.domain.repository.CollectPaymentInput
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.input.ElSearchBar
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.foundation.elMoneyFormat
import com.example.ui.designsystem.theme.ElTheme

/**
 * T-321 (55th session, UI-311) — the counter-payment flow, polished on the
 * canonical design system:
 *  - dismissible error banner visible on ALL steps (was step-3-only plain Text);
 *  - full-screen success state with "Nouvel encaissement" reset (the form no
 *    longer stays editable behind the receipt);
 *  - green "famille à jour" state when outstanding == 0;
 *  - method-conditional field validation (CHECK needs number + bank,
 *    TRANSFER needs a reference) with inline errors;
 *  - ENGINE-DERIVED allocation preview (core.allocatePaymentToInstallments
 *    via the ViewModel — the same inputs `collect` will use) with an honest
 *    unallocated-credit line.
 *
 * Preserved: the 3-step family → member → payment structure (35th session),
 * the SavedStateHandle prefill, the VM contract, the Room → sync write path,
 * and the slider/preset amount entry.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CounterPaymentScreen(
    onBack: () -> Unit,
    initialParentId: String? = null,
    initialStudentId: String? = null,
    viewModel: CounterPaymentViewModel = hiltViewModel(),
) {
    val parents by viewModel.parents.collectAsState()
    val selectedParent by viewModel.selectedParent.collectAsState()
    val students by viewModel.students.collectAsState()
    val selectedStudent by viewModel.selectedStudent.collectAsState()
    val outstanding by viewModel.parentOutstanding.collectAsState()
    val installments by viewModel.installments.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val receiptNumber by viewModel.receiptNumber.collectAsState()

    var parentSearchQuery by rememberSaveable { mutableStateOf("") }
    var isFamilyMemberChosen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialParentId, initialStudentId) {
        if (!initialParentId.isNullOrBlank()) {
            viewModel.initialize(initialParentId, initialStudentId)
            if (!initialStudentId.isNullOrBlank()) {
                isFamilyMemberChosen = true
            }
        }
    }

    var amountDzd by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(PaymentMethod.CASH.name) }
    var category by rememberSaveable { mutableStateOf(PaymentCategory.TUITION.name) }
    var checkNumber by rememberSaveable { mutableStateOf("") }
    var checkBank by rememberSaveable { mutableStateOf("") }
    var transferRef by rememberSaveable { mutableStateOf("") }
    // Per-field validation (transient)
    var checkNumberError by remember { mutableStateOf<String?>(null) }
    var checkBankError by remember { mutableStateOf<String?>(null) }
    var transferRefError by remember { mutableStateOf<String?>(null) }

    val methodValue = PaymentMethod.valueOf(method)
    val categoryValue = PaymentCategory.valueOf(category)

    val maxSliderAmount = remember(outstanding) {
        val debt = (outstanding / 100).toFloat()
        if (debt > 0f) maxOf(debt, 100_000f) else 300_000f
    }
    var sliderPosition by rememberSaveable { mutableFloatStateOf(0f) }

    fun onAmountTextChange(typed: String) {
        val clean = typed.filter { it.isDigit() }
        amountDzd = clean
        val parsed = clean.toFloatOrNull() ?: 0f
        sliderPosition = (parsed / maxSliderAmount).coerceIn(0f, 1f)
    }

    fun onSliderChange(pos: Float) {
        sliderPosition = pos
        val rounded = ((pos * maxSliderAmount) / 500).toInt() * 500
        amountDzd = if (rounded > 0) rounded.toString() else ""
    }

    val filteredParents = remember(parentSearchQuery, parents) {
        if (parentSearchQuery.isBlank()) parents
        else parents.filter {
            it.fullName.contains(parentSearchQuery, ignoreCase = true) ||
                it.phone.contains(parentSearchQuery) ||
                it.code.contains(parentSearchQuery, ignoreCase = true)
        }
    }

    val enteredAmount = amountDzd.toLongOrNull() ?: 0L
    val enteredAmountCents = enteredAmount * 100L

    // Engine-derived preview (VM → core engine; never re-derived in the UI).
    val allocationPreview = remember(enteredAmountCents, categoryValue, methodValue, installments, selectedParent) {
        viewModel.computeAllocationPreview(enteredAmountCents, categoryValue, methodValue)
    }

    fun validateMethodFields(): Boolean {
        var ok = true
        if (methodValue == PaymentMethod.CHECK && checkNumber.isBlank()) {
            checkNumberError = "Le numéro de chèque est requis"
            ok = false
        } else checkNumberError = null
        if (methodValue == PaymentMethod.CHECK && checkBank.isBlank()) {
            checkBankError = "La banque émettrice est requise"
            ok = false
        } else checkBankError = null
        if (methodValue == PaymentMethod.TRANSFER && transferRef.isBlank()) {
            transferRefError = "La référence du virement est requise"
            ok = false
        } else transferRefError = null
        return ok
    }

    fun resetPaymentForm() {
        amountDzd = ""
        notes = ""
        method = PaymentMethod.CASH.name
        category = PaymentCategory.TUITION.name
        checkNumber = ""
        checkBank = ""
        transferRef = ""
        checkNumberError = null
        checkBankError = null
        transferRefError = null
        sliderPosition = 0f
    }

    when {
        selectedParent != null && isFamilyMemberChosen -> {
            BackHandler(enabled = true) {
                isFamilyMemberChosen = false
            }
        }
        selectedParent != null && !isFamilyMemberChosen -> {
            BackHandler(enabled = true) {
                viewModel.selectParent(null)
                isFamilyMemberChosen = false
            }
        }
        else -> {
            BackHandler(enabled = true) {
                onBack()
            }
        }
    }

    val topBarTitle = when {
        selectedParent == null -> "Encaissement au guichet"
        !isFamilyMemberChosen -> "Choisir le membre de la famille"
        else -> "Détails du règlement"
    }

    val topBarOnBack: () -> Unit = when {
        selectedParent != null && isFamilyMemberChosen -> {
            { isFamilyMemberChosen = false }
        }
        selectedParent != null && !isFamilyMemberChosen -> {
            { viewModel.selectParent(null); isFamilyMemberChosen = false }
        }
        else -> onBack
    }

    ElScaffold(
        topBar = { if (receiptNumber == null) ElTopBar(title = topBarTitle, onBack = topBarOnBack) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Dismissible error banner — visible on ALL steps (T-321).
            error?.let {
                ElAlertBanner(
                    title = "Erreur d'encaissement",
                    message = it,
                    severity = ElAlertSeverity.DANGER,
                    onDismiss = viewModel::clearError,
                )
            }

            // FULL-SCREEN SUCCESS (T-321): the form is hidden behind the
            // receipt; "Nouvel encaissement" restarts the flow cleanly.
            if (receiptNumber != null && selectedParent != null) {
                val p = selectedParent!!
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(ElTheme.colors.successContainer),
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Encaissement validé",
                            tint = ElTheme.colors.success,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                    Text(
                        "Encaissement validé !",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    ElCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "NUMÉRO DE REÇU",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElTheme.colors.textSecondary,
                            )
                            Text(
                                receiptNumber ?: "—",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = ElTheme.colors.primary,
                            )
                            Text(
                                "Le grand livre et l'échéancier ont été mis à jour.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElTheme.colors.textSecondary,
                            )
                        }
                    }
                    ElButton(
                        text = "Nouvel encaissement",
                        onClick = {
                            resetPaymentForm()
                            isFamilyMemberChosen = false
                            viewModel.selectParent(null)
                        },
                        variant = ElButtonVariant.SECONDARY,
                        fullWidth = true,
                    )
                    ElButton(
                        text = "Terminer",
                        onClick = onBack,
                        variant = ElButtonVariant.PRIMARY,
                        fullWidth = true,
                    )
                }
            }
            // STEP 1: Select Parent if none selected
            else if (selectedParent == null) {
                ElSectionHeader(
                    title = "Sélectionnez une famille",
                    subtitle = "Recherchez et touchez une famille pour débuter le paiement :",
                )

                ElSearchBar(
                    query = parentSearchQuery,
                    onQueryChange = { parentSearchQuery = it },
                    placeholder = "Nom, téléphone, code…",
                    modifier = Modifier.fillMaxWidth(),
                )

                if (filteredParents.isEmpty()) {
                    ElEmptyState(
                        icon = Icons.Default.Person,
                        title = "Aucune famille trouvée",
                        subtitle = "Vérifiez vos termes de recherche.",
                    )
                } else {
                    filteredParents.forEach { p ->
                        ParentPickCard(
                            parent = p,
                            onClick = {
                                viewModel.selectParent(p)
                                isFamilyMemberChosen = false
                                parentSearchQuery = ""
                            },
                        )
                    }
                }
            }
            // STEP 2: Choose Family Member before proceeding with payment
            else if (!isFamilyMemberChosen) {
                val p = selectedParent!!

                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                ElAvatar(initials = p.fullName, size = ElAvatarSize.M)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(p.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Code : ${p.code} • Tél : ${p.phone}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ElTheme.colors.textSecondary,
                                    )
                                }
                            }
                            ElChip(
                                text = "Changer de famille",
                                onClick = {
                                    viewModel.selectParent(null)
                                    isFamilyMemberChosen = false
                                },
                            )
                        }

                        if (outstanding > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Solde restant dû pour cette famille : ${elMoneyFormat(outstanding)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElTheme.colors.danger,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else {
                            Text(
                                "Famille à jour dans ses cotisations.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElTheme.colors.success,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                ElSectionHeader(
                    title = "Pour quel élève ou membre de la famille ?",
                    subtitle = "Veuillez choisir le bénéficiaire précis pour imputer le règlement :",
                )

                if (students.isNotEmpty()) {
                    students.forEach { student ->
                        StudentPickCard(
                            student = student,
                            onClick = {
                                viewModel.selectStudent(student)
                                isFamilyMemberChosen = true
                            },
                        )
                    }

                    ElCard(
                        modifier = Modifier.fillMaxWidth(),
                        size = ElCardSize.COMPACT,
                        onClick = {
                            viewModel.selectStudent(null)
                            isFamilyMemberChosen = true
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(ElTheme.colors.warningContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Group,
                                        contentDescription = null,
                                        tint = ElTheme.colors.warning,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Toute la famille (Paiement global)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "Règlement général ventilé sur l'échéancier familial",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ElTheme.colors.textSecondary,
                                    )
                                }
                            }
                            ElTag(text = "Global", tone = ElTagTone.WARNING)
                        }
                    }
                } else {
                    ElCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Aucun enfant enregistré pour cette famille.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            ElButton(
                                text = "Continuer avec le compte famille (global)",
                                onClick = {
                                    viewModel.selectStudent(null)
                                    isFamilyMemberChosen = true
                                },
                                variant = ElButtonVariant.SECONDARY,
                                fullWidth = true,
                            )
                        }
                    }
                }
            }
            // STEP 3: Payment Details form
            else {
                val p = selectedParent!!

                BeneficiaryRecapCard(
                    parent = p,
                    selectedStudent = selectedStudent,
                    outstanding = outstanding,
                    onChangeStudent = { isFamilyMemberChosen = false },
                )

                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ElSectionHeader(title = "Montant à encaisser")

                        ElTextField(
                            value = amountDzd,
                            onValueChange = ::onAmountTextChange,
                            label = "Montant en Dinars Algériens (DZD) *",
                            placeholder = "Ex : 25000",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Column {
                            Slider(
                                value = sliderPosition,
                                onValueChange = ::onSliderChange,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = ElTheme.colors.primary,
                                    activeTrackColor = ElTheme.colors.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("0", style = MaterialTheme.typography.labelSmall, color = ElTheme.colors.textSecondary)
                                Text(
                                    elMoneyFormat(maxSliderAmount.toLong() * 100L),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElTheme.colors.textSecondary,
                                )
                            }
                        }

                        Text(
                            "Raccourcis montants",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElTheme.colors.textSecondary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(5_000L, 10_000L, 20_000L, 50_000L, 100_000L).forEach { preset ->
                                ElChip(
                                    text = "+${preset.formatDzd()} DA",
                                    onClick = {
                                        val currentVal = amountDzd.toLongOrNull() ?: 0L
                                        onAmountTextChange((currentVal + preset).toString())
                                    },
                                )
                            }
                            if (outstanding > 0) {
                                ElChip(
                                    text = "Solde exact (${elMoneyFormat(outstanding, showCurrency = false)})",
                                    selected = amountDzd == (outstanding / 100).toString(),
                                    onClick = { onAmountTextChange((outstanding / 100).toString()) },
                                )
                            }
                        }
                    }
                }

                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Mode de règlement",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                PaymentMethod.CASH to "Espèces",
                                PaymentMethod.CHECK to "Chèque",
                                PaymentMethod.TRANSFER to "Virement",
                            ).forEach { (m, label) ->
                                ElChip(
                                    text = label,
                                    selected = methodValue == m,
                                    onClick = { method = m.name },
                                )
                            }
                        }

                        if (methodValue == PaymentMethod.CHECK) {
                            ElTextField(
                                value = checkNumber,
                                onValueChange = { checkNumber = it; checkNumberError = null },
                                label = "Numéro de chèque *",
                                placeholder = "Ex : 0087654321",
                                errorText = checkNumberError,
                                isError = checkNumberError != null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ElTextField(
                                value = checkBank,
                                onValueChange = { checkBank = it; checkBankError = null },
                                label = "Banque émettrice *",
                                placeholder = "BNA, CPA, BEA, Al Baraka…",
                                errorText = checkBankError,
                                isError = checkBankError != null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (methodValue == PaymentMethod.TRANSFER) {
                            ElTextField(
                                value = transferRef,
                                onValueChange = { transferRef = it; transferRefError = null },
                                label = "Référence du virement *",
                                placeholder = "Ex : VIR-2026-98765",
                                errorText = transferRefError,
                                isError = transferRefError != null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Catégorie de paiement",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(
                                PaymentCategory.TUITION to "Scolarité",
                                PaymentCategory.TRANSPORT to "Transport",
                                PaymentCategory.CANTEEN to "Cantine",
                                PaymentCategory.UNIFORM to "Uniforme & Tenue",
                                PaymentCategory.BOOKS to "Fournitures & Livres",
                                PaymentCategory.OTHER to "Autre",
                            ).forEach { (cat, label) ->
                                ElChip(
                                    text = label,
                                    selected = categoryValue == cat,
                                    onClick = { category = cat.name },
                                )
                            }
                        }

                        ElTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = "Remarques / Notes de caisse",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Allocation preview — ENGINE output, honest credit line (T-321).
                if (allocationPreview != null && enteredAmount > 0L) {
                    AllocationPreviewCard(
                        preview = allocationPreview,
                        installmentLabels = installments.associate { it.id to it.label },
                    )
                }

                ElButton(
                    text = if (isLoading) "Validation en cours…" else "Valider l'encaissement (${enteredAmount.formatDzd()} DZD)",
                    onClick = {
                        if (validateMethodFields()) {
                            val input = CollectPaymentInput(
                                parentId = p.id,
                                studentId = selectedStudent?.id,
                                amount = enteredAmount * 100L,
                                method = methodValue,
                                category = categoryValue,
                                notes = notes.ifBlank { null },
                                checkNumber = checkNumber.ifBlank { null },
                                checkBankName = checkBank.ifBlank { null },
                                transferReference = transferRef.ifBlank { null },
                                proofPath = null,
                            )
                            viewModel.collect(input) { }
                        }
                    },
                    enabled = !isLoading && enteredAmount > 0L,
                    loading = isLoading,
                    fullWidth = true,
                    icon = Icons.Default.Payments,
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ── Step cards ──────────────────────────────────────────────────────────────

@Composable
private fun ParentPickCard(parent: Parent, onClick: () -> Unit) {
    ElCard(
        modifier = Modifier.fillMaxWidth(),
        size = ElCardSize.COMPACT,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                ElAvatar(initials = parent.fullName, size = ElAvatarSize.M)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(parent.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Code : ${parent.code} • Tél : ${parent.phone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElTheme.colors.textSecondary,
                    )
                }
            }
            ElTag(text = "Sélectionner", tone = ElTagTone.INFO)
        }
    }
}

@Composable
private fun StudentPickCard(student: Student, onClick: () -> Unit) {
    ElCard(
        modifier = Modifier.fillMaxWidth(),
        size = ElCardSize.COMPACT,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                ElAvatar(initials = student.fullName, size = ElAvatarSize.M)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(student.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Niveau : ${student.gradeLevel.uppercase()} • Matricule : ${student.code}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElTheme.colors.textSecondary,
                    )
                }
            }
            ElTag(text = "Sélectionner", tone = ElTagTone.SUCCESS)
        }
    }
}

@Composable
private fun BeneficiaryRecapCard(
    parent: Parent,
    selectedStudent: Student?,
    outstanding: Long,
    onChangeStudent: () -> Unit,
) {
    ElCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Famille : ${parent.fullName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (selectedStudent != null) {
                            "Bénéficiaire : ${selectedStudent.fullName} (${selectedStudent.gradeLevel.uppercase()})"
                        } else {
                            "Bénéficiaire : Toute la famille (Paiement global)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedStudent != null) ElTheme.colors.success else ElTheme.colors.warning,
                    )
                }
                ElChip(text = "Changer d'élève", onClick = onChangeStudent)
            }

            if (outstanding > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Solde restant dû : ${elMoneyFormat(outstanding)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ElTheme.colors.danger,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    "Famille à jour dans ses cotisations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ElTheme.colors.success,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun AllocationPreviewCard(
    preview: com.example.core.AllocationResult,
    installmentLabels: Map<String, String>,
) {
    val c = ElTheme.colors
    ElCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Ventilation automatique en cascade (simulation)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            if (preview.allocations.isEmpty()) {
                Text(
                    "Aucune tranche éligible pour cette catégorie — le montant sera enregistré comme crédit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            } else {
                preview.allocations.forEach { allocation ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(c.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                installmentLabels[allocation.installmentId] ?: "Tranche",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textPrimary,
                            )
                            Text(
                                when {
                                    allocation.fullySatisfied -> "Soldée"
                                    allocation.cleared -> "Partielle"
                                    else -> "En attente d'encaissement"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textSecondary,
                            )
                        }
                        Text(
                            "+${elMoneyFormat(allocation.allocatedAmount, showCurrency = false)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = c.primary,
                        )
                    }
                }
            }

            if (preview.unallocatedAmount > 0L) {
                Text(
                    "Crédit qui restera non affecté : ${elMoneyFormat(preview.unallocatedAmount)} (versé sur le compte famille)",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.warning,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
