package com.example.ui.features.financials

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Permission
import com.example.core.Result
import com.example.domain.model.Payment
import com.example.domain.repository.InstallmentRepository
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.PaymentRepository
import com.example.domain.repository.PdfRepository
import com.example.domain.repository.StudentRepository
import com.example.session.SessionManager
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElInfoRow
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.foundation.elMoneyFormat
import com.example.ui.designsystem.theme.ElTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * ViewModel for [PaymentDetailScreen]. Loads a single payment by ID via
 * [PaymentRepository.observeById] and exposes it as a StateFlow.
 *
 * Also exposes a [refund] action that calls [PaymentRepository.refund] —
 * mirrors the desktop's payment-detail drawer which allows reversing a
 * payment with a reason — and a [generateReceiptPdf] action rendering the
 * receipt via [PdfRepository] (parity with the desktop's auto-generated
 * receipt PDFs).
 *
 * T-321b fixes: the load() collector is now CANCELLED and replaced on every
 * call (two load() calls used to leave two live collectors); the tranche
 * label is resolved from [InstallmentRepository] instead of rendering the
 * raw installment UUID; the actor fallback stays on collectedBy but the UI
 * hides the row when only a raw UUID is available.
 */
@HiltViewModel
class PaymentDetailViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val parentRepository: ParentRepository,
    private val studentRepository: StudentRepository,
    private val installmentRepository: InstallmentRepository,
    private val pdfRepository: PdfRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _payment = MutableStateFlow<Payment?>(null)
    val payment: StateFlow<Payment?> = _payment.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // FIX (raw IDs): human-readable names for the parent / student / actor.
    private val _parentName = MutableStateFlow<String?>(null)
    val parentName: StateFlow<String?> = _parentName.asStateFlow()

    private val _studentName = MutableStateFlow<String?>(null)
    val studentName: StateFlow<String?> = _studentName.asStateFlow()

    private val _actorName = MutableStateFlow<String?>(null)
    val actorName: StateFlow<String?> = _actorName.asStateFlow()

    /** T-321b: resolved human label for the linked installment (null = none). */
    private val _installmentLabel = MutableStateFlow<String?>(null)
    val installmentLabel: StateFlow<String?> = _installmentLabel.asStateFlow()

    /** Whether the current session may refund payments. */
    val canRefund: Boolean
        get() = sessionManager.current()?.can(Permission.REFUND_PAYMENT) == true ||
            sessionManager.current()?.role in listOf(
                com.example.core.Role.SUPER_ADMIN,
                com.example.core.Role.FINANCIAL_OFFICER,
            )

    /** Whether the current session may generate receipt PDFs. */
    val canGenerateReceipt: Boolean
        get() = sessionManager.current()?.can(Permission.GENERATE_RECEIPT) == true ||
            sessionManager.current()?.role in listOf(
                com.example.core.Role.SUPER_ADMIN,
                com.example.core.Role.FINANCIAL_OFFICER,
            )

    /** One-shot share request: the freshly generated receipt file. */
    private val _pdfFile = MutableStateFlow<File?>(null)
    val pdfFile: StateFlow<File?> = _pdfFile.asStateFlow()

    private var loadJob: Job? = null

    fun load(paymentId: String) {
        // T-321b fix: cancel the previous collector — the old code left one
        // live collector per load() call forever.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                paymentRepository.observeById(paymentId).collectLatest { p ->
                    _payment.value = p
                    if (p != null) {
                        _parentName.value = parentRepository.observeById(p.parentId)
                            .firstOrNull()?.fullName
                        _studentName.value = p.studentId?.let { sid ->
                            studentRepository.observeById(sid).firstOrNull()?.fullName
                        }
                        _actorName.value = p.collectedBy
                        _installmentLabel.value = p.installmentId?.let { insId ->
                            installmentRepository.observeById(insId).firstOrNull()?.label
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur lors du chargement du paiement"
            }
        }
    }

    fun refund(paymentId: String, reason: String) {
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
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            val result = paymentRepository.refund(paymentId, reason, actorId, actorName)
            _busy.value = false
            result.onSuccess { _message.value = "Remboursement enregistré." }
                .onFailure { _error.value = it.userMessage }
        }
    }

    /** Render the receipt PDF for the loaded payment and emit a share request. */
    fun generateReceiptPdf(paymentId: String) {
        viewModelScope.launch {
            _busy.value = true
            when (val result = pdfRepository.generatePaymentReceipt(paymentId)) {
                is Result.Ok -> _pdfFile.value = result.value
                is Result.Err -> _error.value = result.error.userMessage
            }
            _busy.value = false
        }
    }

    /** Called by the UI once the share intent has been dispatched. */
    fun consumePdf() {
        _pdfFile.value = null
    }

    fun clearMessages() {
        _error.value = null
        _message.value = null
    }
}

/** Humanized status label + tone (T-321b: raw enum names no longer leak). */
internal fun paymentStatusLabel(status: String): Pair<String, ElTagTone> = when (status) {
    "PAID" -> "Payé & validé" to ElTagTone.SUCCESS
    "PENDING" -> "En attente d'encaissement" to ElTagTone.WARNING
    "PARTIAL" -> "Partiel" to ElTagTone.INFO
    "OVERDUE" -> "En retard" to ElTagTone.DANGER
    "REFUNDED" -> "Remboursé (extourné)" to ElTagTone.DANGER
    "PENDING_CLEARANCE" -> "En attente d'encaissement du chèque/virement" to ElTagTone.WARNING
    else -> status to ElTagTone.NEUTRAL
}

