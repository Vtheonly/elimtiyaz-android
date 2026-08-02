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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElDropdown
import com.example.ui.components.ElTextField
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
    fun amountChanged(v: String) { _amount.value = v.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } }
    fun categoryChanged(v: String) { _category.value = v }
    fun payeeChanged(v: String) { _payee.value = v }
    fun urgencyChanged(v: String) { _urgency.value = v }

    val canSubmit: Boolean
        get() = _title.value.isNotBlank() &&
                _payee.value.isNotBlank() &&
                _amount.value.replace(",", ".").toDoubleOrNull()?.let { it > 0 } == true &&
                !_isSubmitting.value

    fun submit(onSuccess: () -> Unit) {
        if (!canSubmit) {
            _error.value = "Veuillez remplir tous les champs obligatoires."
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null
            val amountDzd = _amount.value.replace(",", ".").toDouble()
            val input = SubmitExpenseInput(
                title = _title.value.trim(),
                description = _description.value.trim(),
                amount = (amountDzd * 100).toLong(), // centimes
                category = _category.value,
                payee = _payee.value.trim(),
                urgency = _urgency.value,
            )
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = expenseRepository.submit(input, actorId, actorName)) {
                is Result.Ok -> {
                    _isSubmitting.value = false
                    onSuccess()
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nouvelle dépense") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
            )
        },
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
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Text("Détails de la dépense", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            ElTextField(
                value = title,
                onValueChange = viewModel::titleChanged,
                label = "Titre *",
                modifier = Modifier.fillMaxWidth(),
            )

            ElTextField(
                value = description,
                onValueChange = viewModel::descriptionChanged,
                label = "Description",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
            )

            ElTextField(
                value = amount,
                onValueChange = viewModel::amountChanged,
                label = "Montant (DZD) *",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            ElDropdown(
                label = "Catégorie *",
                selectedValue = ExpenseCategoryOptions.labelFor(category),
                options = ExpenseCategoryOptions.AllLabels,
                onSelected = { label -> viewModel.categoryChanged(ExpenseCategoryOptions.codeFor(label)) },
                modifier = Modifier.fillMaxWidth(),
            )

            ElTextField(
                value = payee,
                onValueChange = viewModel::payeeChanged,
                label = "Bénéficiaire *",
                modifier = Modifier.fillMaxWidth(),
            )

            ElDropdown(
                label = "Urgence",
                selectedValue = when (urgency) {
                    "low" -> "Basse"
                    "medium" -> "Moyenne"
                    "high" -> "Haute"
                    "critical" -> "Critique"
                    else -> "Normale"
                },
                options = listOf("Normale", "Basse", "Moyenne", "Haute", "Critique"),
                onSelected = { label ->
                    val code = when (label) {
                        "Basse" -> "low"
                        "Moyenne" -> "medium"
                        "Haute" -> "high"
                        "Critique" -> "critical"
                        else -> "normal"
                    }
                    viewModel.urgencyChanged(code)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Workflow d'approbation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Soumise → Approuvée → Décaissée → Justificatif téléversé.\n" +
                                "L'auto-approbation est interdite (plan §08).\n" +
                                "Le justificatif est obligatoire avant clôture.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            ElButton(
                text = if (isSubmitting) "Soumission…" else "Soumettre la dépense",
                onClick = { viewModel.submit(onBack) },
                style = ElButtonStyle.Primary,
                enabled = viewModel.canSubmit,
                icon = Icons.Default.Send,
                fullWidth = true,
            )
        }
    }
}
