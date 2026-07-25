package com.elimtiyaz.feature.settings

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.ui.AsyncContent
import com.elimtiyaz.core.ui.AvatarCircle
import com.elimtiyaz.core.ui.ElImtiyazCard
import com.elimtiyaz.core.ui.EmptyState
import com.elimtiyaz.core.ui.ListRow
import com.elimtiyaz.core.ui.LoadingState
import com.elimtiyaz.core.ui.StatusChip
import com.elimtiyaz.core.ui.StatusTone
import com.elimtiyaz.domain.model.AuditEntry
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Profile screen — shows the current session's header card, permission grid,
 * recent audit activity, and quick actions (sign-out, open settings).
 *
 * Route: `Route.Profile`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    nav: NavController,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var showSignOutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbar) {
        val msg = state.snackbar
        if (msg != null) {
            snackbarHost.showSnackbar(msg)
            vm.consumeSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Stub — inline edit is out of scope for v1.
                        vm.consumeSnackbar()
                    }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Modifier")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { inner ->
        when {
            state.isLoading -> LoadingState(
                modifier = Modifier.padding(inner),
                message = "Chargement du profil…",
            )
            state.session == null -> EmptyState(
                title = "Aucune session active",
                description = "Veuillez vous reconnecter pour afficher votre profil.",
                icon = Icons.Outlined.Shield,
                modifier = Modifier.padding(inner),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(
                    horizontal = ElimtiyazSpacing.x4,
                    vertical = ElimtiyazSpacing.x3,
                ),
                verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
            ) {
                item { ProfileHeaderCard(state) }
                item { PermissionSection(state) }
                item { RecentActivitySection(state) }
                item { QuickActionsSection(
                    onSignOut = { showSignOutDialog = true },
                    onOpenSettings = { nav.navigate(Route.Settings.route) },
                ) }
                item { Spacer(Modifier.height(ElimtiyazSpacing.x4)) }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Déconnexion") },
            text = { Text("Voulez-vous vraiment vous déconnecter ?") },
            confirmButton = {
                TextButton(
                    enabled = !state.isSigningOut,
                    onClick = {
                        vm.signOut(onDone = {
                            showSignOutDialog = false
                            // Pop the entire back stack (Login is the start destination)
                            // and navigate to a fresh Login entry.
                            nav.navigate(Route.Login.route) {
                                popUpTo(nav.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                            }
                        })
                    },
                ) {
                    if (state.isSigningOut) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(ElimtiyazSpacing.x2))
                    }
                    Text("Se déconnecter")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isSigningOut,
                    onClick = { showSignOutDialog = false },
                ) { Text("Annuler") }
            },
        )
    }
}

/* ------------------------------------------------------------------ *
 * Sections
 * ------------------------------------------------------------------ */

/**
 * Header card — large avatar, display name, email, role badge, tenant id
 * (Mono font) and session expiry.
 */
@Composable
private fun ProfileHeaderCard(state: ProfileUiState) {
    val session = state.session ?: return
    ElImtiyazCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ElimtiyazSpacing.x4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvatarCircle(initial = state.avatarInitial, size = 80)
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Text(
                session.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                session.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            StatusChip(label = state.roleLabel, tone = roleTone(session.role))

            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            HorizontalDivider()
            Spacer(Modifier.height(ElimtiyazSpacing.x3))

            // Tenant ID — Mono font per design tokens (IDs / codes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Identifiant tenant",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    session.tenantId,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(ElimtiyazSpacing.x2))

            // Session expiry — formatted dd/MM/yyyy HH:mm
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Session expire le",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatExpiry(session.expiresAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Permission grid — one chip per [Permission] the user actually has.
 * Includes a summary line "Vous avez X autorisations sur Y".
 */
@Composable
private fun PermissionSection(state: ProfileUiState) {
    val permissions = state.session?.permissions?.toList().orEmpty().sortedBy { it.key }

    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, contentDescription = null)
                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                Text(
                    "Mes permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(
                    label = "${state.permissionCount}/${state.permissionTotal}",
                    tone = StatusTone.Info,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(
                "Vous avez ${state.permissionCount} autorisations sur ${state.permissionTotal}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            if (permissions.isEmpty()) {
                EmptyState(
                    title = "Aucune permission",
                    description = "Votre rôle ne dispose d'aucune autorisation.",
                    icon = Icons.Outlined.Shield,
                    modifier = Modifier.height(160.dp),
                )
            } else {
                // Wrap chips into rows using FlowRow-style layout via LazyVerticalGrid
                // bounded to a fixed height so it nests cleanly inside the card.
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(estimateGridHeight(permissions.size)),
                    horizontalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
                    verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
                    userScrollEnabled = false,
                ) {
                    items(permissions, key = { it.key }) { perm ->
                        PermissionChip(perm)
                    }
                }
            }
        }
    }
}

