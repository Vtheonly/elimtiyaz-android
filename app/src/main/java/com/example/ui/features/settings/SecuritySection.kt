package com.example.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.ElCard
import com.example.ui.components.ElSectionHeader

@Composable
internal fun SecuritySection(
    onChangePassword: () -> Unit,
    onOpenAuditLog: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElSectionHeader(title = "Sécurité")
        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionRow(icon = Icons.Default.Lock, label = "Changer le mot de passe", onClick = onChangePassword)
                ActionRow(icon = Icons.Default.History, label = "Journal d'audit", onClick = onOpenAuditLog)
                ActionRow(icon = Icons.Default.Logout, label = "Se déconnecter", onClick = onSignOut, danger = true)
            }
        }
    }
}
