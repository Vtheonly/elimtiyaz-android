package com.example.ui.features.financials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.domain.repository.ExpenseRepository
import com.example.domain.repository.SubmitExpenseInput
import com.example.session.SessionManager
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.input.ElDropdown
import com.example.ui.designsystem.components.input.ElDropdownOption
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.foundation.elMoneyParse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Expense submission form ViewModel.
 *
 * Restored behavior (commit a34333a + desktop §08):
 *  - Validates title non-blank, amount > 0, payee non-blank.
 *  - Controlled category enum (no free-text).
 *  - Calls `expenseRepository.submit(input, actorId, actorName)`.
 *  - Audit logging happens inside the repository (server-side RPC `submit_expense`).
 *
 * T-322 fix: the amount is parsed with [elMoneyParse] (EXACT centimes) — the
 * previous `toDouble() * 100` rounded 15.07 DZD to 1506¢ (a financial
 * correctness bug). canSubmit uses the same parser.
 */
@HiltViewModel
class ExpenseSubmitViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()

    private val _category = MutableStateFlow(ExpenseCategoryOptions.Supplies)
    val category: StateFlow<String> = _category.asStateFlow()

    private val _payee = MutableStateFlow("")
    val payee: StateFlow<String> = _payee.asStateFlow()

    private val _urgency = MutableStateFlow("normal")
    val urgency: StateFlow<String> = _urgency.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun titleChanged(v: String) { _title.value = v }
    fun descriptionChanged(v: String) { _description.value = v }
    fun amountChanged(v: String) { _amount.value = v.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' || ch == ' ' } }
    fun categoryChanged(v: String) { _category.value = v }
    fun payeeChanged(v: String) { _payee.value = v }
    fun urgencyChanged(v: String) { _urgency.value = v }

    val canSubmit: Boolean
        get() = _title.value.isNotBlank() &&
                _payee.value.isNotBlank() &&
                elMoneyParse(_amount.value) > 0L &&
                !_isSubmitting.value

    fun submit(onSuccess: (String) -> Unit) {
        if (!canSubmit) {
            _error.value = "Veuillez remplir tous les champs obligatoires."
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null
            val input = SubmitExpenseInput(
                title = _title.value.trim(),
                description = _description.value.trim(),
                amount = elMoneyParse(_amount.value), // EXACT centimes (T-322)
                category = _category.value,
                payee = _payee.value.trim(),
                urgency = _urgency.value,
            )
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = expenseRepository.submit(input, actorId, actorName)) {
                is Result.Ok -> {
                    _isSubmitting.value = false
                    onSuccess(result.value.requestCode)
                }
                is Result.Err -> {
                    _isSubmitting.value = false
                    _error.value = result.error.userMessage
                }
            }
        }
    }

    fun clearError() { _error.value = null }
}

/** Canonical expense categories (per desktop migration 0008). */
object ExpenseCategoryOptions {
    const val Utilities = "utilities"
    const val Supplies = "supplies"
    const val Maintenance = "maintenance"
    const val Transport = "transport"
    const val Event = "event"
    const val Salary = "salary"
    const val Tax = "tax"
    const val Rent = "rent"
    const val Other = "other"

    val AllLabels: List<String> = listOf(
        "Utilities", "Fournitures", "Maintenance", "Transport",
        "Événement", "Salaires", "Taxes", "Loyer", "Autre",
    )

    private val LabelToCode = mapOf(
        "Utilities" to Utilities,
        "Fournitures" to Supplies,
        "Maintenance" to Maintenance,
        "Transport" to Transport,
        "Événement" to Event,
        "Salaires" to Salary,
        "Taxes" to Tax,
        "Loyer" to Rent,
        "Autre" to Other,
    )

    fun labelFor(code: String): String = LabelToCode.entries.firstOrNull { it.value == code }?.key ?: "Autre"
    fun codeFor(label: String): String = LabelToCode[label] ?: Other
}

@Composable
fun ExpenseSubmitScreen(
    onBack: () -> Unit,
    viewModel: ExpenseSubmitViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsState()
    val description by viewModel.description.collectAsState()
    val amount by viewModel.amount.collectAsState()
    val category by viewModel.category.collectAsState()
    val payee by viewModel.payee.collectAsState()
    val urgency by viewModel.urgency.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val error by viewModel.error.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    ElScaffold(
        topBar = {
            ElTopBar(
                title = "Nouvelle dépense",
                subtitle = "Soumission au workflow d'approbation",
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let {
                ElAlertBanner(
                    title = "Erreur de soumission",
                    message = it,
                    severity = ElAlertSeverity.DANGER,
                    onDismiss = viewModel::clearError,
                )
            }

            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ElSectionHeader(title = "Informations de la dépense")

                    ElTextField(
                        value = title,
                        onValueChange = viewModel::titleChanged,
                        label = "Titre *",
                        placeholder = "Ex : Achat de fournitures pédagogiques",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ElTextField(
                        value = description,
                        onValueChange = viewModel::descriptionChanged,
                        label = "Description",
                        placeholder = "Précisez l'usage, la classe, le fournisseur…",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                    )

                    ElTextField(
                        value = amount,
                        onValueChange = viewModel::amountChanged,
                        label = "Montant (DZD) *",
                        placeholder = "Ex : 12500,50",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ElDropdown(
                        options = ExpenseCategoryOptions.AllLabels.map {
                            ElDropdownOption(value = ExpenseCategoryOptions.codeFor(it), label = it)
                        },
                        selectedValue = category,
                        onSelected = { option -> viewModel.categoryChanged(option.value) },
                        label = "Catégorie *",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ElTextField(
                        value = payee,
                        onValueChange = viewModel::payeeChanged,
                        label = "Bénéficiaire *",
                        placeholder = "Ex : Librairie En-Nour",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ElDropdown(
                        options = listOf(
                            "Normale" to "normal",
                            "Basse" to "low",
                            "Moyenne" to "medium",
                            "Haute" to "high",
                            "Critique" to "critical",
                        ).map { ElDropdownOption(value = it.second, label = it.first) },
                        selectedValue = urgency,
                        onSelected = { option -> viewModel.urgencyChanged(option.value) },
                        label = "Urgence",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Workflow d'approbation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "1. Soumise → 2. Approuvée → 3. Décaissée → 4. Justificatif téléversé.\n" +
                            "Règle de séparation des tâches : l'auto-approbation est strictement interdite (plan §08).\n" +
                            "Le justificatif est obligatoire avant clôture.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            ElButton(
                text = if (isSubmitting) "Soumission…" else "Soumettre la dépense",
                onClick = {
                    viewModel.submit { requestCode ->
                        // A Toast survives the pop — a snackbar on THIS scaffold
                        // would be destroyed by the navigation.
                        android.widget.Toast.makeText(
                            context,
                            "Dépense $requestCode soumise pour approbation",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                        onBack()
                    }
                },
                variant = ElButtonVariant.PRIMARY,
                enabled = !isSubmitting && viewModel.canSubmit,
                loading = isSubmitting,
                icon = Icons.AutoMirrored.Filled.Send,
                fullWidth = true,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
