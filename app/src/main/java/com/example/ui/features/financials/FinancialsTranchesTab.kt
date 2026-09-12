package com.example.ui.features.financials

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.PaymentStatus
import com.example.core.ParentLedgerSummary
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.model.TrancheWaveItem
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.display.ElInfoRow
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.components.input.ElSearchBar
import com.example.ui.designsystem.foundation.elMoneyFormat
import com.example.ui.designsystem.theme.ElTheme

/**
 * T-322 (55th session, UI-312) — the Tranches tab, migrated to the canonical
 * design system: a REAL filtered-empty state (was a bare "0 trouvées" line),
 * a confirmation dialog on "Valider payée" (an irreversible financial action
 * that previously fired on a single tap), category context on each
 * installment card, and exact-centimes money display via elMoneyFormat.
 *
 * Preserved: the whole tab contract (params + Parity-003 wave meters), the
 * parent → schedule drill-down, and the guichet navigation per installment.
 */
@Composable
internal fun TranchesTab(
    parents: List<Parent>,
    selectedParentId: String?,
    installments: List<Installment>,
    parentSummary: ParentLedgerSummary?,
    busy: Boolean,
    onSelectParent: (String) -> Unit,
    onMarkPaid: (String) -> Unit,
    onNavigateToCounter: (parentId: String?, studentId: String?) -> Unit,
    // PARITY-003 — the GLOBAL T1/T2/T3 wave meters (engine-derived from the
    // repository KPI contract; the desktop installment-schedule-tab twin).
    globalWaves: List<TrancheWaveItem> = emptyList(),
    globalOverdueCount: Int = 0,
) {
    val c = ElTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    val filteredParents = remember(searchQuery, parents) {
        if (searchQuery.isBlank()) parents
        else parents.filter {
            it.fullName.contains(searchQuery, ignoreCase = true) ||
                it.phone.contains(searchQuery) ||
                it.code.contains(searchQuery, ignoreCase = true)
        }
    }

    val selectedParent = parents.firstOrNull { it.id == selectedParentId }
    val totalDue = parentSummary?.totalCharged ?: 0L
    val totalPaid = parentSummary?.totalPaid ?: 0L
    val remainingDebt = (totalDue - totalPaid).coerceAtLeast(0L)
    val progress = if (totalDue > 0) (totalPaid.toFloat() / totalDue.toFloat()).coerceIn(0f, 1f) else 0f

    // T-322: "Valider payée" asks for confirmation — it is an irreversible
    // financial mutation (marks an installment PAID through the repository).
    var markPaidTarget by remember { mutableStateOf<Installment?>(null) }

    BackHandler(enabled = selectedParent != null) {
        onSelectParent("")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // PARITY-003 — the global T1/T2/T3 collection-health meters (the
        // desktop installment-schedule-tab twin; engine-derived values).
        if (globalWaves.isNotEmpty()) {
            item {
                TrancheWaveCard(waves = globalWaves, overdueCount = globalOverdueCount)
            }
        }

        if (selectedParent == null) {
            item {
                ElSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Nom, téléphone, code…",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (filteredParents.isEmpty()) {
                item {
                    ElEmptyState(
                        icon = Icons.Default.Search,
                        title = "Aucune famille trouvée",
                        subtitle = if (searchQuery.isBlank()) {
                            "Aucune famille enregistrée."
                        } else {
                            "Aucune famille ne correspond à « $searchQuery »."
                        },
                    )
                }
            } else {
                item {
                    Text(
                        "Sélectionnez une famille (${filteredParents.size} trouvée${if (filteredParents.size > 1) "s" else ""}) :",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textSecondary,
                    )
                }
                items(filteredParents) { p ->
                    ElCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelectParent(p.id) },
                        size = ElCardSize.COMPACT,
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ElAvatar(initials = p.fullName, size = ElAvatarSize.S)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    p.fullName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Code : ${p.code} • ${p.phone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                )
                            }
                            ElTag(text = "Échéancier", tone = ElTagTone.INFO)
                        }
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectParent("") }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = c.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Retour à la liste des familles",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = c.primary,
                    )
                }
            }

            item {
                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    selectedParent.fullName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Code : ${selectedParent.code} • ${selectedParent.phone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                )
                            }
                            ElChip(
                                text = "Changer",
                                onClick = { onSelectParent("") },
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Progression de scolarité", style = MaterialTheme.typography.labelSmall)
                            Text(
                                "${(progress * 100).toInt()}% réglé",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = c.success,
                            )
                        }
                        ElLinearProgress(progress = progress)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "Facturé : ${elMoneyFormat(totalDue)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textPrimary,
                            )
                            Text(
                                "Payé : ${elMoneyFormat(totalPaid)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.success,
                            )
                        }
                        Text(
                            "Reste à payer : ${elMoneyFormat(remainingDebt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (remainingDebt > 0) c.danger else c.success,
                        )

                        Spacer(Modifier.height(6.dp))
                        ElButton(
                            text = "Encaisser un paiement pour cette famille",
                            onClick = { onNavigateToCounter(selectedParent.id, null) },
                            variant = ElButtonVariant.PRIMARY,
                            fullWidth = true,
                            icon = Icons.Default.Payments,
                        )
                    }
                }
            }

            val validInstallments = installments.filter { it.amountDue > 0 || it.remaining > 0 }

            if (validInstallments.isEmpty()) {
                item {
                    ElEmptyState(
                        icon = Icons.Default.Receipt,
                        title = "Aucune tranche",
                        subtitle = "Aucun échéancier pour cette famille.",
                    )
                }
            } else {
                items(validInstallments) { inst ->
                    val statusTone = when (inst.status) {
                        PaymentStatus.PAID -> ElTagTone.SUCCESS
                        PaymentStatus.OVERDUE -> ElTagTone.DANGER
                        PaymentStatus.PARTIAL -> ElTagTone.WARNING
                        else -> ElTagTone.INFO
                    }
                    val statusText = when (inst.status) {
                        PaymentStatus.PAID -> "Payée"
                        PaymentStatus.OVERDUE -> "En retard"
                        PaymentStatus.PARTIAL -> "Partielle"
                        PaymentStatus.PENDING_CLEARANCE -> "En attente d'encaissement"
                        else -> "En attente"
                    }

                    ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.COMPACT) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        inst.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    // T-322: category context — multi-child families
                                    // have tuition + transport installments mixed.
                                    Text(
                                        installmentCategoryLabel(inst),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = c.textSecondary,
                                    )
                                }
                                ElTag(text = statusText, tone = statusTone)
                            }
                            ElInfoRow(label = "Date d'échéance", value = inst.dueDate.take(10), valueTint = c.textPrimary)
                            ElInfoRow(label = "Montant prévu", value = elMoneyFormat(inst.amountDue))
                            ElInfoRow(label = "Montant réglé", value = elMoneyFormat(inst.amountPaid), valueTint = c.success)
                            ElInfoRow(
                                label = "Solde restant",
                                value = elMoneyFormat(inst.remaining),
                                valueTint = if (inst.remaining > 0) c.danger else c.success,
                            )

                            if (inst.status != PaymentStatus.PAID) {
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ElButton(
                                        text = "Encaisser au guichet",
                                        onClick = { onNavigateToCounter(selectedParent.id, inst.studentId) },
                                        variant = ElButtonVariant.PRIMARY,
                                        modifier = Modifier.weight(1f),
                                    )
                                    ElButton(
                                        text = "Valider payée",
                                        onClick = { markPaidTarget = inst },
                                        variant = ElButtonVariant.SECONDARY,
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    markPaidTarget?.let { inst ->
        AlertDialog(
            onDismissRequest = { markPaidTarget = null },
            title = { Text("Valider la tranche comme payée ?") },
            text = {
                Text(
                    "${inst.label} — ${elMoneyFormat(inst.remaining)}\n" +
                        "L'écriture sera enregistrée dans le grand livre et ventilée sur cette tranche. Cette action ne peut pas être annulée ici.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onMarkPaid(inst.id)
                        markPaidTarget = null
                    },
                    enabled = !busy,
                ) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = { markPaidTarget = null }) { Text("Annuler") }
            },
        )
    }
}

private fun installmentCategoryLabel(installment: Installment): String = when (installment.category.code) {
    "tuition" -> "Scolarité"
    "transport" -> "Transport"
    "canteen" -> "Cantine"
    "uniform" -> "Uniforme"
    "books" -> "Fournitures & Livres"
    "extracurricular" -> "Activité parascolaire"
    "parent_credit" -> "Crédit famille"
    "therapy_psychology" -> "Thérapie — psychologie"
    "therapy_speech" -> "Thérapie — orthophonie"
    "second_apron" -> "Deuxième tablier"
    else -> installment.category.code
}
