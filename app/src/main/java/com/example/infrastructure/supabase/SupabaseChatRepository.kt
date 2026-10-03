package com.example.infrastructure.supabase

import com.example.domain.model.ChatChannel
import com.example.domain.model.ChatMessage
import com.example.domain.repository.ChatRepository
import com.example.core.Errors
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
    // T-463 / CHAT-300 (0135): which chat system — server-derived. Absent
    // on pre-0135 rows (impossible after the live backfill) → internal.
    @SerialName("scope") val scope: String? = null,
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
        scope = scope ?: "internal",
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
    // T-464 / MEDIA-300: the attachments jsonb array — parsed leniently (a
    // malformed entry degrades to a skipped attachment, never a crash).
    @SerialName("attachments") val attachments: JsonElement? = null,
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
        attachments = parseAttachments(attachments),
        deletedAt = deletedAt,
        editedAt = editedAt,
        parentMessageId = parentMessageId,
    )

/** T-464 / MEDIA-300: the attachments jsonb array → typed attachments. */
private fun parseAttachments(element: JsonElement?): List<com.example.domain.model.ChatAttachment> {
    val array = runCatching { element?.jsonArray }.getOrNull() ?: return emptyList()
    return array.mapNotNull { el ->
        runCatching {
            val obj = el.jsonObject
            val storagePath = (obj["storage_path"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            com.example.domain.model.ChatAttachment(
                fileName = (obj["file_name"] as? JsonPrimitive)?.content ?: storagePath.substringAfterLast('/'),
                storagePath = storagePath,
                mimeType = (obj["mime_type"] as? JsonPrimitive)?.content,
                sizeBytes = (obj["size_bytes"] as? JsonPrimitive)?.content?.toLongOrNull(),
            )
        }.getOrNull()
    }
}

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
 * T-102-follow-up — the Supabase-backed chat repository (online-authoritative;
 * v1: reads + sends, 21st session; v2: IO-guard routing, 133rd session).
 *
 * Every query relies on RLS (the caller's JWT scopes rows to their own
 * channels); no client-side permission logic. The membership filter uses
 * PostgREST's array-contains (`cs`) on `member_ids` exactly like the
 * website's `.contains("member_ids", [profileId])`.
 *
 * T-102 v2 — every network call now runs through NetworkTimeouts (the
 * 37th-session registered debt, closed): READS via [NetworkTimeouts.guard]
 * (IO relocation + timeout; a null return maps to an honest timeout/offline
 * error — the v2 cache decorator serves the last cached content in that
 * case), SENDS + read-receipts via [NetworkTimeouts.guardSyncPush] (the
 * CROSS-200 contract: a rejected write NEVER looks successful — the real
 * exception propagates to Result.Err).
 */
@Singleton
class SupabaseChatRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
) : ChatRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun channels(profileId: String): Result<List<ChatChannel>> =
        NetworkTimeouts.guard("chat.channels") {
            val dtos = provider.postgrest.from("chat_channels").select {
                filter {
                    filter("member_ids", FilterOperator.CS, listOf(profileId))
                    filter("archived_at", FilterOperator.IS, null)
                }
                // CHAT-104 (migration 0061): order by last activity, not
                // updated_at; nulls last so never-messaged channels sink.
                order("last_message_at", Order.DESCENDING, false)
            }.decodeList<ChatChannelDto>()
            dtos.map { it.toDomain() }
        }?.let { Result.Ok(it) }
            ?: Result.Err(Errors.timeout("chat.channels: offline, unconfigured or timed out"))

    override suspend fun messages(channelId: String, limit: Int): Result<List<ChatMessage>> =
        NetworkTimeouts.guard("chat.messages") {
            val dtos = provider.postgrest.from("chat_messages").select {
                filter {
                    eq("channel_id", channelId)
                    filter("deleted_at", FilterOperator.IS, null)
                }
                order("sent_at", Order.ASCENDING, false)
                limit(limit.toLong())
            }.decodeList<ChatMessageDto>()
            dtos.map { it.toDomain() }
        }?.let { Result.Ok(it) }
            ?: Result.Err(Errors.timeout("chat.messages: offline, unconfigured or timed out"))

    override suspend fun unreadCount(profileId: String, window: Int): Result<Int> =
        NetworkTimeouts.guard("chat.unreadCount") {
            // Latest `window` messages across ALL the caller's channels (RLS
            // scopes the rows), newest first, then count client-side — the
            // website's documented WEAK-023 shape.
            val dtos = provider.postgrest.from("chat_messages").select {
                order("sent_at", Order.DESCENDING, false)
                limit(window.toLong())
            }.decodeList<ChatMessageDto>()
            dtos.count { dto ->
                dto.authorId != profileId && dto.toDomain().readBy.none { it.userId == profileId }
            }
        }?.let { Result.Ok(it) }
            ?: Result.Err(Errors.timeout("chat.unreadCount: offline, unconfigured or timed out"))

    override suspend fun send(
        channelId: String,
        authorProfileId: String,
        body: String,
        attachments: List<com.example.domain.model.ChatAttachment>,
    ): Result<ChatMessage> = try {
        // guardSyncPush: a REJECTED insert must throw (CROSS-200) — never
        // report a failed send as successful.
        val sent = NetworkTimeouts.guardSyncPush("chat.send") {
            val row = buildJsonObject {
                // The 0061 touch trigger maintains the channel's
                // last_message_at/preview columns on insert.
                put("channel_id", channelId)
                put("author_id", authorProfileId)
                put("body", body)
                // T-464 / MEDIA-300: the attachments metadata rides the
                // insert (the caller uploaded the bytes FIRST — the
                // StorageRepository contract; a failed upload aborts).
                put("attachments", buildJsonArray {
                    attachments.forEach { a ->
                        add(buildJsonObject {
                            put("file_name", a.fileName)
                            put("storage_path", a.storagePath)
                            put("mime_type", a.mimeType ?: "application/octet-stream")
                            put("size_bytes", a.sizeBytes ?: 0L)
                        })
                    }
                })
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
            result.decodeAs<ChatMessageDto>().toDomain()
        }
        sent?.let { Result.Ok(it) }
            ?: Result.Err(Errors.offline("chat.send: Supabase not configured"))
    } catch (e: com.example.infrastructure.supabase.SyncPushTimeoutException) {
        Result.Err(Errors.timeout("chat.send: ${e.message}"))
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun markRead(
        messages: List<ChatMessage>,
        profileId: String,
    ): Result<Int> = try {
        // guardSyncPush: a rejected read-receipt update must throw
        // (REALTIME-101 — never swallow read-receipt failures).
        var marked = 0
        NetworkTimeouts.guardSyncPush("chat.markRead") {
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
                    // rejected update throws here and surfaces.
                    put("read_by", updatedReceipts)
                }
                provider.postgrest.from("chat_messages").update(patch) {
                    filter { eq("id", m.id) }
                }
                marked++
            }
        }
        Result.Ok(marked)
    } catch (e: com.example.infrastructure.supabase.SyncPushTimeoutException) {
        Result.Err(Errors.timeout("chat.markRead: ${e.message}"))
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }
}
