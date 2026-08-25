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
    /** COMPLETE display name (e.g. "BENALI Mohamed"). When null, derived from first+last. Migration 0027. */
    val displayName: String? = null,
    val email: String? = null, val occupation: String? = null,
    val address: String? = null, val transportDestination: String? = null,
    val preferredLanguage: String = "fr",
    // Vault §04.03 — batch registration master-info fields (backend parity:
    // secondary_phone / national_id / relationship on the Supabase parents
    // table). All optional so legacy callers keep working.
    /** Secondary phone — stored on the `whatsapp` column (server secondary_phone). */
    val secondaryPhone: String? = null,
    /** National identity number (N° pièce d'identité). */
    val nationalId: String? = null,
    /** Relationship to the children: father | mother | guardian. */
    val relationship: String? = null,
)

/** Input payload for [ParentRepository.updateParent]. All fields nullable — only set fields are mutated. */
data class UpdateParentInput(
    val firstName: String? = null, val lastName: String? = null,
    val displayName: String? = null,
    val phone: String? = null, val email: String? = null,
    val occupation: String? = null, val address: String? = null,
    val transportDestination: String? = null, val preferredLanguage: String? = null,
    val secondaryPhone: String? = null,
    val nationalId: String? = null,
    val relationship: String? = null,
)
