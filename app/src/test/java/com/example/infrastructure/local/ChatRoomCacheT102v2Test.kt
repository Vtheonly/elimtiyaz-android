package com.example.infrastructure.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.Errors
import com.example.core.Result
import com.example.domain.model.ChatChannel
import com.example.domain.model.ChatMessage
import com.example.domain.repository.ChatRepository
import com.example.infrastructure.room.ChatChannelEntity
import com.example.infrastructure.room.ChatMessageEntity
import com.example.infrastructure.room.ElImtiyazDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T-102 chat v2 (133rd session, ANDR-CHAT-200 residual) — the Room-caching
 * decorator's contract on a REAL in-memory database (the fake remote +
 * the real DAO; the PullCompletenessT039Test in-memory convention).
 *
 * Behaviour under test (the stale-while-error policy):
 *  1. a successful online read REFRESHES the cache (replace-style);
 *  2. a FAILED online read serves the cached content when history exists;
 *  3. a FAILED online read with an EMPTY cache surfaces the honest Err;
 *  4. sends + read-receipts write through to the cache on success;
 *  5. unreadByChannel derives per-channel unread counts from the cache
 *     (the pure [countUnreadByChannel] contract, exercised through the DAO).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatRoomCacheT102v2Test {

    private lateinit var db: ElImtiyazDatabase
    private lateinit var remote: FakeRemoteChatRepository
    private lateinit var repo: CachedChatRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ElImtiyazDatabase::class.java,
        ).allowMainThreadQueries().build()
        remote = FakeRemoteChatRepository()
        repo = CachedChatRepository(remote, db.chatDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun channel(id: String, name: String = "Chan $id") = ChatChannel(
        id = id, tenantId = "t1", code = "CHAT-$id", name = name, channelType = "direct",
        memberIds = listOf("me", "other"),
    )

    private fun message(
        id: String,
        channelId: String,
        authorId: String,
        readBy: List<ChatMessage.ReadReceipt> = emptyList(),
    ) = ChatMessage(
        id = id, tenantId = "t1", channelId = channelId, authorId = authorId,
        body = "body-$id", sentAt = "2026-10-02T09:00:0${id.last()}Z", readBy = readBy,
    )

    // ── 1. online success refreshes the cache ───────────────────────────────

    @Test
    fun `online channels success replaces the cache`() = runTest {
        remote.channelsResult = Result.Ok(listOf(channel("c1"), channel("c2")))
        val r = repo.channels("me")
        assertTrue(r is Result.Ok)
        assertEquals(2, db.chatDao().channels().size)
    }

    @Test
    fun `online messages success replaces the channel window`() = runTest {
        db.chatDao().upsertMessages(
            listOf(ChatMessageEntity.fromDomain(message("stale", "c1", "other"))),
        )
        remote.messagesResult = Result.Ok(listOf(message("m1", "c1", "other")))
        val r = repo.messages("c1")
        assertTrue(r is Result.Ok)
        val ids = db.chatDao().messages("c1", 200).map { it.id }
        assertEquals(listOf("m1"), ids) // the stale row is GONE — replace, not merge
    }

    // ── 2. offline failure serves the cache ─────────────────────────────────

    @Test
    fun `offline channels failure serves the cached content`() = runTest {
        remote.channelsResult = Result.Ok(listOf(channel("c1", "Famille Benali")))
        repo.channels("me") // populate the cache
        remote.channelsResult = Result.Err(Errors.timeout("offline"))
        val r = repo.channels("me")
        assertTrue("history must be served (stale-while-error)", r is Result.Ok)
        assertEquals("Famille Benali", (r as Result.Ok).value.single().name)
    }

    @Test
    fun `offline messages failure serves the cached conversation`() = runTest {
        remote.messagesResult = Result.Ok(listOf(message("m1", "c1", "other")))
        repo.messages("c1") // populate the cache
        remote.messagesResult = Result.Err(Errors.timeout("offline"))
        val r = repo.messages("c1")
        assertTrue(r is Result.Ok)
        assertEquals(listOf("m1"), (r as Result.Ok).value.map { it.id })
    }

    // ── 3. empty cache surfaces the honest error ────────────────────────────

    @Test
    fun `offline channels failure with an EMPTY cache surfaces the honest Err`() = runTest {
        remote.channelsResult = Result.Err(Errors.timeout("offline"))
        val r = repo.channels("me")
        assertTrue("no history → the operator sees the truth, never a silent empty list", r is Result.Err)
    }

    // ── 4. sends + receipts write through ───────────────────────────────────

    @Test
    fun `send success writes through to the cache`() = runTest {
        remote.sendResult = Result.Ok(message("m9", "c1", "me"))
        val r = repo.send("c1", "me", "Bonjour")
        assertTrue(r is Result.Ok)
        assertTrue(db.chatDao().messages("c1", 200).any { it.id == "m9" })
    }

    @Test
    fun `markRead success appends the caller receipt to the cached rows`() = runTest {
        val incoming = message("m1", "c1", "other")
        db.chatDao().upsertMessages(listOf(ChatMessageEntity.fromDomain(incoming)))
        remote.markReadResult = Result.Ok(1)
        val r = repo.markRead(listOf(incoming), "me")
        assertTrue(r is Result.Ok)
        val cached = db.chatDao().messages("c1", 200).single().toDomain()
        assertTrue("the cached row now carries the caller's receipt", cached.isReadBy("me"))
    }

    // ── 5. the unread derivation ─────────────────────────────────────────────

    @Test
    fun `unreadByChannel counts non-authored unread messages per channel`() = runTest {
        val meRead = ChatMessage.ReadReceipt("me", "2026-10-02T09:00:00Z")
        db.chatDao().upsertMessages(
            listOf(
                ChatMessageEntity.fromDomain(message("a", "c1", "other")),               // unread
                ChatMessageEntity.fromDomain(message("b", "c1", "other", listOf(meRead))), // read → not counted
                ChatMessageEntity.fromDomain(message("c", "c1", "me")),                  // own → not counted
                ChatMessageEntity.fromDomain(message("d", "c2", "other")),               // unread (other channel)
                ChatMessageEntity.fromDomain(message("e", "c2", "other")),               // unread (other channel)
            ),
        )
        val counts = repo.unreadByChannel("me")
        assertEquals(1, counts["c1"])
        assertEquals(2, counts["c2"])
        assertEquals(null, counts["c3"]) // no fabricated zeros for untouched channels
    }

    @Test
    fun `unreadByChannel on an empty cache is an empty map`() = runTest {
        assertTrue(repo.unreadByChannel("me").isEmpty())
    }

    @Test
    fun `unreadCount passes through to the remote (the live windowed query)`() = runTest {
        remote.unreadCountResult = Result.Ok(7)
        val r = repo.unreadCount("me")
        assertTrue(r is Result.Ok)
        assertEquals(7, (r as Result.Ok).value)
    }

    /** Scriptable in-memory remote — the online-authoritative half. */
    private class FakeRemoteChatRepository : ChatRepository {
        var channelsResult: Result<List<ChatChannel>> = Result.Ok(emptyList())
        var messagesResult: Result<List<ChatMessage>> = Result.Ok(emptyList())
        var unreadCountResult: Result<Int> = Result.Ok(0)
        var sendResult: Result<ChatMessage> =
            Result.Err(Errors.unknown("not scripted"))
        var markReadResult: Result<Int> = Result.Ok(0)

        override suspend fun channels(profileId: String) = channelsResult
        override suspend fun messages(channelId: String, limit: Int) = messagesResult
        override suspend fun unreadCount(profileId: String, window: Int) = unreadCountResult
        override suspend fun send(
            channelId: String,
            authorProfileId: String,
            body: String,
            attachments: List<com.example.domain.model.ChatAttachment>,
        ) = sendResult
        override suspend fun markRead(messages: List<ChatMessage>, profileId: String) = markReadResult
    }
}
