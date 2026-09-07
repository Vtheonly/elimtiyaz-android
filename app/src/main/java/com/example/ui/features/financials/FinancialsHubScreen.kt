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
fun FinancialsHubScreen(
    session: Session,
    onNavigateToCounterPayment: (parentId: String?, studentId: String?) -> Unit = { _, _ -> },
    onNavigateToProofScanner: () -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
    onNavigateToInstallmentSchedule: () -> Unit,
    onNavigateToExpenseSubmit: () -> Unit = {},
    onNavigateToExpenseDetail: (String) -> Unit = {},
    onNavigateToPaymentDetail: (String) -> Unit = {},
    viewModel: FinancialsHubViewModel = hiltViewModel(),
    installmentViewModel: InstallmentScheduleViewModel = hiltViewModel(),
) {
    val kpis by viewModel.kpis.collectAsState()
    val recentPayments by viewModel.recentPayments.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val ledgerEntries by viewModel.ledgerEntries.collectAsState()
    val collectedToday by viewModel.collectedToday.collectAsState()
    val pendingExpensesCount by viewModel.pendingExpensesCount.collectAsState()
    val debtors by viewModel.debtors.collectAsState()

    val parents by installmentViewModel.parents.collectAsState()
    val selectedParentId by installmentViewModel.selectedParentId.collectAsState()
    val installments by installmentViewModel.installments.collectAsState()
    val parentSummary by installmentViewModel.parentSummary.collectAsState()
    val installmentBusy by installmentViewModel.busy.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Encaissements", "Tranches", "Créances", "Dépenses", "Journal", "Scanner")

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ModernSecondaryTabRow(
                tabs = tabs,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
            )

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                when (selectedTab) {
                    0 -> PaymentsTab(
                        collectedToday = collectedToday,
                        monthlyRevenue = kpis?.monthlyRevenue ?: 0L,
                        payments = recentPayments,
                        onNavigateToPayment = onNavigateToPaymentDetail,
                        onNewPayment = { onNavigateToCounterPayment(null, null) },
                    )
                    1 -> TranchesTab(
                        parents = parents,
                        selectedParentId = selectedParentId,
                        installments = installments,
                        parentSummary = parentSummary,
                        busy = installmentBusy,
                        onSelectParent = { installmentViewModel.selectParent(it) },
                        onMarkPaid = { installmentViewModel.markPaid(it) },
                        onNavigateToCounter = { pId, sId -> onNavigateToCounterPayment(pId, sId) },
                    )
                    2 -> CreancesTab(
                        outstandingDebt = kpis?.outstandingDebt ?: 0L,
                        debtors = debtors,
                        onNavigateToDebtor = onNavigateToDebtDashboard,
                        onNavigateToCounter = { pId, sId -> onNavigateToCounterPayment(pId, sId) },
                    )
                    3 -> DepensesTab(
                        expenses = expenses,
                        pendingCount = pendingExpensesCount,
                        onExpenseClick = onNavigateToExpenseDetail,
                        onNewExpense = onNavigateToExpenseSubmit,
                    )
                    4 -> JournalTab(
                        entries = ledgerEntries,
                    )
                    5 -> ProofScannerScreen(
                        onBack = { selectedTab = 0 },
                    )
                }
            }
        }

        if (selectedTab == 3) {
            ElFab(
                icon = Icons.Default.Add,
                onClick = onNavigateToExpenseSubmit,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                contentDescription = "Nouvelle dépense",
            )
        }
    }
}

