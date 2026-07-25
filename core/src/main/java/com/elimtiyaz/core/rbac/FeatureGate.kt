package com.elimtiyaz.core.rbac

import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Role
import com.elimtiyaz.core.common.Session

/**
 * The single source of truth for access-control decisions.
 *
 * Pure, stateless, fully testable. The UI layer passes a [Session?] (which may
 * be NULL when the user is not signed in) and a [FeatureNode], and receives an
 * [AccessState] back. No other object in the app should branch on permissions
 * or roles directly — that logic lives here.
 *
 * The gate is intentionally a top-level `object` rather than a Hilt-bound
 * singleton because it has no dependencies beyond the [FeatureFlagProvider],
 * which the caller passes in. The Compose helpers ([gated]) grab the provider
 * from a CompositionLocal, so screens never need to inject the gate.
 *
 * Evaluation rules (in order):
 *
 *  1. If the node's requirement is [AccessRequirement.None] → [AccessState.Enabled].
 *  2. If the requirement carries a [PermanentState] → [AccessState.Disabled] with
 *     the corresponding [DisableReason.Permanent], regardless of session.
 *  3. If the session is NULL → [AccessState.Disabled] with
 *     [DisableReason.NotAuthenticated] (or [AccessState.Hidden] when the node
 *     declares [FeatureNode.hideWhenUnauthenticated]).
 *  4. If a feature flag is required and the provider reports it OFF →
 *     [AccessState.Disabled] with [DisableReason.FeatureFlagOff].
 *  5. If a single [AccessRequirement.permission] is required and not held →
 *     [AccessState.Disabled] with [DisableReason.MissingPermission].
 *  6. If a single [AccessRequirement.role] is required and not matched →
 *     [AccessState.Disabled] with [DisableReason.MissingRole].
 *  7. If [AccessRequirement.allOf] is non-empty and at least one is missing →
 *     [AccessState.Disabled] with [DisableReason.MissingAllOf].
 *  8. If [AccessRequirement.anyOf] is non-empty and none is held →
 *     [AccessState.Disabled] with [DisableReason.MissingAnyOf].
 *  9. Otherwise → [AccessState.Enabled].
 *
 * Step ordering matters: permanent > unauthenticated > flag > permission > role.
 */
object FeatureGate {

    fun evaluate(
        node: FeatureNode,
        session: Session?,
        flags: FeatureFlagProvider = NoOpFeatureFlagProvider,
    ): AccessState = evaluateRequirement(node.requirement, session, flags, node.hideWhenUnauthenticated)

    fun evaluateRequirement(
        requirement: AccessRequirement,
        session: Session?,
        flags: FeatureFlagProvider = NoOpFeatureFlagProvider,
        hideWhenUnauthenticated: Boolean = false,
    ): AccessState {
        // 1. No requirement → always enabled.
        if (requirement.isEmpty) return AccessState.Enabled

        // 2. Permanent disable — beats everything else.
        requirement.permanent?.let { return AccessState.Disabled(DisableReason.Permanent(it)) }

        // 3. No session.
        if (session == null) {
            return if (hideWhenUnauthenticated) AccessState.Hidden
                   else AccessState.Disabled(DisableReason.NotAuthenticated)
        }

        // 4. Feature flag.
        requirement.featureFlag?.let { flag ->
            if (!flags.isEnabled(flag)) {
                return AccessState.Disabled(DisableReason.FeatureFlagOff(flag))
            }
        }

        // 5. Single permission.
        requirement.permission?.let { p ->
            if (!session.can(p)) {
                return AccessState.Disabled(DisableReason.MissingPermission(p))
            }
        }

        // 6. Single role.
        requirement.role?.let { r ->
            if (session.role != r) {
                return AccessState.Disabled(DisableReason.MissingRole(r))
            }
        }

        // 7. allOf.
        if (requirement.allOf.isNotEmpty()) {
            val missing = requirement.allOf.filterNot { session.can(it) }.toSet()
            if (missing.isNotEmpty()) {
                return AccessState.Disabled(DisableReason.MissingAllOf(missing))
            }
        }

        // 8. anyOf.
        if (requirement.anyOf.isNotEmpty()) {
            val holdsAny = requirement.anyOf.any { session.can(it) }
            if (!holdsAny) {
                return AccessState.Disabled(DisableReason.MissingAnyOf(requirement.anyOf))
            }
        }

        // 9. All checks passed.
        return AccessState.Enabled
    }

