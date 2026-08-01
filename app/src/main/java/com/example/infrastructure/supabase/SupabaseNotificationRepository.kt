package com.example.infrastructure.supabase

import com.example.core.Errors
import com.example.core.Result
import com.example.core.Session
import com.example.domain.model.AppNotification
import com.example.domain.repository.NotificationRepository
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of NotificationRepository.
 *
 * Table: `notifications` (migration 0013). The DB uses `kind` (alert/info/
 * warning/success/error/system) which we map to the mobile `type` field.
 *
 * RLS policies (migration 0019) handle the user-scoping automatically:
 *   - SELECT: rows where target_user_id = auth.uid() OR target_role in
 *     user's roles OR broadcast (null/null) when admin.
 *   - UPDATE: rows where target_user_id = auth.uid() (or super_admin).
 *
 * Therefore `markAllRead()` doesn't need to filter explicitly — RLS scopes
 * the UPDATE to the current user's notifications automatically.
 *
 * Mutations return [Result.Ok]/[Result.Err]; observers catch all exceptions
 * and emit `emptyList()` so the UI never crashes.
 */
@Singleton
class SupabaseNotificationRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
) : NotificationRepository {

    override fun observe() = flow {
        emit(try {
            provider.postgrest.from("notifications")
                .select {
                    order("created_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<NotificationDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() })
    }

    override fun observeForSession(session: Session) = flow {
        emit(try {
            // Two union queries because Postgrest doesn't support OR-on-different-columns
            // directly in a single filter; we run them in parallel and dedupe by id.
            val byUser = provider.postgrest.from("notifications")
                .select {
                    filter { eq("target_user_id", session.userId) }
                    order("created_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<NotificationDto>()
            val byRole = provider.postgrest.from("notifications")
                .select {
                    filter { eq("target_role", session.role.code) }
                    order("created_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<NotificationDto>()
            // Broadcast = target_user_id AND target_role both null — fetch all, filter client-side
            val broadcast = provider.postgrest.from("notifications")
                .select {
                    order("created_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<NotificationDto>()
                .filter { it.targetUserId == null && it.targetRole == null }
            val merged = (byUser + byRole + broadcast)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt }
            merged.map { it.toDomain() }
        } catch (e: Exception) { emptyList() })
    }

    override suspend fun markRead(id: String): Result<Unit> = try {
        val nowIso = java.time.Instant.now().toString()
        provider.postgrest.from("notifications").update(
            mapOf(
                "read_at" to nowIso,
                "is_read" to "true",
            )
        ) {
            filter { eq("id", id) }
        }
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun markAllRead(): Result<Unit> = try {
        val nowIso = java.time.Instant.now().toString()
        // RLS auto-restricts to current user's notifications.
        provider.postgrest.from("notifications").update(
            mapOf(
                "read_at" to nowIso,
                "is_read" to "true",
            )
        ) {
            filter { eq("is_read", false) }
        }
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun dismiss(id: String): Result<Unit> = try {
        val nowIso = java.time.Instant.now().toString()
        provider.postgrest.from("notifications").update(
            mapOf("dismissed_at" to nowIso)
        ) {
            filter { eq("id", id) }
        }
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    @Serializable
    data class NotificationDto(
        val id: String,
        val tenantId: String,
        val kind: String,
        val title: String,
        val body: String? = null,
        val priority: String = "medium",
        val source: String = "system",
        val sourceLabel: String? = null,
        val targetUserId: String? = null,
        val targetRole: String? = null,
        val isRead: Boolean = false,
        val readAt: String? = null,
        val dismissedAt: String? = null,
        val triggeredAt: String? = null,
        val expiresAt: String? = null,
        val linkEntityType: String? = null,
        val linkEntityId: String? = null,
        val createdBy: String? = null,
        val createdAt: String,
        val updatedAt: String = "",
    ) {
        fun toDomain() = AppNotification(
            id = id,
            tenantId = tenantId,
            title = title,
            body = body ?: "",
            type = kind,
            priority = priority,
            source = source,
            sourceLabel = sourceLabel ?: source,
            entityType = linkEntityType,
            entityId = linkEntityId,
            targetUserId = targetUserId,
            targetRole = targetRole,
            triggeredAt = triggeredAt,
            readAt = readAt,
            createdAt = createdAt,
            createdBy = createdBy ?: "system",
        )
    }
}
