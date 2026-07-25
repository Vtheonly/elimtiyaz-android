package com.elimtiyaz.feature.financials

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Share
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.PaymentMethod
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.domain.model.Installment
import com.elimtiyaz.domain.model.Parent
import com.elimtiyaz.domain.model.PaymentCategory
import java.io.File

/** Counter payment screen — collect a payment at the front desk. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterPaymentScreen(
    nav: NavController,
    vm: CounterPaymentViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Receipt preview / share intent
    val receipt = state.receipt
    if (receipt != null) {
        ReceiptPreviewScaffold(
            receiptNumber = receipt.receiptNumber,
            amount = state.payment?.amount ?: 0.0,
            method = state.payment?.method ?: "",
            collectedAt = state.payment?.collectedAt ?: "",
            pdfUrl = receipt.pdfUrl,
            onShare = { shareReceipt(context, receipt.receiptNumber, receipt.pdfUrl) },
            onDone = { vm.resetForm(); nav.popBackStack() },
            onNewPayment = { vm.resetForm() },
        )
        return
    }

    // Camera capture launcher — writes to a temp file via FileProvider.
    val proofFile = remember { File.createTempFile("proof_${System.currentTimeMillis()}", ".jpg", context.cacheDir) }
    var proofUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && proofUri != null) vm.setProofUri(proofUri.toString())
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setProofUri(uri.toString())
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", proofFile)
            proofUri = uri
            cameraLauncher.launch(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Encaissement", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(ElimtiyazSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
        ) {
            // --- Parent picker (searchable) ---
            ParentPickerField(
                query = state.parentQuery,
                results = state.searchResults,
                onQueryChange = vm::searchParents,
                onSelect = vm::selectParent,
            )

            // --- Student picker (only when a parent is selected) ---
            if (state.selectedParent != null) {
                StudentPickerField(
                    students = state.students,
                    selected = state.selectedStudent,
                    onSelect = vm::selectStudent,
                )
            }

            // --- Amount ---
            OutlinedTextField(
                value = state.amount,
                onValueChange = vm::amountChanged,
                label = { Text("Montant (DZD)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // --- Method (segmented) ---
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PaymentMethod.values().forEachIndexed { idx, m ->
                    SegmentedButton(
                        selected = state.method == m,
                        onClick = { vm.methodChanged(m) },
                        shape = SegmentedButtonDefaults.itemShape(idx, PaymentMethod.values().size),
                    ) { Text(m.displayFr) }
                }
            }

            // --- Category ---
            CategoryPickerField(selected = state.category, onSelect = vm::selectCategory)

            // --- Installment selector (only when installments are available) ---
            if (state.installments.isNotEmpty()) {
                InstallmentPickerField(
                    installments = state.installments,
                    selected = state.selectedInstallment,
                    onSelect = vm::selectInstallment,
                )
            }

            // --- Proof capture (required for Check / Transfer) ---
            ProofCaptureBlock(
                proofUri = state.proofUri,
                required = state.proofRequired,
                onCamera = {
                    cameraPermission.launch(android.Manifest.permission.CAMERA)
                },
                onGallery = { galleryLauncher.launch("image/*") },
                onClear = { vm.setProofUri(null); proofUri = null },
            )

            // --- Notes ---
            OutlinedTextField(
                value = state.notes,
                onValueChange = vm::notesChanged,
                label = { Text("Notes (optionnel)") },
                modifier = Modifier.fillMaxWidth().height(96.dp),
            )

            // --- Error ---
            state.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // --- Submit ---
            Button(
                onClick = { vm.submit(onSuccess = { /* state.receipt flips screen to preview */ }) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = state.canSubmit && !state.isSubmitting,
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(ElimtiyazSpacing.x2))
                    Text("Encaissement…")
                } else {
                    Text("Encaisser", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
        }
    }
}

