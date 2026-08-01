package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.AcademicClass
import kotlinx.coroutines.flow.Flow

/** Academic class repository contract. */
interface ClassRepository {
    fun observe(): Flow<List<AcademicClass>>
    fun observeByLevel(level: String): Flow<List<AcademicClass>>
    fun observeById(id: String): Flow<AcademicClass?>
    suspend fun createClass(input: CreateClassInput, actorId: String, actorName: String): Result<AcademicClass>
    suspend fun updateClass(id: String, input: UpdateClassInput, actorId: String, actorName: String): Result<AcademicClass>
    suspend fun deleteClass(id: String, actorId: String, actorName: String): Result<Unit>
}

/** Input payload for [ClassRepository.createClass]. */
data class CreateClassInput(
    val name: String, val level: String, val gradeYear: Int,
    val room: String? = null, val capacity: Int,
    val academicYear: String, val homeroomTeacherId: String? = null,
)

/** Input payload for [ClassRepository.updateClass]. */
data class UpdateClassInput(
    val name: String? = null, val room: String? = null,
    val capacity: Int? = null, val homeroomTeacherId: String? = null,
)
