package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Chat channel (staff↔parent / staff↔staff messaging).
 *
 * Mirrors the canonical `chat_channels` table (migration 0010 + 0061
 * completion): ordering is by LAST ACTIVITY (`last_message_at`, maintained
 * by the 0061 touch trigger on every chat_messages insert), archived
 * channels are hidden from active lists, and membership is the
 * `member_ids` uuid[] (user_profiles.id, GIN-indexed).
 *
 * Ported from the website's ChatChannelRow (T-101 read path) and the
 * desktop's domain model — the same columns, the same semantics.
 */
@Serializable
data class ChatChannel(
    val id: String,
    val tenantId: String,
    val code: String,
    val name: String,
    val channelType: String,          // direct | group | department | announcement
    val memberIds: List<String>,
    val description: String? = null,
    val departmentId: String? = null,
    val archivedAt: String? = null,
    val lastMessageAt: String? = null,
    val lastMessagePreview: String? = null,
    val createdBy: String? = null,
    val createdAt: String? = null,
) {
    val isDirect: Boolean get() = channelType == "direct"
    val isAnnouncement: Boolean get() = channelType == "announcement"
}

/**
 * Chat message (canonical `chat_messages` table, migration 0010).
 *
 * `readBy` is the jsonb read-receipt array [{user_id, read_at}] — the
 * website's MessagesView appends the reader's own entry when the channel
 * is open (REALTIME-101; hub migration 0051 authorizes channel members to
 * append their own entry).
 */
@Serializable
data class ChatMessage(
    val id: String,
    val tenantId: String,
    val channelId: String,
    val authorId: String,
    val body: String,
    val sentAt: String,
    val readBy: List<ReadReceipt> = emptyList(),
    val deletedAt: String? = null,
    val editedAt: String? = null,
    val parentMessageId: String? = null,
) {
    @Serializable
    data class ReadReceipt(
        val userId: String,
        val readAt: String,
    )

    /** True when [userId] has an entry in the read-receipt array. */
    fun isReadBy(userId: String): Boolean = readBy.any { it.userId == userId }
}
