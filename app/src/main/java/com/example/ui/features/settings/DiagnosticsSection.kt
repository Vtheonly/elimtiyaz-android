package com.example.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.infrastructure.sync.SyncService
import com.example.infrastructure.sync.SyncState
import com.example.ui.components.ElCard
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElSectionHeader
import com.example.ui.theme.PrimaryBlue

@Composable
internal fun DiagnosticsSection(
    online: Boolean,
    syncState: SyncState,
    appVersion: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElSectionHeader(title = "Diagnostics")
        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (online) Icons.Default.Cloud else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = if (online) PrimaryBlue else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (online) "En ligne" else "Hors ligne",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (online) PrimaryBlue else MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(4.dp))
                ElInfoRow(label = "Dernière sync", value = syncState.lastSyncAt?.take(19)?.replace("T", " ") ?: "—")
                ElInfoRow(label = "Entrées en attente", value = syncState.pendingCount.toString())
                ElInfoRow(label = "Version de l'app", value = appVersion)
                ElInfoRow(label = "Supabase URL", value = BuildConfig.SUPABASE_URL.take(30) + "…")
            }
        }
    }
}

/**
 * Single toggle row — icon + label + sublabel on the left, [Switch] on the right.
 * Uses Material 3's [Switch] with the primary color when checked so the
 * visual treatment matches the brand.
 */
@Composable
