package com.example.ui.features.crm

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Whatsapp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.PaymentCategory
import com.example.core.formatDzd
import com.example.domain.model.Parent
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.elDesignTokens

@Composable
fun ParentDetailScreen(
    parentId: String,
    onBack: () -> Unit,
    /** Opens a child's dossier — the children list was previously not tappable. */
    onOpenStudent: (String) -> Unit = {},
    viewModel: ParentDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(parentId) { viewModel.load(parentId) }
    val parent by viewModel.parent.collectAsState()
    val children by viewModel.children.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val error by viewModel.error.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val pdfFile by viewModel.pdfFile.collectAsState()
    val context = LocalContext.current
    val tokens = elDesignTokens()

    // FIX (missing edit feature): edit dialog state.
    var showEditDialog by remember { mutableStateOf(false) }

    // Manual account adjustment dialog state (PaymentRepository.adjust UI).
    var showAdjustDialog by remember { mutableStateOf(false) }

    // Share the freshly generated account-statement PDF (FileProvider + ACTION_SEND).
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
            context.startActivity(Intent.createChooser(shareIntent, "Partager le relevé"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context,
                "Impossible de partager le PDF.",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
        viewModel.consumePdf()
    }

    LaunchedEffect(saveMessage) {
        if (saveMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            ElTopBar(
                title = parent?.fullName ?: "Parent",
                onBack = onBack,
                actions = {
                    if (parent != null) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifier le parent")
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            parent?.let { p ->
                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ElAvatar(initials = p.fullName, size = 56)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(p.fullName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                                Text(p.code, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(tokens.successBrush)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                                data = Uri.parse("tel:${p.phone}")
                                            }
                                            context.startActivity(intent)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Appeler", color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(tokens.successBrush)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            val cleanPhone = (p.whatsapp ?: p.phone).replace("[^0-9]".toRegex(), "")
                                            val formatted = if (cleanPhone.startsWith("0")) "213${cleanPhone.substring(1)}" else cleanPhone
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                data = Uri.parse("https://wa.me/$formatted")
                                            }
                                            context.startActivity(intent)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Whatsapp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("WhatsApp", color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                        }
                    }
                }

                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ElSectionHeader(title = "Contact")
                        Spacer(Modifier.height(4.dp))
                        ElInfoRow(label = "Code", value = p.code)
                        ElInfoRow(label = "Téléphone", value = p.phone)
                        p.email?.let { ElInfoRow(label = "Email", value = it) }
                        p.address?.let { ElInfoRow(label = "Adresse", value = it) }
                        p.occupation?.let { ElInfoRow(label = "Profession", value = it) }
                    }
                }
            }

            summary?.let { s ->
                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ElSectionHeader(title = "Finances")
                        Spacer(Modifier.height(4.dp))
                        ElInfoRow(label = "Total facturé", value = "${(s.totalCharged / 100).formatDzd()} DZD")
                        ElInfoRow(label = "Total payé", value = "${(s.totalPaid / 100).formatDzd()} DZD", valueColor = SuccessGreen)
                        ElInfoRow(label = "Solde", value = "${(s.totalOutstanding / 100).formatDzd()} DZD")
                        if (s.totalOverdue > 0) {
                            ElInfoRow(label = "En retard", value = "${(s.totalOverdue / 100).formatDzd()} DZD", valueColor = DangerRed)
                        }

                        // Financial actions — account statement PDF export +
                        // manual adjustment (both RBAC-gated in the ViewModel).
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (viewModel.canGenerateStatement) {
                                ElButton(
                                    text = if (busy) "Génération…" else "Relevé PDF",
                                    onClick = { viewModel.generateStatementPdf(parentId) },
                                    style = com.example.ui.components.ElButtonStyle.Secondary,
                                    icon = Icons.Default.PictureAsPdf,
                                    modifier = Modifier.weight(1f),
                                    enabled = !busy,
                                )
                            }
                            if (viewModel.canAdjust) {
                                ElButton(
                                    text = "Ajustement",
                                    onClick = { showAdjustDialog = true },
                                    style = com.example.ui.components.ElButtonStyle.Secondary,
                                    icon = Icons.Default.Tune,
                                    modifier = if (viewModel.canGenerateStatement) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                                    enabled = !busy,
                                )
                            }
                        }
                    }
                }
            }

            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElSectionHeader(title = "Enfants (${children.size})")
                    // FIX (not tappable): children rows now open the student
                    // dossier (parity with GlobalSearch and the desktop).
                    children.forEach { kid ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onOpenStudent(kid.id) },
                                )
                                .padding(vertical = 4.dp),
                        ) {
                            ElAvatar(initials = kid.fullName, size = 36)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(kid.fullName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                Text(kid.gradeLevel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(">", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            saveMessage?.let {
                Text(it, color = SuccessGreen, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    // FIX (missing edit feature): edit dialog — first class UI for
    // `updateParent` (identity + contact).
    if (showEditDialog && parent != null) {
        val p = parent!!
        var firstName by remember { mutableStateOf(p.firstName) }
        var lastName by remember { mutableStateOf(p.lastName) }
        var phone by remember { mutableStateOf(p.phone) }
        var email by remember { mutableStateOf(p.email ?: "") }
        var occupation by remember { mutableStateOf(p.occupation ?: "") }
        var address by remember { mutableStateOf(p.address ?: "") }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Modifier le parent") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("Prénom") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Nom") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Téléphone") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = occupation, onValueChange = { occupation = it }, label = { Text("Profession") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Adresse") }, modifier = Modifier.fillMaxWidth())
                    Text("Code ${p.code} — non modifiable.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateParent(
                            parentId = p.id,
                            firstName = firstName.trim(),
                            lastName = lastName.trim(),
                            phone = phone.trim(),
                            email = email.trim().ifBlank { null },
                            occupation = occupation.trim().ifBlank { null },
                            address = address.trim().ifBlank { null },
                        )
                        showEditDialog = false
                    },
                    enabled = firstName.isNotBlank() && lastName.isNotBlank() && phone.isNotBlank(),
                ) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Annuler") }
            },
        )
    }

    // Manual account adjustment dialog — UI entry for
    // `PaymentRepository.adjust` (previously repository-only).
    if (showAdjustDialog && parent != null) {
        AdjustAccountDialog(
            outstanding = summary?.totalOutstanding,
            busy = busy,
            onConfirm = { amountCentimes, category, reason ->
                viewModel.adjustAccount(parentId, amountCentimes, category, reason)
                showAdjustDialog = false
            },
            onDismiss = { showAdjustDialog = false },
        )
    }
}

