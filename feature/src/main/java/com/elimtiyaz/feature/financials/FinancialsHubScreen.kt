package com.elimtiyaz.feature.financials

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.ExpenseStatus
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.PaymentMethod
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AsyncContent
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.ListRow
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.domain.model.DebtSummary
import com.elimtiyaz.domain.model.Expense
import com.elimtiyaz.domain.model.Payment

/** Financials hub — the root of the Finances tab. KPI cards + 3-tab content. */
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialsHubScreen(
    nav: NavController,
    vm: FinancialsHubViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val canCollect = session?.can(Permission.CollectPayment) == true
    val canSubmitExpense = session?.can(Permission.SubmitExpense) == true

    val fabLabel: String
    val fabAction: () -> Unit
    when (selectedTab) {
        1 -> { fabLabel = "Dépense";      fabAction = { nav.navigate(Route.ExpenseSubmit.route) } }
        else -> { fabLabel = "Paiement";  fabAction = { nav.navigate(Route.CounterPayment.build()) } }
    }
    val fabVisible = when (selectedTab) {
        1 -> canSubmitExpense
        else -> canCollect
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Finances", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { nav.navigate(Route.DebtDashboard.route) }) {
                        Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = "Créances")
                    }
                    AvatarCircle(
                        initial = session?.displayName?.firstOrNull()?.toString() ?: "?",
                        size = 32,
                        modifier = Modifier.padding(end = ElimtiyazSpacing.x2),
                    )
                },
            )
        },
        floatingActionButton = {
            if (fabVisible) {
                ExtendedFloatingActionButton(
                    onClick = fabAction,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(fabLabel) },
                )
            }
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            // KPI row
            KpiGrid(state)
            Spacer(Modifier.height(ElimtiyazSpacing.x3))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Paiements") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Dépenses") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Créances") })
            }

            when (selectedTab) {
                0 -> PaymentsTab(state.recentPayments, state.paymentsLoading, state.paymentsError, vm::loadPayments) { p ->
                    nav.navigate(Route.PaymentDetail.build(p.id))
                }
                1 -> ExpensesTab(state.expenses, state.expensesLoading, state.expensesError, vm::loadExpenses) { e ->
                    nav.navigate(Route.ExpenseDetail.build(e.id))
                }
                2 -> DebtorsTab(state.debtors, state.debtLoading, state.debtError, vm::loadDebt) { d ->
                    nav.navigate(Route.Installments.build(d.parentId))
                }
            }
        }
    }
}

/** 2×2 KPI grid at the top of the hub. */
@Composable
private fun KpiGrid(state: FinancialsHubUiState) {
    val k = state.kpis
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
        horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
    ) {
        KpiCard(
            title = "Encaissé aujourd'hui",
            value = Formatters.currency(state.collectedToday),
            icon = Icons.Outlined.AttachMoney,
            tone = ElimtiyazColors.SuccessGreen,
            modifier = Modifier.weight(1f),
        )
        KpiCard(
            title = "Revenu mensuel",
            value = Formatters.currency(k?.monthlyRevenue ?: 0.0),
            icon = Icons.Outlined.ReceiptLong,
            tone = ElimtiyazColors.PrimaryBlue,
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ElimtiyazSpacing.x4),
        horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
    ) {
        KpiCard(
            title = "Créances en retard",
            value = Formatters.currency(k?.outstandingDebt ?: 0.0),
            icon = Icons.Outlined.MoneyOff,
            tone = ElimtiyazColors.DangerRed,
            modifier = Modifier.weight(1f),
        )
        KpiCard(
            title = "Dépenses en attente",
            value = (k?.pendingExpenses ?: 0).toString(),
            icon = Icons.Outlined.PendingActions,
            tone = ElimtiyazColors.WarmGold,
            modifier = Modifier.weight(1f),
        )
    }
}

/** "Paiements" tab — last 30 payments with parent, amount, method, status chip, date. */
@Composable
private fun PaymentsTab(
    items: List<Payment>,
    isLoading: Boolean,
    error: com.elimtiyaz.core.common.AppError?,
    onRetry: () -> Unit,
    onClick: (Payment) -> Unit,
) {
    AsyncContent(
        isLoading = isLoading,
        error = error,
        items = items,
        onRetry = onRetry,
        emptyTitle = "Aucun paiement récent",
        emptyDescription = "Les paiements encaissés aujourd'hui apparaîtront ici.",
        emptyIcon = Icons.Outlined.Receipt,
    ) { list ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ElimtiyazSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
        ) {
            items(list, key = { it.id }) { p ->
                PaymentRow(payment = p, onClick = { onClick(p) })
            }
        }
    }
}

