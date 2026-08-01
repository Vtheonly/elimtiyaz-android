package com.example.ui.features.financials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.Result
import com.example.domain.repository.CollectPaymentInput
import com.example.domain.repository.PaymentRepository
import com.example.session.SessionManager
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTextField
import com.example.ui.components.ElTopBar
import com.example.ui.theme.SuccessGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CounterPaymentViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _receiptNumber = MutableStateFlow<String?>(null)
    val receiptNumber: StateFlow<String?> = _receiptNumber.asStateFlow()

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

@Composable
fun CounterPaymentScreen(
    onBack: () -> Unit,
    viewModel: CounterPaymentViewModel = hiltViewModel(),
) {
    var parentId by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var category by remember { mutableStateOf(PaymentCategory.TUITION) }
    var checkNumber by remember { mutableStateOf("") }
    var checkBank by remember { mutableStateOf("") }
    var transferRef by remember { mutableStateOf("") }

    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val receiptNumber by viewModel.receiptNumber.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ElTopBar(title = "Encaissement", onBack = onBack)

        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ElSectionHeader(title = "Informations")
                ElTextField(value = parentId, onValueChange = { parentId = it }, label = "ID Parent", modifier = Modifier.fillMaxWidth())
                ElTextField(value = studentId, onValueChange = { studentId = it }, label = "ID Eleve (optionnel)", modifier = Modifier.fillMaxWidth())
                ElTextField(
                    value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = "Montant (DZD)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                ElTextField(value = notes, onValueChange = { notes = it }, label = "Notes (optionnel)", modifier = Modifier.fillMaxWidth(), singleLine = false)
            }
        }

        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Mode de paiement", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMethod.values().forEach { m ->
                        ElTag(
                            text = when (m) { PaymentMethod.CASH -> "Especes"; PaymentMethod.CHECK -> "Cheque"; PaymentMethod.TRANSFER -> "Virement" },
                            selected = method == m,
                            onClick = { method = m },
                        )
                    }
                }
                if (method == PaymentMethod.CHECK) {
                    ElTextField(value = checkNumber, onValueChange = { checkNumber = it }, label = "Numero de cheque", modifier = Modifier.fillMaxWidth())
                    ElTextField(value = checkBank, onValueChange = { checkBank = it }, label = "Banque", modifier = Modifier.fillMaxWidth())
                }
                if (method == PaymentMethod.TRANSFER) {
                    ElTextField(value = transferRef, onValueChange = { transferRef = it }, label = "Reference virement", modifier = Modifier.fillMaxWidth())
                }
            }
        }

        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Categorie", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentCategory.values().forEach { c ->
                        ElTag(
                            text = c.name.lowercase().replaceFirstChar { it.uppercase() },
                            selected = category == c,
                            onClick = { category = c },
                        )
                    }
                }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        receiptNumber?.let { rn ->
            ElCard(modifier = Modifier.fillMaxWidth(), accent = SuccessGreen) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Paiement encaisse!", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = SuccessGreen)
                    Text("Recu: $rn", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        ElButton(
            text = "Encaisser & generer recu",
            onClick = {
                val amount = amountText.toLongOrNull() ?: 0L
                val input = CollectPaymentInput(
                    parentId = parentId,
                    studentId = studentId.ifBlank { null },
                    amount = amount * 100,
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
            enabled = !isLoading && parentId.isNotBlank() && amountText.toLongOrNull()?.let { it > 0 } ?: false,
            loading = isLoading,
            fullWidth = true,
            icon = Icons.Default.Payments,
        )
    }
}
