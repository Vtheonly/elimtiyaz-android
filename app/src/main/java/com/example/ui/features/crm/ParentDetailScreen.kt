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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElInfoRow
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.overlays.ElDialogShell
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
    val ledgerEntries by viewModel.ledgerEntries.collectAsState()
    val pdfFile by viewModel.pdfFile.collectAsState()
    val context = LocalContext.current
    val c = ElTheme.colors

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

    ElScaffold(
        topBar = {
            ElTopBar(
                title = parent?.fullName ?: "Parent",
                onBack = onBack,
                actions = {
                    if (parent != null) {
                        ElIconButton(
                            icon = Icons.Default.Edit,
                            onClick = { showEditDialog = true },
                            contentDescription = "Modifier le parent",
                            tint = c.primary,
                            background = androidx.compose.ui.graphics.Color.Transparent,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let { Text(it, color = c.danger) }

            parent?.let { p ->
                ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ElAvatar(initials = p.fullName, size = ElAvatarSize.L)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(p.fullName, style = ElTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                                Text(p.code, style = ElTheme.typography.bodyMedium, color = c.textSecondary)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(com.example.ui.designsystem.theme.ElShapes.small)
                                    .background(c.success)
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
                                    Text("Appeler", color = Color.White, style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(com.example.ui.designsystem.theme.ElShapes.small)
                                    .background(c.success)
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
                                    Text("WhatsApp", color = Color.White, style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                        }
                    }
                }

                ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
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
                ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ElSectionHeader(title = "Finances")
                        Spacer(Modifier.height(4.dp))
                        ElInfoRow(label = "Total facturé", value = "${(s.totalCharged / 100).formatDzd()} DZD")
                        ElInfoRow(label = "Total payé", value = "${(s.totalPaid / 100).formatDzd()} DZD", valueTint = c.success)
                        ElInfoRow(label = "Solde", value = "${(s.totalOutstanding / 100).formatDzd()} DZD")
                        if (s.totalOverdue > 0) {
                            ElInfoRow(label = "En retard", value = "${(s.totalOverdue / 100).formatDzd()} DZD", valueTint = c.danger)
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ElButton(
                                text = "Encaisser",
                                onClick = { parent?.id?.let { onNavigateToCounter(it, null) } },
                                variant = com.example.ui.designsystem.components.button.ElButtonVariant.PRIMARY,
                                icon = Icons.Default.Payments,
                                modifier = Modifier.weight(1f),
                                enabled = !busy,
                            )
                            if (viewModel.canGenerateStatement) {
                                ElButton(
                                    text = if (busy) "Génération…" else "Relevé PDF",
                                    onClick = { viewModel.generateStatementPdf(parentId) },
                                    variant = com.example.ui.designsystem.components.button.ElButtonVariant.SECONDARY,
                                    icon = Icons.Default.PictureAsPdf,
                                    modifier = Modifier.weight(1f),
                                    enabled = !busy,
                                )
                            }
                            if (viewModel.canAdjust) {
                                ElButton(
                                    text = "Ajustement",
                                    onClick = { showAdjustDialog = true },
                                    variant = com.example.ui.designsystem.components.button.ElButtonVariant.SECONDARY,
                                    icon = Icons.Default.Tune,
                                    modifier = Modifier.weight(1f),
                                    enabled = !busy,
                                )
                            }
                        }
                    }
                }
            }

            // T-456 (INV-20e) — « Historique par Année Scolaire » : the
            // canonical per-year debt-history section (the SAME component
            // the Debt Dashboard's « Par année » drawer mounts — one engine,
            // one rendering component, two surfaces, financial-rules §17.3).
            // Renders nothing when the family has no financial history.
            ParentYearHistorySection(
                parentId = parentId,
                installments = installments,
                payments = payments,
                ledgerEntries = ledgerEntries,
            )

            billingBreakdown?.let { bd ->
                if (bd.byChild.isNotEmpty() && bd.totalBilled > 0L) {
                    ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ElSectionHeader(title = "Prestations facturées")
                                Text(
                                    "Année ${bd.academicYear}",
                                    style = ElTheme.typography.labelSmall,
                                    color = c.textSecondary,
                                )
                            }
                            if (bd.hasSyntheticTranches) {
                                Text(
                                    "Échéancier non matérialisé en base pour au moins un enfant — " +
                                        "affichage déduit du décompte canonique (40/30/30, échéances " +
                                        "15 sep / 15 déc / 15 mars). Les montants restent exacts.",
                                    style = ElTheme.typography.labelSmall,
                                    color = c.warning,
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
                                            style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        )
                                        Text(
                                            "${(childBd.billedTotal / 100).formatDzd()} DZD",
                                            style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        )
                                    }
                                    childBd.lineItems.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                item.label,
                                                style = ElTheme.typography.bodySmall,
                                                color = c.textSecondary,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                "${(item.amount / 100).formatDzd()} DZD",
                                                style = ElTheme.typography.bodySmall,
                                                color = c.textSecondary,
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
                                        val statusTone = when (tr.status) {
                                            com.example.core.TrancheDisplayStatus.PAID -> ElTagTone.SUCCESS
                                            com.example.core.TrancheDisplayStatus.PENDING -> ElTagTone.WARNING
                                            else -> ElTagTone.DANGER
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(trancheLabel, style = ElTheme.typography.bodySmall)
                                                Text(
                                                    "Prévu ${(tr.amountDue / 100).formatDzd()} · " +
                                                        "Payé ${(tr.amountPaid / 100).formatDzd()}" +
                                                        if (tr.amountPending > 0L) {
                                                            " · En attente ${(tr.amountPending / 100).formatDzd()}"
                                                        } else "",
                                                    style = ElTheme.typography.labelSmall,
                                                    color = c.textSecondary,
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                ElTag(text = statusLabel, tone = statusTone)
                                                Text(
                                                    "Reste ${(tr.remaining / 100).formatDzd()} DZD",
                                                    style = ElTheme.typography.labelSmall,
                                                    color = if (tr.remaining > 0L) c.danger else c.success,
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
                                    style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = c.textSecondary,
                                )
                                bd.unattributedItems.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            item.label,
                                            style = ElTheme.typography.bodySmall,
                                            color = c.textSecondary,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "${(item.amount / 100).formatDzd()} DZD",
                                            style = ElTheme.typography.bodySmall,
                                            color = c.textSecondary,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Par service :",
                                style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = c.textSecondary,
                            )
                            bd.byService.forEach { svc ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(svc.label, style = ElTheme.typography.bodySmall)
                                        Text(
                                            "${svc.sharePct} % du total · " + svc.childAttribution.joinToString(" · ") {
                                                "${it.studentName} ${(it.amount / 100).formatDzd()}"
                                            },
                                            style = ElTheme.typography.labelSmall,
                                            color = c.textSecondary,
                                        )
                                    }
                                    Text(
                                        "${(svc.amount / 100).formatDzd()} DZD",
                                        style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = c.primary,
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
                                    style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = c.textSecondary,
                                )
                                ReconLine("Brut facturé", recon.grossBilled)
                                if (recon.adjustmentsCredit > 0L) {
                                    ReconLine("− Remises / déductions", -recon.adjustmentsCredit, c.success)
                                }
                                if (recon.adjustmentsDebit > 0L) {
                                    ReconLine("+ Majorations", recon.adjustmentsDebit, c.danger)
                                }
                                ReconLine("= Net à payer", recon.netDue)
                                ReconLine("− Encaissé confirmé", -recon.clearedPaid, c.success)
                                if (recon.pendingPaid > 0L) {
                                    ReconLine("− En attente (chèque/virement)", -recon.pendingPaid, c.warning)
                                }
                                ReconLine("= Reste net (dérivé)", recon.derivedRemaining)
                                if (recon.hasBridge) {
                                    ReconLine("± Pont — autres écritures", recon.bridge, c.warning)
                                }
                                recon.serverOutstanding?.let { server ->
                                    ReconLine(
                                        "Solde du compte (serveur)",
                                        server,
                                        if (server > 0L) c.danger else c.success,
                                        bold = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (classifiedAdjustments.isNotEmpty()) {
                ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ElSectionHeader(title = "Ajustements (${classifiedAdjustments.size})")
                        classifiedAdjustments.forEach { adj ->
                            val isCredit = adj.kind == "credit"
                            val provenanceTone = when (adj.provenance) {
                                com.example.core.AdjustmentProvenance.DOCUMENTED -> ElTagTone.SUCCESS
                                com.example.core.AdjustmentProvenance.REVERSAL_PAIR -> ElTagTone.WARNING
                                com.example.core.AdjustmentProvenance.UNDOCUMENTED -> ElTagTone.DANGER
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(com.example.ui.designsystem.theme.ElShapes.small)
                                    .background(c.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(12.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "${if (isCredit) "−" else "+"}${(kotlin.math.abs(adj.amount) / 100).formatDzd()} DZD",
                                            style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isCredit) c.success else c.danger,
                                        )
                                        ElTag(text = adj.provenanceLabel, tone = provenanceTone)
                                    }
                                    Text(
                                        text = adj.reasonLabel,
                                        style = ElTheme.typography.bodySmall,
                                        color = c.textPrimary,
                                    )
                                    Text(
                                        text = "${adj.at.take(10)} • Auteur: ${adj.approvedBy}",
                                        style = ElTheme.typography.labelSmall,
                                        color = c.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ElSectionHeader(title = "Enfants (${children.size})")
                        if (viewModel.canAddChild) {
                            com.example.ui.designsystem.components.button.ElButton(
                                text = "Ajouter un enfant",
                                onClick = { showAddChildDialog = true },
                                variant = com.example.ui.designsystem.components.button.ElButtonVariant.SECONDARY,
                                enabled = !busy,
                            )
                        }
                    }
                    children.forEach { kid ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(com.example.ui.designsystem.theme.ElShapes.small)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onOpenStudent(kid.id) },
                                )
                                .padding(vertical = 4.dp),
                        ) {
                            ElAvatar(initials = kid.fullName, size = ElAvatarSize.S)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(kid.fullName, style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                Text(kid.gradeLevel, style = ElTheme.typography.bodySmall, color = c.textSecondary)
                            }
                            Text(">", style = ElTheme.typography.bodyMedium, color = c.textSecondary)
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
                    ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
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
                                        style = ElTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        if (remaining > 0L) "${(remaining / 100).formatDzd()} DZD restants" else "Réglé",
                                        style = ElTheme.typography.bodySmall,
                                        color = if (remaining > 0L) c.textSecondary else c.success,
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
                ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ElSectionHeader(title = "Échéancier (${installments.count { it.status != com.example.core.PaymentStatus.PAID }} en cours)")
                        upcoming.forEach { inst ->
                            val statusTone = when (inst.status.name) {
                                "OVERDUE" -> ElTagTone.DANGER
                                "PARTIAL", "PENDING", "PENDING_CLEARANCE" -> ElTagTone.WARNING
                                else -> ElTagTone.INFO
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(inst.label, style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                    Text(
                                        "Échéance ${inst.dueDate} · ${(inst.amountDue / 100).formatDzd()} DZD",
                                        style = ElTheme.typography.labelSmall,
                                        color = c.textSecondary,
                                    )
                                }
                                ElTag(text = inst.status.name, tone = statusTone)
                            }
                        }
                    }
                }
            }

            if (payments.isNotEmpty()) {
                val recent = payments.sortedByDescending { it.collectedAt }.take(10)
                ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ElSectionHeader(title = "Historique des paiements (${payments.size})")
                        recent.forEach { pay ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pay.receiptNumber, style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                    Text(
                                        "${categoryFrenchLabel(pay.category)} · ${pay.method.name} · ${pay.collectedAt.take(10)}",
                                        style = ElTheme.typography.labelSmall,
                                        color = c.textSecondary,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "+${(pay.amount / 100).formatDzd()} DZD",
                                        style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = c.success,
                                    )
                                    Text(
                                        pay.status.name,
                                        style = ElTheme.typography.labelSmall,
                                        color = if (pay.status == com.example.core.PaymentStatus.PAID) c.success else c.warning,
                                    )
                                }
                            }
                        }
                        if (payments.size > recent.size) {
                            Text(
                                "+ ${payments.size - recent.size} paiement(s) antérieur(s)",
                                style = ElTheme.typography.labelSmall,
                                color = c.textSecondary,
                            )
                        }
                    }
                }
            }

            saveMessage?.let {
                Text(it, color = c.success, style = ElTheme.typography.bodySmall)
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

        // T-460 H2 (issue #3 F-10): the raw AlertDialog → the DS dialog shell
        // (ElDialogShell + ElTextField + ElButton). The updateParent contract
        // and every label preserved.
        ElDialogShell(onDismissRequest = { showEditDialog = false }) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Modifier le parent",
                    style = ElTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                )
                ElTextField(value = firstName, onValueChange = { firstName = it }, label = "Prénom", modifier = Modifier.fillMaxWidth())
                ElTextField(value = lastName, onValueChange = { lastName = it }, label = "Nom", modifier = Modifier.fillMaxWidth())
                ElTextField(value = phone, onValueChange = { phone = it }, label = "Téléphone", modifier = Modifier.fillMaxWidth())
                ElTextField(value = email, onValueChange = { email = it }, label = "Email", modifier = Modifier.fillMaxWidth())
                ElTextField(value = occupation, onValueChange = { occupation = it }, label = "Profession", modifier = Modifier.fillMaxWidth())
                ElTextField(value = address, onValueChange = { address = it }, label = "Adresse", singleLine = false, modifier = Modifier.fillMaxWidth())
                Text("Code ${p.code} — non modifiable.", style = ElTheme.typography.labelSmall, color = c.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ElButton(
                        text = "Annuler",
                        onClick = { showEditDialog = false },
                        variant = ElButtonVariant.GHOST,
                        modifier = Modifier.weight(1f),
                    )
                    ElButton(
                        text = "Enregistrer",
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
                        variant = ElButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
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
