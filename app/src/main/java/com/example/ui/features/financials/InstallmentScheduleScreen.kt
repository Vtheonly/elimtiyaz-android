package com.example.ui.features.financials

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.PaymentStatus
import com.example.core.formatDzd
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.display.ElChipVariant
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.theme.ElTheme

@Composable
fun InstallmentScheduleScreen(
    onBack: () -> Unit,
    viewModel: InstallmentScheduleViewModel = hiltViewModel(),
) {
    val c = ElTheme.colors
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

    // T-460 pass C: the DS scaffold + top bar (the back arrow keeps the
    // two-level semantics: parent selected → clear; otherwise → onBack()).
    ElScaffold(
        topBar = {
            ElTopBar(
                title = "Échéancier des Tranches",
                onBack = {
                    if (!selectedParentId.isNullOrBlank()) {
                        viewModel.selectParent("")
                    } else {
                        onBack()
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = ElTheme.spacing.lg, vertical = ElTheme.spacing.md),
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
                            subtitle = "Vérifiez vos termes de recherche.",
                        )
                    }
                } else {
                    item {
                        Text(
                            "Sélectionnez une famille (${filteredParents.size} disponibles) :",
                            style = ElTheme.typography.labelMedium,
                            color = c.textSecondary,
                        )
                    }
                    items(filteredParents) { p ->
                        ElCard(
                            modifier = Modifier.fillMaxWidth(),
                            size = ElCardSize.COMPACT,
                            onClick = { viewModel.selectParent(p.id) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ElAvatar(initials = p.fullName, size = ElAvatarSize.M)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.fullName, style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                                    Text("Code : ${p.code} • Tél : ${p.phone}", style = ElTheme.typography.bodySmall, color = c.textSecondary)
                                }
                                Text("Sélectionner", color = c.primary, style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectParent("") }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour à la liste",
                            tint = c.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Retour à la liste des familles",
                            style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = c.primary,
                        )
                    }
                }

                item {
                    ElCard(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(ElTheme.borders.thin, c.primary.copy(alpha = 0.45f)),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(selectedParent.fullName, style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                                    Text("Code : ${selectedParent.code} • Tél : ${selectedParent.phone}", style = ElTheme.typography.bodySmall, color = c.textSecondary)
                                }
                                ElChip(
                                    text = "Changer",
                                    variant = ElChipVariant.ASSIST,
                                    onClick = { viewModel.selectParent("") },
                                )
                            }

                            Spacer(Modifier.height(4.dp))
                            ElSectionHeader(title = "Progression des règlements")
                            ElLinearProgress(progress = progress)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Facturé : ${(totalDue / 100).formatDzd()} DZD", style = ElTheme.typography.bodySmall, color = c.textPrimary)
                                Text("Payé : ${(totalPaid / 100).formatDzd()} DZD", style = ElTheme.typography.bodySmall, color = c.success)
                            }
                            Text(
                                "Reste à payer : ${(remainingDebt / 100).formatDzd()} DZD (${((1f - progress) * 100).toInt()}%)",
                                style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (remainingDebt > 0) c.danger else c.success,
                            )
                        }
                    }
                }

                message?.let {
                    item {
                        ElCard(
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(ElTheme.borders.thin, c.success.copy(alpha = 0.45f)),
                        ) {
                            Text(it, style = ElTheme.typography.bodyMedium, color = c.textPrimary)
                        }
                    }
                }

                val activeInstallments = installments.filter { it.amountDue > 0 || it.remaining > 0 }

                if (activeInstallments.isEmpty()) {
                    item {
                        ElEmptyState(
                            icon = Icons.Default.Payments,
                            title = "Aucune tranche enregistrée",
                            subtitle = "Aucun échéancier actif pour cette famille.",
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
