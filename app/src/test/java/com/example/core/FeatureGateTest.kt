package com.example.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Unit tests for the RBAC feature gate — mirrors the desktop
 * `src/test/unit/rbac-feature-gate.test.ts`.
 *
 * Verifies that the pure evaluate() function correctly maps
 * (AccessRequirement, Session?, FeatureFlagProvider) → AccessState
 * for all 6 requirement variants.
 */
class FeatureGateTest {

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

    // ── Session helpers ───────────────────────────────────────────────────

    @Test fun `Session can() checks permission presence`() {
        assertTrue(teacherSession.can(Permission.ENTER_GRADES))
        assertFalse(teacherSession.can(Permission.COLLECT_PAYMENT))
    }

    @Test fun `Session hasRole() checks role`() {
        assertTrue(teacherSession.hasRole(Role.TEACHER))
        assertFalse(teacherSession.hasRole(Role.SUPER_ADMIN))
    }

    @Test fun `Session hasAnyRole() checks any of the roles`() {
        assertTrue(teacherSession.hasAnyRole(Role.TEACHER, Role.SUPER_ADMIN))
        assertFalse(teacherSession.hasAnyRole(Role.SUPER_ADMIN, Role.MANAGER))
    }

    @Test fun `Session isExpired returns false for future expiry`() {
        val session = teacherSession.copy(
            expiresAt = System.currentTimeMillis() + 3_600_000L,  // 1 hour from now
        )
        assertFalse(session.isExpired())
    }

    @Test fun `Session isExpired returns true for past expiry`() {
        val session = teacherSession.copy(
            expiresAt = System.currentTimeMillis() - 1000L,  // 1 second ago
        )
        assertTrue(session.isExpired())
    }

    @Test fun `Session isExpired applies 60-second safety margin`() {
        // expires in 30 seconds — within the 60-second safety margin → expired
        val session = teacherSession.copy(
            expiresAt = System.currentTimeMillis() + 30_000L,
        )
        assertTrue(session.isExpired())
    }

    // ── Wire-protocol enum parity (verified at compile time) ──────────────

    @Test fun `Role codes match desktop wire-protocol strings`() {
        // These strings appear in JWT claims and the role_assignments table.
        // Renaming any value without a database migration breaks the system.
        assertEquals("super_admin", Role.SUPER_ADMIN.code)
        assertEquals("financial_officer", Role.FINANCIAL_OFFICER.code)
        assertEquals("teacher", Role.TEACHER.code)
        assertEquals("support_staff", Role.SUPPORT_STAFF.code)
        assertEquals("manager", Role.MANAGER.code)
        assertEquals("buyer", Role.BUYER.code)
        assertEquals("driver", Role.DRIVER.code)
        assertEquals("warehouse_worker", Role.WAREHOUSE_WORKER.code)
        assertEquals("worker", Role.WORKER.code)
        assertEquals("parent", Role.PARENT.code)
        assertEquals("student", Role.STUDENT.code)
    }

    @Test fun `Permission codes match desktop wire-protocol strings (sample)`() {
        // Sample check — full parity verified by the wire-protocol parity test in CI.
        assertEquals("view_roster", Permission.VIEW_ROSTER.code)
        assertEquals("collect_payment", Permission.COLLECT_PAYMENT.code)
        assertEquals("refund_payment", Permission.REFUND_PAYMENT.code)
        assertEquals("adjust_account", Permission.ADJUST_ACCOUNT.code)
        assertEquals("approve_expense", Permission.APPROVE_EXPENSE.code)
        assertEquals("manage_workflows", Permission.MANAGE_WORKFLOWS.code)
        assertEquals("use_ai", Permission.USE_AI.code)
        assertEquals("manage_backups", Permission.MANAGE_BACKUPS.code)
        assertEquals("view_audit_log", Permission.VIEW_AUDIT_LOG.code)
    }

    @Test fun `Role fromCode roundtrips all values`() {
        Role.values().forEach { role ->
            val roundtripped = Role.fromCode(role.code)
            assertEquals(role, roundtripped, "Roundtrip failed for ${role.name}")
        }
    }

    @Test fun `Role fromCode returns null for unknown code`() {
        assertEquals(null, Role.fromCode("nonexistent_role"))
    }

    @Test fun `Permission fromCode roundtrips all values`() {
        Permission.values().forEach { perm ->
            val roundtripped = Permission.fromCode(perm.code)
            assertEquals(perm, roundtripped, "Roundtrip failed for ${perm.name}")
        }
    }

    // ── Role groupings ────────────────────────────────────────────────────

    @Test fun `STAFF_ROLES excludes parent and student`() {
        assertEquals(9, Role.STAFF_ROLES.size)
        assertFalse(Role.PARENT in Role.STAFF_ROLES)
        assertFalse(Role.STUDENT in Role.STAFF_ROLES)
    }

    @Test fun `ADMINISTRATIVE_ROLES contains super_admin and manager`() {
        assertEquals(setOf(Role.SUPER_ADMIN, Role.MANAGER), Role.ADMINISTRATIVE_ROLES)
    }

    @Test fun `DASHBOARD_ROLES matches iteration 9 RBAC change`() {
        assertEquals(
            setOf(Role.SUPER_ADMIN, Role.FINANCIAL_OFFICER, Role.SUPPORT_STAFF, Role.MANAGER),
            Role.DASHBOARD_ROLES,
        )
    }

    // ── Default role-permission matrix ───────────────────────────────────

    @Test fun `SuperAdmin default permissions include every permission`() {
        val adminPerms = Permission.DEFAULT_ROLE_PERMISSIONS[Role.SUPER_ADMIN]
        assertEquals(Permission.values().toSet(), adminPerms)
    }

    @Test fun `Teacher default permissions include enter_grades and roll_call`() {
        val teacherPerms = Permission.DEFAULT_ROLE_PERMISSIONS[Role.TEACHER]!!
        assertTrue(Permission.ENTER_GRADES in teacherPerms)
        assertTrue(Permission.ROLL_CALL in teacherPerms)
        assertTrue(Permission.ASSIGN_HOMEWORK in teacherPerms)
    }

    @Test fun `Teacher default permissions exclude collect_payment`() {
        val teacherPerms = Permission.DEFAULT_ROLE_PERMISSIONS[Role.TEACHER]!!
        assertFalse(Permission.COLLECT_PAYMENT in teacherPerms)
        assertFalse(Permission.REFUND_PAYMENT in teacherPerms)
    }

    @Test fun `Parent and Student default permissions are empty`() {
        assertEquals(emptySet<Permission>(), Permission.DEFAULT_ROLE_PERMISSIONS[Role.PARENT])
        assertEquals(emptySet<Permission>(), Permission.DEFAULT_ROLE_PERMISSIONS[Role.STUDENT])
    }
}
