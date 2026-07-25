package com.elimtiyaz.feature.financials

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Whatsapp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AsyncContent
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.AgingBucket
import com.elimtiyaz.domain.model.DebtSummary
import kotlinx.coroutines.launch

/** Debt dashboard — aging chart + filter + debtors list with reminder action. */
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDashboardScreen(
    nav: NavController,
    vm: DebtDashboardViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val canRemind = session?.can(Permission.SendReminder) == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Créances & Retards", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = vm::toggleSort) {
                        Icon(Icons.Outlined.Sort, contentDescription = "Trier")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            // --- Aging buckets chart ---
            ElImtiyazCard(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
                Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
                    Text("Répartition par ancienneté", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(ElimtiyazSpacing.x3))
                    AgingBucket.values().forEach { b ->
                        val total = state.bucketTotals[b] ?: 0.0
                        val fraction = if (state.maxBucketTotal > 0) (total / state.maxBucketTotal).toFloat() else 0f
                        AgingBucketBar(
                            label = b.displayFr,
                            amount = total,
                            fraction = fraction,
                            color = bucketColor(b),
                        )
                        Spacer(Modifier.height(ElimtiyazSpacing.x2))
                    }
                }
            }

            // --- Bucket filter chips ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
                horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
            ) {
                FilterChip(selected = state.bucketFilter == null, onClick = { vm.filterByBucket(null) }, label = { Text("Tous") })
                AgingBucket.values().forEach { b ->
                    FilterChip(
                        selected = state.bucketFilter == b,
                        onClick = { vm.filterByBucket(if (state.bucketFilter == b) null else b) },
                        label = { Text(b.displayFr) },
                    )
                }
            }

            // --- List ---
            AsyncContent(
                isLoading = state.isLoading,
                error = state.error,
                items = state.visibleDebtors,
                onRetry = vm::reload,
                emptyTitle = "Aucune créance",
                emptyDescription = "Aucun parent ne doit de règlement en cours.",
                emptyIcon = Icons.Outlined.Notifications,
            ) { list ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(ElimtiyazSpacing.x4),
                    verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
                ) {
                    items(list, key = { it.parentId }) { d ->
                        DebtorDetailRow(
                            debtor = d,
                            canRemind = canRemind,
                            onReminder = {
                                vm.sendReminder(d.parentId) { ok, err ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(err ?: "Rappel envoyé à ${d.parentName}")
                                    }
                                }
                            },
                            onWhatsApp = { openWhatsApp(context, d.parentPhone, d.parentName, d.outstandingAmount) },
                            onClick = { nav.navigate(Route.Installments.build(d.parentId)) },
                        )
                    }
                }
            }
        }
    }
}

/** Single debtor row in the dashboard with WhatsApp + reminder actions. */
@Composable
private fun DebtorDetailRow(
    debtor: DebtSummary,
    canRemind: Boolean,
    onReminder: () -> Unit,
    onWhatsApp: () -> Unit,
    onClick: () -> Unit,
) {
    val tone = when (debtor.agingBucket) {
        AgingBucket.Bucket0_30, AgingBucket.Bucket31_60 -> StatusTone.Warning
        AgingBucket.Bucket61_90, AgingBucket.Bucket91_180, AgingBucket.Bucket180Plus -> StatusTone.Danger
    }
    ElImtiyazCard(onClick = onClick) {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(initial = debtor.parentName.firstOrNull()?.toString() ?: "?", size = 40)
                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(debtor.parentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${debtor.studentCount} élève(s) • ${debtor.parentPhone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(label = debtor.agingBucket.displayFr, tone = tone)
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Reste dû", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.currency(debtor.outstandingAmount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = ElimtiyazColors.DangerRed)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${debtor.daysOverdue} j de retard", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(ElimtiyazSpacing.x1))
                    Row(horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2)) {
                        AssistChip(
                            onClick = onWhatsApp,
                            label = { Text("WhatsApp") },
                            leadingIcon = { Icon(Icons.Outlined.Whatsapp, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                        if (canRemind) {
                            AssistChip(
                                onClick = onReminder,
                                label = { Text("Rappel") },
                                leadingIcon = { Icon(Icons.Outlined.Notifications, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Color for an aging bucket (5-bucket palette, escalating from gold to red). */
@Composable
private fun bucketColor(b: AgingBucket): androidx.compose.ui.graphics.Color = when (b) {
    AgingBucket.Bucket0_30     -> ElimtiyazColors.WarningGold
    AgingBucket.Bucket31_60    -> ElimtiyazColors.WarmGold
    AgingBucket.Bucket61_90    -> ElimtiyazColors.MutedBrown
    AgingBucket.Bucket91_180   -> ElimtiyazColors.DangerRed
    AgingBucket.Bucket180Plus  -> ElimtiyazColors.DangerRed
}

/** Fire an ACTION_SEND intent to WhatsApp with a pre-filled French reminder message. */
private fun openWhatsApp(context: Context, phone: String, parentName: String, amount: Double) {
    val msg = "Bonjour $parentName, votre solde dû envers El-Imtiyaz est de ${Formatters.currency(amount)}. " +
        "Merci de régulariser dans les meilleurs délais."
    // Try WhatsApp first; fall back to SMS.
    val waIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, msg)
        setPackage("com.whatsapp")
    }
    val resolved = waIntent.resolveActivity(context.packageManager) != null
    runCatching {
        context.startActivity(if (resolved) waIntent else Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).putExtra("sms_body", msg))
    }.onFailure {
        // Final fallback — generic share
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, msg)
        }, "Partager le rappel"))
    }
}
