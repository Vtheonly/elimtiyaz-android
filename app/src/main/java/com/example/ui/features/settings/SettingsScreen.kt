package com.example.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.ElCard
import com.example.ui.components.ElScaffold
import com.example.ui.components.ElScrollableTabRow
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTopBar

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Général", "Sync", "Config", "Sécurité")

    ElScaffold(
        topBar = { ElTopBar(title = "Paramètres", onBack = onBack) },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ElScrollableTabRow(
                tabs = tabs,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
            )
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (selectedTab) {
                    0 -> GeneralSettingsTab()
                    1 -> SyncSettingsTab()
                    2 -> ConfigurationTab()
                    3 -> SecurityTab()
                }
            }
        }
    }
}

@Composable
private fun GeneralSettingsTab() {
    ElCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ElSectionHeader(title = "Général")
            Text("Thème, langue, devise, fuseau horaire", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SyncSettingsTab() {
    ElCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ElSectionHeader(title = "Synchronisation")
            Text("Statut réseau, file d'attente, sync manuelle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConfigurationTab() {
    ElCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ElSectionHeader(title = "Configuration")
            Text("URL Supabase, anon key, mode mock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SecurityTab() {
    ElCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ElSectionHeader(title = "Sécurité")
            Text("Changer le mot de passe, révoquer les sessions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}