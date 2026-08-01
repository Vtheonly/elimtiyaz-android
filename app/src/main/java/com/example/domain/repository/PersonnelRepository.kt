package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.Personnel
import kotlinx.coroutines.flow.Flow

/** Personnel repository contract. */
interface PersonnelRepository {
    fun observe(): Flow<List<Personnel>>
    fun observeByCategory(category: String): Flow<List<Personnel>>
    fun observeById(id: String): Flow<Personnel?>
    fun observeByUserId(userId: String): Flow<Personnel?>
    suspend fun createPersonnel(input: CreatePersonnelInput, actorId: String, actorName: String): Result<Personnel>
    suspend fun updatePersonnel(id: String, input: UpdatePersonnelInput, actorId: String, actorName: String): Result<Personnel>
    suspend fun deletePersonnel(id: String, actorId: String, actorName: String): Result<Unit>
}

/** Input payload for [PersonnelRepository.createPersonnel]. */
data class CreatePersonnelInput(
    val firstName: String, val lastName: String, val staffCategory: String,
    val roleId: String, val departmentId: String?, val position: String,
    val phone: String, val email: String?, val hireDate: String,
    val salary: Long? = null, val weeklyHoursTarget: Int = 0,
)

/** Input payload for [PersonnelRepository.updatePersonnel]. */
data class UpdatePersonnelInput(
    val position: String? = null, val phone: String? = null,
    val email: String? = null, val salary: Long? = null,
    val status: String? = null, val departmentId: String? = null,
)
