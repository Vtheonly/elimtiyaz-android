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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.PaymentStatus
import com.example.core.formatDzd
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElProgressBar
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTextField
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentScheduleScreen(
    onBack: () -> Unit,
    viewModel: InstallmentScheduleViewModel = hiltViewModel(),
) {
    val parents by viewModel.parents.collectAsState()
    val selectedParentId by viewModel.selectedParentId.collectAsState()
    val installments by viewModel.installments.collectAsState()
    val parentSummary by viewModel.parentSummary.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

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

    BackHandler(enabled = !selectedParentId.isNullOrBlank()) {
        viewModel.selectParent("")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Échéancier des Tranches") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!selectedParentId.isNullOrBlank()) {
                            viewModel.selectParent("")
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selectedParent == null) {
                item {
                    ElTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "Rechercher une famille",
                        placeholder = "Nom, téléphone, matricule...",
                        leadingIcon = Icons.Default.Search,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (filteredParents.isEmpty()) {
                    item {
                        ElEmptyState(
                            icon = Icons.Default.Payments,
                            title = "Aucune famille trouvée",
                            message = "Vérifiez vos termes de recherche.",
                        )
                    }
                } else {
                    item {
                        Text(
                            "Sélectionnez une famille (${filteredParents.size} disponibles) :",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(filteredParents) { p ->
                        ElCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.selectParent(p.id) },
                            compact = true,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ElAvatar(initials = p.fullName, size = 38)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.fullName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Code : ${p.code} • Tél : ${p.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Sélectionner", color = PrimaryBlue, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { viewModel.selectParent("") }
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
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(selectedParent.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Code : ${selectedParent.code} • Tél : ${selectedParent.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                ElTag(
                                    text = "Changer",
                                    color = PrimaryBlue,
                                    onClick = { viewModel.selectParent("") },
                                )
                            }

                            Spacer(Modifier.height(4.dp))
                            ElSectionHeader(title = "Progression des règlements")
                            ElProgressBar(progress = progress)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Facturé : ${(totalDue / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodySmall)
                                Text("Payé : ${(totalPaid / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
                            }
                            Text(
                                "Reste à payer : ${(remainingDebt / 100).formatDzd()} DZD (${((1f - progress) * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (remainingDebt > 0) DangerRed else SuccessGreen,
                            )
                        }
                    }
                }

                message?.let {
                    item {
                        ElCard(modifier = Modifier.fillMaxWidth(), accent = SuccessGreen) {
                            Text(it, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                val activeInstallments = installments.filter { it.amountDue > 0 || it.remaining > 0 }

                if (activeInstallments.isEmpty()) {
                    item {
                        ElEmptyState(
                            icon = Icons.Default.Payments,
                            title = "Aucune tranche enregistrée",
                            message = "Aucun échéancier actif pour cette famille.",
                        )
                    }
                } else {
                    items(activeInstallments) { inst ->
                        InstallmentCard(
                            installment = inst,
                            canMarkPaid = !busy && inst.status != PaymentStatus.PAID,
                            onMarkPaid = { viewModel.markPaid(inst.id) },
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}
