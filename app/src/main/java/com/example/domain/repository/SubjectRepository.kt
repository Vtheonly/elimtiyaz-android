package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.Subject
import kotlinx.coroutines.flow.Flow

/** Subject repository contract. */
interface SubjectRepository {
    fun observe(): Flow<List<Subject>>
    fun observeByLevel(level: String): Flow<List<Subject>>
    fun observeByClass(classId: String): Flow<List<Subject>>
    suspend fun createSubject(input: CreateSubjectInput, actorId: String, actorName: String): Result<Subject>
    suspend fun updateSubject(id: String, input: UpdateSubjectInput, actorId: String, actorName: String): Result<Subject>
    suspend fun archiveSubject(id: String, actorId: String, actorName: String): Result<Unit>
    suspend fun assignSubjectToClass(classId: String, subjectId: String, teacherId: String?, weeklyHours: Int, coefficient: Double, actorId: String, actorName: String): Result<Unit>
}

/** Input payload for [SubjectRepository.createSubject]. */
data class CreateSubjectInput(
    val name: String, val nameAr: String?, val code: String,
    val level: String, val coefficient: Double, val isExtracurricular: Boolean,
    val passingGrade: Double = 10.0,
    // Vault §06.02 (iteration 2) — per-COMPONENT coefficients. Defaults
    // preserve the historical (1, 1, 2) recipe (Examen weighted 2×) so
    // GPAs computed under the previous build remain bit-identical when
    // no override is set.
    val coefficientDevoir1: Double = 1.0,
    val coefficientDevoir2: Double = 1.0,
    val coefficientExamen: Double = 2.0,
)

/** Input payload for [SubjectRepository.updateSubject]. */
data class UpdateSubjectInput(
    val name: String? = null, val coefficient: Double? = null, val passingGrade: Double? = null,
    // Vault §06.02 (iteration 2) — per-COMPONENT coefficient overrides.
    // When ANY of these changes, the repository recomputes subjectAverage
    // on the CURRENT academic year's assessment rows (past years stay
    // immutable per the append-only rule). null = leave the existing value.
    val coefficientDevoir1: Double? = null,
    val coefficientDevoir2: Double? = null,
    val coefficientExamen: Double? = null,
)
