package com.example.infrastructure.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.Permission
import com.example.core.Result
import com.example.core.Role
import com.example.core.Session
import com.example.domain.repository.AuthRepository
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.session.SessionManager
import com.example.infrastructure.supabase.SupabaseClientProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * T-497 (SYNC-304) — the composite (updated_at, id) keyset contracts.
 *
 * The 148th session's live pins: the four `pull_*_for_sync` RPCs paginated
 * on a NON-UNIQUE cursor (`updated_at` alone — or, on the ledger, the
 * BUSINESS date COALESCE(at, entry_date, created_at), with the cursor
 * column not even returned). Three concrete failure modes, all pinned live:
 *
 *  1. THE STRADDLE SKIP (exclusive cursors — parents/students): a tie group
 *     straddling a page boundary loses every tied row after the boundary.
 *     Live: 4 student tie pairs (8 rows) sit exactly one boundary landing
 *     away from this.
 *  2. THE UNIFORM-GROUP STUCK (inclusive cursors — payments/ledger): a tie
 *     group larger than the page can never advance (the tie-guard stops
 *     honestly — partial forever). Live: ALL 2 198 payments share ONE
 *     frozen bulk-backfill timestamp; live ledger: 1 111 tie groups on the
 *     business-date key.
 *  3. THE NULL CURSOR (ledger): the RPC never returned `updated_at`, so
 *     the drain's cursor was NULL on every row — the silent single-page
 *     truncation the moment the table exceeds one page.
 *
 * THE FIX: migration 0143 (each RPC gains `p_after_id` + the composite
 * branch + the deterministic ORDER BY sort_key, id) and this client's
 * [PullSyncRepository.SyncKeyset] cursor. The fake server below implements
 * the 0143 server contract exactly — rows filter by
 * `(updated_at, id) > (p_since, p_after_id)` — so the drain contracts are
 * proven against the real semantics, not a mock of them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PullKeysetT497Test {

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

    /** A row carrying the composite cursor legs exactly like the DTOs do. */
    private data class KeyedRow(val id: String, val updatedAt: String?)

    /**
     * The 0143 server contract, in memory: sort by (updated_at, id), filter
     * by the composite keyset `(p_since, p_after_id)`. Records every call's
     * parameters so the call-shape contracts can assert on them.
     */
    private class KeysetServer(val rows: List<KeyedRow>) {
        data class Call(val since: String?, val afterId: String?)

        val calls = mutableListOf<Call>()

        fun page(pSince: String?, pAfterId: String?, limit: Int): List<KeyedRow> {
            calls += Call(pSince, pAfterId)
            val filtered = rows.filter { r ->
                val u = r.updatedAt ?: return@filter false
                when {
                    pSince == null -> true
                    u > pSince -> true
                    u == pSince -> pAfterId != null && r.id > pAfterId
                    else -> false
                }
            }
            return filtered.sortedWith(compareBy({ it.updatedAt }, { it.id })).take(limit)
        }
    }

    // ─── 1. THE STRADDLE SKIP — the fix's headline contract ─────────────

    @Test
    fun `a tie group straddling the page boundary drains fully under the composite keyset`() = runTest {
        // 10 rows: 6 distinct timestamps, then a 4-row TIE GROUP at T5 — the
        // page size 8 lands the boundary INSIDE the tie group (rows 7-8 of
        // the group in page 1, rows 9-10 in page 2). Under the OLD exclusive
        // single-cursor contract page 2 would ask `updated_at > T5` and get
        // NOTHING — rows 9-10 silently skipped forever.
        val tie = "2026-09-15T10:00:00Z"
        val rows = (1..6).map { i -> KeyedRow("id-%02d".format(i), "2026-09-01T0$i:00:00Z") } +
            listOf("id-07", "id-08", "id-09", "id-10").map { KeyedRow(it, tie) }
        val server = KeysetServer(rows)

        val drained = repo.drainByCursor(
            fetchPage = { cursor: PullSyncRepository.SyncKeyset? ->
                server.page(cursor?.since, cursor?.afterId, 8)
            },
            cursorOf = { row -> row.updatedAt?.let { PullSyncRepository.SyncKeyset(it, row.id) } },
            pageSize = 8,
        )

        assertEquals("the straddled tie group must drain FULLY (was a silent skip)", 10, drained.size)
        assertEquals("no duplicates in the drain", 10, drained.distinctBy { it.id }.size)
    }

    // ─── 2. THE UNIFORM GROUP — larger than the page ─────────────────────

    @Test
    fun `a uniform tie group larger than the page drains fully by id advance`() = runTest {
        // The live payments shape: ONE timestamp shared by every row — the
        // frozen 2 198-row bulk backfill. Under the OLD inclusive contract
        // the uniform full page could never advance (the tie-guard stopped
        // partial); the composite keyset advances on the id leg.
        val frozen = "2026-09-15T15:47:20.541752+00:00"
        val rows = (1..20).map { i -> KeyedRow("id-%02d".format(i), frozen) }
        val server = KeysetServer(rows)

        val drained = repo.drainByCursor(
            fetchPage = { cursor: PullSyncRepository.SyncKeyset? ->
                server.page(cursor?.since, cursor?.afterId, 6)
            },
            cursorOf = { row -> row.updatedAt?.let { PullSyncRepository.SyncKeyset(it, row.id) } },
            pageSize = 6,
        )

        assertEquals("the uniform group must drain FULLY (was a stuck partial)", 20, drained.size)
        assertEquals("every row exactly once", 20, drained.distinctBy { it.id }.size)
    }

    // ─── 3. THE CALL SHAPE — p_after_id on every page after the first ───

    @Test
    fun `every page after the first carries the p_after_id leg`() = runTest {
        val tie = "2026-09-15T10:00:00Z"
        val rows = (1..6).map { i -> KeyedRow("id-%02d".format(i), "2026-09-01T0$i:00:00Z") } +
            listOf("id-07", "id-08", "id-09", "id-10").map { KeyedRow(it, tie) }
        val server = KeysetServer(rows)

        repo.drainByCursor(
            fetchPage = { cursor: PullSyncRepository.SyncKeyset? ->
                server.page(cursor?.since, cursor?.afterId, 8)
            },
            cursorOf = { row -> row.updatedAt?.let { PullSyncRepository.SyncKeyset(it, row.id) } },
            pageSize = 8,
        )

        assertTrue("the drain must paginate", server.calls.size >= 2)
        assertEquals("the FIRST page carries no p_after_id (the epoch sweeps)", null, server.calls.first().afterId)
        server.calls.drop(1).forEach { call ->
            assertNotNull("every subsequent page must carry the p_after_id leg (was the straddle skip)", call.afterId)
        }
        // The cursor advances strictly — no page repeats the previous cursor.
        val pairs = server.calls.mapNotNull { c -> c.since?.let { it to (c.afterId ?: "") } }
        assertEquals("no repeated composite cursors", pairs.size, pairs.distinct().size)
    }

    // ─── 4. THE NULL CURSOR — honest stop, never a rewind ────────────────

    @Test
    fun `a row without the cursor column stops the drain after its page`() = runTest {
        // The pre-0143 ledger shape: the RPC never returned updated_at —
        // cursorOf yields null and the drain must STOP (never rewind, never
        // loop). Under T-493 this stop was SILENT; since T-497 it logs (the
        // contract pinned here: the drain returns the page it has, honestly
        // partial).
        val rows = (1..12).map { i -> KeyedRow("id-%02d".format(i), null) }
        var fetches = 0

        val drained = repo.drainByCursor(
            fetchPage = { _: PullSyncRepository.SyncKeyset? ->
                fetches++
                rows.take(5)
            },
            cursorOf = { row -> row.updatedAt?.let { PullSyncRepository.SyncKeyset(it, row.id) } },
            pageSize = 5,
        )

        assertEquals("the NULL cursor stops after the page it has (honest partial)", 5, drained.size)
        assertEquals("exactly one fetch — no rewind, no loop", 1, fetches)
    }

    // ─── 5. THE WIRING — all four RPC paths pass the composite legs ──────

    @Test
    fun `source scan - all four RPC call sites pass p_after_id and the SyncKeyset cursor`() {
        val src = File("src/main/java/com/example/infrastructure/sync/PullSyncRepository.kt")
        assertTrue("missing PullSyncRepository.kt", src.exists())
        val text = src.readText()

        // The composite cursor type is wired on all four RPC paths.
        assertEquals(
            "all four RPC call sites must extract the SyncKeyset cursor",
            4,
            Regex("cursorOf = \\{ row -> row\\.updatedAt\\?\\.let \\{ SyncKeyset\\(it, row\\.id\\) \\} \\}").findAll(text).count(),
        )
        // p_after_id is passed on all four paths (the `cursor?.afterId?.let` leg).
        assertEquals(
            "all four RPC call sites must pass p_after_id",
            4,
            Regex("cursor\\?\\.afterId\\?\\.let \\{ put\\(\"p_after_id\", it\\) \\}").findAll(text).count(),
        )
        // The four RPC names still drained.
        for (rpc in listOf(
            "pull_parents_for_sync", "pull_students_for_sync",
            "pull_payments_for_sync", "pull_ledger_entries_for_sync",
        )) {
            assertTrue("the $rpc drain went missing", text.contains("\"$rpc\""))
        }
        // The drain is generic over the cursor type (the table paths keep
        // their plain id cursor — the generalization must not force it).
        assertTrue(
            "the drain must be generic over the cursor type",
            text.contains("internal suspend fun <T : Any, C : Any> drainByCursor("),
        )
        // The SyncKeyset definition exists.
        assertTrue(text.contains("data class SyncKeyset(val since: String, val afterId: String)"))
    }
}
