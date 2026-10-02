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
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.theme.ElTheme

@Composable
internal fun SecuritySection(
    onChangePassword: () -> Unit,
    onOpenAuditLog: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md)) {
        ElSectionHeader(title = "Sécurité")
        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(ElTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md)) {
                ActionRow(icon = Icons.Default.Lock, label = "Changer le mot de passe", onClick = onChangePassword)
                ActionRow(icon = Icons.Default.History, label = "Journal d'audit", onClick = onOpenAuditLog)
                ActionRow(icon = Icons.Default.Logout, label = "Se déconnecter", onClick = onSignOut, danger = true)
            }
        }
    }
}
