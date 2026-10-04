package com.example.infrastructure.sync

import com.example.core.Permission
import com.example.core.Role
import com.example.core.Session
import com.example.domain.repository.AuthRepository
import com.example.core.Result
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.session.SessionManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.infrastructure.supabase.SupabaseClientProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T-493 (SYNC-301) — the keyset-drain pagination contract.
 *
 * The live census (T-487): installments 5 963 / ledger 3 342 / payments 2 198 —
 * every pull used to cap at 2 000 rows, so the device computed the tranche
 * statistics (the owner: "The tranches are incorrect") on an arbitrary subset.
 * [PullSyncRepository.drainByCursor] is the loop that replaces every cap; this
 * suite pins its CONTRACT without touching the network:
 *
 *  1. a >page source drains to completion (the 5 963-installment class);
 *  2. a short page ends the drain (the common single-page path);
 *  3. the RPC tie-guard: a bulk-import timestamp tie larger than a page can
 *     never advance an INCLUSIVE cursor — the drain must STOP (not loop
 *     forever) and keep what it has;
 *  4. the page cap (the infinite-loop guard);
 *  5. an empty source / a null cursor edge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PullPaginationT493Test {

    private lateinit var db: ElImtiyazDatabase
    private lateinit var repo: PullSyncRepository

    private class NoopAuthRepository : AuthRepository {
        override suspend fun signIn(email: String, password: String): Result<Session> =
            Result.Err(com.example.core.Errors.unknown("noop"))
        override suspend fun signOut(): Result<Unit> = Result.Ok(Unit)
        override suspend fun refreshSession(): Result<Session?> = Result.Ok(null)
        override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = Result.Ok(Unit)
        override fun observeSession(): kotlinx.coroutines.flow.Flow<Session?> = kotlinx.coroutines.flow.flowOf(null)
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ElImtiyazDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sessionManager = SessionManager(NoopAuthRepository())
        sessionManager.setSession(
            Session(
                userId = "usr-test", tenantId = "tenant-1", email = "t@t.dz", displayName = "T",
                avatarUrl = null, role = Role.SUPER_ADMIN, permissions = Permission.entries.toSet(),
                accessToken = "tok", refreshToken = null,
                expiresAt = System.currentTimeMillis() + 3_600_000L, locale = "fr",
            ),
        )
        repo = PullSyncRepository(db, SupabaseClientProvider(context), sessionManager)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Rows carrying the two cursor shapes: a unique id (table path) or a shared updated_at (RPC path). */
    private data class Row(val id: String, val updatedAt: String?)

    // ─── 1. the full drain: a >page source reaches completion ────────────

    @Test
    fun `a 5963-row source drains fully at page size 1000`() = runTest {
        // The LIVE installments census — the exact scale the old limit(2000)
        // truncated to an arbitrary third.
        val source = (1..5_963).map { Row("row-$it", null) }
        var fetches = 0
        val drained = repo.drainByCursor(
            fetchPage = { cursor: String? ->
                fetches++
                val afterIndex = cursor?.let { it.removePrefix("row-").toInt() } ?: 0
                source.filter { it.id.removePrefix("row-").toInt() > afterIndex }.take(1_000)
            },
            cursorOf = { it.id },
            pageSize = 1_000,
        )
        assertEquals(5_963, drained.size)
        assertEquals(6, fetches) // 5 full pages + 1 short page (963)
        assertEquals(source.last(), drained.last())
    }

    @Test
    fun `a 2500-row source drains fully via 3 pages`() = runTest {
        val source = (1..2_500).map { Row("r-$it", null) }
        val drained = repo.drainByCursor(
            fetchPage = { cursor: String? ->
                val after = cursor?.let { it.removePrefix("r-").toInt() } ?: 0
                source.filter { it.id.removePrefix("r-").toInt() > after }.take(1_000)
            },
            cursorOf = { it.id },
            pageSize = 1_000,
        )
        assertEquals(2_500, drained.size)
    }

    // ─── 2. the short page: the common single-page path ──────────────────

    @Test
    fun `a short page ends the drain with one fetch`() = runTest {
        var fetches = 0
        val drained = repo.drainByCursor(
            fetchPage = { cursor: String? ->
                fetches++
                if (cursor == null) listOf(Row("a", null), Row("b", null)) else emptyList()
            },
            cursorOf = { it.id },
            pageSize = 1_000,
        )
        assertEquals(2, drained.size)
        assertEquals(1, fetches)
    }

    @Test
    fun `an exactly-page-sized source drains via 2 fetches`() = runTest {
        // A source of exactly pageSize: page 1 is FULL → a second (empty)
        // page must be requested before the drain ends.
        var fetches = 0
        val drained = repo.drainByCursor(
            fetchPage = { cursor: String? ->
                fetches++
                if (cursor == null) (1..1_000).map { Row("x-$it", null) } else emptyList()
            },
            cursorOf = { it.id },
            pageSize = 1_000,
        )
        assertEquals(1_000, drained.size)
        assertEquals(2, fetches)
    }

    // ─── 3. the RPC tie-guard: an inclusive cursor that cannot advance ───

    @Test
    fun `the tie-guard stops the drain when the cursor is stuck`() = runTest {
        // 5 963 rows ALL sharing one bulk-import updated_at (the T-420 Excel
        // import shape): an INCLUSIVE `>=` cursor re-fetches the same first
        // N rows forever. The guard must stop after the FIRST full page —
        // a uniform-cursor full page can never advance.
        val source = (1..5_963).map { Row("t-$it", "2026-09-30T23:00:40Z") }
        var fetches = 0
        val drained = repo.drainByCursor(
            fetchPage = { cursor: String? ->
                fetches++
                // The server shape: updated_at >= cursor (inclusive!) LIMIT page.
                val since = cursor ?: "1970-01-01T00:00:00Z"
                source.filter { (it.updatedAt ?: "") >= since }.take(1_000)
            },
            cursorOf = { it.updatedAt },
            pageSize = 1_000,
        )
        // Honest partial: the first page only, no infinite loop, no refetch.
        assertEquals(1_000, drained.size)
        assertEquals(1, fetches)
    }

    @Test
    fun `distinct timestamps paginate past the tie`() = runTest {
        // Distinct updated_at values: the cursor advances normally.
        val source = (1..2_500).map { Row("u-$it", "2026-09-30T23:00:%04dZ".format(it)) }
        val drained = repo.drainByCursor(
            fetchPage = { cursor: String? ->
                val since = cursor ?: "1970-01-01T00:00:00Z"
                source.filter { (it.updatedAt ?: "") >= since }.take(1_000)
            },
            cursorOf = { it.updatedAt },
            pageSize = 1_000,
        )
        // Inclusive cursors re-fetch the boundary row each page (page 2
        // re-fetches row 1000, page 3 re-fetches row 1999) — the drain still
        // COMPLETES and the Room upsert is idempotent, so the boundary
        // duplicates are harmless by design (2 500 unique + 2 re-fetched).
        assertEquals(2_502, drained.size)
        assertEquals(source.last(), drained.last())
    }

    // ─── 4. the page cap: the infinite-loop guard ────────────────────────

    @Test
    fun `the page cap bounds the drain`() = runTest {
        var fetches = 0
        val drained = repo.drainByCursor(
            fetchPage = { cursor: String? ->
                fetches++
                // A full single-row page whose cursor ADVANCES every fetch —
                // only the page cap can end this drain.
                listOf(Row("cap-$fetches", "cursor-$fetches"))
            },
            cursorOf = { it.updatedAt },
            pageSize = 1,
            maxPages = 5,
        )
        assertEquals(5, drained.size) // cap × 1 row/page
        assertEquals(5, fetches)
    }

    // ─── 5. the edges ────────────────────────────────────────────────────

    @Test
    fun `an empty source returns an empty drain`() = runTest {
        val drained = repo.drainByCursor(
            fetchPage = { _: String? -> emptyList<Row>() },
            cursorOf = { it.id },
            pageSize = 1_000,
        )
        assertEquals(0, drained.size)
    }

    @Test
    fun `a null cursor on a full page stops honestly`() = runTest {
        // Defensive: a DTO row whose cursor field is null (server schema
        // drift) — the drain must not NPE or loop; it keeps the page.
        var fetches = 0
        val drained = repo.drainByCursor(
            fetchPage = { cursor: String? ->
                fetches++
                if (cursor == null) (1..1_000).map { Row("n-$it", null) } else emptyList()
            },
            cursorOf = { it.updatedAt }, // null cursor → stop
            pageSize = 1_000,
        )
        assertEquals(1_000, drained.size)
        assertEquals(1, fetches)
    }
}
