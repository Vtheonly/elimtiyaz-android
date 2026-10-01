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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.display.ElChipVariant
import com.example.ui.designsystem.components.display.ElInfoRow
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.overlays.ElBottomSheet
import com.example.ui.designsystem.theme.ElTheme
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
    val c = ElTheme.colors
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

    ElScaffold(
        topBar = { ElTopBar(title = "Créances & Retards", onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = ElTheme.spacing.lg, vertical = ElTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ElCard(modifier = Modifier.weight(1f), size = ElCardSize.COMPACT) {
                        Column {
                            Text("Total créances", style = ElTheme.typography.labelSmall, color = c.textSecondary)
                            Spacer(Modifier.height(ElTheme.spacing.xs))
                            Text("${(totalOutstanding / 100).formatDzd()} DA", style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.primary)
                        }
                    }
                    ElCard(
                        modifier = Modifier.weight(1f),
                        size = ElCardSize.COMPACT,
                        border = BorderStroke(ElTheme.borders.thin, c.danger.copy(alpha = 0.45f)),
                    ) {
                        Column {
                            Text("Total en retard", style = ElTheme.typography.labelSmall, color = c.danger)
                            Spacer(Modifier.height(ElTheme.spacing.xs))
                            Text("${(totalOverdue / 100).formatDzd()} DA", style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.danger)
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
                        ElChip(
                            text = label,
                            variant = ElChipVariant.FILTER,
                            selected = bucketFilter == b,
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
                        subtitle = "Toutes les familles sélectionnées sont à jour.",
                    )
                }
            } else {
                items(filtered) { debtor ->
                    // T-457 (§15.1/§15.3): the canonical 4-tier status chip —
                    // the tone mirrors the desktop's DEBT_AGING_STATUS_TONE
                    // (green success · yellow warning · orange warning · red
                    // danger); the §15.3 wording is IDENTICAL on every surface.
                    val statusTone = when (debtor.statusLevel) {
                        "green" -> ElTagTone.SUCCESS
                        "yellow", "orange" -> ElTagTone.WARNING
                        else -> ElTagTone.DANGER
                    }
                    val statusColor = when (statusTone) {
                        ElTagTone.SUCCESS -> c.success
                        ElTagTone.WARNING -> c.warning
                        else -> c.danger
                    }
                    ElCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToParent(debtor.parentId) },
                        size = ElCardSize.STANDARD,
                        border = if (debtor.statusLevel == "red" || debtor.statusLevel == "yellow" || debtor.statusLevel == "orange") {
                            BorderStroke(ElTheme.borders.thin, statusColor.copy(alpha = 0.45f))
                        } else null,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(debtor.parentName, style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                                    Text("${debtor.studentCount} élève(s) rattaché(s)", style = ElTheme.typography.bodySmall, color = c.textSecondary)
                                }
                                // The §15.3 canonical label (INV-16d: never a bare
                                // color — the explanation travels with the row).
                                ElTag(text = debtor.statusLabel, tone = statusTone, size = ElTagSize.MD)
                            }
                            Spacer(Modifier.height(8.dp))
                            ElInfoRow(label = "Téléphone", value = debtor.parentPhone, valueTint = c.textPrimary)
                            ElInfoRow(label = "Montant dû", value = "${(debtor.outstandingAmount / 100).formatDzd()} DZD", valueTint = c.danger)
                            // INV-16d: the explanation is part of the contract —
                            // the tier, the thresholds actually applied, the
                            // payeur-actif annotation.
                            if (debtor.statusExplanation.isNotBlank()) {
                                Text(
                                    debtor.statusExplanation,
                                    style = ElTheme.typography.bodySmall,
                                    color = statusColor,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (debtor.daysOverdue > 0) {
                                    Text("En retard de ${debtor.daysOverdue} jours", style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = c.danger)
                                } else {
                                    Spacer(Modifier.width(1.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // T-456 (INV-20e): the « Par année » drill-down —
                                    // the per-year debt-history drawer.
                                    ElIconButton(
                                        icon = Icons.Default.CalendarMonth,
                                        onClick = { yearHistoryTarget = debtor },
                                        contentDescription = "Historique par année",
                                        tint = c.primary,
                                        background = Color.Transparent,
                                    )
                                    ElIconButton(
                                        icon = Icons.Default.Payments,
                                        onClick = { onNavigateToCounter(debtor.parentId, null) },
                                        contentDescription = "Encaisser",
                                        tint = c.primary,
                                        background = Color.Transparent,
                                    )
                                    if (debtor.parentPhone.isNotBlank()) {
                                        ElIconButton(
                                            icon = Icons.Default.Call,
                                            onClick = { PhoneUtils.dial(context, debtor.parentPhone) },
                                            contentDescription = "Appeler",
                                            tint = c.success,
                                            background = Color.Transparent,
                                        )
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

@Composable
private fun YearHistoryDrawer(
    debtor: DebtSummary,
    viewModel: DebtDashboardViewModel,
    onDismiss: () -> Unit,
) {
    val c = ElTheme.colors
    // The per-parent canonical stream — collected only while this drawer is
    // composed (the conditional mount; the flow is cold in the ViewModel).
    val history by remember(debtor.parentId) { viewModel.yearHistory(debtor.parentId) }
        .collectAsState(initial = null)
    // T-460 pass C: the DS sheet chrome (ElBottomSheet with handle + scrim,
    // navigation-bars padding) replaces the raw M3 ModalBottomSheet.
    ElBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ElTheme.spacing.lg, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(debtor.parentName, style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                    Text(
                        "Dette actuelle : ${(debtor.outstandingAmount / 100).formatDzd()} DZD",
                        style = ElTheme.typography.bodySmall,
                        color = c.danger,
                    )
                }
                Text("Par année", style = ElTheme.typography.labelLarge, color = c.primary)
            }
            Spacer(Modifier.height(ElTheme.spacing.md))
            if (history == null) {
                Text("Chargement de l'historique…", style = ElTheme.typography.bodyMedium, color = c.textSecondary)
            } else {
                ParentYearHistoryBody(history = history!!)
            }
            Spacer(Modifier.height(ElTheme.spacing.xl))
        }
    }
}
