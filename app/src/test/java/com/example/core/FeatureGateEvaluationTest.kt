package com.example.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [FeatureGate.evaluate] — mirrors the desktop
 * `src/test/unit/rbac-feature-gate.test.ts`.
 *
 * Verifies that the pure `evaluate()` function correctly maps
 * `(AccessRequirement, Session?, FeatureFlagProvider) → AccessState`
 * for all 6 requirement variants:
 *   - Empty, Permanent, RequiresPermission, RequiresAnyOf, RequiresAllOf, RequiresRole
 *
 * Role/Permission wire-protocol parity, role groupings, the default
 * role-permission matrix, and `Session` helpers live in [RolePermissionTest].
 */
class FeatureGateEvaluationTest {

    private val adminSession = Session(
        userId = "u1", tenantId = "t1", email = "admin@elimtiyaz.dz",
        displayName = "Alice Admin", avatarUrl = null,
        role = Role.SUPER_ADMIN,
        permissions = Permission.values().toSet(),  // SuperAdmin has all permissions
        accessToken = "jwt-1", refreshToken = null,
        expiresAt = System.currentTimeMillis() + 3_600_000L,
        locale = "fr",
    )

    private val teacherSession = Session(
        userId = "u2", tenantId = "t1", email = "teacher@elimtiyaz.dz",
        displayName = "Bob Teacher", avatarUrl = null,
        role = Role.TEACHER,
        permissions = Permission.DEFAULT_ROLE_PERMISSIONS[Role.TEACHER]!!,
        accessToken = "jwt-2", refreshToken = null,
        expiresAt = System.currentTimeMillis() + 3_600_000L,
        locale = "fr",
    )

    // ── Empty requirement ─────────────────────────────────────────────────

    @Test fun `Empty requirement returns Enabled for everyone`() {
        assertEquals(
            AccessState.Enabled,
            FeatureGate.evaluate(AccessRequirement.Empty, session = null),
        )
        assertEquals(
            AccessState.Enabled,
            FeatureGate.evaluate(AccessRequirement.Empty, session = adminSession),
        )
    }

    // ── Permanent requirement ─────────────────────────────────────────────

    @Test fun `Permanent requirement returns Disabled with Permanent reason`() {
        val state = FeatureGate.evaluate(
            AccessRequirement.Permanent(PermanentState.DESKTOP_ONLY),
            session = adminSession,
        )
        assertTrue(state is AccessState.Disabled)
        val reason = state.reason
        assertTrue(reason is DisableReason.Permanent)
        assertEquals(PermanentState.DESKTOP_ONLY, reason.state)
    }

    // ── RequiresPermission ────────────────────────────────────────────────

    @Test fun `RequiresPermission returns Enabled when session has permission`() {
        // Teacher has ENTER_GRADES per the default matrix
        val state = FeatureGate.evaluate(
            AccessRequirement.RequiresPermission(Permission.ENTER_GRADES),
            session = teacherSession,
        )
        assertEquals(AccessState.Enabled, state)
    }

    @Test fun `RequiresPermission returns Disabled-MissingPermission when session lacks permission`() {
        // Teacher does NOT have COLLECT_PAYMENT per the default matrix
        val state = FeatureGate.evaluate(
            AccessRequirement.RequiresPermission(Permission.COLLECT_PAYMENT),
            session = teacherSession,
        )
        assertTrue(state is AccessState.Disabled)
        assertTrue(state.reason is DisableReason.MissingPermission)
        assertEquals(Permission.COLLECT_PAYMENT, (state.reason as DisableReason.MissingPermission).permission)
    }

    @Test fun `RequiresPermission returns Disabled-NotAuthenticated when session is null`() {
        val state = FeatureGate.evaluate(
            AccessRequirement.RequiresPermission(Permission.COLLECT_PAYMENT),
            session = null,
        )
        assertTrue(state is AccessState.Disabled)
        assertTrue(state.reason is DisableReason.NotAuthenticated)
    }

    @Test fun `RequiresPermission with hideWhenUnauthenticated returns Hidden when session is null`() {
        val state = FeatureGate.evaluate(
            AccessRequirement.RequiresPermission(
                Permission.COLLECT_PAYMENT,
                hideWhenUnauthenticated = true,
            ),
            session = null,
        )
        assertEquals(AccessState.Hidden, state)
    }

    // ── RequiresAnyOf ─────────────────────────────────────────────────────

    @Test fun `RequiresAnyOf returns Enabled when session has any of the permissions`() {
        val state = FeatureGate.evaluate(
            AccessRequirement.RequiresAnyOf(
                listOf(Permission.COLLECT_PAYMENT, Permission.ENTER_GRADES),
            ),
            session = teacherSession,  // has ENTER_GRADES
        )
        assertEquals(AccessState.Enabled, state)
    }

    @Test fun `RequiresAnyOf returns Disabled-MissingPermission when session has none`() {
        val state = FeatureGate.evaluate(
            AccessRequirement.RequiresAnyOf(
                listOf(Permission.COLLECT_PAYMENT, Permission.REFUND_PAYMENT),
            ),
            session = teacherSession,  // has neither
        )
        assertTrue(state is AccessState.Disabled)
        assertTrue(state.reason is DisableReason.MissingPermission)
        // Should report the FIRST permission in the list as missing
        assertEquals(Permission.COLLECT_PAYMENT, (state.reason as DisableReason.MissingPermission).permission)
    }

    // ── RequiresAllOf ─────────────────────────────────────────────────────

    @Test fun `RequiresAllOf returns Enabled when session has all permissions`() {
        val state = FeatureGate.evaluate(
            AccessRequirement.RequiresAllOf(
                listOf(Permission.ENTER_GRADES, Permission.ROLL_CALL),
            ),
            session = teacherSession,  // has both
        )
        assertEquals(AccessState.Enabled, state)
    }

    @Test fun `RequiresAllOf returns Disabled-MissingPermission when session lacks any`() {
        val state = FeatureGate.evaluate(
            AccessRequirement.RequiresAllOf(
                listOf(Permission.ENTER_GRADES, Permission.COLLECT_PAYMENT),
            ),
            session = teacherSession,  // has ENTER_GRADES, lacks COLLECT_PAYMENT
        )
        assertTrue(state is AccessState.Disabled)
        assertTrue(state.reason is DisableReason.MissingPermission)
        assertEquals(Permission.COLLECT_PAYMENT, (state.reason as DisableReason.MissingPermission).permission)
    }

    // ── RequiresRole ──────────────────────────────────────────────────────

    @Test fun `RequiresRole returns Enabled when session role matches`() {
        val state = FeatureGate.evaluate(
            AccessRequirement.RequiresRole(listOf(Role.SUPER_ADMIN, Role.MANAGER)),
            session = adminSession,  // SUPER_ADMIN
        )
        assertEquals(AccessState.Enabled, state)
    }

    @Test fun `RequiresRole returns Disabled-MissingRole when session role doesn't match`() {
        val state = FeatureGate.evaluate(
            AccessRequirement.RequiresRole(listOf(Role.SUPER_ADMIN, Role.MANAGER)),
            session = teacherSession,  // TEACHER
        )
        assertTrue(state is AccessState.Disabled)
        assertTrue(state.reason is DisableReason.MissingRole)
        val roles = (state.reason as DisableReason.MissingRole).roles
        assertEquals(listOf(Role.SUPER_ADMIN, Role.MANAGER), roles)
    }
}
