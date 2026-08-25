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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Permission
import com.example.domain.repository.PdfRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
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
 * FIX (dead buttons): `generate` previously showed a snackbar, delayed 2 s and
 * produced nothing. It now REALLY assembles the report from live Room data
 * via [PdfRepository.generateMacroReport] and emits the written PDF file so
 * the UI can share it (FileProvider + ACTION_SEND).
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val pdfRepository: PdfRepository,
) : ViewModel() {

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    private val _generating = MutableStateFlow<Set<String>>(emptySet())
    val generating: StateFlow<Set<String>> = _generating.asStateFlow()

    /** One-shot share request: the freshly generated report file. */
    private val _shareRequest = MutableStateFlow<File?>(null)
    val shareRequest: StateFlow<File?> = _shareRequest.asStateFlow()

    val canViewAuditLog: Boolean get() = sessionManager.current()?.can(Permission.VIEW_AUDIT_LOG) == true
    val canViewSalary: Boolean get() = sessionManager.current()?.can(Permission.VIEW_SALARY) == true

    fun generate(reportId: String, reportLabel: String) {
        if (reportId in _generating.value) return
        _generating.value = _generating.value + reportId
        viewModelScope.launch {
            _snackbar.value = "Génération en cours : $reportLabel"
            when (val r = pdfRepository.generateMacroReport(reportId)) {
                is com.example.core.Result.Ok -> {
                    _snackbar.value = "$reportLabel généré (${r.value.name})"
                    _shareRequest.value = r.value
                }
                is com.example.core.Result.Err -> {
                    _snackbar.value = r.error.userMessage.ifBlank { r.error.message }
                }
            }
            _generating.value = _generating.value - reportId
        }
    }

    /** Called by the UI once the share intent has been dispatched. */
    fun consumeShareRequest() { _shareRequest.value = null }

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
    val context = LocalContext.current
    val snackbar by viewModel.snackbar.collectAsState()
    val generating by viewModel.generating.collectAsState()
    val shareRequest by viewModel.shareRequest.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbar) {
        snackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    // Share the freshly generated report PDF (ACTION_SEND via FileProvider —
    // same mechanism as the payment-receipt share).
    LaunchedEffect(shareRequest) {
        shareRequest?.let { file ->
            runCatching {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension.replace('_', ' '))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Partager le rapport"))
            }
            viewModel.consumeShareRequest()
        }
    }

    val reports = listOf(
        ReportType("revenu-mensuel", "Revenu mensuel", "Synthèse des encaissements du mois par méthode et par catégorie.", "PDF", Icons.Default.Assessment),
        ReportType("creances-agees", "Créances âgées", "Ventilation des créances par tranche d'âge (0-30, 31-60, 61-90, 91-180, 180+).", "PDF", Icons.Default.WarningAmber),
        ReportType("effectifs-niveau", "Effectifs par niveau", "Répartition des élèves par niveau (Préscolaire/Primaire/CEM/Lycée) et par classe.", "PDF", Icons.Default.People),
        ReportType("depenses-categorie", "Dépenses par catégorie", "Synthèse des dépenses par catégorie avec statut d'approbation.", "PDF", Icons.Default.Receipt),
        ReportType("annuaire-personnel", "Annuaire du personnel", "Liste complète du personnel avec coordonnées et salaire (rôle requis).", "PDF", Icons.Default.People, requiresSalaryPermission = true),
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
                val isGenerating = report.id in generating
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
                            androidx.compose.material3.TextButton(
                                onClick = { viewModel.generate(report.id, report.title) },
                                enabled = !isGenerating,
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Text(if (isGenerating) " Génération…" else " Générer le PDF")
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
