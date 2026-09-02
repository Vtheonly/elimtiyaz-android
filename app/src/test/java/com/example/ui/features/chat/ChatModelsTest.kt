package com.example.ui.features.chat

import com.example.domain.model.ChatChannel
import com.example.domain.model.ChatMessage
import com.example.infrastructure.supabase.ChatChannelDto
import com.example.infrastructure.supabase.ChatMessageDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-102-follow-up / ANDR-CHAT-200 — chat model + DTO mapping tests.
 *
 * The DTO layer bridges the canonical chat tables (migrations 0010 +
 * 0051 + 0061) to the domain models; the read-receipt array (`read_by`
 * jsonb) parsing is the risky part — a malformed row must degrade to
 * "unread", never crash the screen.
 */
class ChatModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `chat channel row maps to the domain model`() {
        val row = """
            {
              "id": "c1", "tenant_id": "t1", "code": "dm-abc", "name": "M. MAMER",
              "channel_type": "direct", "member_ids": ["u1", "u2"],
              "description": null, "department_id": null, "archived_at": null,
              "last_message_at": "2026-09-02T10:00:00Z",
              "last_message_preview": "Bonjour",
              "created_by": "u2", "created_at": "2026-08-31T09:00:00Z"
            }
        """.trimIndent()
        val dto = json.decodeFromString(ChatChannelDto.serializer(), row)
        val domain = dto.toDomain()
        assertEquals("c1", domain.id)
        assertEquals("direct", domain.channelType)
        assertEquals(listOf("u1", "u2"), domain.memberIds)
        assertEquals("Bonjour", domain.lastMessagePreview)
        assertTrue(domain.isDirect)
        assertFalse(domain.isAnnouncement)
    }

    @Test
    fun `announcement channels flag correctly`() {
        val dto = ChatChannelDto(
            id = "c2", tenantId = "t1", code = "ann-1", name = "Annonces",
            channelType = "announcement", memberIds = emptyList(),
        )
        assertTrue(dto.toDomain().isAnnouncement)
        assertFalse(dto.toDomain().isDirect)
    }

    @Test
    fun `read_by parses valid receipts`() {
        val readBy = buildJsonArray {
            add(
                buildJsonObject {
                    put("user_id", "u1")
                    put("read_at", "2026-09-02T10:05:00Z")
                },
            )
            add(
                buildJsonObject {
                    put("user_id", "u2")
                    put("read_at", "2026-09-02T10:06:00Z")
                },
            )
        }
        val dto = ChatMessageDto(
            id = "m1", tenantId = "t1", channelId = "c1", authorId = "u1",
            body = "Bonjour", sentAt = "2026-09-02T10:00:00Z", readBy = readBy,
        )
        val domain = dto.toDomain()
        assertEquals(2, domain.readBy.size)
        assertTrue(domain.isReadBy("u1"))
        assertTrue(domain.isReadBy("u2"))
        assertFalse(domain.isReadBy("u3"))
    }

    @Test
    fun `a null read_by degrades to unread`() {
        val dto = ChatMessageDto(
            id = "m2", tenantId = "t1", channelId = "c1", authorId = "u1",
            body = "Salut", sentAt = "2026-09-02T10:00:00Z", readBy = null,
        )
        assertEquals(emptyList<ChatMessage.ReadReceipt>(), dto.toDomain().readBy)
    }

    @Test
    fun `a malformed read_by entry is skipped without crashing`() {
        val malformed = buildJsonArray {
            add(buildJsonObject { put("wrong_key", "x") })          // no user_id
            add(kotlinx.serialization.json.JsonPrimitive("not an object"))
            add(buildJsonObject { put("user_id", "u9") })          // no read_at — still valid
        }
        val dto = ChatMessageDto(
            id = "m3", tenantId = "t1", channelId = "c1", authorId = "u2",
            body = "x", sentAt = "2026-09-02T10:00:00Z", readBy = malformed,
        )
        val receipts = dto.toDomain().readBy
        assertEquals(listOf("u9"), receipts.map { it.userId })
    }
}
