package com.example.ui.features.financials

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.Result
import com.example.core.formatDzd
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.model.Student
import com.example.domain.repository.CollectPaymentInput
import com.example.domain.repository.InstallmentRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.PaymentRepository
import com.example.domain.repository.StudentRepository
import com.example.session.SessionManager
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElDropdown
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTextField
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CounterPaymentViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val parentRepository: ParentRepository,
    private val studentRepository: StudentRepository,
    private val installmentRepository: InstallmentRepository,
    private val ledgerRepository: LedgerRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val parents: StateFlow<List<Parent>> = parentRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedParent = MutableStateFlow<Parent?>(null)
    val selectedParent: StateFlow<Parent?> = _selectedParent.asStateFlow()

    private val _students = MutableStateFlow<List<Student>>(emptyList())
    val students: StateFlow<List<Student>> = _students.asStateFlow()

    private val _selectedStudent = MutableStateFlow<Student?>(null)
    val selectedStudent: StateFlow<Student?> = _selectedStudent.asStateFlow()

    private val _parentOutstanding = MutableStateFlow(0L)
    val parentOutstanding: StateFlow<Long> = _parentOutstanding.asStateFlow()

    private val _installments = MutableStateFlow<List<Installment>>(emptyList())
    val installments: StateFlow<List<Installment>> = _installments.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _receiptNumber = MutableStateFlow<String?>(null)
    val receiptNumber: StateFlow<String?> = _receiptNumber.asStateFlow()

    fun selectParent(parent: Parent) {
        _selectedParent.value = parent
        _selectedStudent.value = null
        _receiptNumber.value = null
        _error.value = null

        viewModelScope.launch {
            studentRepository.observeByParent(parent.id).collect {
                _students.value = it
            }
        }
        viewModelScope.launch {
            installmentRepository.observeByParent(parent.id).collect {
                _installments.value = it
            }
        }
        viewModelScope.launch {
            when (val res = ledgerRepository.summary(parent.id)) {
                is Result.Ok -> _parentOutstanding.value = res.value.totalOutstanding.coerceAtLeast(0L)
                is Result.Err -> _parentOutstanding.value = 0L
            }
        }
    }

    fun selectStudent(student: Student?) {
        _selectedStudent.value = student
    }

    fun collect(input: CollectPaymentInput, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = paymentRepository.collect(input, actorId, actorName)) {
                is Result.Ok -> {
                    _isLoading.value = false
                    _receiptNumber.value = result.value.receiptNumber
                    // Refresh parent balance
                    _selectedParent.value?.let { selectParent(it) }
                    onResult(result.value.receiptNumber)
                }
                is Result.Err -> {
                    _isLoading.value = false
                    _error.value = result.error.userMessage
                    onResult(null)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CounterPaymentScreen(
    onBack: () -> Unit,
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

    var showParentPicker by remember { mutableStateOf(false) }
    var parentSearchQuery by remember { mutableStateOf("") }

    var amountDzd by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var category by remember { mutableStateOf(PaymentCategory.TUITION) }
    var checkNumber by remember { mutableStateOf("") }
    var checkBank by remember { mutableStateOf("") }
    var transferRef by remember { mutableStateOf("") }

    // Dynamic slider range
    val maxSliderAmount = remember(outstanding) {
        val debt = (outstanding / 100).toFloat()
        if (debt > 0f) maxOf(debt, 100_000f) else 300_000f
    }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    // Synchronize Slider when amount text changes
    fun onAmountTextChange(typed: String) {
        val clean = typed.filter { it.isDigit() }
        amountDzd = clean
        val parsed = clean.toFloatOrNull() ?: 0f
        sliderPosition = (parsed / maxSliderAmount).coerceIn(0f, 1f)
    }

    // Synchronize Numeric input when Slider changes
    fun onSliderChange(pos: Float) {
        sliderPosition = pos
        val rounded = ((pos * maxSliderAmount) / 500).toInt() * 500
        amountDzd = if (rounded > 0) rounded.toString() else ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ElTopBar(title = "Encaissement au guichet", onBack = onBack)

        // ── 1. Parent Selection Card (Human Friendly) ──────────────────────
        ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ElSectionHeader(
                    title = "Famille / Parent",
                    actionText = if (selectedParent == null) "Sélectionner" else "Changer",
                    onAction = { showParentPicker = true },
                )

                if (selectedParent == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { showParentPicker = true }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryBlue)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Rechercher et sélectionner un parent…",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = PrimaryBlue,
                            )
                        }
                    }
                } else {
                    val p = selectedParent!!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ElAvatar(initials = p.fullName, size = 44)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text("Code: ${p.code} • Tél: ${p.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (outstanding > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(DangerRed.copy(alpha = 0.1f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Solde restant dû :", style = MaterialTheme.typography.bodySmall, color = DangerRed)
                            Text("${(outstanding / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DangerRed)
                        }
                    }
                }
            }
        }

        // ── 2. Student Selection ─────────────────────────────────────────────
        if (selectedParent != null && students.isNotEmpty()) {
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Élève concerné (optionnel)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))

                    val studentOptions = listOf("Toute la famille (Global)") + students.map { "${it.fullName} (${it.gradeLevel})" }
                    val currentSelectionLabel = selectedStudent?.let { "${it.fullName} (${it.gradeLevel})" } ?: "Toute la famille (Global)"

                    ElDropdown(
                        label = "",
                        selectedValue = currentSelectionLabel,
                        options = studentOptions,
                        onSelected = { label ->
                            if (label.startsWith("Toute la famille")) {
                                viewModel.selectStudent(null)
                            } else {
                                val match = students.firstOrNull { "${it.fullName} (${it.gradeLevel})" == label }
                                viewModel.selectStudent(match)
                            }
                        },
                    )
                }
            }
        }

        // ── 3. Synchronized Amount Input (Numeric + Range Slider) ────────────
        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ElSectionHeader(title = "Montant à encaisser")

                ElTextField(
                    value = amountDzd,
                    onValueChange = ::onAmountTextChange,
                    label = "Montant en Dinars Algériens (DZD)",
                    placeholder = "Ex: 25000",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Range Slider synchronized with amount
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

                // Quick Amount Presets
                Text("Raccourcis montants", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val presets = listOf(5_000L, 10_000L, 20_000L, 50_000L, 100_000L)
                    presets.forEach { preset ->
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

        // ── 4. Payment Method & Category ─────────────────────────────────────
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
                    Text("Le grand livre et les tranches ont été mis à jour.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── 5. Submit Button ─────────────────────────────────────────────────
        val enteredAmount = amountDzd.toLongOrNull() ?: 0L
        ElButton(
            text = if (isLoading) "Validation en cours…" else "Valider l'encaissement (${enteredAmount.formatDzd()} DZD)",
            onClick = {
                val p = selectedParent ?: return@ElButton
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
            enabled = !isLoading && selectedParent != null && enteredAmount > 0L,
            loading = isLoading,
            fullWidth = true,
            icon = Icons.Default.Payments,
        )
    }

    // ── Parent Search Modal Dialog ───────────────────────────────────────────
    if (showParentPicker) {
        AlertDialog(
            onDismissRequest = { showParentPicker = false },
            title = { Text("Sélectionner un parent", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = parentSearchQuery,
                        onValueChange = { parentSearchQuery = it },
                        label = { Text("Rechercher par nom, téléphone ou code") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))

                    val filteredParents = remember(parentSearchQuery, parents) {
                        if (parentSearchQuery.isBlank()) parents.take(20)
                        else parents.filter {
                            it.fullName.contains(parentSearchQuery, ignoreCase = true) ||
                            it.phone.contains(parentSearchQuery) ||
                            it.code.contains(parentSearchQuery, ignoreCase = true)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(filteredParents) { parent ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectParent(parent)
                                        showParentPicker = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (parent.id == selectedParent?.id)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ElAvatar(initials = parent.fullName, size = 36)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(parent.fullName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("${parent.code} • ${parent.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showParentPicker = false }) { Text("Fermer") }
            },
        )
    }
}