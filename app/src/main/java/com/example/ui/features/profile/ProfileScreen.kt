package com.example.ui.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Permission
import com.example.domain.model.AuditLog
import com.example.domain.repository.AuditRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Profile ViewModel — restores the pre-redesign `ProfileViewModel`.
 *
 * - Loads current session + recent activity (10 most-recent audit entries by this user).
 * - Computes permission progress (count / total).
 * - Computes session expiry countdown.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auditRepository: AuditRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val session = sessionManager.state

    val recentActivity: StateFlow<List<AuditLog>> = auditRepository.observe(100)
        .map { entries ->
            val uid = sessionManager.currentUserId()
            entries.filter { it.actorId == uid }.sortedByDescending { it.occurredAt }.take(10)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val permissionCount: StateFlow<Int> = session.map { s -> s?.permissions?.size ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val permissionTotal: Int get() = Permission.entries.size

    val sessionExpiresAt: StateFlow<Long?> = session.map { s -> s?.expiresAt }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            sessionManager.setSession(null)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsState()
    val recentActivity by viewModel.recentActivity.collectAsState()
    val permissionCount by viewModel.permissionCount.collectAsState()
    val sessionExpiresAt by viewModel.sessionExpiresAt.collectAsState()

    var showSignOutConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val s = session
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = s?.displayName?.take(2)?.uppercase() ?: "?",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s?.displayName ?: "Utilisateur", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(s?.email ?: "", style = MaterialTheme.typography.bodySmall)
                                s?.role?.let { r ->
                                    Text("Rôle: ${r.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        s?.tenantId?.let { Text("Tenant: $it", style = MaterialTheme.typography.labelSmall) }
                        s?.userId?.let { Text("User ID: $it", style = MaterialTheme.typography.labelSmall) }
                        sessionExpiresAt?.let { exp ->
                            val minutesLeft = ((exp - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
                            Text("Session expire dans: ${minutesLeft}min", style = MaterialTheme.typography.labelSmall, color = if (minutesLeft < 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(8.dp))
                            Text("Permissions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("$permissionCount / ${viewModel.permissionTotal}", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (viewModel.permissionTotal > 0) permissionCount.toFloat() / viewModel.permissionTotal else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        s?.permissions?.take(12)?.forEach { p ->
                            Text("• ${p.code}", style = MaterialTheme.typography.labelSmall)
                        }
                        if ((s?.permissions?.size ?: 0) > 12) {
                            Text("… et ${(s?.permissions?.size ?: 0) - 12} de plus", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Gouvernance du mot de passe", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onChangePassword) {
                            Icon(Icons.Default.Password, contentDescription = null)
                            Spacer(Modifier.size(4.dp))
                            Text("Modifier mon mot de passe")
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Activité récente (10 dernières actions)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        if (recentActivity.isEmpty()) {
                            Text("Aucune activité.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        } else {
                            recentActivity.forEach { entry ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text("${entry.action} • ${entry.entityType}/${entry.entityId.take(8)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(entry.occurredAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }

            item {
                TextButton(
                    onClick = { showSignOutConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.size(4.dp))
                    Text("Se déconnecter", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Se déconnecter ?") },
            text = { Text("Votre session sera terminée et vous reviendrez à l'écran de connexion.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    viewModel.signOut(onSignOut)
                }) { Text("Se déconnecter") }
            },
            dismissButton = { TextButton(onClick = { showSignOutConfirm = false }) { Text("Annuler") } },
        )
    }
}
