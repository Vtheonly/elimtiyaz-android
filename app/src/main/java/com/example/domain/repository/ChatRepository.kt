package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.ChatChannel
import com.example.domain.model.ChatMessage

/**
 * T-102-follow-up / ANDR-CHAT-200 — the Android chat READ-SIDE + ONLINE
 * SENDS repository (v1, 21st session 2026-09-02).
 *
 * Scope decisions (recorded in the task entry):
 *   - READ + SEND are ONLINE-ONLY in v1: the canonical chat tables are
 *     live on the backend since migration 0061 and the desktop + website
 *     are live; the Android v1 mirrors the website's MessagesView
 *     semantics verbatim (channels by membership, ordering by
 *     last_message_at, messages asc, send = direct insert, markRead =
 *     append own read_by entry). NO Room cache: a Room schema bump (v11 →
 *     v12 + explicit migration + MigrationTestHelper, per the ARCH-004
 *     discipline) is deliberately deferred — offline chat history is a
 *     v2 decision, not a guess.
 *   - Channel CREATION is staff-only by design (ADR-008: parents see the
 *     channels staff open) — this repository does NOT create channels.
 *   - The server remains the system of record; RLS scopes every query to
 *     the caller's visible rows.
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
     * Send a message to [channelId] (online only — failures surface as
     * Result.Err; the caller decides UX). Mirrors the website's insert:
     * own read-receipt pre-seeded, attachments empty (v1 has no
     * attachments UI).
     */
    suspend fun send(channelId: String, authorProfileId: String, body: String): Result<ChatMessage>

    /**
     * Append the caller's own read receipt to [messages] (the 0051
     * contract: a channel member may append their OWN entry; the
     * append-only guard trigger enforces server-side). Returns the count
     * of messages actually marked (already-read messages are skipped by
     * the caller).
     */
    suspend fun markRead(messages: List<ChatMessage>, profileId: String): Result<Int>
}
