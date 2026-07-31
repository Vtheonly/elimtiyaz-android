package com.example.ui.features.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Général", "Sync", "Config", "Sécurité")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopStart) {
            when (selectedTab) {
                0 -> GeneralSettingsTab()
                1 -> SyncSettingsTab()
                2 -> ConfigurationTab()
                3 -> SecurityTab()
            }
        }
    }
}

@Composable
private fun GeneralSettingsTab() {
    Column {
        Text("Général", style = MaterialTheme.typography.titleMedium)
        Text("(Thème, langue, devise, fuseau horaire)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // TODO: implement theme picker + locale/timezone/currency selectors
    }
}

@Composable
private fun SyncSettingsTab() {
    Column {
        Text("Synchronisation", style = MaterialTheme.typography.titleMedium)
        Text("(Statut réseau, file d'attente, sync manuelle)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // TODO: implement sync status card + queue table + manual sync button
    }
}

@Composable
private fun ConfigurationTab() {
    Column {
        Text("Configuration", style = MaterialTheme.typography.titleMedium)
        Text("(URL Supabase, anon key, mode mock)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // TODO: implement Supabase connection config form
    }
}

@Composable
private fun SecurityTab() {
    Column {
        Text("Sécurité", style = MaterialTheme.typography.titleMedium)
        Text("(Changer le mot de passe, révoquer les sessions)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // TODO: implement change password modal trigger + session list
    }
}
