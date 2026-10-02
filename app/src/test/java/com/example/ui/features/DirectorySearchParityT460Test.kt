package com.example.ui.features

import com.example.domain.model.AcademicClass
import com.example.domain.model.Personnel
import com.example.domain.model.Subject
import com.example.ui.features.academics.filterClasses
import com.example.ui.features.academics.filterSubjects
import com.example.ui.features.personnel.filterPersonnel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-460 pass J (issue #3 F-13) — the directory search-parity contract, pinned
 * at the pure-filter level (fast JVM tests; no composition needed — the
 * ARCH-012 release-exclusion rule does not apply).
 *
 * The parity standard: EVERY directory with real data volume offers the same
 * text-search contract the Student/Parents directories established —
 * case-insensitive match on the natural lookup keys, combined with the
 * directory's own filters, with an honest filtered-empty state.
 */
class DirectorySearchParityT460Test {

    private fun personnel(
        first: String,
        last: String,
        category: String = "teacher",
        position: String = "Enseignant",
        phone: String = "0555000000",
        email: String? = null,
    ) = Personnel(
        id = "$first-$last", tenantId = "t", userId = null,
        firstName = first, lastName = last, staffCategory = category,
        roleId = category, departmentId = null, position = position,
        phone = phone, email = email, hireDate = "2026-09-01",
    )

    private fun subject(
        name: String,
        code: String,
        level: String = "primaire",
        extracurricular: Boolean = false,
    ) = Subject(
        id = name, tenantId = "t", name = name, nameAr = null, code = code,
        level = level, coefficient = 1.0, isExtracurricular = extracurricular,
    )

    private fun klass(
        name: String,
        teacher: String? = "M. Dupont",
        room: String? = "B12",
    ) = AcademicClass(
        id = name, tenantId = "t", name = name, level = "primaire",
        gradeYear = 1, homeroomTeacherId = null, homeroomTeacherName = teacher,
        room = room, capacity = 30, enrolledCount = 20, academicYear = "2025-2026",
    )

    // ── Employee directory ───────────────────────────────────────────────

    @Test
    fun `employee search matches full name case-insensitively`() {
        val staff = listOf(
            personnel("Karim", "BENALI"),
            personnel("Amine", "Hamdi"),
        )
        assertEquals(1, filterPersonnel(staff, "all", "karim").size)
        assertEquals("Karim BENALI", filterPersonnel(staff, "all", "benali").first().fullName)
    }

    @Test
    fun `employee search also matches position phone and email`() {
        val staff = listOf(
            personnel("A", "Surveillant", position = "Surveillant général"),
            personnel("B", "Secrétaire", position = "Secrétaire", phone = "0770123456", email = "b@el-imtiyaz.dz"),
        )
        assertEquals(1, filterPersonnel(staff, "all", "surveillant").size)
        assertEquals(1, filterPersonnel(staff, "all", "0770").size)
        assertEquals(1, filterPersonnel(staff, "all", "el-imtiyaz").size)
    }

    @Test
    fun `employee search combines with the category tab`() {
        val staff = listOf(
            personnel("Karim", "B", category = "teacher"),
            personnel("Nadia", "K", category = "administration"),
        )
        // the category filter applies AND the query must match within it
        assertTrue(filterPersonnel(staff, "teacher", "nadia").isEmpty())
        assertEquals(1, filterPersonnel(staff, "teacher", "karim").size)
        assertEquals(2, filterPersonnel(staff, "all", "").size)
        assertEquals(1, filterPersonnel(staff, "administration", "").size)
    }

    // ── Subjects directory ───────────────────────────────────────────────

    @Test
    fun `subject search matches name and code`() {
        val subjects = listOf(
            subject("Mathématiques", "MATH"),
            subject("Éducation physique", "EPS"),
        )
        assertEquals(1, filterSubjects(subjects, null, null, "math").size)
        assertEquals(1, filterSubjects(subjects, null, null, "eps").size)
    }

    @Test
    fun `subject search combines with the level and domain filters`() {
        val subjects = listOf(
            subject("Mathématiques", "MATH", level = "primaire"),
            subject("Maths avancées", "MATH-L", level = "lycee"),
            subject("Club échecs", "CLUB-ECH", level = "all", extracurricular = true),
        )
        // §05.01 domain split preserved: scolarite excludes the club
        assertEquals(2, filterSubjects(subjects, null, "scolarite", "").size)
        // level chips keep the "all applies to every level" rule
        assertEquals(listOf("Mathématiques", "Club échecs"), filterSubjects(subjects, "primaire", null, "").map { it.name })
        // and the query intersects both
        assertEquals(listOf("Club échecs"), filterSubjects(subjects, "primaire", "extracurricular", "échecs").map { it.name })
        assertEquals(1, filterSubjects(subjects, "lycee", "scolarite", "avancées").size)
    }

    // ── Classes directory ────────────────────────────────────────────────

    @Test
    fun `class search matches name teacher and room`() {
        val classes = listOf(
            klass("1AP1", teacher = "M. Dupont", room = "B12"),
            klass("2AP2", teacher = "Mme Saidi", room = "C07"),
        )
        assertEquals(1, filterClasses(classes, "1ap").size)
        assertEquals(1, filterClasses(classes, "saidi").size)
        assertEquals(1, filterClasses(classes, "b12").size)
        assertEquals(2, filterClasses(classes, "").size)
    }

    @Test
    fun `a blank query never filters anything out in any directory`() {
        val staff = listOf(personnel("A", "B"))
        val subjects = listOf(subject("S", "S"))
        val classes = listOf(klass("C"))
        assertEquals(staff, filterPersonnel(staff, "all", "   "))
        assertEquals(subjects, filterSubjects(subjects, null, null, " "))
        assertEquals(classes, filterClasses(classes, "\t"))
    }
}
