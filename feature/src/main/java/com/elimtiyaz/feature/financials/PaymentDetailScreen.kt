package com.elimtiyaz.feature.financials

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.PaymentMethod
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.ErrorState
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone

/** Payment detail screen — receipt + proof + refund action. */
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDetailScreen(
    nav: NavController,
    vm: PaymentDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val canRefund = session?.can(Permission.RefundPayment) == true && state.canRefund
    val canGenerateReceipt = session?.can(Permission.GenerateReceipt) == true

    var showRefundDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paiement", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { inner ->
        when {
            state.isLoading && state.payment == null -> LoadingState(Modifier.padding(inner))
            state.error != null && state.payment == null ->
                ErrorState(state.error!!, onRetry = vm::reload, modifier = Modifier.padding(inner))
            else -> {
                val p = state.payment ?: return@Scaffold
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .verticalScroll(rememberScrollState())
                        .padding(ElimtiyazSpacing.x4),
                    verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
                ) {
                    // --- Payment card ---
                    ElImtiyazCard {
                        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.receiptNumber, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                StatusChip(label = p.status.replaceFirstChar { it.uppercase() }, tone = paymentTone(p.status))
                            }
                            Spacer(Modifier.height(ElimtiyazSpacing.x3))
                            DetailRow("Parent", p.parentId, onClick = { nav.navigate(Route.ParentDetail.build(p.parentId)) })
                            DetailRow("Élève", p.studentId ?: "—", onClick = p.studentId?.let { { nav.navigate(Route.StudentDetail.build(it)) } })
                            DetailRow("Montant", Formatters.currency(p.amount))
                            DetailRow("Mode", PaymentMethod.from(p.method)?.displayFr ?: p.method)
                            DetailRow("Catégorie", labelForPaymentCategory(p.category))
                            DetailRow("Encaissé par", p.collectedBy)
                            DetailRow("Encaissé le", Formatters.dateTime(p.collectedAt))
                            p.notes?.let { DetailRow("Notes", it) }
                        }
                    }

                    // --- Proof image (if any) ---
                    if (!p.proofUrl.isNullOrBlank()) {
                        SectionHeader("Justificatif")
                        ElImtiyazCard {
                            AsyncImage(
                                model = p.proofUrl,
                                contentDescription = "Justificatif",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .padding(ElimtiyazSpacing.x2),
                            )
                        }
                    } else {
                        // For Check/Transfer without proof, flag as missing.
                        if (p.method != PaymentMethod.Cash.key) {
                            ElImtiyazCard {
                                Row(
                                    modifier = Modifier.padding(ElimtiyazSpacing.x4),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Image, contentDescription = null, tint = ElimtiyazColors.WarningGold)
                                    Spacer(Modifier.width(ElimtiyazSpacing.x2))
                                    Text("Justificatif manquant", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    // --- Receipt preview (if any) ---
                    state.receipt?.let { rc ->
                        SectionHeader("Reçu PDF")
                        ElImtiyazCard(onClick = { openPdf(context, rc.pdfUrl) }) {
                            Row(
                                modifier = Modifier.padding(ElimtiyazSpacing.x4),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = ElimtiyazColors.PrimaryBlue)
                                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rc.receiptNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("Généré le ${Formatters.dateTime(rc.generatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // --- Actions ---
                    Row(horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2)) {
                        if (state.receipt == null && canGenerateReceipt) {
                            OutlinedButton(
                                onClick = { vm.generateReceipt { ok, _ -> if (!ok) Unit } },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Text("Générer reçu") }
                        } else {
                            OutlinedButton(
                                onClick = { shareReceipt(context, state.receipt?.receiptNumber ?: p.receiptNumber, state.receipt?.pdfUrl.orEmpty()) },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null)
                                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                                Text("Partager")
                            }
                        }
                        if (canRefund) {
                            Button(
                                onClick = { showRefundDialog = true },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElimtiyazColors.DangerRed),
                            ) { Text("Rembourser") }
                        }
                    }
                    Spacer(Modifier.height(ElimtiyazSpacing.x4))
                }
            }
        }
    }

    if (showRefundDialog) {
        AlertDialog(
            onDismissRequest = { showRefundDialog = false },
            title = { Text("Confirmer le remboursement") },
            text = { Text("Cette action est irréversible. Le paiement sera marqué « Remboursé ».") },
            confirmButton = {
                TextButton(onClick = {
                    showRefundDialog = false
                    vm.refund { ok, _ -> if (ok) nav.popBackStack() }
                }) { Text("Confirmer", color = ElimtiyazColors.DangerRed) }
            },
            dismissButton = { TextButton(onClick = { showRefundDialog = false }) { Text("Annuler") } },
        )
    }
}

/** A small label/value row inside a card. Optionally clickable. */
@Composable
private fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ElimtiyazSpacing.x1)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).rotate(180f))
            }
        }
    }
}

/** French label for a [PaymentCategory]. */
private fun labelForPaymentCategory(c: com.elimtiyaz.domain.model.PaymentCategory): String = when (c) {
    com.elimtiyaz.domain.model.PaymentCategory.Tuition         -> "Scolarité"
    com.elimtiyaz.domain.model.PaymentCategory.Transport       -> "Transport"
    com.elimtiyaz.domain.model.PaymentCategory.Canteen         -> "Cantine"
    com.elimtiyaz.domain.model.PaymentCategory.Uniform         -> "Uniforme"
    com.elimtiyaz.domain.model.PaymentCategory.Books           -> "Livres"
    com.elimtiyaz.domain.model.PaymentCategory.Extracurricular -> "Activité parascolaire"
    com.elimtiyaz.domain.model.PaymentCategory.Other           -> "Autre"
}

/** Fire an ACTION_VIEW intent for the PDF URL. */
private fun openPdf(context: Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

/** Share the receipt via an ACTION_SEND intent. */
private fun shareReceipt(context: Context, receiptNumber: String, pdfUrl: String) {
    val text = buildString {
        appendLine("Reçu El-Imtiyaz: $receiptNumber")
        if (pdfUrl.isNotBlank()) appendLine("PDF: $pdfUrl")
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Reçu $receiptNumber")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Partager le reçu"))
}
