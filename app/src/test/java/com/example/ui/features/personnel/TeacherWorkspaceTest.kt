package com.example.ui.features.personnel

import com.example.core.Permission
import com.example.core.Role
import com.example.core.Session
import com.example.domain.model.AcademicClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T-237 / RBAC-300 (35th session) — the teacher workspace class-scoping
 * filter, pinned as a PURE function (no Hilt / Robolectric needed).
 *
 * Rules under test:
 *  - teacher → only homeroom-assigned classes (id OR display-name match);
 *  - unlinked teacher → ZERO classes (never the full catalog);
 *  - non-teacher / null session → empty (teacher-only workspace);
 *  - name matching is case-insensitive (the RollCallViewModel convention).
 */
class TeacherWorkspaceTest {

    private fun session(role: Role, userId: String = "profile-1", name: String = "Amine Teacher") = Session(
        userId = userId,
        tenantId = "tenant-1",
        email = "teacher@elimtiyaz.dz",
        displayName = name,
        avatarUrl = null,
        role = role,
        permissions = Permission.DEFAULT_ROLE_PERMISSIONS[role]!!,
        accessToken = "jwt",
        refreshToken = null,
        expiresAt = System.currentTimeMillis() + 3_600_000L,
        locale = "fr",
    )

    private fun cls(
        id: String,
        name: String,
        homeroomTeacherId: String? = null,
        homeroomTeacherName: String? = null,
        enrolledCount: Int = 12,
    ) = AcademicClass(
        id = id,
        tenantId = "tenant-1",
        name = name,
        level = "primary",
        gradeYear = 3,
        homeroomTeacherId = homeroomTeacherId,
        homeroomTeacherName = homeroomTeacherName,
        room = "R-101",
        capacity = 30,
        enrolledCount = enrolledCount,
        academicYear = "2026-2027",
    )

    private val catalog = listOf(
        cls("c1", "3A", homeroomTeacherId = "profile-1"),
        cls("c2", "3B", homeroomTeacherId = "profile-2"),
        cls("c3", "4A", homeroomTeacherId = null, homeroomTeacherName = "Amine Teacher"),
        cls("c4", "5A", homeroomTeacherId = null, homeroomTeacherName = "someone else"),
    )

    @Test
    fun `teacher sees only homeroom-assigned classes (id + name matches)`() {
        val scoped = TeacherWorkspaceViewModel.filterTeacherClasses(catalog, session(Role.TEACHER))
        assertEquals(listOf("c1", "c3"), scoped.map { it.id })
    }

    @Test
    fun `unlinked teacher sees ZERO classes (never the full catalog)`() {
        // Neither the id NOR the display name matches any catalog entry —
        // an account with no personnel link and no homeroom assignment.
        val scoped = TeacherWorkspaceViewModel.filterTeacherClasses(
            catalog,
            session(Role.TEACHER, userId = "profile-999", name = "Nobody Assigned"),
        )
        assertTrue(scoped.isEmpty())
    }

    @Test
    fun `non-teacher session gets an empty workspace`() {
        val scoped = TeacherWorkspaceViewModel.filterTeacherClasses(catalog, session(Role.SUPPORT_STAFF))
        assertTrue(scoped.isEmpty())
    }

    @Test
    fun `null session gets an empty workspace`() {
        val scoped = TeacherWorkspaceViewModel.filterTeacherClasses(catalog, null)
        assertTrue(scoped.isEmpty())
    }

    @Test
    fun `name matching is case-insensitive`() {
        val scoped = TeacherWorkspaceViewModel.filterTeacherClasses(catalog, session(Role.TEACHER, userId = "x", name = "amine TEACHER"))
        assertEquals(listOf("c3"), scoped.map { it.id })
    }
}
