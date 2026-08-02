package com.example.ui.features.settings

import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import com.example.core.Session
import com.example.infrastructure.sync.OnlineDetector
import com.example.infrastructure.sync.SyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

@Composable
internal fun ChangePasswordModalSheet(onDismiss: () -> Unit) {
    com.example.ui.features.auth.ChangePasswordModal(onDismiss = onDismiss)
}
