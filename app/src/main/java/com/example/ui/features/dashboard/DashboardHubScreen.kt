package com.example.ui.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.ui.components.ElAlertBanner
import com.example.ui.components.ElAlertSeverity
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElFab
import com.example.ui.components.ElGradientStatCard
import com.example.ui.components.ElIconButton
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElScaffold
import com.example.ui.components.ElStatCard
import com.example.ui.components.ElTag
import com.example.ui.components.ElTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Session
import com.example.domain.model.AppNotification
import com.example.domain.model.DashboardKpi
import com.example.domain.repository.NotificationRepository
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold
import com.example.ui.theme.elDesignTokens
import com.example.ui.theme.elDesignTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val defaultKpi = DashboardKpi(
        totalStudents = 390,
        totalParents = 185,
        totalStaff = 45,
        monthlyRevenue = 12450000L, // 12.45M DZD
        outstandingDebt = 3200000L, // 3.2M DZD
        pendingExpenses = 3,
        attendanceRateToday = 96.5,
        overdueAlerts = 2,
    )

    private val _kpis = MutableStateFlow<DashboardKpi?>(defaultKpi)
    val kpis: StateFlow<DashboardKpi?> = _kpis.asStateFlow()

    private val sampleAlerts = listOf(
        AppNotification("N-1", "ten-001", "Alerte Dépense Tier-2", "Demande d'achat matériel informatique (45,000 DZD) en attente de validation par l'administration.", "expense_pending", "high", "system", "Système", "EXP-004", null, null, null, null, null, "2026-07-31T09:30:00Z", "system"),
        AppNotification("N-2", "ten-001", "Seuil 3+ Absences Atteint", "L'élève Yacine Belkacem (PRIM - CE1 B) a atteint 3 absences non justifiées.", "attendance_alert", "urgent", "system", "Système", "STU-003", null, null, null, null, null, "2026-07-31T08:15:00Z", "system"),
        AppNotification("N-3", "ten-001", "Échéance Chèque de Banque", "Chèque BNA #883921 (150,000 DZD) à déposer pour compensation aujourd'hui.", "payment_overdue", "medium", "system", "Système", "CHK-001", null, null, null, null, "2026-07-30T16:00:00Z", "2026-07-30T16:00:00Z", "system"),
    )

    val alerts: StateFlow<List<AppNotification>> = MutableStateFlow(sampleAlerts)
        .stateIn(viewModelScope, SharingStarted.Lazily, sampleAlerts)
}

data class LivePaymentFeedItem(
    val id: String,
    val studentName: String,
    val parentName: String,
    val amountDzd: Long,
    val method: String, // Cash, Check, Transfer
    val timestamp: String,
    val receiptBookCode: String, // e.g. B01, B11
)

val SAMPLE_PAYMENT_FEED = listOf(
    LivePaymentFeedItem("PAY-101", "Amine Benali", "M. Khelil Benali", 25000L, "Chèque", "Aujourd'hui 10:15", "B11-042"),
    LivePaymentFeedItem("PAY-102", "Sarra Khelifi", "Mme. Nassima Khelifi", 30000L, "Espèces", "Aujourd'hui 09:40", "B01-118"),
    LivePaymentFeedItem("PAY-103", "Lina Brahimi", "M. Omar Brahimi", 15000L, "Virement", "Hier 16:20", "B12-005"),
    LivePaymentFeedItem("PAY-104", "Mehdi Mansouri", "Mme. Salima Mansouri", 52000L, "Espèces", "Hier 14:10", "B01-117"),
)

