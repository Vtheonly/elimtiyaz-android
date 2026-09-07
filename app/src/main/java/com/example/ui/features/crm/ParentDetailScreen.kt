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
import androidx.compose.material.icons.filled.Payments
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
import com.example.ui.theme.WarmGold
import com.example.ui.theme.elDesignTokens
import com.example.ui.util.PhoneUtils

@Composable
fun ParentDetailScreen(
    parentId: String,
    onBack: () -> Unit,
    onOpenStudent: (String) -> Unit = {},
    onNavigateToCounter: (parentId: String?, studentId: String?) -> Unit = { _, _ -> },
    viewModel: ParentDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(parentId) { viewModel.load(parentId) }
    val parent by viewModel.parent.collectAsState()
    val children by viewModel.children.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val installments by viewModel.installments.collectAsState()
    val billingBreakdown by viewModel.billingBreakdown.collectAsState()
    val classifiedAdjustments by viewModel.classifiedAdjustments.collectAsState()
    val classes by viewModel.classes.collectAsState()
    val error by viewModel.error.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val pdfFile by viewModel.pdfFile.collectAsState()
    val context = LocalContext.current
    val tokens = elDesignTokens()

    var showEditDialog by remember { mutableStateOf(false) }
    var showAddChildDialog by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf(false) }

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
                                        onClick = { PhoneUtils.dial(context, p.phone) },
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
                                        onClick = { PhoneUtils.openWhatsApp(context, p.whatsapp ?: p.phone) },
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

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ElButton(
                                text = "Encaisser",
                                onClick = { parent?.id?.let { onNavigateToCounter(it, null) } },
                                style = com.example.ui.components.ElButtonStyle.Primary,
                                icon = Icons.Default.Payments,
                                modifier = Modifier.weight(1f),
                                enabled = !busy,
                            )
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
                                    modifier = Modifier.weight(1f),
                                    enabled = !busy,
                                )
                            }
                        }
                    }
                }
            }

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

            if (classifiedAdjustments.isNotEmpty()) {
                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ElSectionHeader(title = "Ajustements (${classifiedAdjustments.size})")
                        classifiedAdjustments.forEach { c ->
                            val isCredit = c.kind == "credit"
                            val provenanceColor = when (c.provenance) {
                                com.example.core.AdjustmentProvenance.DOCUMENTED -> SuccessGreen
                                com.example.core.AdjustmentProvenance.REVERSAL_PAIR -> WarmGold
                                com.example.core.AdjustmentProvenance.UNDOCUMENTED -> DangerRed
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(12.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                        text = c.reasonLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "${c.at.take(10)} • Auteur: ${c.approvedBy}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
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
                        if (viewModel.canAddChild) {
                            com.example.ui.components.ElButton(
                                text = "Ajouter un enfant",
                                onClick = { showAddChildDialog = true },
                                style = com.example.ui.components.ElButtonStyle.Secondary,
                                enabled = !busy,
                            )
                        }
                    }
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
            Spacer(Modifier.height(88.dp))
        }
    }

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