/** Standard adjustment motifs (reason codes persisted in the ledger entry). */
private val ADJUSTMENT_MOTIFS = listOf(
    "Remise fratrie",
    "Remise direction",
    "Bourse / aide sociale",
    "Pénalité de retard",
    "Correction d'erreur de saisie",
    "Autre",
)

/** Categories applicable to debit adjustments (credits auto-route to parent_credit). */
private val ADJUSTMENT_CATEGORIES = listOf(
    PaymentCategory.TUITION to "Scolarité",
    PaymentCategory.TRANSPORT to "Transport",
    PaymentCategory.CANTEEN to "Cantine",
    PaymentCategory.UNIFORM to "Uniforme",
    PaymentCategory.BOOKS to "Livres",
    PaymentCategory.OTHER to "Autre",
)

/**
 * Manual account adjustment dialog — mirrors the desktop's
 * `AdjustAccountModal` (parent-detail-drawer.tsx): signed amount, mandatory
 * motif (reason code for audit), category, and an optional note.
 *
 * Sign convention follows the CANONICAL engine (core/Ledger.kt):
 * positive = debit (pénalité / majoration), negative = crédit (remise / avoir).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AdjustAccountDialog(
    outstanding: Long?,
    busy: Boolean,
    onConfirm: (amountCentimes: Long, category: PaymentCategory, reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var motif by remember { mutableStateOf(ADJUSTMENT_MOTIFS.first()) }
    var category by remember { mutableStateOf(ADJUSTMENT_CATEGORIES.first().first) }

    val amountDzd = amountText.replace(" ", "").replace(",", ".").toDoubleOrNull()
    val validAmount = amountDzd != null && amountDzd != 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustement de compte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (outstanding != null) "Solde en cours : ${(outstanding / 100).formatDzd()} DZD" else "Solde en cours : —",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { raw ->
                        amountText = raw.filter { it.isDigit() || it == '-' }.take(12)
                    },
                    label = { Text("Montant signé (DZD) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Positif = débit (pénalité / majoration) · Négatif = crédit (remise / avoir)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Motif *", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ADJUSTMENT_MOTIFS.forEach { m ->
                        ElTag(text = m, selected = motif == m, color = PrimaryBlue, onClick = { motif = m })
                    }
                }
                Text("Catégorie (débits uniquement)", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ADJUSTMENT_CATEGORIES.forEach { (c, label) ->
                        ElTag(text = label, selected = category == c, color = PrimaryBlue, onClick = { category = c })
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optionnel)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val dzd = amountText.replace(" ", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                    val centimes = kotlin.math.round(dzd * 100).toLong()
                    val reason = if (note.isNotBlank()) "$motif — ${note.trim()}" else motif
                    onConfirm(centimes, category, reason)
                },
                enabled = !busy && validAmount,
            ) { Text("Appliquer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}