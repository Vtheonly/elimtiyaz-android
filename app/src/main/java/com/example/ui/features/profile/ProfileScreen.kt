package com.example.ui.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Permission
import com.example.domain.model.AuditLog
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.AuditRepository
import com.example.session.SessionManager
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.card.ElGradientStatCard
import com.example.ui.designsystem.components.display.ElGradient
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.overlays.ElDialogShell
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.features.settings.roleLabel
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
    private val authRepository: AuthRepository,
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
            // T-460/issue-#3 F-01: the canonical sign-out path — authRepository
            // .signOut() deactivates this device's FCM tokens BEFORE revoking
            // the JWT (the AGENTS.md §3 FCM lifecycle contract); the old
            // setSession(null)-only bypass left a signed-out device receiving
            // push notifications. Mirrors MainScreen's ViewModel exactly.
            runCatching { authRepository.signOut() }
                .onFailure { /* sign-out proceeds locally even on RPC failure */ }
            sessionManager.setSession(null)
            onDone()
        }
    }
}

/**
 * T-460 pass G-a (issue #3 F-06): the raw-M3 Profile screen → the design
 * system. The session header uses the SAME ElGradientStatCard language as the
 * Personnel hub's SignOutScreen (the two surfaces are registered for a pass-J
 * consolidation decision — until then they at least share one visual
 * language); permissions render as ElTag chips on the DS progress bar; the
 * sign-out confirm dialog runs on ElDialogShell. The F-01 canonical sign-out
 * path (fixed in its own commit) is preserved verbatim.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val c = ElTheme.colors
    val session by viewModel.session.collectAsState()
    val recentActivity by viewModel.recentActivity.collectAsState()
    val permissionCount by viewModel.permissionCount.collectAsState()
    val sessionExpiresAt by viewModel.sessionExpiresAt.collectAsState()

    var showSignOutConfirm by remember { mutableStateOf(false) }

    ElScaffold(
        topBar = {
            ElTopBar(
                title = "Profil",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val s = session
                ElGradientStatCard(
                    title = "Session Utilisateur",
                    value = s?.displayName ?: "Utilisateur",
                    subtitle = s?.email ?: "",
                    gradient = ElGradient.BRAND,
                    icon = Icons.Default.Verified,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                val s = session
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = ElCardSize.STANDARD,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ElAvatar(
                                initials = s?.displayName?.take(2)?.uppercase(),
                                icon = null,
                                size = ElAvatarSize.L,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    s?.displayName ?: "Utilisateur",
                                    style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = c.textPrimary,
                                )
                                s?.email?.let { email ->
                                    Text(email, style = ElTheme.typography.bodySmall, color = c.textSecondary)
                                }
                                s?.role?.let { r ->
                                    ElTag(
                                        text = roleLabel(r),
                                        tone = ElTagTone.INFO,
                                        size = ElTagSize.MD,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        s?.tenantId?.let { InfoLabel("Tenant", it) }
                        s?.userId?.let { InfoLabel("User ID", it) }
                        sessionExpiresAt?.let { exp ->
                            val minutesLeft = ((exp - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = if (minutesLeft < 30) c.danger else c.textSecondary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    "Session expire dans : ${minutesLeft}min",
                                    style = ElTheme.typography.labelSmall,
                                    color = if (minutesLeft < 30) c.danger else c.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            item {
                val s = session
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = ElCardSize.STANDARD,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Permissions",
                                style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "$permissionCount / ${viewModel.permissionTotal}",
                                style = ElTheme.typography.labelMedium,
                                color = c.textSecondary,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        ElLinearProgress(
                            progress = if (viewModel.permissionTotal > 0) permissionCount.toFloat() / viewModel.permissionTotal else 0f,
                        )
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            s?.permissions?.take(12)?.forEach { p ->
                                ElTag(text = p.code, tone = ElTagTone.NEUTRAL, size = ElTagSize.SM)
                            }
                        }
                        if ((s?.permissions?.size ?: 0) > 12) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "… et ${(s?.permissions?.size ?: 0) - 12} de plus",
                                style = ElTheme.typography.labelSmall,
                                color = c.textSecondary,
                            )
                        }
                    }
                }
            }

            item {
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = ElCardSize.STANDARD,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "Gouvernance du mot de passe",
                            style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Spacer(Modifier.height(10.dp))
                        ElButton(
                            text = "Modifier mon mot de passe",
                            onClick = onChangePassword,
                            variant = ElButtonVariant.SECONDARY,
                            icon = Icons.Default.Password,
                            fullWidth = true,
                        )
                    }
                }
            }

            item {
                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = ElCardSize.STANDARD,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "Activité récente (10 dernières actions)",
                            style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Spacer(Modifier.height(8.dp))
                        if (recentActivity.isEmpty()) {
                            Text(
                                "Aucune activité.",
                                style = ElTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        } else {
                            recentActivity.forEach { entry ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        "${entry.action} • ${entry.entityType}/${entry.entityId.take(8)}",
                                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = c.textPrimary,
                                    )
                                    Text(
                                        entry.occurredAt,
                                        style = ElTheme.typography.labelSmall,
                                        color = c.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                ElButton(
                    text = "Se déconnecter",
                    onClick = { showSignOutConfirm = true },
                    variant = ElButtonVariant.DANGER,
                    icon = Icons.Default.Logout,
                    fullWidth = true,
                )
            }
        }
    }

    if (showSignOutConfirm) {
        ElDialogShell(onDismissRequest = { showSignOutConfirm = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Se déconnecter ?",
                    style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                )
                Text(
                    "Votre session sera terminée et vous reviendrez à l'écran de connexion.",
                    style = ElTheme.typography.bodyMedium,
                    color = c.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ElButton(
                        text = "Annuler",
                        onClick = { showSignOutConfirm = false },
                        variant = ElButtonVariant.GHOST,
                        modifier = Modifier.weight(1f),
                    )
                    ElButton(
                        text = "Se déconnecter",
                        onClick = {
                            showSignOutConfirm = false
                            viewModel.signOut(onSignOut)
                        },
                        variant = ElButtonVariant.DANGER,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLabel(label: String, value: String) {
    val c = ElTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label :", style = ElTheme.typography.labelSmall, color = c.textSecondary)
        Text(value, style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = c.textPrimary)
    }
}
