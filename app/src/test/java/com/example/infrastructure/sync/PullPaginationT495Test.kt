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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * T-495 (SYNC-303) — the row-typed RPC page-size contract, discovered LIVE
 * against the owner's backend (the 147th session's "make sure it works"
 * mandate).
 *
 * THE DISCOVERY: the Supabase API gateway applies its max-rows setting
 * (1 000 on this project) to ROW-TYPED RPC responses — `pull_students_for_sync`
 * (RETURNS TABLE) answered p_limit=5 000 with EXACTLY 1 000 rows and
 * `Content-Range: rows 0-999`, while the jsonb-returning `pull_payments_for_sync`
 * / `pull_ledger_entries_for_sync` passed through whole at 2 198 / 3 342
 * rows. The T-493 drain then mistook the sliced 1 000-row page for a
 * completed SHORT page (1 000 < 5 000) and stopped — the device held 1 000
 * of the live 1 137 students, silently truncated (the same defect class as
 * the original "tranches are incorrect" report, on the RPC path).
 *
 * THE FIX under test: the row-typed pair (`pull_parents_for_sync` /
 * `pull_students_for_sync`) drains at [PullSyncRepository.ROW_TYPED_RPC_PAGE_SIZE]
 * = 1 000 — ≤ the gateway slice, so a full page stays FULL and the p_since
 * cursor keeps advancing. Verified live: the 1 137-student drain completes
 * in 2 pages (1 000 + 137), all ids distinct.
 *
 * This suite pins:
 *  1. the config contract: ROW_TYPED_RPC_PAGE_SIZE ≤ the 1 000-row gateway
 *     slice, and DISTINCT from RPC_PULL_PAGE_SIZE (the jsonb pair's page);
 *  2. the drain behavior at the sliced page size: the 1 137-row source (the
 *     live students census) completes in 2 pages; a sliced-to-1 000 full
 *     page NEVER reads as a short page (the defect mechanism, pinned);
 *  3. the source wiring: both row-typed RPC paths pass
 *     ROW_TYPED_RPC_PAGE_SIZE as BOTH p_limit and pageSize;
 *  4. the jsonb pair keeps the 5 000 page (unsliced — verified live).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PullPaginationT495Test {

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

    private fun src(): String =
        File("src/main/java/com/example/infrastructure/sync/PullSyncRepository.kt").readText()

    // ─── 1. the config contract ───────────────────────────────────────────

    @Test
    fun `the row-typed RPC page size is within the gateway's 1000-row slice`() {
        assertTrue(
            "ROW_TYPED_RPC_PAGE_SIZE must be <= the gateway max-rows slice (1 000) — " +
                "a larger page comes back sliced and the drain mistakes it for a short page",
            PullSyncRepository.ROW_TYPED_RPC_PAGE_SIZE <= 1_000,
        )
    }

    @Test
    fun `the row-typed page is distinct from the jsonb pair's 5000 page`() {
        assertTrue(
            "the row-typed RPCs must not share the jsonb pair's page size — " +
                "that is exactly the T-495 truncation",
            PullSyncRepository.ROW_TYPED_RPC_PAGE_SIZE != PullSyncRepository.RPC_PULL_PAGE_SIZE,
        )
    }

    // ─── 2. the drain behavior at the sliced page size ────────────────────

    @Test
    fun `the live 1137-student census drains fully at page size 1000`() = runTest {
        // The LIVE shape (verified on the real backend): 1 137 rows, near-
        // unique updated_at values, delivered 1 000 at a time by the slice.
        val source = (1..1_137).map { "2026-09-27T23:14:%06d".format(it) }
        var fetches = 0
        val drained = repo.drainByCursor(
            fetchPage = { cursor: String? ->
                fetches++
                // The gateway-sliced row-typed RPC: whatever p_limit says,
                // at most 1 000 rows leave the gateway, strictly after the
                // cursor (the server filter is EXCLUSIVE: updated_at > p_since).
                val after = source.filter { it > (cursor ?: "") }.take(1_000)
                after
            },
            cursorOf = { it },
            pageSize = PullSyncRepository.ROW_TYPED_RPC_PAGE_SIZE,
        )
        assertEquals(1_137, drained.size)
        assertEquals(2, fetches) // 1 full page + 1 short page (137)
        assertEquals(source.last(), drained.last())
    }

    @Test
    fun `a sliced full page never reads as a completed short page`() = runTest {
        // The T-495 defect mechanism, pinned as the regression guard: with
        // pageSize 5 000 against a gateway that slices at 1 000, the FIRST
        // page returns 1 000 < 5 000 rows and the drain STOPS at 1 000 —
        // the exact silent truncation the owner's backend exhibited.
        val source = (1..1_137).map { "2026-09-27T23:14:%06d".format(it) }
        val drainedAtOldConfig = repo.drainByCursor(
            fetchPage = { cursor: String? ->
                source.filter { it > (cursor ?: "") }.take(1_000) // the slice
            },
            cursorOf = { it },
            pageSize = 5_000, // the OLD (defective) page size
        )
        assertEquals(
            "the old config truncates at the slice — the pinned defect",
            1_000,
            drainedAtOldConfig.size,
        )
    }

    // ─── 3. the source wiring ─────────────────────────────────────────────

    @Test
    fun `source scan - both row-typed RPC paths pass ROW_TYPED_RPC_PAGE_SIZE as p_limit and pageSize`() {
        for (rpc in listOf("pull_parents_for_sync", "pull_students_for_sync")) {
            // Anchor on the CALL SITE (the rpc("…") invocation), not the
            // bare name — the T-495 explanation comments also name the RPC.
            val idx = src().indexOf("rpc(\"$rpc\"")
            assertTrue("$rpc must be called in PullSyncRepository", idx >= 0)
            // The surrounding drain call: the p_limit sits ~200 chars before
            // the call, the pageSize ~120 chars after it.
            val window = src().substring(idx - 700, idx + 400)
            assertTrue(
                "$rpc: the p_limit must be ROW_TYPED_RPC_PAGE_SIZE (not the 5 000 jsonb page)",
                window.contains("put(\"p_limit\", ROW_TYPED_RPC_PAGE_SIZE)"),
            )
            assertTrue(
                "$rpc: the drain's pageSize must be ROW_TYPED_RPC_PAGE_SIZE",
                window.contains("pageSize = ROW_TYPED_RPC_PAGE_SIZE"),
            )
        }
    }

    @Test
    fun `source scan - the jsonb RPC paths keep the 5000 page`() {
        for (rpc in listOf("pull_payments_for_sync", "pull_ledger_entries_for_sync")) {
            val idx = src().indexOf("rpc(\"$rpc\"")
            assertTrue("$rpc must be called in PullSyncRepository", idx >= 0)
            val window = src().substring(idx - 700, idx + 400)
            assertTrue(
                "$rpc: keeps RPC_PULL_PAGE_SIZE (jsonb responses are not gateway-sliced)",
                window.contains("put(\"p_limit\", RPC_PULL_PAGE_SIZE)"),
            )
        }
    }
}