    /** Convenience — returns TRUE only when the state is [AccessState.Enabled]. */
    fun isAccessible(
        node: FeatureNode,
        session: Session?,
        flags: FeatureFlagProvider = NoOpFeatureFlagProvider,
    ): Boolean = evaluate(node, session, flags) is AccessState.Enabled

    /** Convenience — same as [isAccessible] but for a raw requirement. */
    fun isRequirementMet(
        requirement: AccessRequirement,
        session: Session?,
        flags: FeatureFlagProvider = NoOpFeatureFlagProvider,
    ): Boolean = evaluateRequirement(requirement, session, flags) is AccessState.Enabled
}

/**
 * A node in the application's hierarchical feature tree.
 *
 * The architecture is: **Section → Option → (Page | Action | Feature)**.
 *
 * - A **Section** is a top-level grouping (e.g. the 5 bottom-nav hubs, or a
 *   drawer section in the future).
 * - An **Option** is a tile / list-item / sub-tab inside a section (e.g.
 *   "Counter Payment" inside the Financials section).
 * - A **Page** is a full screen reachable from an option.
 * - An **Action** is a button / FAB / menu item (e.g. "Approve expense").
 * - A **Feature** is a cross-cutting capability (e.g. camera proof capture)
 *   that may be required by multiple actions.
 *
 * Each node carries an [AccessRequirement]. The requirement is OPTIONAL — when
 * NULL, the node is always accessible (used for nodes whose access is purely
 * structural / navigational).
 *
 * The node's [id] should be stable across releases so audit logs and analytics
 * can reference it.
 *
 * @property id                    Stable identifier (e.g. "fin.counter_payment").
 * @property title                 Display title (French primary).
 * @property description           Optional longer description for tooltips.
 * @property requirement           Access requirement. NULL = no requirement.
 * @property hideWhenUnauthenticated When TRUE and no session, render as [AccessState.Hidden]
 *                                  rather than [AccessState.Disabled]. Default FALSE.
 * @property children              Sub-options / sub-pages. Empty for leaf nodes.
 */
data class FeatureNode(
    val id: String,
    val title: String,
    val description: String? = null,
    val requirement: AccessRequirement = AccessRequirement.None,
    val hideWhenUnauthenticated: Boolean = false,
    val children: List<FeatureNode> = emptyList(),
) {
    val isLeaf: Boolean get() = children.isEmpty()
    val isSection: Boolean get() = children.isNotEmpty() && id.count { it == '.' } == 0
    val isOption: Boolean get() = children.isNotEmpty() && id.count { it == '.' } == 1

    /** Construct a leaf node with a single-permission requirement. */
    constructor(
        id: String, title: String, description: String? = null,
        permission: Permission,
        hideWhenUnauthenticated: Boolean = false,
    ) : this(id, title, description, AccessRequirement.require(permission), hideWhenUnauthenticated, emptyList())

    /** Construct a leaf node with a role requirement. */
    constructor(
        id: String, title: String, description: String? = null,
        role: Role,
        hideWhenUnauthenticated: Boolean = false,
    ) : this(id, title, description, AccessRequirement.requireRole(role), hideWhenUnauthenticated, emptyList())

    /** Construct a permanently-disabled leaf (e.g. removed features). */
    constructor(
        id: String, title: String, description: String? = null,
        permanent: PermanentState,
    ) : this(id, title, description, AccessRequirement.permanently(permanent), false, emptyList())

    /** Walk the tree and yield every node (this + descendants). */
    fun walk(): Sequence<FeatureNode> = sequence {
        yield(this@FeatureNode)
        children.forEach { yieldAll(it.walk()) }
    }

    /** Find a descendant by id. */
    fun find(id: String): FeatureNode? = walk().firstOrNull { it.id == id }
}
