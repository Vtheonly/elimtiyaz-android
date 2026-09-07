package com.example.ui.features.financials

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.LedgerEntry
import com.example.core.PaymentCategory
import com.example.core.PaymentStatus
import com.example.core.Session
import com.example.core.formatDzd
import com.example.domain.model.DebtSummary
import com.example.domain.model.Expense
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.model.Payment
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElFab
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElProgressBar
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTextField
import com.example.ui.components.ModernSecondaryTabRow
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold
import com.example.ui.util.PhoneUtils

@Composable
internal fun TranchesTab(
    parents: List<Parent>,
    selectedParentId: String?,
    installments: List<Installment>,
    parentSummary: com.example.core.ParentLedgerSummary?,
    busy: Boolean,
    onSelectParent: (String) -> Unit,
    onMarkPaid: (String) -> Unit,
    onNavigateToCounter: (parentId: String?, studentId: String?) -> Unit,
) {
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

    BackHandler(enabled = selectedParent != null) {
        onSelectParent("")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selectedParent == null) {
            item {
                ElTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "Rechercher une famille",
                    placeholder = "Nom, téléphone, code...",
                    leadingIcon = Icons.Default.Search,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Text(
                    "Sélectionnez une famille (${filteredParents.size} trouvées) :",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(filteredParents) { p ->
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelectParent(p.id) },
                    compact = true,
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ElAvatar(initials = p.fullName, size = 36)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.fullName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text("Code: ${p.code} • ${p.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Sélectionner", color = PrimaryBlue, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onSelectParent("") }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour à la liste",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Retour à la liste des familles",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = PrimaryBlue,
                    )
                }
            }

            item {
                ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(selectedParent.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text("Code: ${selectedParent.code} • ${selectedParent.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ElTag(
                                text = "Changer",
                                color = PrimaryBlue,
                                onClick = { onSelectParent("") },
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Progression de scolarité", style = MaterialTheme.typography.labelSmall)
                            Text("${(progress * 100).toInt()}% réglé", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SuccessGreen)
                        }
                        ElProgressBar(progress = progress)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Facturé : ${(totalDue / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodySmall)
                            Text("Payé : ${(totalPaid / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
                        }
                        Text(
                            "Reste à payer : ${(remainingDebt / 100).formatDzd()} DZD",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (remainingDebt > 0) DangerRed else SuccessGreen,
                        )

                        Spacer(Modifier.height(6.dp))
                        ElButton(
                            text = "Encaisser un paiement pour cette famille",
                            onClick = { onNavigateToCounter(selectedParent.id, null) },
                            style = ElButtonStyle.Primary,
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
                        message = "Aucun échéancier pour cette famille.",
                    )
                }
            } else {
                items(validInstallments) { inst ->
                    val (statusColor, statusText) = when (inst.status) {
                        PaymentStatus.PAID -> SuccessGreen to "Payée"
                        PaymentStatus.OVERDUE -> DangerRed to "En retard"
                        PaymentStatus.PARTIAL -> WarmGold to "Partielle"
                        else -> PrimaryBlue to "En attente"
                    }

                    ElCard(modifier = Modifier.fillMaxWidth(), accent = statusColor, compact = true) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(inst.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                                ElTag(text = statusText, color = statusColor)
                            }
                            ElInfoRow(label = "Date d'échéance", value = inst.dueDate.take(10))
                            ElInfoRow(label = "Montant prévu", value = "${(inst.amountDue / 100).formatDzd()} DZD")
                            ElInfoRow(label = "Montant réglé", value = "${(inst.amountPaid / 100).formatDzd()} DZD", valueColor = SuccessGreen)
                            ElInfoRow(label = "Solde restant", value = "${(inst.remaining / 100).formatDzd()} DZD", valueColor = if (inst.remaining > 0) DangerRed else SuccessGreen)

                            if (inst.status != PaymentStatus.PAID) {
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ElButton(
                                        text = "Encaisser au guichet",
                                        onClick = { onNavigateToCounter(selectedParent.id, inst.studentId) },
                                        style = ElButtonStyle.Primary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    ElButton(
                                        text = "Valider payée",
                                        onClick = { onMarkPaid(inst.id) },
                                        style = ElButtonStyle.Secondary,
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
}
