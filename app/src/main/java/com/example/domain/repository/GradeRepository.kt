package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.Assessment
import kotlinx.coroutines.flow.Flow

/** Grade / assessment repository contract. */
interface GradeRepository {
    fun observeForStudent(studentId: String, term: String, academicYear: String): Flow<List<Assessment>>

    /**
     * Observe assessments for one class, ONE subject and one term.
     *
     * FIX (ignored parameter): the local implementation previously dropped
     * `subjectId` on the floor and returned every subject's rows — the caller
     * believed it was scoped to a single subject.
     */
    fun observeForClass(classId: String, subjectId: String, term: String, academicYear: String): Flow<List<Assessment>>

    /**
     * Observe ALL assessments for one class and one term (every subject).
     * Backs class-wide statistics and per-student GPA ranking — both computed
     * with the canonical `computeOverallGpa` engine on the consumer side.
     */
    fun observeForClass(classId: String, term: String, academicYear: String): Flow<List<Assessment>>

    /**
     * Vault §04.07 / §06.05 — Student Academic History. Every assessment of
     * one student across ALL academic years and terms — the permanent,
     * append-only record embedded in the Student Profile drawer.
     */
    fun observeAllForStudent(studentId: String): Flow<List<Assessment>>
    suspend fun enterGrade(input: EnterGradeInput, actorId: String, actorName: String): Result<Assessment>
}

/** Input payload for [GradeRepository.enterGrade]. */
data class EnterGradeInput(
    val studentId: String, val subjectId: String, val classId: String,
    val term: String, val academicYear: String,
    val devoir1: Double?, val devoir2: Double?, val examen: Double?,
    val coefficient: Double,
)
