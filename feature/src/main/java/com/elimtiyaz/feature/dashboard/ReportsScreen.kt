package com.elimtiyaz.feature.dashboard

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.ElImtiyazCard

/**
 * Reports catalog — a fixed list of report cards the staff can browse. Per
 * master plan §13, XLSX/CSV/PDF *generation* is desktop-only: this screen
 * therefore exposes a "Partager" action that fires an `ACTION_SEND` intent
 * with the report's metadata (title + description + timestamp), letting the
 * user pick a share target (mail, WhatsApp, Drive…).
 *
 * Tapping a card opens a stub preview dialog with the report's metadata —
 * the real PDF rendering lives on the Desktop Electron terminal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(nav: NavController) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<ReportItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rapports") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = ElimtiyazSpacing.x4,
                vertical = ElimtiyazSpacing.x4,
            ),
            verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
        ) {
            items(ReportCatalog) { item ->
                ReportCard(
                    item = item,
                    onClick = { preview = item },
                    onShare = { shareReport(context, item) },
                )
            }
        }
    }

    preview?.let { item ->
        ReportPreviewDialog(item = item, onDismiss = { preview = null })
    }
}

/** Report card — leading icon chip + title + description + share button. */
@Composable
private fun ReportCard(
    item: ReportItem,
    onClick: () -> Unit,
    onShare: () -> Unit,
) {
    ElImtiyazCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ElimtiyazSpacing.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(item.tone.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.tone,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(ElimtiyazSpacing.x4))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(ElimtiyazSpacing.x1))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(ElimtiyazSpacing.x3))
            IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = "Partager")
            }
        }
    }
}

/** Stub preview dialog showing the report metadata. */
@Composable
private fun ReportPreviewDialog(item: ReportItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(ElimtiyazSpacing.x3))
                Text(
                    text = "Type: ${item.format}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Généré le ${Formatters.dateTime(Formatters.nowIso())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(ElimtiyazSpacing.x2))
                Text(
                    text = "La génération PDF complète est disponible sur le terminal de bureau.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

/**
 * Fire an `ACTION_SEND` intent with the report metadata. Per master plan §13,
 * on-device PDF generation is prohibited — mobile can only share pre-rendered
 * PDFs / metadata.
 */
private fun shareReport(context: android.content.Context, item: ReportItem) {
    val text = buildString {
        appendLine("Rapport: ${item.title}")
        appendLine(item.description)
        appendLine("Format: ${item.format}")
        appendLine("Généré le ${Formatters.dateTime(Formatters.nowIso())}")
        appendLine("— El-Imtiyaz")
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Rapport El-Imtiyaz — ${item.title}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Partager le rapport"))
}

/** A single report catalog entry. */
data class ReportItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tone: androidx.compose.ui.graphics.Color,
    val format: String = "PDF",
)

/** The fixed catalog of reports exposed to staff on mobile. */
private val ReportCatalog: List<ReportItem> = listOf(
    ReportItem(
        title = "Revenu mensuel",
        description = "Synthèse des encaissements par catégorie et par tranche sur les 12 derniers mois.",
        icon = Icons.Outlined.BarChart,
        tone = ElimtiyazColors.PrimaryBlue,
    ),
    ReportItem(
        title = "Créances par tranche d'âge",
        description = "Échéancier des impayés ventilé par buckets 0–30j, 31–60j, 61–90j, 91–180j, 180+j.",
        icon = Icons.Outlined.Payments,
        tone = ElimtiyazColors.DangerRed,
    ),
    ReportItem(
        title = "Effectifs par niveau",
        description = "Répartition des élèves par niveau (Primaire / CEM / Lycée) et par classe.",
        icon = Icons.Outlined.School,
        tone = ElimtiyazColors.LightBlue,
    ),
    ReportItem(
        title = "Relevé enseignant",
        description = "Heures de cours déclarées par enseignant et par semaine, avec total mensuel.",
        icon = Icons.Outlined.Badge,
        tone = ElimtiyazColors.SuccessGreen,
    ),
    ReportItem(
        title = "Journal d'audit",
        description = "Flux horodaté des actions mutantes (création, modification, suppression) par utilisateur.",
        icon = Icons.Outlined.VerifiedUser,
        tone = ElimtiyazColors.SlateGray,
    ),
    ReportItem(
        title = "Paiements du jour",
        description = "Liste des encaissements de la journée avec méthode, catégorie et référence reçue.",
        icon = Icons.Outlined.Receipt,
        tone = ElimtiyazColors.WarmGold,
    ),
    ReportItem(
        title = "Dépenses par catégorie",
        description = "Récapitulatif des dépenses par catégorie (Salaires, Fournitures, Maintenance, Transport…).",
        icon = Icons.Outlined.Assessment,
        tone = ElimtiyazColors.WarningGold,
    ),
    ReportItem(
        title = "Annuaire du personnel",
        description = "Liste du personnel actif avec catégorie, contact et date d'embauche.",
        icon = Icons.Outlined.Group,
        tone = ElimtiyazColors.MutedBrown,
    ),
    ReportItem(
        title = "Relevé de notes",
        description = "Bulletin de notes par classe et par matière, avec moyenne et rang.",
        icon = Icons.Outlined.Description,
        tone = ElimtiyazColors.DeepBlue,
    ),
    ReportItem(
        title = "Bulletins trimestriels",
        description = "Génération des bulletins par élève avec moyenne générale, rang et décision de conseil.",
        icon = Icons.Outlined.Article,
        tone = ElimtiyazColors.PrimaryBlue,
    ),
)
