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
    val payments by viewModel.payments.collectAsState()
    val installments by viewModel.installments.collectAsState()
    // T-167 — canonical itemized billing breakdown (parity with the desktop
    // parent-drawer Finances tab + the website Facturation tab).
    val billingBreakdown by viewModel.billingBreakdown.collectAsState()
    val classifiedAdjustments by viewModel.classifiedAdjustments.collectAsState()
    val classes by viewModel.classes.collectAsState()
    val error by viewModel.error.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val pdfFile by viewModel.pdfFile.collectAsState()
    val context = LocalContext.current
    val tokens = elDesignTokens()

    // FIX (missing edit feature): edit dialog state.
    var showEditDialog by remember { mutableStateOf(false) }

    // Vault §04.05 — "Add Another Child" action embedded in the drawer.
    var showAddChildDialog by remember { mutableStateOf(false) }

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
                        // Vault §04.03 — secondary phone shown when distinct.
                        p.whatsapp?.takeIf { it.isNotBlank() && it != p.phone }?.let {
                            ElInfoRow(label = "Téléphone secondaire", value = it)
                        }
                        p.email?.let { ElInfoRow(label = "Email", value = it) }
                        p.nationalId?.let { ElInfoRow(label = "N° pièce d'identité", value = it) }
                        p.address?.let { ElInfoRow(label = "Adresse", value = it) }
                        p.occupation?.let { ElInfoRow(label = "Profession", value = it) }
                        p.relationship?.let { rel ->
                            ElInfoRow(
                                label = "Lien de parenté",
                                value = when (rel) {
                                    "father" -> "Père"
                                    "mother" -> "Mère"
                                    "guardian" -> "Tuteur"
                                    else -> rel
                                },
                            )
                        }
                        p.transportDestination?.let { ElInfoRow(label = "Destination transport", value = it) }
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

            // ── T-167 — Prestations facturées (itemized billing breakdown) ──
            // Canonical derivation (core/BillingBreakdown.kt): per-child
            // charge items + REAL tranche coverage; the 40/30/30 synthesis
            // only fills display gaps for children without physical rows
            // (flagged so staff knows the schedule is deduced, not stored).
            billingBreakdown?.let { bd ->
                if (bd.byChild.isNotEmpty() && bd.totalBilled > 0L) {
                    ElCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ElSectionHeader(title = "Prestations facturées")
                                Text(
                                    "Année ${bd.academicYear}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (bd.hasSyntheticTranches) {
                                Text(
                                    "Échéancier non matérialisé en base pour au moins un enfant — " +
                                        "affichage déduit du décompte canonique (40/30/30, échéances " +
                                        "15 sep / 15 déc / 15 mars). Les montants restent exacts.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = com.example.ui.theme.WarmGold,
                                )
                            }
                            bd.byChild.forEach { childBd ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "${childBd.child.displayName} (${childBd.child.gradeLevelLabel})",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        )
                                        Text(
                                            "${(childBd.billedTotal / 100).formatDzd()} DZD",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        )
                                    }
                                    // Itemized charge line items — the child's
                                    // "shopping list" (T-168: exhaustive — family
                                    // rows are folded in for single-child
                                    // families, listed separately otherwise).
                                    childBd.lineItems.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                item.label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                "${(item.amount / 100).formatDzd()} DZD",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    // Tranche coverage — where the money landed.
                                    childBd.tranches.forEach { tr ->
                                        val trancheLabel = "${tr.label} · ${tr.dueDate?.take(10) ?: "—"}"
                                        val statusLabel = when (tr.status) {
                                            com.example.core.TrancheDisplayStatus.PAID -> "Payée"
                                            com.example.core.TrancheDisplayStatus.PARTIAL -> "Partielle"
                                            com.example.core.TrancheDisplayStatus.PENDING -> "En attente"
                                            com.example.core.TrancheDisplayStatus.UNPAID -> "Due"
                                        }
                                        val statusColor = when (tr.status) {
                                            com.example.core.TrancheDisplayStatus.PAID -> SuccessGreen
                                            com.example.core.TrancheDisplayStatus.PENDING -> com.example.ui.theme.WarmGold
                                            else -> DangerRed
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(trancheLabel, style = MaterialTheme.typography.bodySmall)
                                                Text(
                                                    "Prévu ${(tr.amountDue / 100).formatDzd()} · " +
                                                        "Payé ${(tr.amountPaid / 100).formatDzd()}" +
                                                        if (tr.amountPending > 0L) {
                                                            " · En attente ${(tr.amountPending / 100).formatDzd()}"
                                                        } else "",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                ElTag(text = statusLabel, color = statusColor)
                                                Text(
                                                    "Reste ${(tr.remaining / 100).formatDzd()} DZD",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (tr.remaining > 0L) DangerRed else SuccessGreen,
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                            }

                            // T-168 — family-level items (multi-child only):
                            // keeps the shopping list exhaustive.
                            if (bd.unattributedItems.isNotEmpty()) {
                                Text(
                                    "Famille — éléments non rattachés à un enfant",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                bd.unattributedItems.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            item.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "${(item.amount / 100).formatDzd()} DZD",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            // T-168 — per-service recap (share % + attribution).
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Par service :",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            bd.byService.forEach { svc ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(svc.label, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "${svc.sharePct} % du total · " + svc.childAttribution.joinToString(" · ") {
                                                "${it.studentName} ${(it.amount / 100).formatDzd()}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        "${(svc.amount / 100).formatDzd()} DZD",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            // T-168 — adjustment-aware reconciliation footer
                            // (every term visible; identical to the desktop
                            // drawer + website Facturation tab).
                            Spacer(Modifier.height(6.dp))
                            val recon = bd.reconciliation
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    "Réconciliation du compte",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                ReconLine("Brut facturé", recon.grossBilled)
                                if (recon.adjustmentsCredit > 0L) {
                                    ReconLine("− Remises / déductions", -recon.adjustmentsCredit, SuccessGreen)
                                }
                                if (recon.adjustmentsDebit > 0L) {
                                    ReconLine("+ Majorations", recon.adjustmentsDebit, DangerRed)
                                }
                                ReconLine("= Net à payer", recon.netDue)
                                ReconLine("− Encaissé confirmé", -recon.clearedPaid, SuccessGreen)
                                if (recon.pendingPaid > 0L) {
                                    ReconLine("− En attente (chèque/virement)", -recon.pendingPaid, com.example.ui.theme.WarmGold)
                                }
                                ReconLine("= Reste net (dérivé)", recon.derivedRemaining)
                                if (recon.hasBridge) {
                                    ReconLine("± Pont — autres écritures", recon.bridge, com.example.ui.theme.WarmGold)
                                }
                                recon.serverOutstanding?.let { server ->
                                    ReconLine(
                                        "Solde du compte (serveur)",
                                        server,
                                        if (server > 0L) DangerRed else SuccessGreen,
                                        bold = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── T-168 — Classified adjustment history (provenance) ─────────
            if (classifiedAdjustments.isNotEmpty()) {
                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ElSectionHeader(title = "Ajustements (${classifiedAdjustments.size})")
                        classifiedAdjustments.forEach { c ->
                            val isCredit = c.kind == "credit"
                            val provenanceColor = when (c.provenance) {
                                com.example.core.AdjustmentProvenance.DOCUMENTED -> SuccessGreen
                                com.example.core.AdjustmentProvenance.REVERSAL_PAIR -> com.example.ui.theme.WarmGold
                                com.example.core.AdjustmentProvenance.UNDOCUMENTED -> DangerRed
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${if (isCredit) "−" else "+"}${(kotlin.math.abs(c.amount) / 100).formatDzd()} DZD",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isCredit) SuccessGreen else DangerRed,
                                    )
                                    ElTag(text = c.provenanceLabel, color = provenanceColor)
                                }
                                Text(
                                    "${c.reasonLabel} · ${c.at.take(10)} · Auteur : ${c.approvedBy}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    c.meaningLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ElSectionHeader(title = "Enfants (${children.size})")
                        // Vault §04.05 — "Add Another Child" direct action.
                        if (viewModel.canAddChild) {
                            com.example.ui.components.ElButton(
                                text = "Ajouter un enfant",
                                onClick = { showAddChildDialog = true },
                                style = com.example.ui.components.ElButtonStyle.Secondary,
                                enabled = !busy,
                            )
                        }
                    }
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

            // ── Vault §04.05 — Active services across all children ─────────
            // Derived from the family's installments: every distinct service
            // category with a remaining balance is an active service.
            if (installments.isNotEmpty()) {
                val childById = children.associateBy { it.id }
                val activeServices = installments
                    .filter { it.remaining > 0L || it.status == com.example.core.PaymentStatus.PAID }
                    .groupBy { (it.studentId ?: "") to it.category }
                    .map { (key, rows) ->
                        Triple(key.first, key.second, rows.sumOf { it.remaining })
                    }
                    .sortedBy { it.first }
                if (activeServices.isNotEmpty()) {
                    ElCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ElSectionHeader(title = "Services actifs (${activeServices.size})")
                            activeServices.forEach { (studentId, category, remaining) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${childById[studentId]?.fullName ?: "Famille"} · " +
                                            categoryFrenchLabel(category),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        if (remaining > 0L) "${(remaining / 100).formatDzd()} DZD restants" else "Réglé",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (remaining > 0L) MaterialTheme.colorScheme.onSurfaceVariant else SuccessGreen,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Vault §04.05 — Installment schedules (embedded, never a
            // separate top-level tab) ────────────────────────────────────────
            val upcoming = installments
                .filter { it.status != com.example.core.PaymentStatus.PAID }
                .sortedBy { it.dueDate }
                .take(5)
            if (upcoming.isNotEmpty()) {
                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ElSectionHeader(title = "Échéancier (${installments.count { it.status != com.example.core.PaymentStatus.PAID }} en cours)")
                        upcoming.forEach { inst ->
                            val statusColor = when (inst.status.name) {
                                "OVERDUE" -> DangerRed
                                "PARTIAL", "PENDING", "PENDING_CLEARANCE" -> com.example.ui.theme.WarmGold
                                else -> PrimaryBlue
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(inst.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                    Text(
                                        "Échéance ${inst.dueDate} · ${(inst.amountDue / 100).formatDzd()} DZD",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                ElTag(text = inst.status.name, color = statusColor)
                            }
                        }
                    }
                }
            }

            // ── Vault §04.05 — Itemized ledger of historic payments ─────────
            if (payments.isNotEmpty()) {
                val recent = payments.sortedByDescending { it.collectedAt }.take(10)
                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ElSectionHeader(title = "Historique des paiements (${payments.size})")
                        recent.forEach { pay ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pay.receiptNumber, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                    Text(
                                        "${categoryFrenchLabel(pay.category)} · ${pay.method.name} · ${pay.collectedAt.take(10)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "+${(pay.amount / 100).formatDzd()} DZD",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = SuccessGreen,
                                    )
                                    Text(
                                        pay.status.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (pay.status == com.example.core.PaymentStatus.PAID) SuccessGreen else com.example.ui.theme.WarmGold,
                                    )
                                }
                            }
                        }
                        if (payments.size > recent.size) {
                            Text(
                                "+ ${payments.size - recent.size} paiement(s) antérieur(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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

    // Vault §04.05 — "Add Another Child" dialog (canonical createStudent,
    // parent-first dependency enforced by the repository).
    if (showAddChildDialog && parent != null) {
        AddChildDialog(
            parentName = parent!!.fullName,
            classes = classes,
            busy = busy,
            onConfirm = { firstName, lastName, birthDate, gender, gradeLevel, classId ->
                viewModel.addChild(parentId, firstName, lastName, birthDate, gender, gradeLevel, classId)
                showAddChildDialog = false
            },
            onDismiss = { showAddChildDialog = false },
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

/**
 * T-168 — one labelled line of the reconciliation equation (mirrors the
 * desktop ReconRow / the website recon footer rows).
 */
@Composable
private fun ReconLine(
    label: String,
    amount: Long,
    color: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${if (amount < 0L) "−" else if (amount > 0L) "+" else ""}${(kotlin.math.abs(amount) / 100).formatDzd()} DZD",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium),
            color = color,
        )
    }
}

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
/** French display label for a payment category (vault §04.05 services list). */
private fun categoryFrenchLabel(category: PaymentCategory): String = when (category) {
    PaymentCategory.TUITION -> "Scolarité"
    PaymentCategory.TRANSPORT -> "Transport"
    PaymentCategory.CANTEEN -> "Cantine"
    PaymentCategory.UNIFORM -> "Uniforme"
    PaymentCategory.BOOKS -> "Livres"
    PaymentCategory.EXTRACURRICULAR -> "Club / Activité"
    PaymentCategory.THERAPY_PSYCHOLOGY -> "Psychologie"
    PaymentCategory.THERAPY_SPEECH -> "Orthophonie"
    PaymentCategory.PARENT_CREDIT -> "Crédit parent"
    PaymentCategory.SECOND_APRON -> "Tablier"
    PaymentCategory.OTHER -> "Autre"
}

/**
 * Vault §04.05 / §04.01 — "Add Another Child" dialog embedded in the Parent
 * drawer. The child is created through the canonical `createStudent`, which
 * enforces the parent-first dependency (parentId is mandatory).
 */
@Composable
private fun AddChildDialog(
    parentName: String,
    classes: List<com.example.domain.model.AcademicClass>,
    busy: Boolean,
    onConfirm: (
        firstName: String,
        lastName: String,
        birthDate: String,
        gender: String,
        gradeLevel: String,
        classId: String?,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var genderLabel by remember { mutableStateOf("Non précisé") }
    var gradeLevel by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("Aucune") }

    val genderOptions = listOf("Non précisé", "Masculin", "Féminin")
    val genderCode = when (genderLabel) {
        "Masculin" -> "M"
        "Féminin" -> "F"
        else -> ""
    }
    val cycle = com.example.core.academicLevelForGradeCode(gradeLevel)
    val cycleClasses = classes.filter { it.level == cycle }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un enfant — $parentName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("Prénom *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Nom") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = birthDate, onValueChange = { birthDate = it }, label = { Text("Date de naissance (AAAA-MM-JJ) *") }, modifier = Modifier.fillMaxWidth())
                // Gender chips (vault §04.03 child block: Gender).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    genderOptions.forEach { opt ->
                        ElTag(text = opt, selected = genderLabel == opt, color = PrimaryBlue, onClick = { genderLabel = opt })
                    }
                }
                com.example.ui.components.ElDropdown(
                    label = "Niveau scolaire",
                    selectedValue = gradeLevel,
                    options = com.example.core.GRADE_LEVEL_CODES,
                    onSelected = {
                        gradeLevel = it
                        className = "Aucune"
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (gradeLevel.isNotBlank() && cycleClasses.isNotEmpty()) {
                    com.example.ui.components.ElDropdown(
                        label = "Classe (optionnel)",
                        selectedValue = className,
                        options = listOf("Aucune") + cycleClasses.map { it.name },
                        onSelected = { name -> className = name },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "L'élève sera rattaché à ce parent (dépendance parent-first). La facturation est générée selon la tarification du niveau.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val classId = cycleClasses.firstOrNull { it.name == className }?.id
                    onConfirm(firstName.trim(), lastName.trim(), birthDate.trim(), genderCode, gradeLevel, classId)
                },
                enabled = !busy && firstName.isNotBlank() && birthDate.isNotBlank() && gradeLevel.isNotBlank(),
            ) { Text("Ajouter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
