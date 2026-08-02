package com.example.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.infrastructure.sync.SyncService
import com.example.infrastructure.sync.SyncState
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElSectionHeader

@Composable
internal fun SyncSection(
    syncState: SyncState,
    onSyncNow: () -> Unit,
) {
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
                    enabled = !syncState.isRunning,
                )
            }
        }
    }
}