@Composable
fun DashboardHubScreen(
    session: Session,
    onNavigateToStudent: (String) -> Unit,
    onNavigateToParent: (String) -> Unit,
    onNavigateToCounterPayment: () -> Unit,
    onNavigateToDebtDashboard: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val kpis by viewModel.kpis.collectAsState()
    val alerts by viewModel.alerts.collectAsState()
    val tokens = elDesignTokens()

    var showAiDrawer by remember { mutableStateOf(false) }
    var showReceiptModalItem by remember { mutableStateOf<LivePaymentFeedItem?>(null) }
    val sheetState = rememberModalBottomSheetState()

    ElScaffold(
        floatingActionButton = {
            ElFab(
                icon = Icons.Default.AutoAwesome,
                onClick = { showAiDrawer = true },
                contentDescription = "Assistant IA Mobile",
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header with gradient hero card
            ElGradientStatCard(
                title = "Tableau de Bord Opérationnel",
                value = "Bienvenue, ${session.displayName}",
                subtitle = "Rôle: ${session.role.code} • ${alerts.count { it.readAt == null }} alertes non lues",
                modifier = Modifier.fillMaxWidth(),
                gradient = tokens.primaryDiagonalBrush,
            )

            // KPI Carousel
            ElSectionHeader(title = "Indicateurs Clés de Performance")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    ElStatCard(
                        title = "Revenu Mensuel",
                        value = "${(kpis?.monthlyRevenue ?: 12450000L).formatDzd()} DZD",
                        subtitle = "Objectif: 15,000,000 DZD",
                        icon = Icons.Default.TrendingUp,
                        accentColor = SuccessGreen,
                    )
                }
                item {
                    ElStatCard(
                        title = "Créances Restantes",
                        value = "${(kpis?.outstandingDebt ?: 3200000L).formatDzd()} DZD",
                        subtitle = "Taux de recouvrement: 85.2%",
                        icon = Icons.Default.MoneyOff,
                        accentColor = DangerRed,
                    )
                }
                item {
                    ElStatCard(
                        title = "Élèves Inscrits",
                        value = "${kpis?.totalStudents ?: 390}",
                        subtitle = "Présence aujourd'hui: 96.5%",
                        icon = Icons.Default.Groups,
                        accentColor = PrimaryBlue,
                    )
                }
                item {
                    ElStatCard(
                        title = "Demandes Dépenses",
                        value = "${kpis?.pendingExpenses ?: 3} en attente",
                        subtitle = "Tier 2 Validation Requise",
                        icon = Icons.Default.AccountBalance,
                        accentColor = WarmGold,
                    )
                }
            }

            // AI Quick Actions Widget
            ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Assistant IA — Raccourcis", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ElTag(text = "Résumé Encaisses", selected = false, onClick = { showAiDrawer = true })
                        ElTag(text = "Relance Boumerdès", selected = false, onClick = { onNavigateToDebtDashboard() }, color = WarmGold)
                    }
                }
            }

            // Live Collection Feed
            ElSectionHeader(
                title = "Flux des Encaissements",
                actionText = "+ Nouveau",
                onAction = onNavigateToCounterPayment,
            )

            SAMPLE_PAYMENT_FEED.forEach { item ->
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showReceiptModalItem = item },
                    compact = true,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (item.method == "Espèces") SuccessGreen else PrimaryBlue),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (item.method == "Espèces") Icons.Default.Payment else Icons.Default.CreditCard,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.studentName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp))
                            Text("Parent: ${item.parentName} • Reçu: ${item.receiptBookCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${item.method} • ${item.timestamp}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${item.amountDzd.formatDzd()} DZD", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SuccessGreen, fontSize = 15.sp))
                            Icon(Icons.Default.Receipt, contentDescription = "Voir Reçu", tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Notification & Alert Center
            ElSectionHeader(title = "Avis & Notifications")
            alerts.forEach { alert ->
                val severity = when (alert.type) {
                    "attendance_alert" -> ElAlertSeverity.Danger
                    "payment_overdue" -> ElAlertSeverity.Warning
                    else -> ElAlertSeverity.Info
                }
                ElAlertBanner(
                    title = alert.title,
                    message = alert.body,
                    severity = severity,
                )
            }
        }
    }

    // Receipt PDF Modal Sheet
    showReceiptModalItem?.let { receipt ->
        ModalBottomSheet(
            onDismissRequest = { showReceiptModalItem = null },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Reçu de Paiement (${receipt.receiptBookCode})", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ElSectionHeader(title = "Détails du Paiement")
                        Text("Élève: ${receipt.studentName}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Parent: ${receipt.parentName}", style = MaterialTheme.typography.bodyMedium)
                        Text("Montant: ${receipt.amountDzd.formatDzd()} DZD", style = MaterialTheme.typography.titleMedium.copy(color = SuccessGreen, fontWeight = FontWeight.Bold))
                        Text("Mode: ${receipt.method}", style = MaterialTheme.typography.bodyMedium)
                        Text("Date/Heure: ${receipt.timestamp}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                ElButton(
                    text = "Partager / Imprimer le Reçu PDF",
                    onClick = { showReceiptModalItem = null },
                    fullWidth = true,
                    icon = Icons.Default.Receipt,
                )
            }
        }
    }

    // AI Assistant Bottom Sheet
    if (showAiDrawer) {
        ModalBottomSheet(
            onDismissRequest = { showAiDrawer = false },
            sheetState = sheetState,
        ) {
            AiAssistantDrawerContent(onDismiss = { showAiDrawer = false })
        }
    }
}

@Composable
fun AiAssistantDrawerContent(onDismiss: () -> Unit) {
    var queryText by remember { mutableStateOf("") }
    val chatMessages = remember {
        mutableStateListOf(
            "IA: Bonjour! Je suis votre assistant opérationnel El-Imtiyaz. Comment puis-je vous aider aujourd'hui?"
        )
    }
    val tokens = elDesignTokens()

    Column(
        modifier = Modifier
            .padding(20.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tokens.primaryBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Assistant IA Mobile", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        }

        Text("Suggestions:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ElTag(text = "Résumé Encaissements", onClick = { queryText = "Résumer les encaissements d'aujourd'hui" }) }
            item { ElTag(text = "Transport Boumerdès", onClick = { queryText = "Lister les transports impayés à Boumerdès" }, color = WarmGold) }
            item { ElTag(text = "Convocation Absence", onClick = { queryText = "Rédiger une convocation absence" }, color = DangerRed) }
        }

        // Chat History
        ElCard(modifier = Modifier.fillMaxWidth().height(180.dp), gradient = false) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chatMessages.forEach { msg ->
                    Text(msg, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ElTextField(
                value = queryText,
                onValueChange = { queryText = it },
                label = "Posez votre question...",
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            ElIconButton(
                icon = Icons.Default.Send,
                onClick = {
                    if (queryText.isNotBlank()) {
                        chatMessages.add("Vous: $queryText")
                        val prompt = queryText
                        queryText = ""
                        val response = when {
                            prompt.contains("résumer", ignoreCase = true) || prompt.contains("encaissement", ignoreCase = true) ->
                                "IA: Aujourd'hui, 3 paiements ont été enregistrés pour un total de 55,000 DZD."
                            prompt.contains("transport", ignoreCase = true) ->
                                "IA: Il y a 4 élèves inscrits au transport Boumerdès ayant une tranche en retard."
                            else ->
                                "IA: Résultat généré par l'IA pour '$prompt': Opération analysée et prête."
                        }
                        chatMessages.add(response)
                    }
                },
                size = 48,
                background = PrimaryBlue,
                tint = Color.White,
            )
        }
    }
}

fun Long.formatDzd(): String = "%,.0f".format(this.toDouble())