/** Searchable parent picker using an [ExposedDropdownMenuBox]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentPickerField(
    query: String,
    results: List<Parent>,
    onQueryChange: (String) -> Unit,
    onSelect: (Parent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && results.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChange(it); expanded = it.isNotBlank() },
            label = { Text("Parent") },
            placeholder = { Text("Rechercher par nom, code ou téléphone") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded = expanded && results.isNotEmpty(), onDismissRequest = { expanded = false }) {
            results.take(10).forEach { p ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("${p.firstName} ${p.lastName}", fontWeight = FontWeight.SemiBold)
                            Text("${p.code} • ${p.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { onSelect(p); expanded = false },
                )
            }
        }
    }
}

/** Student picker dropdown (only the children of the selected parent). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentPickerField(
    students: List<com.elimtiyaz.domain.model.Student>,
    selected: com.elimtiyaz.domain.model.Student?,
    onSelect: (com.elimtiyaz.domain.model.Student?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { "${it.firstName} ${it.lastName} (${it.code})" } ?: "",
            onValueChange = { },
            readOnly = true,
            label = { Text("Élève") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Aucun (parent seul)") }, onClick = { onSelect(null); expanded = false })
            students.forEach { s ->
                DropdownMenuItem(
                    text = { Text("${s.firstName} ${s.lastName} — ${s.code}") },
                    onClick = { onSelect(s); expanded = false },
                )
            }
        }
    }
}

/** Category picker (Tuition / Transport / … / Other). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPickerField(selected: PaymentCategory, onSelect: (PaymentCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = labelForCategory(selected),
            onValueChange = { },
            readOnly = true,
            label = { Text("Catégorie") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PaymentCategory.values().forEach { c ->
                DropdownMenuItem(text = { Text(labelForCategory(c)) }, onClick = { onSelect(c); expanded = false })
            }
        }
    }
}

/** Installment picker — the auto-suggested oldest unpaid one is pre-selected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallmentPickerField(
    installments: List<Installment>,
    selected: Installment?,
    onSelect: (Installment?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { "${it.label} — ${Formatters.currency(it.amountDue)} (échéance ${Formatters.date(it.dueDate)})" } ?: "Aucune",
            onValueChange = { },
            readOnly = true,
            label = { Text("Tranche (optionnel)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Aucune tranches") }, onClick = { onSelect(null); expanded = false })
            installments.forEach { i ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("${i.label} — ${Formatters.currency(i.amountDue)}", fontWeight = FontWeight.SemiBold)
                            Text("Payé ${Formatters.currency(i.amountPaid)} • ${installmentLabel(i.status)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { onSelect(i); expanded = false },
                )
            }
        }
    }
}

/** Proof capture block — camera + gallery buttons + preview thumbnail. */
@Composable
private fun ProofCaptureBlock(
    proofUri: String?,
    required: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onClear: () -> Unit,
) {
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Justificatif", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (required) {
                    Text(
                        "Requis pour ce mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElimtiyazColors.DangerRed,
                    )
                }
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Row(horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2)) {
                FilterChip(selected = false, onClick = onCamera, leadingIcon = { Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }, label = { Text("Caméra") })
                FilterChip(selected = false, onClick = onGallery, leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp)) }, label = { Text("Galerie") })
                if (proofUri != null) {
                    FilterChip(selected = false, onClick = onClear, label = { Text("Retirer") })
                }
            }
            if (proofUri != null) {
                Spacer(Modifier.height(ElimtiyazSpacing.x3))
                AsyncImage(
                    model = proofUri,
                    contentDescription = "Aperçu du justificatif",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

/** Success-state receipt preview overlay with a Share button. */
@Composable
private fun ReceiptPreviewScaffold(
    receiptNumber: String,
    amount: Double,
    method: String,
    collectedAt: String,
    pdfUrl: String,
    onShare: () -> Unit,
    onDone: () -> Unit,
    onNewPayment: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Reçu généré", fontWeight = FontWeight.SemiBold) })
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(ElimtiyazSpacing.x6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(ElimtiyazColors.SuccessGreen.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ElimtiyazColors.SuccessGreen, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
            Text("Paiement encaissé", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(receiptNumber, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(ElimtiyazSpacing.x1))
            Text("${Formatters.currency(amount)} • ${PaymentMethod.from(method)?.displayFr ?: method}", style = MaterialTheme.typography.bodyLarge)
            Text(Formatters.dateTime(collectedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(ElimtiyazSpacing.x6))
            Button(onClick = onShare, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Share, contentDescription = null)
                Spacer(Modifier.width(ElimtiyazSpacing.x2))
                Text("Partager le reçu")
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Row(horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2)) {
                OutlinedRowButton("Nouveau", onClick = onNewPayment, modifier = Modifier.weight(1f))
                OutlinedRowButton("Terminer", onClick = onDone, modifier = Modifier.weight(1f))
            }
            if (pdfUrl.isNotBlank()) {
                Spacer(Modifier.height(ElimtiyazSpacing.x3))
                Text("PDF: $pdfUrl", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Small outlined button used by the receipt preview row. */
@Composable
private fun OutlinedRowButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp)) {
        Text(text)
    }
}

/** French label for a [PaymentCategory]. */
private fun labelForCategory(c: PaymentCategory): String = when (c) {
    PaymentCategory.Tuition         -> "Scolarité"
    PaymentCategory.Transport       -> "Transport"
    PaymentCategory.Canteen         -> "Cantine"
    PaymentCategory.Uniform         -> "Uniforme"
    PaymentCategory.Books           -> "Livres"
    PaymentCategory.Extracurricular -> "Activité parascolaire"
    PaymentCategory.Other           -> "Autre"
}

/** Fire a share intent for the receipt (text body + optional PDF URL). */
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