/** Single payment list row. */
@Composable
private fun PaymentRow(payment: Payment, onClick: () -> Unit) {
    ElImtiyazCard(onClick = onClick) {
        ListRow(
            leading = { AvatarCircle(initial = payment.parentId.firstOrNull()?.toString() ?: "?", size = 36) },
            title = "${payment.receiptNumber} • ${Formatters.currency(payment.amount)}",
            subtitle = "${PaymentMethod.from(payment.method)?.displayFr ?: payment.method} • ${Formatters.dateTime(payment.collectedAt)}",
            trailing = { StatusChip(label = payment.status.replaceFirstChar { it.uppercase() }, tone = paymentTone(payment.status)) },
        )
    }
}

/** "Dépenses" tab — filtered by status chips, list of expenses. */
@Composable
private fun ExpensesTab(
    items: List<Expense>,
    isLoading: Boolean,
    error: com.elimtiyaz.core.common.AppError?,
    onRetry: () -> Unit,
    onClick: (Expense) -> Unit,
) {
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val filtered = if (statusFilter == null) items else items.filter { it.status == statusFilter }

    Column(modifier = Modifier.fillMaxSize()) {
        // Status filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
            horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
        ) {
            FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("Tous") })
            ExpenseStatus.values().take(5).forEach { st ->
                FilterChip(
                    selected = statusFilter == st.key,
                    onClick = { statusFilter = if (statusFilter == st.key) null else st.key },
                    label = { Text(st.displayFr) },
                )
            }
        }
        AsyncContent(
            isLoading = isLoading,
            error = error,
            items = filtered,
            onRetry = onRetry,
            emptyTitle = "Aucune dépense",
            emptyDescription = "Les demandes soumises apparaîtront ici.",
            emptyIcon = Icons.Outlined.PendingActions,
        ) { list ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
            ) {
                items(list, key = { it.id }) { e ->
                    ExpenseRow(expense = e, onClick = { onClick(e) })
                }
            }
        }
    }
}

/** Single expense list row. */
@Composable
private fun ExpenseRow(expense: Expense, onClick: () -> Unit) {
    val st = ExpenseStatus.from(expense.status)
    ElImtiyazCard(onClick = onClick) {
        ListRow(
            leading = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.PendingActions, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            },
            title = "${expense.requestCode} • ${expense.title}",
            subtitle = "${Formatters.currency(expense.amount)} • ${expense.payee}",
            trailing = { StatusChip(label = st?.displayFr ?: expense.status, tone = expenseTone(expense.status)) },
        )
    }
}

/** "Créances" tab — top 20 debtors with aging chip; tap → installments. */
@Composable
private fun DebtorsTab(
    items: List<DebtSummary>,
    isLoading: Boolean,
    error: com.elimtiyaz.core.common.AppError?,
    onRetry: () -> Unit,
    onClick: (DebtSummary) -> Unit,
) {
    AsyncContent(
        isLoading = isLoading,
        error = error,
        items = items,
        onRetry = onRetry,
        emptyTitle = "Aucune créance en cours",
        emptyDescription = "Toutes les familles sont à jour.",
        emptyIcon = Icons.Outlined.AccountBalanceWallet,
    ) { list ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ElimtiyazSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
        ) {
            items(list, key = { it.parentId }) { d ->
                DebtorRow(debtor = d, onClick = { onClick(d) })
            }
        }
    }
}

/** Single debtor row in the Créances tab. */
@Composable
private fun DebtorRow(debtor: DebtSummary, onClick: () -> Unit) {
    val tone = when (debtor.agingBucket) {
        com.elimtiyaz.domain.model.AgingBucket.Bucket0_30 -> com.elimtiyaz.core.ui.StatusTone.Warning
        com.elimtiyaz.domain.model.AgingBucket.Bucket31_60 -> com.elimtiyaz.core.ui.StatusTone.Warning
        com.elimtiyaz.domain.model.AgingBucket.Bucket61_90 -> com.elimtiyaz.core.ui.StatusTone.Danger
        com.elimtiyaz.domain.model.AgingBucket.Bucket91_180 -> com.elimtiyaz.core.ui.StatusTone.Danger
        com.elimtiyaz.domain.model.AgingBucket.Bucket180Plus -> com.elimtiyaz.core.ui.StatusTone.Danger
    }
    ElImtiyazCard(onClick = onClick) {
        ListRow(
            leading = { AvatarCircle(initial = debtor.parentName.firstOrNull()?.toString() ?: "?", size = 36) },
            title = "${debtor.parentName} • ${Formatters.currency(debtor.outstandingAmount)}",
            subtitle = "${debtor.studentCount} élève(s) • ${debtor.daysOverdue} jour(s) de retard",
            trailing = { StatusChip(label = debtor.agingBucket.displayFr, tone = tone) },
        )
    }
}
