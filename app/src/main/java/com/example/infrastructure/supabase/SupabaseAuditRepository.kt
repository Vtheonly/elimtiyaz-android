package com.example.infrastructure.supabase

import com.example.core.Errors
import com.example.core.Result
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audit sink — writes via the `write_audit_log` PostgreSQL function
 * (SECURITY DEFINER). The Android client NEVER writes directly to the
 * `audit_logs` table — that would bypass the function's enforcement of
 * actor_id non-nullability and the consistent before/after shape.
 *
 * The audit log is append-only server-side (a BEFORE UPDATE/DELETE trigger
 * blocks mutations). The client can only SELECT (filtered by RLS by tenant
 * + role) and INSERT (via this RPC).
 *
 * Offline strategy: audit log writes are NOT enqueued via [SyncSupport]
 * because that would create a Hilt dependency cycle
 * (`SyncService` → `AuditRepository` → `SyncSupport` → `SyncService`).
 * Audit entries lost while offline are acceptable — the server-side
 * triggers + RLS enforce invariants regardless, and the desktop also
 * writes audit logs directly without queueing. If the write fails, the
 * error is surfaced to the caller; the user can retry the originating
 * action.
 */
@Singleton
class SupabaseAuditRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
) : AuditRepository {

    override fun observe(limit: Int) = kotlinx.coroutines.flow.flow {
        emit(fetchRecent(limit))
    }

    override fun observeByEntity(entityType: String, entityId: String) =
        kotlinx.coroutines.flow.flow {
            val rows = try {
                provider.postgrest.from("audit_logs")
                    .select {
                        filter {
                            eq("entity_type", entityType)
                            eq("entity_id", entityId)
                        }
                        order("occurred_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(50)
                    }
                    .decodeList<AuditLogDto>()
            } catch (e: Exception) { emptyList() }
            emit(rows.map { it.toDomain() })
        }

    override suspend fun query(filter: com.example.domain.repository.AuditFilter): Result<List<com.example.domain.model.AuditLog>> = try {
        val rows = provider.postgrest.from("audit_logs")
            .select {
                filter {
                    filter.action?.let { eq("action", it) }
                    filter.entityType?.let { eq("entity_type", it) }
                    filter.entityId?.let { eq("entity_id", it) }
                    filter.actorId?.let { eq("actor_id", it) }
                    filter.from?.let { gte("occurred_at", it) }
                    filter.to?.let { lte("occurred_at", it) }
                }
                order("occurred_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(filter.limit.toLong())
                // offset not directly supported in this SDK version — would use range()
            }
            .decodeList<AuditLogDto>()
        Result.Ok(rows.map { it.toDomain() })
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun log(input: AuditLogInput): Result<com.example.domain.model.AuditLog> = try {
        val params = buildJsonObject {
            put("p_action", input.action)
            put("p_entity_type", input.entityType)
            put("p_entity_id", input.entityId)
            put("p_before_json", input.beforeJson)
            put("p_after_json", input.afterJson)
            put("p_note", input.note)
            // actor_id, actor_name, tenant_id are derived server-side from the JWT
        }
        val resultId = provider.postgrest.rpc("write_audit_log", params)
            .decodeAs<String>()
        // Fetch the row back so the caller has the full record.
        val row = provider.postgrest.from("audit_logs")
            .select { filter { eq("id", resultId) } }
            .decodeList<AuditLogDto>()
            .firstOrNull()
            ?: error("Audit log row not found after insert: $resultId")
        Result.Ok(row.toDomain())
    } catch (e: Exception) {
        // Audit log writes are not enqueued (would create a Hilt cycle with
        // SyncService → AuditRepository → SyncSupport → SyncService).
        // Surface the error so the caller can retry the originating action.
        Result.Err(Errors.fromException(e))
    }

    private suspend fun fetchRecent(limit: Int): List<com.example.domain.model.AuditLog> = try {
        provider.postgrest.from("audit_logs")
            .select {
                order("occurred_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<AuditLogDto>()
            .map { it.toDomain() }
    } catch (e: Exception) { emptyList() }

    @Serializable
    data class AuditLogDto(
        val id: String,
        val tenantId: String,
        val action: String,
        val entityType: String,
        val entityId: String,
        val actorId: String,
        val actorName: String,
        val actorRole: String? = null,
        val beforeJson: String? = null,
        val afterJson: String? = null,
        val note: String? = null,
        val ipAddress: String? = null,
        val userAgent: String? = null,
        val occurredAt: String,
    ) {
        fun toDomain() = com.example.domain.model.AuditLog(
            id = id, tenantId = tenantId, action = action,
            entityType = entityType, entityId = entityId,
            actorId = actorId, actorName = actorName, actorRole = actorRole,
            beforeJson = beforeJson, afterJson = afterJson, note = note,
            ipAddress = ipAddress, userAgent = userAgent, occurredAt = occurredAt,
        )
    }
}
