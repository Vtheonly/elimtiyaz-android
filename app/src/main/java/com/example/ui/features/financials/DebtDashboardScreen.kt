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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.features.crm.ParentYearHistoryBody
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ParentYearHistory
import com.example.core.YearHistoryInput
import com.example.core.computeParentYearHistory
import com.example.core.formatDzd
import com.example.domain.model.DebtSummary
import com.example.domain.repository.DebtRepository
import com.example.domain.repository.InstallmentRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.PaymentRepository
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElTag
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.ui.util.PhoneUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DebtDashboardViewModel @Inject constructor(
    private val debtRepository: DebtRepository,
    private val installmentRepository: InstallmentRepository,
    private val paymentRepository: PaymentRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {
    val debtors: StateFlow<List<DebtSummary>> = debtRepository.observeSummary()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * T-456 (INV-20e) — the per-parent canonical year-history stream feeding
     * the « Par année » drawer (the desktop DebtAgingDetailDrawer's per-year
     * tab, T-430 conditional-mount: the collectors start only when the drawer
     * subscribes — this flow is cold). The derivation is the ONE engine
     * [computeParentYearHistory]; this method never computes anything else.
     */
    fun yearHistory(parentId: String): Flow<ParentYearHistory> = combine(
        installmentRepository.observeByParent(parentId),
        paymentRepository.observeByParent(parentId),
        ledgerRepository.observeByParent(parentId),
    ) { installments, payments, ledgerEntries ->
        computeParentYearHistory(
            YearHistoryInput(
                parentId = parentId,
                installments = installments,
                payments = payments,
                ledgerEntries = ledgerEntries,
            ),
        )
    }
}

@Composable
fun DebtDashboardScreen(
    onBack: () -> Unit,
    onNavigateToParent: (String) -> Unit = {},
    onNavigateToCounter: (parentId: String?, studentId: String?) -> Unit = { _, _ -> },
    viewModel: DebtDashboardViewModel = hiltViewModel(),
) {
    val debtors by viewModel.debtors.collectAsState()
    val totalOutstanding = debtors.sumOf { it.outstandingAmount }
    // PARITY-002 (T-286): the INV-4 overdue portion from the shared
    // derivation (never the UI's whole-outstanding approximation).
    val totalOverdue = debtors.sumOf { it.overdueAmount }

    var bucketFilter by remember { mutableStateOf<String?>(null) }
    val filtered = if (bucketFilter == null) debtors else debtors.filter { it.bucket == bucketFilter }
    val context = LocalContext.current
    // T-456 (INV-20e): the « Par année » drawer's target family (null = closed).
    var yearHistoryTarget by remember { mutableStateOf<DebtSummary?>(null) }

    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        ElTopBar(title = "Créances & Retards", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ElCard(modifier = Modifier.weight(1f), compact = true) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total créances", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("${(totalOutstanding / 100).formatDzd()} DA", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryBlue)
                        }
                    }
                    ElCard(modifier = Modifier.weight(1f), accent = DangerRed, compact = true) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total en retard", style = MaterialTheme.typography.labelSmall, color = DangerRed)
                            Spacer(Modifier.height(4.dp))
                            Text("${(totalOverdue / 100).formatDzd()} DA", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DangerRed)
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // PARITY-002 (T-286): ALL five aging buckets are filterable
                    // (the 91–180 j chip was missing — the bucket holding ~46%
                    // of the real debt was unreachable).
                    listOf(
                        null to "Toutes (${debtors.size})",
                        "0_30" to "0–30 j",
                        "31_60" to "31–60 j",
                        "61_90" to "61–90 j",
                        "91_180" to "91–180 j",
                        "180_plus" to "180+ j",
                    ).forEach { (b, label) ->
                        ElTag(
                            text = label,
                            selected = bucketFilter == b,
                            color = if (b == "180_plus" || b == "91_180") DangerRed else PrimaryBlue,
                            onClick = { bucketFilter = b },
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    ElEmptyState(
                        icon = Icons.Default.Call,
                        title = "Aucune créance",
                        message = "Toutes les familles sélectionnées sont à jour.",
                    )
                }
            } else {
                items(filtered) { debtor ->
                    // T-457 (§15.1/§15.3): the canonical 4-tier status chip —
                    // the tone mirrors the desktop's DEBT_AGING_STATUS_TONE
                    // (green success · yellow warning · orange warning · red
                    // danger); the §15.3 wording is IDENTICAL on every surface.
                    val statusColor = when (debtor.statusLevel) {
                        "green" -> SuccessGreen
                        "yellow", "orange" -> WarningOrange
                        else -> DangerRed
                    }
                    ElCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToParent(debtor.parentId) },
                        accent = if (debtor.statusLevel == "red") DangerRed else if (debtor.statusLevel == "green") null else WarningOrange,
                        compact = true,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(debtor.parentName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("${debtor.studentCount} élève(s) rattaché(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                // The §15.3 canonical label (INV-16d: never a bare
                                // color — the explanation travels with the row).
                                ElTag(text = debtor.statusLabel, color = statusColor)
                            }
                            Spacer(Modifier.height(8.dp))
                            ElInfoRow(label = "Téléphone", value = debtor.parentPhone)
                            ElInfoRow(label = "Montant dû", value = "${(debtor.outstandingAmount / 100).formatDzd()} DZD", valueColor = DangerRed)
                            // INV-16d: the explanation is part of the contract —
                            // the tier, the thresholds actually applied, the
                            // payeur-actif annotation.
                            if (debtor.statusExplanation.isNotBlank()) {
                                Text(
                                    debtor.statusExplanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (debtor.daysOverdue > 0) {
                                    Text("En retard de ${debtor.daysOverdue} jours", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = DangerRed)
                                } else {
                                    Spacer(Modifier.width(1.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // T-456 (INV-20e): the « Par année » drill-down —
                                    // the per-year debt-history drawer.
                                    IconButton(onClick = { yearHistoryTarget = debtor }) {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = "Historique par année", tint = PrimaryBlue)
                                    }
                                    IconButton(onClick = { onNavigateToCounter(debtor.parentId, null) }) {
                                        Icon(Icons.Default.Payments, contentDescription = "Encaisser", tint = PrimaryBlue)
                                    }
                                    if (debtor.parentPhone.isNotBlank()) {
                                        IconButton(onClick = { PhoneUtils.dial(context, debtor.parentPhone) }) {
                                            Icon(Icons.Default.Call, contentDescription = "Appeler", tint = SuccessGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    // T-456 (INV-20e) — the « Par année » per-year debt-history drawer (the
    // desktop DebtAgingDetailDrawer's per-year tab). The sheet's content
    // composes ONLY while open (the T-430 conditional-mount discipline: the
    // per-parent collectors start on open, stop on dismiss) and mounts the
    // SAME ParentYearHistorySection the CRM parent screen mounts.
    yearHistoryTarget?.let { target ->
        YearHistoryDrawer(
            debtor = target,
            viewModel = viewModel,
            onDismiss = { yearHistoryTarget = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearHistoryDrawer(
    debtor: DebtSummary,
    viewModel: DebtDashboardViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The per-parent canonical stream — collected only while this drawer is
    // composed (the conditional mount; the flow is cold in the ViewModel).
    val history by remember(debtor.parentId) { viewModel.yearHistory(debtor.parentId) }
        .collectAsState(initial = null)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(debtor.parentName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Dette actuelle : ${(debtor.outstandingAmount / 100).formatDzd()} DZD",
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed,
                    )
                }
                Text("Par année", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
            }
            Spacer(Modifier.height(12.dp))
            if (history == null) {
                Text("Chargement de l'historique…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ParentYearHistoryBody(history = history!!)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
