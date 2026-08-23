package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.Parent
import com.example.domain.model.Student
import kotlinx.coroutines.flow.Flow

/** Student entity repository contract. */
interface StudentRepository {
    fun observe(): Flow<List<Student>>
    fun observeByParent(parentId: String): Flow<List<Student>>
    fun observeByClass(classId: String): Flow<List<Student>>
    fun observeById(id: String): Flow<Student?>
    fun search(query: String): Flow<List<Student>>
    suspend fun createStudent(input: CreateStudentInput, actorId: String, actorName: String): Result<Student>
    suspend fun updateStudent(id: String, input: UpdateStudentInput, actorId: String, actorName: String): Result<Student>
    suspend fun batchRegister(parent: CreateParentInput, students: List<CreateStudentInput>, actorId: String, actorName: String): Result<BatchRegisterResult>
    suspend fun promoteStudents(academicYear: String, decisions: List<PromotionDecision>, actorId: String, actorName: String): Result<Unit>
}

/** Input payload for [StudentRepository.createStudent]. */
data class CreateStudentInput(
    val firstName: String, val lastName: String, val gender: String,
    /** COMPLETE display name. When null, derived from first+last. Migration 0027. */
    val displayName: String? = null,
    val birthDate: String, val level: String, val gradeLevel: String,
    val classId: String? = null, val parentId: String? = null,
    val medicalNotes: String? = null,
    // ── CANONICAL-FINANCIAL-LOGIC.md §5 + §2.6 — fields needed by the
    // 5-rule discount engine. All optional so legacy callers keep working.
    /** The student's previous grade level (for `passage_palier` discount). */
    val previousGradeLevel: String? = null,
    /** Payment plan: `"full_annual"` or `"tranches"`. Defaults to `tranches`. */
    val paymentPlan: String? = null,
    /** Original enrollment date (ISO-8601) for `seniority_5y` discount. */
    val enrollmentDate: String? = null,
    /** Previous-year class rank (1 = top of palier) for `highest_average` discount. */
    val previousRank: Int? = null,
)

/**
 * Input payload for [StudentRepository.updateStudent].
 *
 * FIX (incomplete edits): previously only identity/class/status/medical fields
 * were accepted — `displayName` was silently dropped by the implementation
 * and birth date / grade level could not be corrected at all. All fields are
 * nullable; only set fields are mutated.
 */
data class UpdateStudentInput(
    val firstName: String? = null, val lastName: String? = null,
    val displayName: String? = null,
    val birthDate: String? = null,
    val level: String? = null,
    val gradeLevel: String? = null,
    val classId: String? = null, val status: String? = null,
    val medicalNotes: String? = null,
)

/** Result of [StudentRepository.batchRegister] — the created parent + students + activation code. */
data class BatchRegisterResult(val parent: Parent, val students: List<Student>, val activationCode: String?)

/** Single promotion decision for [StudentRepository.promoteStudents]. */
data class PromotionDecision(val studentId: String, val decision: String, val note: String? = null)
