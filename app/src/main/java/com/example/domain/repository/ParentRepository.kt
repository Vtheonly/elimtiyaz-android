package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.Parent
import kotlinx.coroutines.flow.Flow

/** Parent entity repository contract. */
interface ParentRepository {
    fun observe(): Flow<List<Parent>>
    fun observeById(id: String): Flow<Parent?>
    fun search(query: String): Flow<List<Parent>>
    suspend fun createParent(input: CreateParentInput, actorId: String, actorName: String): Result<Parent>
    suspend fun updateParent(id: String, input: UpdateParentInput, actorId: String, actorName: String): Result<Parent>
    suspend fun deleteParent(id: String, actorId: String, actorName: String): Result<Unit>
}

/** Input payload for [ParentRepository.createParent]. */
data class CreateParentInput(
    val firstName: String, val lastName: String, val phone: String,
    val email: String? = null, val occupation: String? = null,
    val address: String? = null, val transportDestination: String? = null,
    val preferredLanguage: String = "fr",
)

/** Input payload for [ParentRepository.updateParent]. All fields nullable — only set fields are mutated. */
data class UpdateParentInput(
    val firstName: String? = null, val lastName: String? = null,
    val phone: String? = null, val email: String? = null,
    val occupation: String? = null, val address: String? = null,
    val transportDestination: String? = null, val preferredLanguage: String? = null,
)