/** A single permission rendered as an assist chip with a French label. */
@Composable
private fun PermissionChip(permission: Permission) {
    AssistChip(
        onClick = {},
        label = { Text(permissionLabel(permission)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/**
 * Recent activity section — the last 10 audit entries authored by the current
 * user. Uses [AsyncContent] to handle loading/error/empty uniformly.
 *
 * The audit list is best-effort: a failure here surfaces an inline error
 * message but never blanks the surrounding profile card.
 */
@Composable
private fun RecentActivitySection(state: ProfileUiState) {
    ElImtiyazCard {
        Column(modifier = Modifier.padding(ElimtiyazSpacing.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, contentDescription = null)
                Spacer(Modifier.width(ElimtiyazSpacing.x3))
                Text(
                    "Activité récente",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x2))

            AsyncContent(
                isLoading = state.isLoading,
                error = state.activityError,
                items = state.recentActivity,
                emptyTitle = "Aucune activité récente",
                emptyDescription = "Vos actions apparaîtront ici.",
                emptyIcon = Icons.Outlined.History,
            ) { entries ->
                Column(verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x1)) {
                    entries.forEach { entry -> AuditRow(entry) }
                }
            }
        }
    }
}

/**
 * Single audit entry — action + entity + relative timestamp.
 *
 * Uses [ListRow] from the design system so the layout stays consistent with
 * other list rows across the app (avatar dot as leading, action/entity as
 * title, timestamp as subtitle).
 */
@Composable
private fun AuditRow(entry: AuditEntry) {
    ListRow(
        leading = {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        },
        title = "${entry.action} • ${entry.entityType}",
        subtitle = Formatters.dateTime(entry.at),
    )
}

/** Bottom-of-screen quick actions: sign-out (red) and open settings. */
@Composable
private fun QuickActionsSection(
    onSignOut: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x2),
    ) {
        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(ElimtiyazSpacing.x2))
            Text("Se déconnecter")
        }
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(ElimtiyazSpacing.x2))
            Text("Paramètres")
        }
    }
}

/* ------------------------------------------------------------------ *
 * Helpers
 * ------------------------------------------------------------------ */

/** Pick a StatusTone for the role badge based on hierarchy. */
private fun roleTone(role: com.elimtiyaz.core.common.Role): StatusTone = when (role) {
    com.elimtiyaz.core.common.Role.SuperAdmin       -> StatusTone.Danger
    com.elimtiyaz.core.common.Role.FinancialOfficer -> StatusTone.Warning
    com.elimtiyaz.core.common.Role.Teacher          -> StatusTone.Info
    com.elimtiyaz.core.common.Role.SupportStaff     -> StatusTone.Neutral
    com.elimtiyaz.core.common.Role.Parent           -> StatusTone.Success
    com.elimtiyaz.core.common.Role.Student          -> StatusTone.Success
}

/** French label for a [Permission] token — used by permission chips. */
private fun permissionLabel(permission: Permission): String = when (permission) {
    // CRM
    Permission.ViewRoster       -> "Voir le registre"
    Permission.CreateParent     -> "Créer un parent"
    Permission.EditParent       -> "Modifier un parent"
    Permission.DeleteParent     -> "Supprimer un parent"
    Permission.CreateStudent    -> "Créer un élève"
    Permission.EditStudent      -> "Modifier un élève"
    Permission.PromoteStudent   -> "Promouvoir un élève"
    // Academic
    Permission.ViewAcademics    -> "Voir les académiques"
    Permission.EnterGrades      -> "Saisir des notes"
    Permission.ManageSubjects   -> "Gérer les matières"
    Permission.ManageClasses    -> "Gérer les classes"
    Permission.AssignHomework   -> "Assigner des devoirs"
    Permission.RollCall         -> "Faire l'appel"
    // Financial
    Permission.ViewFinancials   -> "Voir les finances"
    Permission.CollectPayment   -> "Encaisser un paiement"
    Permission.RefundPayment    -> "Rembourser un paiement"
    Permission.AdjustAccount    -> "Ajuster un compte"
    Permission.GenerateReceipt  -> "Générer un reçu"
    Permission.ViewDebt         -> "Voir les dettes"
    Permission.SendReminder     -> "Envoyer un rappel"
    // Expenses
    Permission.SubmitExpense    -> "Soumettre une dépense"
    Permission.ApproveExpense   -> "Approuver une dépense"
    Permission.DisburseExpense  -> "Débourser une dépense"
    Permission.SettleExpenseProof -> "Régler une preuve"
    // HR
    Permission.ViewPersonnel    -> "Voir le personnel"
    Permission.ManagePersonnel  -> "Gérer le personnel"
    Permission.ViewAuditLog     -> "Voir le journal d'audit"
    Permission.ViewReleve       -> "Voir les relevés"
    // Routing
    Permission.AccessDriverMode -> "Mode chauffeur"
    // Settings
    Permission.ManageSettings   -> "Gérer les paramètres"
    Permission.ManageTenants    -> "Gérer les tenants"
}

/**
 * Format the session's `expiresAt` epoch millis as a localized timestamp.
 * Uses [SimpleDateFormat] rather than [Formatters.dateTime] because the
 * session field is a Long epoch, not an ISO string.
 */
private fun formatExpiry(epochMillis: Long): String {
    val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
    return fmt.format(java.util.Date(epochMillis))
}

/**
 * Estimate the height needed to render [count] permission chips in a grid
 * with 120-dp minimum columns. Two rows fit ~6 chips on a typical phone.
 */
private fun estimateGridHeight(count: Int): androidx.compose.ui.unit.Dp {
    val rows = (count + 2) / 3 // ~3 chips per row on average
    val rowHeight = 40.dp
    val spacing = 8.dp
    val total = rowHeight * rows + spacing * (rows - 1).coerceAtLeast(0)
    return total.coerceAtLeast(40.dp).coerceAtMost(240.dp)
}
