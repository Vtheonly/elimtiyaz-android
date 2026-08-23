package com.example.ui.features.financials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.PaymentStatus
import com.example.core.formatDzd
import com.example.domain.model.Parent
import com.example.ui.components.ElCard
import com.example.ui.components.ElDropdown
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElProgressBar
import com.example.ui.components.ElScaffold
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import androidx.compose.runtime.getValue

@Composable
fun InstallmentScheduleScreen(
    onBack: () -> Unit,
    viewModel: InstallmentScheduleViewModel = hiltViewModel(),
) {
    val parents by viewModel.parents.collectAsState()
    val selectedParentId by viewModel.selectedParentId.collectAsState()
    val installments by viewModel.installments.collectAsState()
    // TIER 4 FIX (bypass #2) — canonical parent summary from
    // `LedgerEngine.computeParentSummary`, collected reactively.
    val parentSummary by viewModel.parentSummary.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    val selectedParent = parents.firstOrNull { it.id == selectedParentId }

    ElScaffold(
        topBar = { ElTopBar(title = "Tranches", onBack = onBack) },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Parent selector — required because the screen takes no route arg.
            if (parents.isEmpty()) {
                ElEmptyState(
                    icon = Icons.Default.Payments,
                    title = "Aucun parent",
                    message = "Aucun parent enregistré. Ajoutez-en depuis le CRM.",
                )
                return@Column
            }

            ElDropdown(
                label = "Parent",
                selectedValue = selectedParent?.fullName ?: "— Sélectionner —",
                options = parents.map { it.fullName },
                onSelected = { name -> parents.first { it.fullName == name }.let { viewModel.selectParent(it.id) } },
                modifier = Modifier.fillMaxWidth(),
            )

            if (selectedParentId == null) {
                ElEmptyState(
                    icon = Icons.Default.Payments,
                    title = "Sélectionnez un parent",
                    message = "Choisissez un parent pour voir ses tranches de paiement.",
                )
                return@Column
            }

            if (installments.isEmpty()) {
                ElEmptyState(
                    icon = Icons.Default.Payments,
                    title = "Aucune tranche",
                    message = "Aucune tranche définie pour ce parent. Utilisez « Régénérer » pour créer le planning par défaut.",
                )
                return@Column
            }

            // TIER 4 FIX (bypass #2) — replaced inline `installments.sumOf`
            // with canonical `ParentLedgerSummary` values from
            // `LedgerEngine.computeParentSummary`. Falls back to 0L while the
            // summary loads (the user just selected the parent).
            val totalDue = parentSummary?.totalCharged ?: 0L
            val totalPaid = parentSummary?.totalPaid ?: 0L
            val progress = if (totalDue > 0) totalPaid.toFloat() / totalDue.toFloat() else 0f

            ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    ElSectionHeader(title = "Progression")
                    Spacer(Modifier.height(8.dp))
                    ElProgressBar(progress = progress)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(totalPaid / 100).formatDzd()} / ${(totalDue / 100).formatDzd()} DZD",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }

            message?.let {
                ElCard(modifier = Modifier.fillMaxWidth(), accent = if (it.contains("payée")) SuccessGreen else DangerRed) {
                    Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(installments) { inst ->
                    InstallmentCard(
                        installment = inst,
                        canMarkPaid = !busy && inst.status != PaymentStatus.PAID,
                        // FIX (actor mis-attribution): the ViewModel now derives
                        // the actor from the session instead of receiving the
                        // parent's id/name here.
                        onMarkPaid = { viewModel.markPaid(inst.id) },
                    )
                }
            }
        }
    }
}
