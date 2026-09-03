package com.example.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.infrastructure.sync.SyncService
import com.example.infrastructure.sync.SyncState
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElInfoRow
import com.example.ui.designsystem.components.display.ElSectionHeader

@Composable
internal fun SyncSection(
    syncState: SyncState,
    onSyncNow: () -> Unit,
    // FIX (out of context): DB connection configuration lives HERE now —
    // it was previously buried inside the student roster screen.
    dbConfigured: Boolean = false,
    savedUrl: String = "",
    savedKey: String = "",
    onSaveDbConfig: (String, String) -> Unit = { _, _ -> },
) {
    var showConfigDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElSectionHeader(title = "Synchronisation")
        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ElInfoRow(
                    label = "État",
                    value = if (syncState.isRunning) "Synchronisation en cours…" else "Prêt",
                )
                ElInfoRow(
                    label = "File d'attente",
                    value = "${syncState.pendingCount} entrée(s)",
                )
                ElInfoRow(
                    label = "Dernière sync",
                    value = syncState.lastSyncAt?.take(19)?.replace("T", " ") ?: "—",
                )
                ElInfoRow(
                    label = "Base de données",
                    value = if (dbConfigured) "Connectée" else "Non configurée (mode local)",
                )
                syncState.lastError?.let {
                    Text(
                        text = "Erreur: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(4.dp))
                ElButton(
                    text = if (syncState.isRunning) "…" else "Synchroniser maintenant",
                    onClick = onSyncNow,
                    icon = Icons.Default.Sync,
                    fullWidth = true,
                    enabled = !syncState.isRunning && dbConfigured,
                )
                ElButton(
                    text = if (dbConfigured) "Modifier la connexion" else "Connecter la base de données",
                    onClick = { showConfigDialog = true },
                    icon = Icons.Default.Settings,
                    variant = ElButtonVariant.SECONDARY,
                    fullWidth = true,
                )
            }
        }
    }

    if (showConfigDialog) {
        SupabaseConfigDialog(
            currentUrl = savedUrl,
            currentKey = savedKey,
            onDismiss = { showConfigDialog = false },
            onSave = { url, key ->
                onSaveDbConfig(url, key)
                showConfigDialog = false
            },
        )
    }
}
