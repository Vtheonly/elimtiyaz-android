package com.example.infrastructure.supabase

import com.example.domain.model.ChatChannel
import com.example.domain.model.ChatMessage
import com.example.domain.repository.ChatRepository
import com.example.core.Result
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * T-102-follow-up — Supabase DTOs for the canonical chat tables
 * (migrations 0010 + 0051 + 0061). Column names mirror the SQL schema
 * exactly (snake_case @SerialName), same convention as SharedDtos.kt.
 */
@Serializable
data class ChatChannelDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("code") val code: String,
    @SerialName("name") val name: String,
    @SerialName("channel_type") val channelType: String,
    @SerialName("member_ids") val memberIds: List<String> = emptyList(),
    @SerialName("description") val description: String? = null,
    @SerialName("department_id") val departmentId: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_message_preview") val lastMessagePreview: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toDomain(): ChatChannel = ChatChannel(
        id = id,
        tenantId = tenantId ?: "",
        code = code,
        name = name,
        channelType = channelType,
        memberIds = memberIds,
        description = description,
        departmentId = departmentId,
        archivedAt = archivedAt,
        lastMessageAt = lastMessageAt,
        lastMessagePreview = lastMessagePreview,
        createdBy = createdBy,
        createdAt = createdAt,
    )
}

@Serializable
data class ChatMessageDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("channel_id") val channelId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("body") val body: String,
    @SerialName("sent_at") val sentAt: String,
    @SerialName("read_by") val readBy: JsonElement? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("parent_message_id") val parentMessageId: String? = null,
) {
    /**
     * read_by jsonb → typed receipts. Tolerates a null/absent array (the
     * column default is '[]' but a malformed row must not crash the chat
     * screen — degrade to "unread").
     */
    fun toDomain(): ChatMessage = ChatMessage(
        id = id,
        tenantId = tenantId ?: "",
        channelId = channelId,
        authorId = authorId,
        body = body,
        sentAt = sentAt,
        readBy = parseReadBy(readBy),
        deletedAt = deletedAt,
        editedAt = editedAt,
        parentMessageId = parentMessageId,
    )

private fun parseReadBy(element: JsonElement?): List<ChatMessage.ReadReceipt> {
    val array = runCatching { element?.jsonArray }.getOrNull() ?: return emptyList()
    return array.mapNotNull { el ->
        runCatching {
            val obj = el.jsonObject
            val userId = (obj["user_id"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: return@mapNotNull null
            val readAt = (obj["read_at"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
            ChatMessage.ReadReceipt(userId = userId, readAt = readAt)
        }.getOrNull()
    }
}
}

/**
 * T-102-follow-up — the Supabase-backed chat repository (v1: online-only
 * reads + sends, verbatim port of the website MessagesView's semantics).
 *
 * Every query relies on RLS (the caller's JWT scopes rows to their own
 * channels); no client-side permission logic. The membership filter uses
 * PostgREST's array-contains (`cs`) on `member_ids` exactly like the
 * website's `.contains("member_ids", [profileId])`.
 */
@Singleton
class SupabaseChatRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
) : ChatRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun channels(profileId: String): Result<List<ChatChannel>> = try {
        val dtos = provider.postgrest.from("chat_channels").select {
            filter {
                filter("member_ids", FilterOperator.CS, listOf(profileId))
                filter("archived_at", FilterOperator.IS, null)
            }
            // CHAT-104 (migration 0061): order by last activity, not
            // updated_at; nulls last so never-messaged channels sink.
            order("last_message_at", Order.DESCENDING, false)
        }.decodeList<ChatChannelDto>()
        Result.Ok(dtos.map { it.toDomain() })
    } catch (e: Exception) {
        Result.Err(com.example.core.Errors.fromException(e))
    }

    override suspend fun messages(channelId: String, limit: Int): Result<List<ChatMessage>> = try {
        val dtos = provider.postgrest.from("chat_messages").select {
            filter {
                eq("channel_id", channelId)
                filter("deleted_at", FilterOperator.IS, null)
            }
            order("sent_at", Order.ASCENDING, false)
            limit(limit.toLong())
        }.decodeList<ChatMessageDto>()
        Result.Ok(dtos.map { it.toDomain() })
    } catch (e: Exception) {
        Result.Err(com.example.core.Errors.fromException(e))
    }

    override suspend fun unreadCount(profileId: String, window: Int): Result<Int> = try {
        // Latest `window` messages across ALL the caller's channels (RLS
        // scopes the rows), newest first, then count client-side — the
        // website's documented WEAK-023 shape.
        val dtos = provider.postgrest.from("chat_messages").select {
            order("sent_at", Order.DESCENDING, false)
            limit(window.toLong())
        }.decodeList<ChatMessageDto>()
        val unread = dtos.count { dto ->
            dto.authorId != profileId && dto.toDomain().readBy.none { it.userId == profileId }
        }
        Result.Ok(unread)
    } catch (e: Exception) {
        Result.Err(com.example.core.Errors.fromException(e))
    }

    override suspend fun send(
        channelId: String,
        authorProfileId: String,
        body: String,
    ): Result<ChatMessage> = try {
        val row = buildJsonObject {
            // The 0061 touch trigger maintains the channel's
            // last_message_at/preview columns on insert.
            put("channel_id", channelId)
            put("author_id", authorProfileId)
            put("body", body)
            put("attachments", buildJsonArray { })
            put("read_by", buildJsonArray {
                add(buildJsonObject {
                    put("user_id", authorProfileId)
                    put("read_at", java.time.Instant.now().toString())
                })
            })
        }
        val result = provider.postgrest.from("chat_messages").insert(row) {
            // return the inserted row (with its server-generated id/sent_at)
            select()
        }
        val sent = result.decodeAs<ChatMessageDto>()
        Result.Ok(sent.toDomain())
    } catch (e: Exception) {
        Result.Err(com.example.core.Errors.fromException(e))
    }

    override suspend fun markRead(
        messages: List<ChatMessage>,
        profileId: String,
    ): Result<Int> = try {
        var marked = 0
        for (m in messages) {
            if (m.isReadBy(profileId)) continue
            val updatedReceipts = buildJsonArray {
                m.readBy.forEach { r ->
                    add(buildJsonObject {
                        put("user_id", r.userId)
                        put("read_at", r.readAt)
                    })
                }
                add(buildJsonObject {
                    put("user_id", profileId)
                    put("read_at", java.time.Instant.now().toString())
                })
            }
            val patch = buildJsonObject {
                // 0051's append-only guard trigger enforces server-side
                // that only the caller's OWN entry is appended — a
                // rejected update throws here and surfaces (REALTIME-101
                // lesson: never swallow read-receipt failures).
                put("read_by", updatedReceipts)
            }
            provider.postgrest.from("chat_messages").update(patch) {
                filter { eq("id", m.id) }
            }
            marked++
        }
        Result.Ok(marked)
    } catch (e: Exception) {
        Result.Err(com.example.core.Errors.fromException(e))
    }
}
