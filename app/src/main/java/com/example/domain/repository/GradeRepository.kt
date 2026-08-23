package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.Assessment
import kotlinx.coroutines.flow.Flow

/** Grade / assessment repository contract. */
interface GradeRepository {
    fun observeForStudent(studentId: String, term: String, academicYear: String): Flow<List<Assessment>>
    fun observeForClass(classId: String, subjectId: String, term: String, academicYear: String): Flow<List<Assessment>>
    suspend fun enterGrade(input: EnterGradeInput, actorId: String, actorName: String): Result<Assessment>
}

/** Input payload for [GradeRepository.enterGrade]. */
data class EnterGradeInput(
    val studentId: String, val subjectId: String, val classId: String,
    val term: String, val academicYear: String,
    val devoir1: Double?, val devoir2: Double?, val examen: Double?,
    val coefficient: Double,
)
