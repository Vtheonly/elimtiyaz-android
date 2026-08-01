package com.example.ui.features.financials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.formatDzd
import com.example.domain.model.Payment
import com.example.domain.repository.PaymentRepository
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElTag
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for [PaymentDetailScreen]. Loads a single payment by ID via
 * [PaymentRepository.observeById] and exposes it as a StateFlow.
 *
 * Also exposes a [refund] action that calls [PaymentRepository.refund] —
 * mirrors the desktop's payment-detail drawer which allows reversing a
 * payment with a reason.
 */
@HiltViewModel
class PaymentDetailViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
) : ViewModel() {

    private val _payment = MutableStateFlow<Payment?>(null)
    val payment: StateFlow<Payment?> = _payment.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun load(paymentId: String) {
        viewModelScope.launch {
            try {
                paymentRepository.observeById(paymentId).collect { p ->
                    _payment.value = p
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur lors du chargement du paiement"
            }
        }
    }

    fun refund(paymentId: String, reason: String, actorId: String, actorName: String) {
        if (reason.isBlank()) {
            _error.value = "Le motif du remboursement est obligatoire"
            return
        }
        if (reason.length < 3) {
            _error.value = "Le motif doit contenir au moins 3 caractères"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val result = paymentRepository.refund(paymentId, reason, actorId, actorName)
            _busy.value = false
            result.onSuccess { /* UI will observe updated payment via flow */ }
                .onFailure { _error.value = it.userMessage }
        }
    }
}

/**
 * Read-only receipt view for a single payment. Reached from the dashboard's
 * "Flux des Encaissements" feed and from the parent-detail screen.
 *
 * Mirrors the desktop's `RecentPaymentReceiptPdf` layout (without the PDF
 * rendering): receipt number, amount, method, status, dates, and the
 * refund action for permitted roles.
 */
@Composable
fun PaymentDetailScreen(
    paymentId: String,
    onBack: () -> Unit,
    viewModel: PaymentDetailViewModel = hiltViewModel(),
) {
    val payment by viewModel.payment.collectAsState()
    val error by viewModel.error.collectAsState()
    val busy by viewModel.busy.collectAsState()

    // Trigger load once on first composition
    androidx.compose.runtime.LaunchedEffect(paymentId) {
        viewModel.load(paymentId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ElTopBar(title = "Reçu", onBack = onBack)

        val p = payment
        if (p == null) {
            ElEmptyState(
                icon = androidx.compose.material.icons.Icons.Default.Receipt,
                title = "Paiement introuvable",
                message = error ?: "Chargement…",
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Hero card — receipt number + amount
            ElCard(modifier = Modifier.fillMaxWidth(), accent = SuccessGreen) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "Reçu ${p.receiptNumber}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "${(p.amount / 100).formatDzd()} DZD",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val statusColor = when (p.status.name) {
                        "PAID" -> SuccessGreen
                        "PENDING" -> MaterialTheme.colorScheme.tertiary
                        "REFUNDED" -> DangerRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    ElTag(text = p.status.name, color = statusColor)
                }
            }

            // Details card
            ElCard(modifier = Modifier.fillMaxWidth(), compact = true) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    ElInfoRow(label = "Méthode", value = paymentMethodLabel(p.method.name))
                    ElInfoRow(label = "Catégorie", value = paymentCategoryLabel(p.category.name))
                    ElInfoRow(label = "Date encaissement", value = p.collectedAt)
                    ElInfoRow(label = "Parent ID", value = p.parentId)
                    p.studentId?.let { ElInfoRow(label = "Élève ID", value = it) }
                    p.installmentId?.let { ElInfoRow(label = "Tranche ID", value = it) }
                    p.notes?.let { ElInfoRow(label = "Notes", value = it) }
                    ElInfoRow(label = "Encaissé par", value = p.collectedBy)
                }
            }

            error?.let { msg ->
                ElCard(modifier = Modifier.fillMaxWidth(), accent = DangerRed) {
                    Text(
                        msg,
                        color = DangerRed,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

private fun paymentMethodLabel(code: String): String = when (code) {
    "CASH" -> "Espèces"
    "CHECK" -> "Chèque"
    "TRANSFER" -> "Virement"
    else -> code
}

private fun paymentCategoryLabel(code: String): String = when (code) {
    "TUITION" -> "Scolarité"
    "TRANSPORT" -> "Transport"
    "CANTEEN" -> "Cantine"
    "UNIFORM" -> "Uniforme"
    "BOOKS" -> "Livres"
    "EXTRACURRICULAR" -> "Activité parascolaire"
    "OTHER" -> "Autre"
    else -> code
}