/** dd/MM/yyyy HH:mm from an ISO instant — the honest transaction moment. */
internal fun formatTransactionTimestamp(iso: String): String = try {
    OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
} catch (_: Exception) {
    iso.take(10)
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
    val message by viewModel.message.collectAsState()
    val parentName by viewModel.parentName.collectAsState()
    val studentName by viewModel.studentName.collectAsState()
    val actorName by viewModel.actorName.collectAsState()
    val installmentLabel by viewModel.installmentLabel.collectAsState()
    val pdfFile by viewModel.pdfFile.collectAsState()
    val context = LocalContext.current
    val c = ElTheme.colors

    // Trigger load once on first composition
    LaunchedEffect(paymentId) {
        viewModel.load(paymentId)
    }

    // Share the freshly generated receipt PDF (ACTION_SEND via FileProvider).
    LaunchedEffect(pdfFile) {
        val file = pdfFile ?: return@LaunchedEffect
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Partager le reçu"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context,
                "Impossible de partager le PDF.",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
        viewModel.consumePdf()
    }

    // FIX (unreachable feature): refund dialog — `refund()` was fully
    // implemented in the ViewModel but NO button existed to trigger it.
    var showRefundDialog by remember { mutableStateOf(false) }
    var refundReason by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        ElTopBar(title = "Reçu officiel d'encaissement", onBack = onBack)

        val p = payment
        if (p == null) {
            ElEmptyState(
                icon = Icons.Default.Receipt,
                title = "Paiement introuvable",
                subtitle = error ?: "Chargement…",
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        val (statusLabel, statusTone) = paymentStatusLabel(p.status.name)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Hero card — icon, receipt number, amount, humanized status
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(c.successContainer),
                    ) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            tint = c.success,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text(
                        "Reçu ${p.receiptNumber}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        elMoneyFormat(p.amount),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (p.status.name == "REFUNDED") c.danger else c.success,
                    )
                    ElTag(text = statusLabel, tone = statusTone, size = ElTagSize.MD)
                }
            }

            message?.let { msg ->
                ElAlertBanner(
                    title = "Opération réussie",
                    message = msg,
                    severity = ElAlertSeverity.SUCCESS,
                    onDismiss = viewModel::clearMessages,
                )
            }

            // Details card
            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ElSectionHeader(title = "Détails de l'opération", divider = false)
                    ElInfoRow(label = "Méthode", value = paymentMethodLabel(p.method.name))
                    ElInfoRow(label = "Catégorie", value = paymentCategoryLabel(p.category.name))
                    ElInfoRow(
                        label = "Date encaissement",
                        value = formatTransactionTimestamp(p.collectedAt),
                    )
                    // FIX (raw IDs): show human-readable names instead of
                    // opaque internal UUIDs.
                    ElInfoRow(label = "Parent", value = parentName ?: "—", valueTint = c.textPrimary)
                    studentName?.let { ElInfoRow(label = "Élève", value = it, valueTint = c.textPrimary) }
                    ElInfoRow(
                        label = "Encaissé par",
                        value = actorName ?: "—",
                        valueTint = c.textPrimary,
                    )
                    // T-321b: the resolved tranche LABEL — the raw UUID row is gone.
                    installmentLabel?.let { label ->
                        ElInfoRow(label = "Tranche", value = label, valueTint = c.textPrimary)
                    }
                    p.notes?.let { ElInfoRow(label = "Notes", value = it, valueTint = c.textPrimary) }
                }
            }

            // Refund action — visible only for payments that can be refunded
            // and sessions with the REFUND_PAYMENT permission.
            if (viewModel.canRefund && p.status.name == "PAID") {
                ElButton(
                    text = "Rembourser ce paiement (extourne)",
                    onClick = { showRefundDialog = true },
                    variant = ElButtonVariant.DANGER,
                    fullWidth = true,
                    enabled = !busy,
                    icon = Icons.Default.Replay,
                )
            }

            // PDF receipt — renders the A4 receipt via the PdfRepository and
            // opens the system share sheet (FileProvider + ACTION_SEND).
            if (viewModel.canGenerateReceipt) {
                ElButton(
                    text = if (busy) "Génération…" else "Partager / Imprimer le reçu PDF",
                    onClick = { viewModel.generateReceiptPdf(paymentId) },
                    variant = ElButtonVariant.PRIMARY,
                    fullWidth = true,
                    enabled = !busy,
                    icon = Icons.Default.PictureAsPdf,
                )
            }

            error?.let { msg ->
                ElAlertBanner(
                    title = "Erreur",
                    message = msg,
                    severity = ElAlertSeverity.DANGER,
                    onDismiss = viewModel::clearMessages,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // Refund confirmation dialog with mandatory reason.
    if (showRefundDialog && payment != null) {
        AlertDialog(
            onDismissRequest = { showRefundDialog = false },
            title = { Text("Rembourser le reçu ${payment!!.receiptNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Montant : ${elMoneyFormat(payment!!.amount)}\n" +
                            "Le remboursement annule l'effet du paiement (écriture d'extourne) et ne peut pas être défait.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = refundReason,
                        onValueChange = { refundReason = it },
                        label = { Text("Motif du remboursement *") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.refund(paymentId, refundReason.trim())
                        showRefundDialog = false
                        refundReason = ""
                    },
                    enabled = !busy && refundReason.trim().length >= 3,
                ) { Text("Confirmer") }
            },
            dismissButton = {
                TextButton(onClick = { showRefundDialog = false }) { Text("Annuler") }
            },
        )
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
