package com.example.ui.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Session
import com.example.domain.model.AppNotification
import com.example.domain.model.DashboardKpi
import com.example.domain.repository.NotificationRepository
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LightBlue
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
    )

    private val _kpis = MutableStateFlow<DashboardKpi?>(defaultKpi)
    val kpis: StateFlow<DashboardKpi?> = _kpis.asStateFlow()

    private val sampleAlerts = listOf(
        AppNotification("N-1", "Alerte Dépense Tier-2", "Demande d'achat matériel informatique (45,000 DZD) en attente de validation par l'administration.", "expense", "EXP-004", false, "2026-07-31T09:30:00Z"),
        AppNotification("N-2", "Seuil 3+ Absences Atteint", "L'élève Yacine Belkacem (PRIM - CE1 B) a atteint 3 absences non justifiées.", "academic", "STU-003", false, "2026-07-31T08:15:00Z"),
        AppNotification("N-3", "Échéance Chèque de Banque", "Chèque BNA #883921 (150,000 DZD) à déposer pour compensation aujourd'hui.", "financial", "CHK-001", true, "2026-07-30T16:00:00Z"),
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

@OptIn(ExperimentalMaterial3Api::class)
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

    var showAiDrawer by remember { mutableStateOf(false) }
    var showReceiptModalItem by remember { mutableStateOf<LivePaymentFeedItem?>(null) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAiDrawer = true },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "Assistant IA Mobile")
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Title & Welcome
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Tableau de Bord Operationnel", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Bienvenue, ${session.displayName} (${session.role.code})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BadgedBox(
                    badge = {
                        if (alerts.any { !it.read }) {
                            Badge { Text(alerts.count { !it.read }.toString()) }
                        }
                    },
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = "Alertes", tint = PrimaryBlue)
                }
            }

            // 1. Executive KPI Carousel
            Text("Indicateurs Clés de Performance (KPIs)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    KpiCarouselCard(
                        title = "Revenu Mensuel",
                        value = "${(kpis?.monthlyRevenue ?: 12450000L).formatDzd()} DZD",
                        subtitle = "Objectif: 15,000,000 DZD",
                        icon = Icons.Default.TrendingUp,
                        accentColor = SuccessGreen,
                    )
                }
                item {
                    KpiCarouselCard(
                        title = "Créances Restantes (Q)",
                        value = "${(kpis?.outstandingDebt ?: 3200000L).formatDzd()} DZD",
                        subtitle = "Taux de recouvrement: 85.2%",
                        icon = Icons.Default.MoneyOff,
                        accentColor = DangerRed,
                    )
                }
                item {
                    KpiCarouselCard(
                        title = "Élèves Inscrits",
                        value = "${kpis?.totalStudents ?: 390}",
                        subtitle = "Taux de présence aujourd'hui: 96.5%",
                        icon = Icons.Default.Groups,
                        accentColor = PrimaryBlue,
                    )
                }
                item {
                    KpiCarouselCard(
                        title = "Demandes Dépenses",
                        value = "${kpis?.pendingExpenses ?: 3} en attente",
                        subtitle = "Tier 2 Validation Requise",
                        icon = Icons.Default.AccountBalance,
                        accentColor = WarmGold,
                    )
                }
            }

            // 2. AI Quick Actions Widget
            Card(
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryBlue)
                        Spacer(Modifier.width(8.dp))
                        Text("Assistant IA — Raccourcis Rapides", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = { showAiDrawer = true },
                            label = { Text("Résumé Encaisses", style = MaterialTheme.typography.labelSmall) },
                        )
                        AssistChip(
                            onClick = { onNavigateToDebtDashboard() },
                            label = { Text("Relance Boumerdès", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            // 3. Live Collection Feed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Flux des Encaissements en Direct", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                androidx.compose.material3.TextButton(onClick = onNavigateToCounterPayment) {
                    Text("+ Nouveau Paiement")
                }
            }

            SAMPLE_PAYMENT_FEED.forEach { item ->
                Card(
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth().clickable { showReceiptModalItem = item },
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (item.method == "Espèces") SuccessGreen else PrimaryBlue)
                                    .padding(10.dp),
                            ) {
                                Icon(
                                    if (item.method == "Espèces") Icons.Default.Payment else Icons.Default.CreditCard,
                                    contentDescription = null,
                                    tint = Color.White,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(item.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Parent: ${item.parentName} • Reçu: ${item.receiptBookCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${item.method} • ${item.timestamp}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${item.amountDzd.formatDzd()} DZD", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SuccessGreen)
                            Icon(Icons.Default.Receipt, contentDescription = "Voir Reçu PDF", tint = PrimaryBlue)
                        }
                    }
                }
            }

            // 4. Notification & Alert Center
            Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Avis & Notifications Push (FCM)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    alerts.forEach { alert ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                if (alert.category == "academic") Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (alert.category == "academic") DangerRed else WarmGold,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(alert.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(alert.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
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
                Text("Reçu de Paiement Récent (${receipt.receiptBookCode})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Élève: ${receipt.studentName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Parent: ${receipt.parentName}", style = MaterialTheme.typography.bodyMedium)
                        Text("Montant Encaissé: ${receipt.amountDzd.formatDzd()} DZD", style = MaterialTheme.typography.titleMedium, color = SuccessGreen, fontWeight = FontWeight.Bold)
                        Text("Mode de Règlement: ${receipt.method}", style = MaterialTheme.typography.bodyMedium)
                        Text("Date/Heure: ${receipt.timestamp}", style = MaterialTheme.typography.bodySmall)
                        Text("Carnet de Reçu Code: ${receipt.receiptBookCode}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(
                    onClick = { showReceiptModalItem = null },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Partager / Imprimer le Reçu PDF")
                }
            }
        }
    }

    // AI Assistant Bottom Sheet Drawer (Groq / OpenRouter Integration)
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

    Column(
        modifier = Modifier
            .padding(20.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryBlue)
            Spacer(Modifier.width(8.dp))
            Text("Assistant IA Mobile (Groq / OpenRouter)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        // Contextual Prompt Chips
        Text("Suggestions de Prompts:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                AssistChip(
                    onClick = { queryText = "Résumer les encaissements d'aujourd'hui" },
                    label = { Text("Résumé Encaissements") },
                )
            }
            item {
                AssistChip(
                    onClick = { queryText = "Lister les transports impayés à Boumerdès" },
                    label = { Text("Transport Boumerdès") },
                )
            }
            item {
                AssistChip(
                    onClick = { queryText = "Rédiger une convocation absence en français/arabe" },
                    label = { Text("Convocation Absence") },
                )
            }
        }

        // Chat History Box
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().height(180.dp),
        ) {
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
            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it },
                label = { Text("Posez votre question...") },
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    if (queryText.isNotBlank()) {
                        chatMessages.add("Vous: $queryText")
                        val prompt = queryText
                        queryText = ""
                        // Process mock response
                        val response = when {
                            prompt.contains("résumer", ignoreCase = true) || prompt.contains("encaissement", ignoreCase = true) ->
                                "IA: Aujourd'hui, 3 paiements ont été enregistrés pour un total de 55,000 DZD (Espèces: 30,000 DZD, Chèque: 25,000 DZD)."
                            prompt.contains("transport", ignoreCase = true) ->
                                "IA: Il y a 4 élèves inscrits au transport Boumerdès ayant une tranche en retard ($Q > 0)."
                            else ->
                                "IA: Résultat généré par l'IA pour '$prompt': Opération analysée et prête."
                        }
                        chatMessages.add(response)
                    }
                },
                modifier = Modifier.background(PrimaryBlue, CircleShape),
            ) {
                Icon(Icons.Default.Send, contentDescription = "Envoyer", tint = Color.White)
            }
        }
    }
}

@Composable
private fun KpiCarouselCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
) {
    Card(
        elevation = CardDefaults.cardElevation(3.dp),
        modifier = Modifier.width(220.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Icon(icon, contentDescription = null, tint = accentColor)
            }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accentColor)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun Long.formatDzd(): String = "%,.0f".format(this.toDouble())

