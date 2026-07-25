package com.elimtiyaz.core.rbac

import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Role

/**
 * The three possible outcomes when evaluating a [FeatureNode] against a session.
 *
 * Design rule: the UI only ever sees these three states. It never branches on
 * permissions or roles directly. This keeps the gating logic in ONE place
 * (the [FeatureGate]) and lets us change the rules without touching every screen.
 *
 * - [Enabled]  → render normally, accept clicks.
 * - [Disabled] → render greyed-out, ignore clicks, optionally show a reason on long-press.
 *                The user can SEE the feature exists but cannot interact with it.
 * - [Hidden]   → do not render at all. Reserved for cases where even revealing
 *                the existence of the feature would leak information (rare).
 */
sealed class AccessState {
    /** Node is fully available — render and interact normally. */
    data object Enabled : AccessState()

    /**
     * Node is visible but not interactive. Use [reason] for the tooltip/announcement.
     * The UI should apply a ~40% alpha and a lock icon.
     */
    data class Disabled(val reason: DisableReason) : AccessState()

    /** Node should not be rendered at all. */
    data object Hidden : AccessState()
}

/**
 * Why a node is disabled. Drives the icon + tooltip text shown to the user.
 */
sealed class DisableReason(val displayFr: String) {
    /** Missing a specific permission. */
    data class MissingPermission(val permission: Permission) :
        DisableReason("Autorisation requise: ${permission.key}")

    /** Missing a role. */
    data class MissingRole(val role: Role) :
        DisableReason("Rôle requis: ${role.displayFr}")

    /** Missing any of these permissions (OR was required, none held). */
    data class MissingAnyOf(val permissions: Set<Permission>) :
        DisableReason("Une des autorisations suivantes est requise: ${permissions.joinToString { it.key }}")

    /** Missing at least one of these permissions (AND was required). */
    data class MissingAllOf(val missing: Set<Permission>) :
        DisableReason("Autorisations manquantes: ${missing.joinToString { it.key }}")

    /** A non-RBAC feature flag is off (paid plan, experiment, rollout). */
    data class FeatureFlagOff(val flag: String) :
        DisableReason("Fonctionnalité non activée: $flag")

    /** Permanent — e.g. removed feature, desktop-only, plan upgrade required. */
    data class Permanent(val state: PermanentState) :
        DisableReason(state.displayFr)

    /** No session — the user is not signed in. */
    data object NotAuthenticated :
        DisableReason("Veuillez vous connecter pour accéder à cette fonctionnalité.")
}
