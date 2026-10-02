package com.example.infrastructure.local

import com.example.core.Result
import com.example.domain.model.ChatChannel
import com.example.domain.model.ChatMessage
import com.example.domain.repository.ChatRepository
import com.example.infrastructure.room.ChatChannelEntity
import com.example.infrastructure.room.ChatDao
import com.example.infrastructure.room.ChatMessageEntity
import javax.inject.Singleton

/**
 * T-102 chat v2 (133rd session, ANDR-CHAT-200 residual) — the Room READ
 * cache over the online-authoritative chat repository (the T-129 "v2
 * session" deferral, closed; schema v17).
 *
 * Cache policy (stale-while-error, the app-wide Room-first doctrine):
 *  - A successful online read REFRESHES the cache (replace-style: the fresh
 *    server pull IS the caller's full channel set / the channel's full
 *    message window — keeping the cache honest, same semantics as the
 *    notification pull's eviction).
 *  - A FAILED online read serves the last cached content (Result.Ok) so the
 *    channel list and conversation history survive cold starts offline;
 *    only an EMPTY cache surfaces the honest Result.Err.
 *  - SENDS and read-receipts stay ONLINE-ONLY (fail visibly — a queued send
 *    cannot be drained later because the 0051/0061 server triggers must
 *    see the caller's live JWT); their SUCCESSFUL results write through to
 *    the cache so the local view updates without a re-fetch.
 *
 * Constructed by SupabaseModule.provideChatRepository (the @Provides
 * factory injects the SupabaseChatRepository + the DB); depending on the
 * ChatRepository INTERFACE keeps the decorator unit-testable with a fake
 * remote. NOT a Local* repository: chat never joins the sync-queue write
 * path; the server remains the system of record.
 */
@Singleton
class CachedChatRepository(
    private val remote: ChatRepository,
    private val chatDao: ChatDao,
) : ChatRepository {

    override suspend fun channels(profileId: String): Result<List<ChatChannel>> {
        when (val fresh = remote.channels(profileId)) {
            is Result.Ok -> {
                chatDao.clearChannels()
                chatDao.upsertChannels(fresh.value.map { ChatChannelEntity.fromDomain(it) })
                return fresh
            }
            is Result.Err -> {
                val cached = chatDao.channels().map { it.toDomain() }
                // Offline cold start with history → serve it. No history →
                // the honest error (the operator sees the truth, never a
                // silently empty list — the v1 contract preserved).
                return if (cached.isNotEmpty()) Result.Ok(cached) else fresh
            }
        }
    }

    override suspend fun messages(channelId: String, limit: Int): Result<List<ChatMessage>> {
        when (val fresh = remote.messages(channelId, limit)) {
            is Result.Ok -> {
                chatDao.clearMessages(channelId)
                chatDao.upsertMessages(fresh.value.map { ChatMessageEntity.fromDomain(it) })
                return fresh
            }
            is Result.Err -> {
                val cached = chatDao.messages(channelId, limit).map { it.toDomain() }
                return if (cached.isNotEmpty()) Result.Ok(cached) else fresh
            }
        }
    }

    /**
     * The windowed global count stays a LIVE query (WEAK-023's documented
     * shape needs the server's cross-channel window; the cache cannot answer
     * it faithfully). Offline → the badge simply hides (the caller treats
     * Err as "unknown", never as a fabricated 0).
     */
    override suspend fun unreadCount(profileId: String, window: Int): Result<Int> =
        remote.unreadCount(profileId, window)

    /** Per-channel unread badges, derived from the cached messages. */
    override suspend fun unreadByChannel(profileId: String): Map<String, Int> =
        countUnreadByChannel(chatDao.allMessages().map { it.toDomain() }, profileId)

    override suspend fun send(
        channelId: String,
        authorProfileId: String,
        body: String,
    ): Result<ChatMessage> {
        val sent = remote.send(channelId, authorProfileId, body)
        if (sent is Result.Ok) {
            // Write-through: the local view updates without a re-fetch
            // (the ChatDetail optimistic append also keeps its copy).
            chatDao.upsertMessages(listOf(ChatMessageEntity.fromDomain(sent.value)))
        }
        return sent
    }

    override suspend fun markRead(
        messages: List<ChatMessage>,
        profileId: String,
    ): Result<Int> {
        val marked = remote.markRead(messages, profileId)
        if (marked is Result.Ok) {
            // Write-through: append the caller's own receipt to the cached
            // rows so the per-channel unread badges clear without a refresh.
            val updated = messages.map { m ->
                ChatMessageEntity.fromDomain(
                    m.copy(readBy = m.readBy + ChatMessage.ReadReceipt(profileId, nowIso()))
                )
            }
            chatDao.upsertMessages(updated)
        }
        return marked
    }

    private fun nowIso(): String = java.time.Instant.now().toString()
}

/**
 * The pure per-channel unread derivation (v2): a message is unread when it
 * was NOT authored by [profileId] and carries no [profileId] read receipt.
 * Top-level so the badge contract is unit-testable without Room.
 */
internal fun countUnreadByChannel(
    messages: List<ChatMessage>,
    profileId: String,
): Map<String, Int> =
    messages.asSequence()
        .filter { it.authorId != profileId && !it.isReadBy(profileId) }
        .groupingBy { it.channelId }
        .eachCount()
