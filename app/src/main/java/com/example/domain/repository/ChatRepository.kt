package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.ChatAttachment
import com.example.domain.model.ChatChannel
import com.example.domain.model.ChatMessage

/**
 * T-102-follow-up / ANDR-CHAT-200 — the Android chat repository
 * (v1: 21st session 2026-09-02; v2: 133rd session 2026-10-02).
 *
 * Scope decisions (recorded in the task entry):
 *   - The SERVER is the system of record; RLS scopes every query to the
 *     caller's visible rows. SENDS are ONLINE-ONLY forever (a queued send
 *     would need server-side channel-membership checks at drain time that
 *     the 0051/0061 triggers cannot give).
 *   - v2 added the Room READ cache (schema v17, the T-129 deferral closed):
 *     reads succeed online → the cache is refreshed; reads fail offline →
 *     the last cached content is served (Result.Ok) so the channel list
 *     and history survive cold starts; an EMPTY cache surfaces the honest
 *     Result.Err. The bound implementation is the caching decorator
 *     (CachedChatRepository) over the Supabase repository.
 *   - Channel CREATION is staff-only by design (ADR-008: parents see the
 *     channels staff open) — this repository does NOT create channels.
 */
interface ChatRepository {

    /**
     * The caller's active channels (membership via `member_ids` contains
     * profileId), archived hidden, ordered by last activity (desc,
     * nulls last). Mirrors the website's useChatChannels query.
     */
    suspend fun channels(profileId: String): Result<List<ChatChannel>>

    /**
     * The channel's non-deleted messages, oldest first. Mirrors the
     * website's useChatMessages query (deleted_at IS NULL, sent_at ASC).
     */
    suspend fun messages(channelId: String, limit: Int = 200): Result<List<ChatMessage>>

    /**
     * Count of unread messages across the caller's channels: latest
     * [window] messages (RLS-scoped to the caller's channels), unread =
     * no own entry in `read_by` and not authored by the caller. Mirrors
     * the website's useUnreadChatCount shape (WEAK-023's documented
     * 500-message window).
     */
    suspend fun unreadCount(profileId: String, window: Int = 500): Result<Int>

    /**
     * Per-channel unread counts derived from the LOCAL cache (v2): a
     * message is unread when it was not authored by [profileId] and has no
     * [profileId] entry in its read-receipt array. Default = no badges —
     * the caching decorator overrides this with the cache-derived map;
     * a cache-less implementation serves nothing (the online windowed
     * [unreadCount] remains the global source).
     */
    suspend fun unreadByChannel(profileId: String): Map<String, Int> = emptyMap()

    /**
     * Send a message to [channelId] (online only — failures surface as
     * Result.Err; the caller decides UX). Mirrors the website's insert:
     * own read-receipt pre-seeded.
     *
     * T-464 / MEDIA-300: [attachments] rides the insert (the jsonb array
     * of {file_name, storage_path, mime_type, size_bytes}); the CALLER
     * uploads the bytes to the chat-attachments bucket FIRST (the
     * StorageRepository) and passes the returned storage paths — a failed
     * upload must abort the send, never reference a missing file.
     */
    suspend fun send(
        channelId: String,
        authorProfileId: String,
        body: String,
        attachments: List<ChatAttachment> = emptyList(),
    ): Result<ChatMessage>

    /**
     * Append the caller's own read receipt to [messages] (the 0051
     * contract: a channel member may append their OWN entry; the
     * append-only guard trigger enforces server-side). Returns the count
     * of messages actually marked (already-read messages are skipped by
     * the caller).
     */
    suspend fun markRead(messages: List<ChatMessage>, profileId: String): Result<Int>
}
