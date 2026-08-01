package com.example.ui.features.settings

import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.core.Role
import com.example.core.Session
import com.example.domain.repository.AuthRepository
import com.example.infrastructure.sync.OnlineDetector
import com.example.infrastructure.sync.SyncService
import com.example.session.SessionManager
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElDropdown
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElScaffold
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTopBar
import com.example.ui.theme.PrimaryBlue
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings screen — full replacement of the placeholder cards.
 *
 * Sections:
 *   1. **Profile**        — avatar, display name, email, role badge (from [SessionManager]).
 *   2. **Préférences**    — dark mode, notifications, force-offline, language.
 *   3. **Sécurité**       — change password, view audit log, sign out.
 *   4. **Synchronisation**— Sync now button + pending count + last sync.
 *   5. **Diagnostics**    — online status, last sync, pending count, app version.
 *
 * Toggles are persisted immediately via [DataStore]<[Preferences]> so the
 * next app launch restores the user's choices.
 *
 * @param onBack              Pops the settings screen.
 * @param onChangePassword    Opens the change-password modal.
 * @param onOpenAuditLog      Navigates to the audit log screen.
 * @param onSignOut           Called after the session is cleared.
 */
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

/**
 * Profile card — shows the signed-in user's avatar, name, email, and role
 * badge. Reads from the active [Session].
 */
@Composable
private fun ProfileCard(session: Session?) {
    if (session == null) return
    ElCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ElAvatar(
                initials = session.displayName.take(2).uppercase(),
                size = 56,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = session.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ElTag(
                    text = roleLabel(session.role),
                    color = roleColor(session.role),
                )
            }
        }
    }
}

/**
 * Preferences section — toggles for dark mode, notifications, force-offline,
 * plus a language dropdown. Each toggle persists immediately via [DataStore].
 */
