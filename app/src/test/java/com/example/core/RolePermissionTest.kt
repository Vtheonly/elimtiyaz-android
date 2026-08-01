package com.example.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the RBAC primitives — [Role], [Permission], and [Session]
 * helpers — mirrors the desktop `src/test/unit/rbac-feature-gate.test.ts`
 * parity / matrix section.
 *
 * Verifies:
 *   - `Session` helpers (`can`, `hasRole`, `hasAnyRole`, `isExpired` with
 *     the 60-second safety margin)
 *   - Wire-protocol enum parity (`Role.code`, `Permission.code`, `fromCode`
 *     roundtrips for both)
 *   - Role groupings (`STAFF_ROLES`, `ADMINISTRATIVE_ROLES`, `DASHBOARD_ROLES`)
 *   - The default role-permission matrix
 *     (`Permission.DEFAULT_ROLE_PERMISSIONS`)
 *
 * Pure `FeatureGate.evaluate()` coverage lives in [FeatureGateEvaluationTest].
 */
class RolePermissionTest {

    private val teacherSession = Session(
        userId = "u2", tenantId = "t1", email = "teacher@elimtiyaz.dz",
        displayName = "Bob Teacher", avatarUrl = null,
        role = Role.TEACHER,
        permissions = Permission.DEFAULT_ROLE_PERMISSIONS[Role.TEACHER]!!,
        accessToken = "jwt-2", refreshToken = null,
        expiresAt = System.currentTimeMillis() + 3_600_000L,
        locale = "fr",
    )

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
