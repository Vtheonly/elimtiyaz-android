package com.elimtiyaz.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.NoAccounts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.OfflineBanner
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone

/**
 * Settings screen — exposes appearance, language, notifications, sync, security,
 * about and data preferences. Backed by [SettingsViewModel].
 *
 * Route: `Route.Settings`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    nav: NavController,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    // Surface transient VM messages
    LaunchedEffect(state.snackbar) {
        val msg = state.snackbar
        if (msg != null) {
            snackbarHost.showSnackbar(msg)
            vm.consumeSnackbar()
        }
    }

    var showPasswordDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { inner ->
        if (state.isLoading) {
            LoadingState(modifier = Modifier.padding(inner), message = "Chargement des paramètres…")
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(
                horizontal = ElimtiyazSpacing.x4,
                vertical = ElimtiyazSpacing.x3,
            ),
            verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
        ) {
            if (state.isOffline) item { OfflineBanner(pendingCount = state.pendingWrites) }

            item { AppearanceSection(state, vm) }
            item { LanguageSection(state, vm) }
            item { NotificationsSection(state, vm) }
            item { SyncSection(state, vm) }
            item { SecuritySection(state, vm, onChangePassword = { showPasswordDialog = true }) }
            item { AboutSection() }
            item { DataSection(state, vm) }
            item { LockedFeaturesCard() }
            item { Spacer(Modifier.height(ElimtiyazSpacing.x4)) }
        }
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { _, _ ->
                showPasswordDialog = false
                // Stub — full flow requires AuthRepository.updatePassword(...)
                // which is not yet in the contract.
                vm.notifyMessage("Le changement de mot de passe sera disponible prochainement.")
            },
        )
    }
}

/* ------------------------------------------------------------------ *
 * Sections
 * ------------------------------------------------------------------ */

/** Section header — small uppercase label between groups of content. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = ElimtiyazSpacing.x2, bottom = ElimtiyazSpacing.x1),
    )
}

/**
 * Appearance section — theme mode (System/Light/Dark) and dynamic-color toggle.
 *
 * Note: Compose's `isSystemInDarkTheme()` won't auto-react to a user's choice
 * here without a CompositionLocal override at the app root (out of scope for
 * this task). The chosen mode is persisted via [SettingsRepository.setThemeMode]
 * and applied on the next app start.
 */
@Composable
private fun AppearanceSection(state: SettingsUiState, vm: SettingsViewModel) {
    SectionTitle("Apparence")
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Text(
                "Thème",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.values().forEach { mode ->
                    val selected = state.themeMode == mode.key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { vm.setThemeMode(mode) },
                            )
                            .padding(vertical = ElimtiyazSpacing.x2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(ElimtiyazSpacing.x3))
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = ElimtiyazSpacing.x3))

            // Dynamic colors (Material You) — Android 12+ only
            val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Couleurs dynamiques",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        if (supportsDynamic) "Material You (Android 12+)"
                        else "Nécessite Android 12 ou supérieur",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.dynamicColors && supportsDynamic,
                    onCheckedChange = vm::setDynamicColors,
                    enabled = supportsDynamic,
                )
            }
        }
    }
}

/** Language section — FR / AR radio buttons. EN is shown disabled as "soon". */
@Composable
private fun LanguageSection(state: SettingsUiState, vm: SettingsViewModel) {
    SectionTitle("Langue")
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Column(modifier = Modifier.selectableGroup()) {
                AppLocale.values().forEach { locale ->
                    val selected = state.locale == locale.key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                enabled = locale.available,
                                role = Role.RadioButton,
                                onClick = { vm.setLocale(locale) },
                            )
                            .padding(vertical = ElimtiyazSpacing.x2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null, enabled = locale.available)
                        Spacer(Modifier.width(ElimtiyazSpacing.x3))
                        Column {
                            Text(
                                locale.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (locale.available)
                                    MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!locale.available) {
                                Text(
                                    "Prochainement",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(
                "L'anglais sera disponible prochainement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Notifications section — master toggle + per-category switches. */
@Composable
private fun NotificationsSection(state: SettingsUiState, vm: SettingsViewModel) {
    SectionTitle("Notifications")
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Activez ou désactivez toutes les notifications",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.masterNotifications,
                    onCheckedChange = vm::setMasterNotifications,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = ElimtiyazSpacing.x3))

            NotificationCategory.values().forEach { cat ->
                val checked = state.notificationsByCategory[cat.key] ?: true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ElimtiyazSpacing.x1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        cat.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = checked && state.masterNotifications,
                        onCheckedChange = { vm.setNotificationCategory(cat, it) },
                        enabled = state.masterNotifications,
                    )
                }
            }
        }
    }
}

/**
 * Synchronization section — last sync label, pending writes count, "sync now"
 * action and (conditionally) the demo-mode toggle.
 */
