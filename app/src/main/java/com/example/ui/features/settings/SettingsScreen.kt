package com.example.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.ui.components.ElScaffold
import com.example.ui.components.ElTopBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangePassword: () -> Unit = {},
    onOpenAuditLog: () -> Unit = {},
    onSignOut: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val online by viewModel.online.collectAsState()
    val dbConfigured by viewModel.supabaseConfigured.collectAsState()
    var showChangePassword by remember { mutableStateOf(false) }

    if (showChangePassword) {
        ChangePasswordModalSheet(onDismiss = { showChangePassword = false })
    }

    ElScaffold(
        topBar = { ElTopBar(title = "Paramètres", onBack = onBack) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileCard(session = session)

            PreferencesSection(
                settings = settings,
                onDarkMode = viewModel::setDarkMode,
                onNotifications = viewModel::setNotifications,
                onForceOffline = viewModel::setForceOffline,
                onLanguage = viewModel::setLanguage,
            )

            SecuritySection(
                onChangePassword = { showChangePassword = true; onChangePassword() },
                onOpenAuditLog = onOpenAuditLog,
                onSignOut = { viewModel.signOut(onSignOut) },
            )

            SyncSection(
                syncState = syncState,
                onSyncNow = { viewModel.syncNow() },
                // FIX (out of context): DB connection configuration moved from
                // the student roster into Settings.
                dbConfigured = dbConfigured,
                savedUrl = viewModel.getSavedSupabaseUrl(),
                savedKey = viewModel.getSavedSupabaseKey(),
                onSaveDbConfig = { url, key -> viewModel.saveSupabaseConfig(url, key) },
            )

            DiagnosticsSection(
                online = online,
                syncState = syncState,
                appVersion = viewModel.appVersion(),
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}
