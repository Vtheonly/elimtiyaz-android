package com.example.ui.features.dashboard

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Permission
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Reports ViewModel — handles report-generation requests.
 *
 * Per desktop spec §5.1: the global Reports tab lists only the macro reports
 * (revenue, debt, enrollment, audit, expenses). Entity-specific reports
 * (bulletins, account statements, payslips) live in their respective profile drawers.
 *
 * Generation is wired through [StorageRepository] for proof/upload flows;
 * for v1 the button shows a snackbar "Génération en cours…" and would invoke
 * an Edge Function in production.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    val canViewAuditLog: Boolean get() = sessionManager.current()?.can(Permission.VIEW_AUDIT_LOG) == true
    val canViewSalary: Boolean get() = sessionManager.current()?.can(Permission.VIEW_SALARY) == true

    fun generate(reportId: String, reportLabel: String) {
        viewModelScope.launch {
            // The report-generation Edge Function is not yet deployed.
            // Show a clear "coming soon" message instead of a fake progress
            // indicator so the user knows the feature is pending.
            _snackbar.value = "Bientôt disponible : $reportLabel sera généré depuis Supabase."
        }
    }

    fun consumeSnackbar() { _snackbar.value = null }
}

data class ReportType(
    val id: String,
    val title: String,
    val description: String,
    val format: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val requiresSalaryPermission: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    onNavigateToAuditLog: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val snackbar by viewModel.snackbar.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbar) {
        snackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    val reports = listOf(
        ReportType("revenu-mensuel", "Revenu mensuel", "Synthèse des encaissements par méthode, catégorie et transactions.", "XLSX", Icons.Default.Assessment),
        ReportType("creances-agees", "Créances âgées", "Ventilation des créances par tranche d'âge (0-30, 31-60, 61-90, 91-180, 180+).", "XLSX", Icons.Default.WarningAmber),
        ReportType("effectifs-niveau", "Effectifs par niveau", "Répartition des élèves par niveau (Primaire/CEM/Lycée) et par classe.", "XLSX", Icons.Default.People),
        ReportType("depenses-categorie", "Dépenses par catégorie", "Synthèse mensuelle des dépenses par catégorie.", "XLSX", Icons.Default.Receipt),
        ReportType("annuaire-personnel", "Annuaire du personnel", "Liste complète du personnel avec coordonnées et salaire (rôle requis).", "XLSX", Icons.Default.People, requiresSalaryPermission = true),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rapports") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Journal d'audit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Redirige vers le journal d'audit complet (SuperAdmin / FinancialOfficer).", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        androidx.compose.material3.TextButton(onClick = onNavigateToAuditLog) {
                            Text("Ouvrir le journal")
                        }
                    }
                }
            }

            items(reports) { report ->
                val allowed = !report.requiresSalaryPermission || viewModel.canViewSalary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(1.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row {
                            Icon(report.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(report.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(report.description, style = MaterialTheme.typography.bodySmall)
                                Text("Format: ${report.format}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (allowed) {
                            androidx.compose.material3.TextButton(onClick = { viewModel.generate(report.id, report.title) }) {
                                Text("Générer")
                            }
                        } else {
                            Text("Permission VIEW_SALARY requise.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
