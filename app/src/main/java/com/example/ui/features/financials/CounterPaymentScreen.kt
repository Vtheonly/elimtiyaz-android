package com.example.ui.features.financials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.core.Result
import com.example.domain.repository.CollectPaymentInput
import com.example.domain.repository.PaymentRepository
import com.example.session.SessionManager
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var proofPath by remember { mutableStateOf<String?>(null) }
    var checkNumber by remember { mutableStateOf("") }
    var checkBank by remember { mutableStateOf("") }
    var transferRef by remember { mutableStateOf("") }

    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val receiptNumber by viewModel.receiptNumber.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Encaissement") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(value = parentId, onValueChange = { parentId = it }, label = { Text("ID Parent") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = studentId, onValueChange = { studentId = it }, label = { Text("ID Élève (optionnel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                label = { Text("Montant (DZD)") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (optionnel)") }, modifier = Modifier.fillMaxWidth())

            // Method selector
            Text("Mode de paiement", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMethod.values().forEach { m ->
                    FilterChip(
                        selected = method == m,
                        onClick = { method = m },
                        label = { Text(when (m) { PaymentMethod.CASH -> "Espèces"; PaymentMethod.CHECK -> "Chèque"; PaymentMethod.TRANSFER -> "Virement" }) },
                    )
                }
            }

            // Method-specific fields
            if (method == PaymentMethod.CHECK) {
                OutlinedTextField(value = checkNumber, onValueChange = { checkNumber = it }, label = { Text("Numéro de chèque") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = checkBank, onValueChange = { checkBank = it }, label = { Text("Banque") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (method == PaymentMethod.TRANSFER) {
                OutlinedTextField(value = transferRef, onValueChange = { transferRef = it }, label = { Text("Référence virement") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            // Category selector
            Text("Catégorie", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentCategory.values().forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            receiptNumber?.let { rn ->
                Card(elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Paiement encaissé!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Reçu: $rn")
                    }
                }
            }

            Button(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: 0L
                    val input = CollectPaymentInput(
                        parentId = parentId,
                        studentId = studentId.ifBlank { null },
                        amount = amount * 100, // DZD → centimes
                        method = method,
                        category = category,
                        notes = notes.ifBlank { null },
                        checkNumber = checkNumber.ifBlank { null },
                        checkBankName = checkBank.ifBlank { null },
                        transferReference = transferRef.ifBlank { null },
                        proofPath = proofPath,
                    )
                    viewModel.collect(input) { /* result handled via state */ }
                },
                enabled = !isLoading && parentId.isNotBlank() && amountText.toLongOrNull()?.let { it > 0 } ?: false,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(if (isLoading) "Encaissement..." else "Encaisser & générer reçu")
            }
        }
    }
}
