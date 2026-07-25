package com.elimtiyaz.feature.financials

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AsyncContent
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.domain.model.Installment

/** Installment schedule screen — header totals + per-row "Encaisser" action. */
@Composable
fun InstallmentScheduleScreen(
    nav: NavController,
    vm: InstallmentScheduleViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tranches — ${state.parentName}", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            // Header totals card
            if (state.profile != null) {
                TotalsHeader(state)
            }
            // List
            if (state.isLoading && state.profile == null) {
                LoadingState()
            } else if (state.error != null && state.profile == null) {
                com.elimtiyaz.core.ui.ErrorState(state.error!!, onRetry = vm::reload)
            } else {
                AsyncContent(
                    isLoading = false,
                    error = null,
                    items = state.installments,
                    emptyTitle = "Aucune tranche",
                    emptyDescription = "Aucune échéance définie pour ce parent.",
                    emptyIcon = Icons.Outlined.AttachMoney,
                ) { list ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
                    ) {
                        items(list, key = { it.id }) { inst ->
                            InstallmentRow(
                                installment = inst,
                                onCollect = {
                                    nav.navigate(
                                        Route.CounterPayment.build(
                                            parentId = inst.parentId,
                                            studentId = inst.studentId,
                                            installmentId = inst.id,
                                            category = inst.category.name,
                                            amount = (inst.amountDue - inst.amountPaid).toString(),
                                        )
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Header card with totalDue / totalPaid / outstanding / overdue. */
@Composable
private fun TotalsHeader(state: InstallmentScheduleUiState) {
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Text(state.parentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TotalsCell("Total dû", Formatters.currency(state.totalDue), ElimtiyazColors.PrimaryBlue)
                TotalsCell("Payé", Formatters.currency(state.totalPaid), ElimtiyazColors.SuccessGreen)
                TotalsCell("Reste", Formatters.currency(state.outstanding), ElimtiyazColors.WarmGold)
                TotalsCell("Retard", Formatters.currency(state.overdue), ElimtiyazColors.DangerRed)
            }
        }
    }
}

@Composable
private fun TotalsCell(label: String, value: String, tone: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tone.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.AttachMoney, contentDescription = null, tint = tone, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(ElimtiyazSpacing.x1))
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Single installment row with paid/partial/overdue chip and "Encaisser" button. */
@Composable
private fun InstallmentRow(installment: Installment, onCollect: () -> Unit) {
    val remaining = (installment.amountDue - installment.amountPaid).coerceAtLeast(0.0)
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(installment.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                StatusChip(label = installmentLabel(installment.status), tone = installmentTone(installment.status))
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Échéance: ${Formatters.date(installment.dueDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Dû: ${Formatters.currency(installment.amountDue)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Payé: ${Formatters.currency(installment.amountPaid)}", style = MaterialTheme.typography.bodyMedium)
                    installment.paidDate?.let {
                        Text("Réglé le: ${Formatters.date(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Reste", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.currency(remaining), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ElimtiyazColors.PrimaryBlue)
                    if (installment.status != "paid") {
                        Spacer(Modifier.height(ElimtiyazSpacing.x2))
                        TextButton(onClick = onCollect) {
                            Icon(Icons.Outlined.AttachMoney, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(ElimtiyazSpacing.x1))
                            Text("Encaisser")
                        }
                    }
                }
            }
        }
    }
}
