package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.Result
import com.example.domain.model.Parent
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.CreateParentInput
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.UpdateParentInput
import com.example.infrastructure.room.toCacheEntity
import com.example.infrastructure.room.toDomain
import com.example.infrastructure.sync.SyncSupport
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseParentRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
    private val syncSupport: SyncSupport,
) : ParentRepository {

    /**
     * Cache-then-network: emit cached rows immediately, then fetch from
     * Supabase, update cache, emit again. Offline → cache only.
     *
     * BUGFIX (iter 2): previously the cache DAOs were never read; offline
     * users saw empty lists. Now we mirror the desktop's cache-then-network
     * pattern so the last-known data is shown when the device is offline.
     */
    override fun observe() = syncSupport.cacheThenNetwork(
        cacheRead = {
            syncSupport.listCachedParents().map { it.toDomain() }
        },
        cacheWrite = { parents: List<Parent> ->
            syncSupport.upsertParents(parents.map { it.toCacheEntity() })
        },
        fetch = { fetchAll() },
    )

    override fun observeById(id: String) = flow {
        // Emit cached value first (instant UI), then network.
        val cached = syncSupport.getCachedParent(id)?.toDomain()
        emit(cached)
        val fresh = fetchById(id)
        if (fresh != null) emit(fresh) else emit(cached)
    }

    override fun search(query: String) = flow {
        val rows = if (query.isBlank()) fetchAll() else try {
            provider.postgrest.from("parents")
                .select {
                    filter {
                        or {
                            ilike("first_name", "%$query%")
                            ilike("last_name", "%$query%")
                            ilike("phone", "%$query%")
                            ilike("code", "%$query%")
                        }
                    }
                    order("last_name", Order.ASCENDING)
                    limit(50)
                }
                .decodeList<ParentDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() }
        emit(rows)
    }

    override suspend fun createParent(input: CreateParentInput, actorId: String, actorName: String): Result<Parent> {
        validateCreateInput(input)
        val dto = ParentInsertDto(
            firstName = input.firstName,
            lastName = input.lastName,
            phone = input.phone,
            whatsapp = null,
            email = input.email,
            occupation = input.occupation,
            address = input.address,
            transportDestination = input.transportDestination,
            preferredLanguage = input.preferredLanguage,
        )
        // Try direct insert; on offline, enqueue for sync.
        return syncSupport.tryThenEnqueue(
            entity = "parent",
            operation = "create",
            payload = {
                syncSupport.json().encodeToString(ParentInsertDto.serializer(), dto)
            },
            sourceScreen = "ParentsDirectory",
        ) {
            val inserted = provider.postgrest.from("parents").insert(dto) {
                select()
            }.decodeList<ParentDto>().first()
            val parent = inserted.toDomain()
            // Persist to cache so the next observe() emits it instantly.
            syncSupport.upsertParents(listOf(parent.toCacheEntity()))
            auditRepository.log(AuditLogInput(
                action = AuditActions.PARENT_CREATE,
                entityType = "parent",
                entityId = parent.id,
                afterJson = """{"code":"${parent.code}","name":"${parent.fullName}"}""",
                note = "Parent created from Android app",
            ))
            parent
        }
    }

    override suspend fun updateParent(id: String, input: UpdateParentInput, actorId: String, actorName: String): Result<Parent> {
        return try {
            val updates = mutableMapOf<String, String>()
            input.firstName?.let { updates["first_name"] = it }
            input.lastName?.let { updates["last_name"] = it }
            input.phone?.let { updates["phone"] = it }
            input.email?.let { updates["email"] = it }
            input.occupation?.let { updates["occupation"] = it }
            input.address?.let { updates["address"] = it }
            input.transportDestination?.let { updates["transport_destination"] = it }
            input.preferredLanguage?.let { updates["preferred_language"] = it }
            if (updates.isEmpty()) return Result.Err(Errors.validation("No fields to update"))
            val updated = provider.postgrest.from("parents").update(updates) {
                filter { eq("id", id) }
                select()
            }.decodeList<ParentDto>().first()
            val parent = updated.toDomain()
            auditRepository.log(AuditLogInput(
                action = AuditActions.PARENT_UPDATE,
                entityType = "parent",
                entityId = id,
                beforeJson = "{}", // Best-effort; full diff would require a pre-fetch
                afterJson = updates.entries.joinToString(",", "{", "}") { (k, v) -> """"$k":"$v"""" },
                note = "Parent updated from Android app",
            ))
            Result.Ok(parent)
        } catch (e: Exception) {
            Result.Err(Errors.fromException(e))
        }
    }

    override suspend fun deleteParent(id: String, actorId: String, actorName: String): Result<Unit> = try {
        // Soft-delete (deleted_at) — RLS policies filter out soft-deleted rows on SELECT
        provider.postgrest.from("parents").update(mapOf("deleted_at" to java.time.Instant.now().toString())) {
            filter { eq("id", id) }
        }
        auditRepository.log(AuditLogInput(
            action = AuditActions.PARENT_DELETE,
            entityType = "parent",
            entityId = id,
            note = "Parent soft-deleted from Android app",
        ))
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    private fun validateCreateInput(input: CreateParentInput) {
        require(input.firstName.isNotBlank()) { "First name is required" }
        require(input.lastName.isNotBlank()) { "Last name is required" }
        require(input.phone.isNotBlank()) { "Phone is required" }
    }

    private suspend fun fetchAll(): List<Parent> = try {
        provider.postgrest.from("parents")
            .select {
                order("last_name", Order.ASCENDING)
                limit(200)
            }
            .decodeList<ParentDto>()
            .map { it.toDomain() }
    } catch (e: Exception) { emptyList() }

    private suspend fun fetchById(id: String): Parent? = try {
        provider.postgrest.from("parents")
            .select { filter { eq("id", id) } }
            .decodeList<ParentDto>()
            .firstOrNull()
            ?.toDomain()
    } catch (e: Exception) { null }

    @Serializable
    data class ParentDto(
        val id: String,
        val tenantId: String,
        val code: String,
        val firstName: String,
        val lastName: String,
        val phone: String,
        val whatsapp: String? = null,
        val email: String? = null,
        val occupation: String? = null,
        val address: String? = null,
        val transportDestination: String? = null,
        val preferredLanguage: String = "fr",
        val avatarUrl: String? = null,
        val createdAt: String,
        val updatedAt: String,
    ) {
        fun toDomain() = Parent(
            id = id, tenantId = tenantId, code = code,
            firstName = firstName, lastName = lastName, phone = phone,
            whatsapp = whatsapp, email = email, occupation = occupation,
            address = address, transportDestination = transportDestination,
            preferredLanguage = preferredLanguage, avatarUrl = avatarUrl,
            createdAt = createdAt, updatedAt = updatedAt,
        )
    }

    @Serializable
    data class ParentInsertDto(
        val firstName: String,
        val lastName: String,
        val phone: String,
        val whatsapp: String? = null,
        val email: String? = null,
        val occupation: String? = null,
        val address: String? = null,
        val transportDestination: String? = null,
        val preferredLanguage: String = "fr",
    )
}
