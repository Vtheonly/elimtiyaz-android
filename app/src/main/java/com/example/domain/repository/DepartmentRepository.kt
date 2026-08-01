package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.Department
import kotlinx.coroutines.flow.Flow

/** Department repository contract. */
interface DepartmentRepository {
    fun observe(): Flow<List<Department>>
    fun observeById(id: String): Flow<Department?>
    suspend fun createDepartment(input: CreateDepartmentInput, actorId: String, actorName: String): Result<Department>
    suspend fun archiveDepartment(id: String, actorId: String, actorName: String): Result<Unit>
    suspend fun unarchiveDepartment(id: String, actorId: String, actorName: String): Result<Unit>
}

/** Input payload for [DepartmentRepository.createDepartment]. */
data class CreateDepartmentInput(
    val name: String, val description: String?,
    val headPersonnelId: String?, val parentDepartmentId: String?, val colorHex: String?,
)
