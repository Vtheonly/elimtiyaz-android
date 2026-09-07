package com.example.ui.features.financials

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.formatDzd
import com.example.domain.repository.CollectPaymentInput
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTextField
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold

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
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val receiptNumber by viewModel.receiptNumber.collectAsState()

    var parentSearchQuery by remember { mutableStateOf("") }
    var isFamilyMemberChosen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialParentId, initialStudentId) {
        if (!initialParentId.isNullOrBlank()) {
            viewModel.initialize(initialParentId, initialStudentId)
            if (!initialStudentId.isNullOrBlank()) {
                isFamilyMemberChosen = true
            }
        }
    }

    var amountDzd by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var category by remember { mutableStateOf(PaymentCategory.TUITION) }
    var checkNumber by remember { mutableStateOf("") }
    var checkBank by remember { mutableStateOf("") }
    var transferRef by remember { mutableStateOf("") }

    val maxSliderAmount = remember(outstanding) {
        val debt = (outstanding / 100).toFloat()
        if (debt > 0f) maxOf(debt, 100_000f) else 300_000f
    }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

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

    Column(modifier = Modifier.fillMaxSize()) {
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

        ElTopBar(title = topBarTitle, onBack = topBarOnBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // STEP 1: Select Parent if none selected
            if (selectedParent == null) {
                ElSectionHeader(
                    title = "Sélectionnez une famille",
                    subtitle = "Recherchez et touchez une famille pour débuter le paiement :",
                )

                ElTextField(
                    value = parentSearchQuery,
                    onValueChange = { parentSearchQuery = it },
                    label = "Rechercher par nom, téléphone ou code",
                    placeholder = "Ex : Benali, 0555...",
                    leadingIcon = Icons.Default.Search,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (filteredParents.isEmpty()) {
                    ElEmptyState(
                        icon = Icons.Default.Person,
                        title = "Aucune famille trouvée",
                        message = "Vérifiez vos termes de recherche.",
                    )
                } else {
                    filteredParents.forEach { p ->
                        ElCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                viewModel.selectParent(p)
                                isFamilyMemberChosen = false
                                parentSearchQuery = ""
                            },
                            compact = true,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    ElAvatar(initials = p.fullName, size = 42)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(p.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("Code : ${p.code} • Tél : ${p.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                ElTag(text = "Sélectionner", color = PrimaryBlue)
                            }
                        }
                    }
                }
            }
            // STEP 2: Choose Family Member before proceeding with payment
            else if (!isFamilyMemberChosen) {
                val p = selectedParent!!

                ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                ElAvatar(initials = p.fullName, size = 44)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(p.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Code : ${p.code} • Tél : ${p.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            ElTag(
                                text = "Changer de famille",
                                color = PrimaryBlue,
                                onClick = {
                                    viewModel.selectParent(null)
                                    isFamilyMemberChosen = false
                                },
                            )
                        }

                        if (outstanding > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Solde restant dû pour cette famille : ${(outstanding / 100).formatDzd()} DZD",
                                style = MaterialTheme.typography.bodySmall,
                                color = DangerRed,
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
                        ElCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                viewModel.selectStudent(student)
                                isFamilyMemberChosen = true
                            },
                            accent = PrimaryBlue,
                            compact = true,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    ElAvatar(initials = student.fullName, size = 40)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(student.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        Text(
                                            "Niveau : ${student.gradeLevel.uppercase()} • Matricule : ${student.code}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                ElTag(text = "Sélectionner", color = SuccessGreen)
                            }
                        }
                    }

                    ElCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.selectStudent(null)
                            isFamilyMemberChosen = true
                        },
                        compact = true,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(WarmGold.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.Group, contentDescription = null, tint = WarmGold, modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Toute la famille (Paiement global)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Règlement général ventilé sur l'échéancier familial", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            ElTag(text = "Global", color = WarmGold)
                        }
                    }
                } else {
                    ElCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Aucun enfant enregistré pour cette famille.", style = MaterialTheme.typography.bodyMedium)
                            ElButton(
                                text = "Continuer avec le compte famille (global)",
                                onClick = {
                                    viewModel.selectStudent(null)
                                    isFamilyMemberChosen = true
                                },
                                style = com.example.ui.components.ElButtonStyle.Secondary,
                                fullWidth = true,
                            )
                        }
                    }
                }
            }
            // STEP 3: Payment Details form
            else {
                val p = selectedParent!!

                ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Famille : ${p.fullName}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    if (selectedStudent != null) "Bénéficiaire : ${selectedStudent!!.fullName} (${selectedStudent!!.gradeLevel.uppercase()})"
                                    else "Bénéficiaire : Toute la famille (Paiement global)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (selectedStudent != null) SuccessGreen else WarmGold,
                                )
                            }
                            ElTag(
                                text = "Changer d'élève",
                                color = PrimaryBlue,
                                onClick = { isFamilyMemberChosen = false },
                            )
                        }

                        if (outstanding > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Solde restant dû : ${(outstanding / 100).formatDzd()} DZD",
                                style = MaterialTheme.typography.bodySmall,
                                color = DangerRed,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    thumbColor = PrimaryBlue,
                                    activeTrackColor = PrimaryBlue,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("0 DZD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${(maxSliderAmount.toLong()).formatDzd()} DZD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Text("Raccourcis montants", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(5_000L, 10_000L, 20_000L, 50_000L, 100_000L).forEach { preset ->
                                ElTag(
                                    text = "+${preset.formatDzd()} DA",
                                    onClick = {
                                        val currentVal = amountDzd.toLongOrNull() ?: 0L
                                        onAmountTextChange((currentVal + preset).toString())
                                    },
                                )
                            }
                            if (outstanding > 0) {
                                ElTag(
                                    text = "Solde exact (${(outstanding / 100).formatDzd()} DA)",
                                    color = DangerRed,
                                    selected = amountDzd == (outstanding / 100).toString(),
                                    onClick = { onAmountTextChange((outstanding / 100).toString()) },
                                )
                            }
                        }
                    }
                }

                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Mode de règlement", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                PaymentMethod.CASH to "Espèces",
                                PaymentMethod.CHECK to "Chèque",
                                PaymentMethod.TRANSFER to "Virement",
                            ).forEach { (m, label) ->
                                ElTag(
                                    text = label,
                                    selected = method == m,
                                    color = PrimaryBlue,
                                    onClick = { method = m },
                                )
                            }
                        }

                        if (method == PaymentMethod.CHECK) {
                            ElTextField(value = checkNumber, onValueChange = { checkNumber = it }, label = "Numéro de chèque *", modifier = Modifier.fillMaxWidth())
                            ElTextField(value = checkBank, onValueChange = { checkBank = it }, label = "Banque émettrice *", modifier = Modifier.fillMaxWidth())
                        }
                        if (method == PaymentMethod.TRANSFER) {
                            ElTextField(value = transferRef, onValueChange = { transferRef = it }, label = "Référence du virement *", modifier = Modifier.fillMaxWidth())
                        }

                        Spacer(Modifier.height(4.dp))
                        Text("Catégorie de paiement", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(
                                PaymentCategory.TUITION to "Scolarité",
                                PaymentCategory.TRANSPORT to "Transport",
                                PaymentCategory.CANTEEN to "Cantine",
                                PaymentCategory.UNIFORM to "Uniforme",
                                PaymentCategory.BOOKS to "Livres",
                                PaymentCategory.OTHER to "Autre",
                            ).forEach { (c, label) ->
                                ElTag(
                                    text = label,
                                    selected = category == c,
                                    color = PrimaryBlue,
                                    onClick = { category = c },
                                )
                            }
                        }

                        ElTextField(value = notes, onValueChange = { notes = it }, label = "Remarques / Notes de caisse", modifier = Modifier.fillMaxWidth())
                    }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                receiptNumber?.let { rn ->
                    ElCard(modifier = Modifier.fillMaxWidth(), accent = SuccessGreen) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("✓ Encaissement validé avec succès !", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = SuccessGreen)
                            Spacer(Modifier.height(4.dp))
                            Text("Numéro de reçu : $rn", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Le grand livre et l'échéancier ont été mis à jour.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                val enteredAmount = amountDzd.toLongOrNull() ?: 0L
                ElButton(
                    text = if (isLoading) "Validation en cours…" else "Valider l'encaissement (${enteredAmount.formatDzd()} DZD)",
                    onClick = {
                        val input = CollectPaymentInput(
                            parentId = p.id,
                            studentId = selectedStudent?.id,
                            amount = enteredAmount * 100L,
                            method = method,
                            category = category,
                            notes = notes.ifBlank { null },
                            checkNumber = checkNumber.ifBlank { null },
                            checkBankName = checkBank.ifBlank { null },
                            transferReference = transferRef.ifBlank { null },
                            proofPath = null,
                        )
                        viewModel.collect(input) { }
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
