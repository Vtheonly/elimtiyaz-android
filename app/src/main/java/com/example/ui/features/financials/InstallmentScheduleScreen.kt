package com.example.ui.features.financials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.PaymentStatus
import com.example.core.formatDzd
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.repository.InstallmentRepository
import com.example.domain.repository.ParentRepository
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElDropdown
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElProgressBar
import com.example.ui.components.ElScaffold
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for [InstallmentScheduleScreen].
 *
 * BUGFIX (iter 2): the previous version injected [InstallmentRepository]
 * then discarded it — the StateFlow emitted a permanent empty list and
 * there was no way to select a parent. Now the ViewModel:
 *   - Loads the parent list (so the user can pick one).
 *   - On parent selection, switches the installments flow to
 *     [InstallmentRepository.observeByParent].
 *   - Exposes the selected parent so the UI can show its name.
 */
@HiltViewModel
class InstallmentScheduleViewModel @Inject constructor(
    private val installmentRepository: InstallmentRepository,
    private val parentRepository: ParentRepository,
) : ViewModel() {

    val parents: StateFlow<List<Parent>> = parentRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedParentId = MutableStateFlow<String?>(null)
    val selectedParentId: StateFlow<String?> = _selectedParentId.asStateFlow()

    private val _installments = MutableStateFlow<List<Installment>>(emptyList())
    val installments: StateFlow<List<Installment>> = _installments.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun selectParent(parentId: String) {
        _selectedParentId.value = parentId
        viewModelScope.launch {
            installmentRepository.observeByParent(parentId).collect { _installments.value = it }
        }
    }

    /**
     * Mark an installment as paid. Mirrors desktop's "Mark Paid" action —
     * calls [InstallmentRepository.markPaid] which invokes the
     * `mark_installment_paid` SECURITY DEFINER RPC.
     */
    fun markPaid(installmentId: String, actorId: String, actorName: String) {
        viewModelScope.launch {
            _busy.value = true
            val result = installmentRepository.markPaid(installmentId, actorId, actorName)
            _busy.value = false
            result.onSuccess { _message.value = "Tranche marquée comme payée." }
                .onFailure { _message.value = it.userMessage }
        }
    }

    fun clearMessage() { _message.value = null }
}

@Composable
fun InstallmentScheduleScreen(
    onBack: () -> Unit,
    viewModel: InstallmentScheduleViewModel = hiltViewModel(),
) {
    val parents by viewModel.parents.collectAsState()
    val selectedParentId by viewModel.selectedParentId.collectAsState()
    val installments by viewModel.installments.collectAsState()
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
                    icon = androidx.compose.material.icons.Icons.Default.Payments,
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
                    icon = androidx.compose.material.icons.Icons.Default.Payments,
                    title = "Sélectionnez un parent",
                    message = "Choisissez un parent pour voir ses tranches de paiement.",
                )
                return@Column
            }

            if (installments.isEmpty()) {
                ElEmptyState(
                    icon = androidx.compose.material.icons.Icons.Default.Payments,
                    title = "Aucune tranche",
                    message = "Aucune tranche définie pour ce parent. Utilisez « Régénérer » pour créer le planning par défaut.",
                )
                return@Column
            }

            val totalDue = installments.sumOf { it.amountDue }
            val totalPaid = installments.sumOf { it.amountPaid }
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
                        onMarkPaid = { viewModel.markPaid(inst.id, selectedParentId ?: "", selectedParent?.fullName ?: "") },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallmentCard(
    installment: Installment,
    canMarkPaid: Boolean,
    onMarkPaid: () -> Unit,
) {
    val (statusColor, statusText) = when (installment.status) {
        PaymentStatus.PAID -> SuccessGreen to "Payée"
        PaymentStatus.OVERDUE -> DangerRed to "En retard"
        PaymentStatus.PENDING -> PrimaryBlue to "En attente"
        PaymentStatus.PARTIAL -> WarmGold to "Partielle"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to installment.status.name
    }
    ElCard(
        modifier = Modifier.fillMaxWidth(),
        accent = statusColor,
        compact = true,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    installment.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                ElTag(text = statusText, color = statusColor, selected = true)
            }
            Spacer(Modifier.height(8.dp))
            ElInfoRow(label = "Échéance", value = installment.dueDate)
            ElInfoRow(label = "Montant", value = "${(installment.amountDue / 100).formatDzd()} DZD")
            ElInfoRow(label = "Payé", value = "${(installment.amountPaid / 100).formatDzd()} DZD", valueColor = SuccessGreen)
            ElInfoRow(label = "Restant", value = "${(installment.remaining / 100).formatDzd()} DZD", valueColor = if (installment.remaining > 0) DangerRed else SuccessGreen)

            if (canMarkPaid) {
                Spacer(Modifier.height(8.dp))
                ElButton(
                    text = "Marquer comme payée",
                    onClick = onMarkPaid,
                    style = ElButtonStyle.Secondary,
                    fullWidth = true,
                )
            }
        }
    }
}
