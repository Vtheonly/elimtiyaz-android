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
)

/** Input payload for [SubjectRepository.updateSubject]. */
data class UpdateSubjectInput(
    val name: String? = null, val coefficient: Double? = null, val passingGrade: Double? = null,
)