@Composable
private fun PaymentsTab(
    collectedToday: Long,
    monthlyRevenue: Long,
    payments: List<Payment>,
    onNavigateToPayment: (String) -> Unit,
    onNewPayment: () -> Unit,
) {
    var methodFilter by remember { mutableStateOf<String?>(null) }
    val filtered = if (methodFilter == null) payments else payments.filter { it.method.code.equals(methodFilter, ignoreCase = true) }

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
                MetricMiniCard("Aujourd'hui", "${(collectedToday / 100).formatDzd()} DZD", SuccessGreen, Modifier.weight(1f))
                MetricMiniCard("Ce mois", "${(monthlyRevenue / 100).formatDzd()} DZD", PrimaryBlue, Modifier.weight(1f))
            }
        }

        item {
            ElButton(
                text = "Encaisser un paiement au guichet",
                onClick = onNewPayment,
                fullWidth = true,
                icon = Icons.Default.Payments,
                style = ElButtonStyle.Primary,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    null to "Tous",
                    "cash" to "Espèces",
                    "check" to "Chèques",
                    "transfer" to "Virements",
                ).forEach { (code, label) ->
                    ElTag(
                        text = label,
                        selected = methodFilter == code,
                        color = PrimaryBlue,
                        onClick = { methodFilter = code },
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                ElEmptyState(
                    icon = Icons.Default.Payments,
                    title = "Aucun encaissement",
                    message = "Aucun paiement enregistré pour ce filtre.",
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        } else {
            items(filtered) { payment ->
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onNavigateToPayment(payment.id) },
                    compact = true,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SuccessGreen.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(payment.receiptNumber, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    "${payment.category.name.replace("_", " ")} • ${payment.collectedAt.take(10)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "+${(payment.amount / 100).formatDzd()} DZD",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = SuccessGreen,
                            )
                            ElTag(
                                text = payment.method.name,
                                color = PrimaryBlue,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranchesTab(
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

@Composable
private fun CreancesTab(
    outstandingDebt: Long,
    debtors: List<DebtSummary>,
    onNavigateToDebtor: () -> Unit,
    onNavigateToCounter: (parentId: String?, studentId: String?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var bucketFilter by remember { mutableStateOf<String?>(null) }
    val filtered = if (bucketFilter == null) debtors else debtors.filter { it.bucket == bucketFilter }
    val totalOverdue = debtors.filter { it.daysOverdue > 0 }.sumOf { it.outstandingAmount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricMiniCard("Créances totales", "${(outstandingDebt / 100).formatDzd()} DZD", PrimaryBlue, Modifier.weight(1f))
                MetricMiniCard("En retard", "${(totalOverdue / 100).formatDzd()} DZD", DangerRed, Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    null to "Toutes",
                    "0_30" to "0-30j",
                    "31_60" to "31-60j",
                    "61_90" to "61-90j",
                    "180_plus" to "180j+",
                ).forEach { (b, label) ->
                    ElTag(
                        text = label,
                        selected = bucketFilter == b,
                        color = if (b == "180_plus") DangerRed else PrimaryBlue,
                        onClick = { bucketFilter = b },
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                ElEmptyState(
                    icon = Icons.Default.CheckCircle,
                    title = "Aucune créance en retard",
                    message = "Toutes les familles sont à jour dans leurs paiements.",
                )
            }
        } else {
            items(filtered) { debtor ->
                ElCard(modifier = Modifier.fillMaxWidth(), accent = DangerRed, compact = true) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(debtor.parentName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    "${debtor.studentCount} enfant(s) inscrit(s) • Tél : ${debtor.parentPhone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
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

                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Retard de ${debtor.daysOverdue} jours",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = DangerRed,
                            )
                            Text(
                                "${(debtor.outstandingAmount / 100).formatDzd()} DZD",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = DangerRed,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DepensesTab(
    expenses: List<Expense>,
    pendingCount: Int,
    onExpenseClick: (String) -> Unit,
    onNewExpense: () -> Unit,
) {
    var statusFilter by remember { mutableStateOf<String?>(null) }
    val filtered = if (statusFilter == null) expenses else expenses.filter { it.status.equals(statusFilter, ignoreCase = true) }
    val totalAmount = expenses.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricMiniCard("Total engagé", "${(totalAmount / 100).formatDzd()} DZD", PrimaryBlue, Modifier.weight(1f))
                MetricMiniCard("En attente", "$pendingCount demande(s)", if (pendingCount > 0) WarmGold else SuccessGreen, Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    null to "Toutes",
                    "submitted" to "En attente",
                    "approved" to "Approuvées",
                    "disbursed" to "Décaissées",
                    "settled" to "Clôturées",
                ).forEach { (st, label) ->
                    ElTag(
                        text = label,
                        selected = statusFilter == st,
                        color = PrimaryBlue,
                        onClick = { statusFilter = st },
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                ElEmptyState(
                    icon = Icons.Default.Receipt,
                    title = "Aucune dépense",
                    message = "Aucun ticket de dépense n'a été créé.",
                    actionText = "Créer une dépense",
                    onAction = onNewExpense,
                )
            }
        } else {
            items(filtered) { exp ->
                val (badgeColor, statusFr) = when (exp.status.lowercase()) {
                    "submitted" -> WarmGold to "En attente"
                    "approved" -> PrimaryBlue to "Approuvée"
                    "disbursed" -> SuccessGreen to "Décaissée"
                    "settled" -> SuccessGreen to "Clôturée"
                    else -> MaterialTheme.colorScheme.outline to exp.status
                }

                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onExpenseClick(exp.id) },
                    compact = true,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(exp.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                            ElTag(text = statusFr, color = badgeColor)
                        }
                        Text(
                            "${exp.requestCode} • Catégorie : ${exp.category} • Bénéficiaire : ${exp.payee}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Date : ${exp.submittedAt.take(10)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${(exp.amount / 100).formatDzd()} DZD",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalTab(
    entries: List<LedgerEntry>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ElSectionHeader(
                title = "Grand Livre — Mouvements",
                subtitle = "Historique transparent des facturations, encaissements et régularisations",
            )
        }

        if (entries.isEmpty()) {
            item {
                ElEmptyState(
                    icon = Icons.Default.Receipt,
                    title = "Grand livre vide",
                    message = "Aucun mouvement financier enregistré.",
                )
            }
        } else {
            items(entries.sortedByDescending { it.at }) { entry ->
                val isCredit = entry.type.code == "payment" || (entry.type.code == "adjustment" && entry.amount < 0) || entry.type.code == "refund"
                val (typeBadgeColor, typeLabel) = when (entry.type.code) {
                    "charge" -> DangerRed to "Facturation"
                    "payment" -> SuccessGreen to "Encaissement"
                    "adjustment" -> if (entry.amount < 0) SuccessGreen to "Remise / Déduction" else WarmGold to "Régularisation"
                    "refund" -> DangerRed to "Remboursement"
                    "reversal" -> WarmGold to "Extourne"
                    else -> PrimaryBlue to entry.type.code.replaceFirstChar { it.uppercase() }
                }

                val displayDescription = remember(entry.description) {
                    cleanDescription(entry.description, entry.category.name)
                }

                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    accent = if (isCredit) SuccessGreen else DangerRed,
                    compact = true,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ElTag(text = typeLabel, color = typeBadgeColor)
                            Text(
                                text = entry.at.take(10),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Text(
                            text = displayDescription,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Service : ${categoryFr(entry.category)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val formattedAmount = (kotlin.math.abs(entry.amount) / 100).formatDzd()
                            Text(
                                text = "${if (entry.amount < 0) "− " else "+ "}$formattedAmount DZD",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isCredit) SuccessGreen else DangerRed,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun cleanDescription(raw: String, fallbackCategory: String): String {
    if (raw.isBlank()) return "Opération sur $fallbackCategory"
    return when {
        raw.contains("réconciliation 0063", ignoreCase = true) -> "Régularisation de remise (recalcul import)"
        raw.contains("Devis annuel", ignoreCase = true) -> "Devis annuel de scolarité"
        raw.contains("Encaissement", ignoreCase = true) -> raw
        raw.contains("Tranche", ignoreCase = true) -> raw
        else -> raw
    }
}

private fun categoryFr(cat: PaymentCategory): String = when (cat) {
    PaymentCategory.TUITION -> "Scolarité"
    PaymentCategory.TRANSPORT -> "Transport"
    PaymentCategory.CANTEEN -> "Cantine"
    PaymentCategory.UNIFORM -> "Uniforme"
    PaymentCategory.BOOKS -> "Fournitures & Livres"
    PaymentCategory.PARENT_CREDIT -> "Crédit Parent"
    PaymentCategory.EXTRACURRICULAR -> "Parascolaire"
    else -> cat.name.replace("_", " ")
}

@Composable
private fun MetricMiniCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    ElCard(modifier = modifier, compact = true) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
        }
    }
}