@Composable
private fun PreferencesSection(
    settings: SettingsState,
    onDarkMode: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onForceOffline: (Boolean) -> Unit,
    onLanguage: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElSectionHeader(title = "Préférences")
        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow(
                    icon = Icons.Default.DarkMode,
                    label = "Mode sombre",
                    sublabel = "Thème foncé pour les écrans",
                    checked = settings.darkMode,
                    onCheckedChange = onDarkMode,
                )
                ToggleRow(
                    icon = Icons.Default.Notifications,
                    label = "Notifications",
                    sublabel = "Recevoir les alertes push",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = onNotifications,
                )
                ToggleRow(
                    icon = Icons.Default.CloudOff,
                    label = "Mode hors-ligne",
                    sublabel = "Forcer la désactivation du réseau",
                    checked = settings.forceOffline,
                    onCheckedChange = onForceOffline,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        ElDropdown(
                            label = "Langue",
                            selectedValue = languageLabel(settings.language),
                            options = listOf("Français", "العربية", "English"),
                            onSelected = { code -> onLanguage(languageCodeFromLabel(code)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Security section — change-password, view audit log, sign out.
 */
@Composable
private fun SecuritySection(
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

/**
 * Sync section — Sync now button + live pending count + last sync time.
 */
@Composable
private fun SyncSection(
    syncState: SyncService.SyncState,
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

/**
 * Diagnostics section — read-only runtime info: online status, last sync,
 * pending count, app version.
 */
@Composable
private fun DiagnosticsSection(
    online: Boolean,
    syncState: SyncService.SyncState,
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
private fun ToggleRow(
    icon: ImageVector,
    label: String,
    sublabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = PrimaryBlue,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

/**
 * Tappable row used in the security section — icon + label, with optional
 * danger styling for destructive actions (sign out).
 */
@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ElButton(
        text = label,
        onClick = onClick,
        icon = icon,
        style = if (danger) ElButtonStyle.Danger else ElButtonStyle.Secondary,
        fullWidth = true,
    )
}

/** Human-readable label for a [Role]. */
private fun roleLabel(role: Role): String = when (role) {
    Role.SUPER_ADMIN -> "Super Admin"
    Role.FINANCIAL_OFFICER -> "Agent financier"
    Role.TEACHER -> "Enseignant"
    Role.SUPPORT_STAFF -> "Support"
    Role.MANAGER -> "Manager"
    Role.BUYER -> "Acheteur"
    Role.DRIVER -> "Chauffeur"
    Role.WAREHOUSE_WORKER -> "Magasinier"
    Role.WORKER -> "Employé"
    Role.PARENT -> "Parent"
    Role.STUDENT -> "Élève"
}

/** Brand color for a [Role] badge. */
private fun roleColor(role: Role): Color = when (role) {
    Role.SUPER_ADMIN, Role.MANAGER -> PrimaryBlue
    Role.FINANCIAL_OFFICER -> Color(0xFF2E7D32)
    Role.TEACHER -> Color(0xFF6A1B9A)
    Role.SUPPORT_STAFF -> Color(0xFFEF6C00)
    else -> Color(0xFF546E7A)
}

/** ISO 639-1 → display label. */
private fun languageLabel(code: String): String = when (code) {
    "ar" -> "العربية"
    "en" -> "English"
    else -> "Français"
}

/** Display label → ISO 639-1 code. */
private fun languageCodeFromLabel(label: String): String = when (label) {
    "العربية" -> "ar"
    "English" -> "en"
    else -> "fr"
}

/**
 * Thin wrapper around the [ChangePasswordModal] composable so the settings
 * screen can display it inline. Kept as a separate function for clarity
 * and to keep the [SettingsScreen] body readable.
 */
@Composable
private fun ChangePasswordModalSheet(onDismiss: () -> Unit) {
    com.example.ui.features.auth.ChangePasswordModal(onDismiss = onDismiss)
}

/**
 * ViewModel for [SettingsScreen].
 *
 * Exposes the active [Session], the persisted [SettingsState], the live
 * [SyncService.SyncState], and the [OnlineDetector]'s online flag. Each
 * toggle mutates the [DataStore] immediately so the next app launch
 * restores the user's choices.
 *
 * @param sessionManager   Source of the active session.
 * @param authRepository   Used for sign-out.
 * @param syncService      Used for sync-now + sync state observation.
 * @param onlineDetector   Used for the diagnostics online indicator.
 * @param dataStore        Backing store for the persisted toggles.
 * @param context          Used to read the package version.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val syncService: SyncService,
    private val onlineDetector: OnlineDetector,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Active session (null when signed out). */
    val session: StateFlow<Session?> = sessionManager.state

    /** Persisted user preferences — lazily started, with a sensible default. */
    val settings: StateFlow<SettingsState> = dataStore.data
        .map { p ->
            SettingsState(
                darkMode = p[DARK_MODE_KEY] ?: false,
                notificationsEnabled = p[NOTIFICATIONS_KEY] ?: true,
                forceOffline = p[FORCE_OFFLINE_KEY] ?: false,
                language = p[LANGUAGE_KEY] ?: "fr",
            )
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, SettingsState())

    /** Live sync state from [SyncService.observeSyncState]. */
    val syncState: StateFlow<SyncService.SyncState> = syncService.observeSyncState()
        .stateIn(viewModelScope, SharingStarted.Lazily, SyncService.SyncState(false, null, 0, null))

    /** Combined online flag from [OnlineDetector.observeOnline]. */
    val online: StateFlow<Boolean> = onlineDetector.observeOnline()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    /** Persist the dark-mode toggle. */
    fun setDarkMode(enabled: Boolean) = editAsync { it[DARK_MODE_KEY] = enabled }

    /** Persist the notifications-enabled toggle. */
    fun setNotifications(enabled: Boolean) = editAsync { it[NOTIFICATIONS_KEY] = enabled }

    /** Persist the force-offline toggle. */
    fun setForceOffline(enabled: Boolean) = editAsync { it[FORCE_OFFLINE_KEY] = enabled }

    /** Persist the language selection (ISO 639-1 code). */
    fun setLanguage(code: String) = editAsync { it[LANGUAGE_KEY] = code }

    /** Trigger an immediate one-shot sync (NOT via WorkManager). */
    fun syncNow() = syncService.syncNow()

    /**
     * Sign out — clears the auth session and the local [SessionManager]
     * state, then invokes [onComplete] so the UI can navigate to login.
     */
    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            sessionManager.setSession(null)
            onComplete()
        }
    }

    /** Read the package's versionName (e.g. "2.0.0"). */
    fun appVersion(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "unknown"
    }.getOrDefault("unknown")

    private fun editAsync(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        viewModelScope.launch {
            runCatching { dataStore.edit(block) }
        }
    }

    private companion object {
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")
        val FORCE_OFFLINE_KEY = booleanPreferencesKey("force_offline")
        val LANGUAGE_KEY = stringPreferencesKey("language")
    }
}

/**
 * Persisted settings state — the four toggles + language code.
 *
 * @property darkMode             True when the user has explicitly chosen dark mode.
 * @property notificationsEnabled True when push notifications are allowed.
 * @property forceOffline         True when the user has forced offline mode (no sync).
 * @property language             ISO 639-1 language code (`fr` / `ar` / `en`).
 */
data class SettingsState(
    val darkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val forceOffline: Boolean = false,
    val language: String = "fr",
)
