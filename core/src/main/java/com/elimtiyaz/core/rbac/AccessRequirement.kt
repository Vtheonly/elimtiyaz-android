package com.elimtiyaz.core.rbac

import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Role

/**
 * The set of conditions that gate access to a UI node (section / option / page / action / feature).
 *
 * The requirement is a *conjunction* — every clause in [allOf] must be satisfied.
 * For OR semantics, use [anyOf]. The two can be combined: the requirement is
 * satisfied when (every clause in [allOf] is satisfied) AND (at least one clause
 * in [anyOf] is satisfied, if [anyOf] is non-empty).
 *
 * Use [AccessRequirement.None] for a node that is always available.
 * Use [AccessRequirement.Disabled] for a node that is always disabled (e.g. a
 * feature not yet rolled out, or removed like the legacy AI assistant).
 *
 * Design goal: this type is the ONLY place the UI thinks about access control.
 * Screens receive a [FeatureNode] (which carries an [AccessRequirement]) and ask
 * a [FeatureGate] to evaluate it. The gate reads the current [com.elimtiyaz.core.common.Session]
 * and returns an [AccessState]. The screen never inspects permissions directly.
 *
 * @property permission   Required permission. NULL = no permission requirement.
 * @property role         Required role. NULL = no role requirement.
 * @property anyOf        When non-empty, the session must hold at least one of these permissions.
 * @property allOf        When non-empty, the session must hold ALL of these permissions.
 * @property featureFlag  Optional non-RBAC flag (paid plan, experiment, rollout).
 *                        The UI's [FeatureFlagProvider] decides whether the flag is on.
 * @property permanent    When TRUE, the requirement is structural (not driven by the
 *                        current session) — e.g. a removed feature. The node is always
 *                        rendered in the Disabled state regardless of who is signed in.
 */
data class AccessRequirement(
    val permission: Permission? = null,
    val role: Role? = null,
    val anyOf: Set<Permission> = emptySet(),
    val allOf: Set<Permission> = emptySet(),
    val featureFlag: String? = null,
    val permanent: PermanentState? = null,
) {

    /** Whether the requirement carries any clause at all. */
    val isEmpty: Boolean get() =
        permission == null && role == null && anyOf.isEmpty() && allOf.isEmpty() && featureFlag == null && permanent == null

    companion object {
        /** Always enabled. */
        val None = AccessRequirement()

        /** Always disabled with a permanent reason (used for removed features). */
        fun permanently(reason: PermanentState) = AccessRequirement(permanent = reason)

        /** Require a single permission. */
        fun require(permission: Permission) = AccessRequirement(permission = permission)

        /** Require a single role. */
        fun requireRole(role: Role) = AccessRequirement(role = role)

        /** Require ALL of the given permissions. */
        fun requireAll(vararg permissions: Permission) =
            AccessRequirement(allOf = permissions.toSet())

        /** Require ANY of the given permissions (OR semantics). */
        fun requireAny(vararg permissions: Permission) =
            AccessRequirement(anyOf = permissions.toSet())

        /** Require a feature flag (non-RBAC, e.g. paid plan or experiment). */
        fun requireFlag(flag: String) = AccessRequirement(featureFlag = flag)
    }
}

/**
 * Permanent disable reasons — for nodes that should NEVER be enabled regardless
 * of the user's role. Used for features that were removed (e.g. the legacy AI
 * assistant) or features not yet rolled out.
 *
 * Permanent-disabled nodes are rendered greyed-out with a small lock icon and
 * a tooltip explaining the reason. They are NOT hidden — the architecture
 * deliberately keeps them visible so users know the feature exists but is
 * currently unavailable.
 */
enum class PermanentState(val displayFr: String, val displayAr: String) {
    Removed("Fonctionnalité retirée", "ميزة محذوفة"),
    NotYetAvailable("Bientôt disponible", "قريباً"),
    DesktopOnly("Disponible sur le terminal de bureau", "متوفر على محطة سطح المكتب"),
    PlanUpgradeRequired("Plan supérieur requis", "يتطلب خطة أعلى"),
}
