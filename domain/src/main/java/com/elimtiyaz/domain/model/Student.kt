package com.elimtiyaz.domain.model

import kotlinx.serialization.Serializable

/**
 * Student — belongs to exactly one parent. The plan enforces unlimited children
 * per parent (1→N) and atomic batch registration of siblings (§04.03).
 */
@Serializable
data class Student(
    val id: String,
    val tenantId: String,
    val code: String,                  // ELV-2025-001234
    val parentId: String,
    val firstName: String,
    val lastName: String,
    val gender: Gender = Gender.Unspecified,
    val birthDate: String,
    val enrollmentDate: String,
    val level: String,                 // primaire / cem / lycee
    val gradeYear: Int,                // 1..5 for primaire, 1..4 for cem, 1..3 for lycee
    val classId: String? = null,
    val photoUrl: String? = null,
    val medicalNotes: String? = null,
    val transportTier: String? = null, // t1/t2/t3 — drives transport tranche
    val status: StudentStatus = StudentStatus.Active,
    val createdAt: String,
    val updatedAt: String,
    val parent: Parent? = null,
    val academicHistory: List<AcademicHistoryEntry> = emptyList(),
)

@Serializable
enum class StudentStatus { Active, Graduated, Transferred, Suspended, Withdrawn }

@Serializable
data class CreateStudentInput(
    val parentId: String,
    val firstName: String,
    val lastName: String,
    val gender: Gender = Gender.Unspecified,
    val birthDate: String,
    val level: String,
    val gradeYear: Int,
    val classId: String? = null,
    val medicalNotes: String? = null,
    val transportTier: String? = null,
)

/**
 * Atomic batch input — used by the dynamic batch registration wizard (§04.03).
 * The repository wraps the entire creation in a single transaction so siblings
 * are never partially persisted.
 */
@Serializable
data class BatchRegistrationInput(
    val parent: CreateParentInput,
    val students: List<CreateStudentInput>,
)

@Serializable
data class BatchRegistrationResult(
    val parent: Parent,
    val students: List<Student>,
)

/** Snapshot of a student's record at the end of an academic year (§06.05). */
@Serializable
data class AcademicHistoryEntry(
    val academicYear: String,
    val level: String,
    val gradeYear: Int,
    val classId: String?,
    val className: String?,
    val gpa: Double,
    val rank: Int? = null,
    val decision: PromotionDecision,
    val narrative: String? = null,
)

@Serializable
enum class PromotionDecision { Promoted, Repeated, Graduated, Transferred }