@Composable
private fun SyncSection(state: SettingsUiState, vm: SettingsViewModel) {
    SectionTitle("Synchronisation")
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudSync, contentDescription = null)
                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.isOffline) "Hors ligne" else "En ligne",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Dernière sync: ${state.lastSyncLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(
                    label = if (state.isOffline) "Hors ligne" else "Synchronisé",
                    tone = if (state.isOffline) StatusTone.Warning else StatusTone.Success,
                )
            }

            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(
                "Écritures en attente: ${state.pendingWrites}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Button(
                onClick = vm::syncNow,
                enabled = !state.isSyncing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.isSyncing) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(ElimtiyazSpacing.x2))
                    Text("Synchronisation…")
                } else {
                    Text("Synchroniser maintenant")
                }
            }

            // Demo-mode toggle — only shown when the data layer reported mock mode
            if (state.showMockToggle) {
                Spacer(Modifier.height(ElimtiyazSpacing.x3))
                HorizontalDivider()
                Spacer(Modifier.height(ElimtiyazSpacing.x3))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Mode démo",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Utilise les données fictives. Désactivé en production.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.isMockMode,
                        onCheckedChange = vm::setMockMode,
                    )
                }
            }
        }
    }
}

/**
 * Security section — change password (dialog), biometric toggle, session-expiry
 * info row.
 */
@Composable
private fun SecuritySection(
    state: SettingsUiState,
    vm: SettingsViewModel,
    onChangePassword: () -> Unit,
) {
    SectionTitle("Sécurité")
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = ElimtiyazSpacing.x1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null)
                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                Text(
                    "Changer le mot de passe",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onChangePassword) { Text("Modifier") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = ElimtiyazSpacing.x2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Authentification biométrique",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Déverrouillage par empreinte ou reconnaissance faciale",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.biometricEnabled,
                    onCheckedChange = vm::setBiometricEnabled,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = ElimtiyazSpacing.x2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.LockClock, contentDescription = null)
                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Session",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    val mins = state.sessionExpiresInMinutes
                    Text(
                        if (mins == null) "Aucune session active"
                        else "Expire dans: $mins min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** About section — version, build, terms, privacy, report-a-problem. */
@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("1.0.0")
    }
    val buildInfo = remember {
        "Build ${Build.MANUFACTURER}/${Build.MODEL} • API ${Build.VERSION.SDK_INT}"
    }

    SectionTitle("À propos")
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null)
                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                Column(modifier = Modifier.weight(1f)) {
                    Text("El-Imtiyaz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Version $versionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(
                buildInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = ElimtiyazSpacing.x3))

            AboutLinkRow(label = "Conditions d'utilisation", onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://elimtiyaz.dz/terms"))
                intent.runCatching { context.startActivity(intent) }
            })
            AboutLinkRow(label = "Politique de confidentialité", onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://elimtiyaz.dz/privacy"))
                intent.runCatching { context.startActivity(intent) }
            })
            AboutLinkRow(
                label = "Signaler un problème",
                icon = Icons.Outlined.BugReport,
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:support@elimtiyaz.dz")
                        putExtra(Intent.EXTRA_SUBJECT, "Signalement de problème — El-Imtiyaz Android")
                    }
                    intent.runCatching { context.startActivity(intent) }
                },
            )
        }
    }
}

/** Single "link"-style row in the About card. */
@Composable
private fun AboutLinkRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.Mail,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(ElimtiyazSpacing.x2))
        Text(label, modifier = Modifier.weight(1f))
    }
}

/**
 * Data section — displays the Mobile Backup Prohibition notice (master plan
 * §13.05) and the cache size + "clear cache" action.
 */
@Composable
private fun DataSection(state: SettingsUiState, vm: SettingsViewModel) {
    SectionTitle("Données")
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NoAccounts, contentDescription = null)
                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                Text(
                    "Sauvegarde",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(ElimtiyazSpacing.x3),
            ) {
                Text(
                    "La sauvegarde est gérée par le terminal de bureau. " +
                        "Aucune sauvegarde locale n'est disponible sur mobile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = ElimtiyazSpacing.x3))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Cache",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Taille: ${state.cacheSizeLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = vm::clearCache) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(ElimtiyazSpacing.x2))
                    Text("Vider le cache")
                }
            }
        }
    }
}

/**
 * "Change password" dialog — current + new + confirm fields. Stub: confirm just
 * dismisses the dialog; a real implementation would call
 * `AuthRepository.updatePassword(...)` (not yet exposed by the contract).
 */
@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (current: String, next: String) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Changer le mot de passe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2)) {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it; error = null },
                    label = { Text("Mot de passe actuel") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = next,
                    onValueChange = { next = it; error = null },
                    label = { Text("Nouveau mot de passe") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text("Confirmer le mot de passe") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    current.isBlank() -> error = "Veuillez saisir votre mot de passe actuel."
                    next.length < 8   -> error = "Le nouveau mot de passe doit contenir au moins 8 caractères."
                    next != confirm   -> error = "Les mots de passe ne correspondent pas."
                    else              -> onConfirm(current, next)
                }
            }) { Text("Confirmer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

/* ------------------------------------------------------------------ *
 * Local helpers — kept tiny so the file stays focused on UI.
 * ------------------------------------------------------------------ */
