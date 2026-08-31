package com.example.infrastructure.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.Permission
import com.example.session.SessionManager
import com.example.core.Result
import com.example.core.Role
import com.example.infrastructure.room.AuditLogDao
import com.example.infrastructure.room.AuditLogEntity
import com.example.infrastructure.supabase.SupabaseClientProvider
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T-002 regression suite — SEC-101 / SEC-102 / WEAK-101.
 *
 * What was wrong (2026-08-29 fix, see hub problem registry):
 *  * **SEC-101** — [LocalAuthRepository.signIn] fell back to a "demo/offline"
 *    session on ANY failed or empty Supabase sign-in (wrong password,
 *    timeout, server error), minting a valid 24-hour session whose role was
 *    guessed from the email substring and defaulted to SUPER_ADMIN.
 *  * **SEC-102** — even on a SUCCESSFUL Supabase sign-in, the role from
 *    `user_profiles` was overridden by email-substring matching, again
 *    defaulting to SUPER_ADMIN when nothing matched.
 *  * **WEAK-101** — `Session.accessToken` stored the user UUID (or a
 *    synthetic "local-…" string) instead of the real Supabase JWT.
 *
 * What must hold now (each test names the behaviour it pins):
 *  1. Failed/empty sign-in on a configured build → [Result.Err], NO session
 *     (SEC-101).
 *  2. The demo fallback exists ONLY for unconfigured + debug builds; a
 *     release build without configuration fails closed (SEC-101).
 *  3. Roles resolve EXCLUSIVELY from role-assignment codes with the
 *     least-privilege support_staff fallback — never from the email, never
 *     SUPER_ADMIN by default (SEC-102). A source-level scan additionally
 *     guards against re-introducing the email-substring inference (same
 *     technique as the desktop T-001 credential regression test).
 *  4. `buildServerSession` passes the REAL JWT, refresh token and expiry
 *     through verbatim (WEAK-101).
 *
 * The configured-build failure path (test 1) exercises the real
 * [SupabaseClientProvider] under Robolectric: the build is unconfigured
 * (`.env` placeholders), so any attempted auth call fails fast or is rejected
 * by the server — every possible outcome is `Result.Err` with no session,
 * which is exactly the invariant under test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LocalAuthRepositoryTest {

    // ─── Fixtures ────────────────────────────────────────────────────────────

    /** In-memory [AuditLogDao] — records rows for sign-in audit assertions. */
    private class FakeAuditLogDao : AuditLogDao {
        val rows = mutableListOf<AuditLogEntity>()
        private val flow = MutableStateFlow<List<AuditLogEntity>>(emptyList())

        override fun observeRecent(): Flow<List<AuditLogEntity>> = flow
        override suspend fun listByEntity(entityId: String): List<AuditLogEntity> =
            rows.filter { it.entityId == entityId }
        override suspend fun listByType(entityType: String): List<AuditLogEntity> =
            rows.filter { it.entityType == entityType }
        override suspend fun upsert(row: AuditLogEntity) {
            rows.add(row)
            flow.value = rows.toList()
        }
        override suspend fun upsertAll(rows: List<AuditLogEntity>) {
            this.rows.addAll(rows)
            flow.value = this.rows.toList()
        }
    }

    private fun newRepo(dao: FakeAuditLogDao): LocalAuthRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // T-051: LocalAuthRepository now takes the session-aware AuditContext
        // (first param). The Lazy<SessionManager> closes the construction
        // cycle against the repo being created here (same as production's
        // Dagger graph, flattened for a manual test construction).
        var repoRef: LocalAuthRepository? = null
        val lazySession = dagger.Lazy<SessionManager> {
            SessionManager(repoRef ?: error("repo not yet constructed"))
        }
        val repo = LocalAuthRepository(AuditContext(lazySession), dao, SupabaseClientProvider(context))
        repoRef = repo
        return repo
    }

    // ─── resolveRoleFromAssignments (SEC-102 core) ───────────────────────────

    @Test
    fun `no role assignments resolve to the least-privilege staff role`() {
        assertEquals(Role.SUPPORT_STAFF, resolveRoleFromAssignments(emptyList()))
    }

    @Test
    fun `first recognisable role-assignment code wins`() {
        assertEquals(Role.TEACHER, resolveRoleFromAssignments(listOf("teacher", "manager")))
        assertEquals(Role.MANAGER, resolveRoleFromAssignments(listOf("manager")))
        assertEquals(
            Role.FINANCIAL_OFFICER,
            resolveRoleFromAssignments(listOf("unknown-code", "financial_officer")),
        )
    }

    @Test
    fun `legacy role aliases still resolve through Role_fromCode`() {
        assertEquals(Role.SUPER_ADMIN, resolveRoleFromAssignments(listOf("direction")))
        assertEquals(Role.FINANCIAL_OFFICER, resolveRoleFromAssignments(listOf("comptable")))
    }

    @Test
    fun `unrecognisable codes never escalate — fallback stays support_staff`() {
        assertEquals(Role.SUPPORT_STAFF, resolveRoleFromAssignments(listOf("totally-unknown")))
        assertEquals(Role.SUPPORT_STAFF, resolveRoleFromAssignments(listOf("", "  ")))
    }

    // ─── buildServerSession (SEC-102 + WEAK-101 assembly) ────────────────────

    @Test
    fun `signed-in user WITHOUT role assignments gets support_staff and its default permissions only`() {
        val session = buildServerSession(
            userId = "prof-1", tenantId = "t-1", email = "x@elimtiyaz.dz",
            displayName = "X", avatarUrl = null, locale = "fr",
            roleCodes = emptyList(),
            accessToken = "real.jwt.token", refreshToken = "rt", expiresAtEpochMs = 123_456L,
        )
        assertEquals(Role.SUPPORT_STAFF, session.role)
        assertEquals(
            Permission.DEFAULT_ROLE_PERMISSIONS[Role.SUPPORT_STAFF],
            session.permissions,
        )
        // The least-privilege session must NOT carry admin-class permissions.
        assertFalse(session.can(Permission.MANAGE_SETTINGS))
        assertFalse(session.can(Permission.MANAGE_TENANTS))
        assertFalse(session.can(Permission.MANAGE_PERSONNEL))
    }

    @Test
    fun `server session passes the REAL JWT, refresh token and expiry through verbatim`() {
        val session = buildServerSession(
            userId = "prof-1", tenantId = "t-1", email = "x@elimtiyaz.dz",
            displayName = "X", avatarUrl = null, locale = "fr",
            roleCodes = listOf("teacher"),
            accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.real.payload",
            refreshToken = "refresh-token-value",
            expiresAtEpochMs = 1_799_999_999_999L,
        )
        assertEquals(Role.TEACHER, session.role)
        // WEAK-101: the token must be the real JWT, not a user UUID.
        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.real.payload", session.accessToken)
        assertEquals("refresh-token-value", session.refreshToken)
        assertEquals(1_799_999_999_999L, session.expiresAt)
    }

    @Test
    fun `unknown role code must not expand to all permissions`() {
        val session = buildServerSession(
            userId = "prof-1", tenantId = "t-1", email = "x@elimtiyaz.dz",
            displayName = "X", avatarUrl = null, locale = "fr",
            roleCodes = listOf("no-such-role"),
            accessToken = "jwt", refreshToken = null, expiresAtEpochMs = 1L,
        )
        assertEquals(Role.SUPPORT_STAFF, session.role)
        assert(session.permissions != Permission.entries.toSet())
        assertEquals(
            Permission.DEFAULT_ROLE_PERMISSIONS[Role.SUPPORT_STAFF],
            session.permissions,
        )
    }

    // ─── AuthEnvironment policy (SEC-101 core) ───────────────────────────────

    @Test
    fun `demo fallback is allowed ONLY when unconfigured AND debug`() {
        assertTrue(AuthEnvironment(supabaseConfigured = false, isDebugBuild = true).isDemoFallbackAllowed())
        assertFalse(AuthEnvironment(supabaseConfigured = false, isDebugBuild = false).isDemoFallbackAllowed())
        assertFalse(AuthEnvironment(supabaseConfigured = true, isDebugBuild = true).isDemoFallbackAllowed())
        assertFalse(AuthEnvironment(supabaseConfigured = true, isDebugBuild = false).isDemoFallbackAllowed())
    }

    // ─── signIn behaviour (SEC-101 end-to-end) ───────────────────────────────

    @Test
    fun `unconfigured debug build keeps a local demo sandbox with a FIXED role — no email inference`() = runBlocking {
        val dao = FakeAuditLogDao()
        val repo = newRepo(dao)
        val env = AuthEnvironment(supabaseConfigured = false, isDebugBuild = true)

        // "finance" / "teacher" in the email must no longer pick the role.
        val resultFinance = repo.signInInternal("finance.admin@elimtiyaz.dz", "pw", env)
        val resultTeacher = repo.signInInternal("teacher@elimtiyaz.dz", "pw", env)

        assertTrue(resultFinance is Result.Ok)
        assertTrue(resultTeacher is Result.Ok)
        val financeSession = (resultFinance as Result.Ok).value
        val teacherSession = (resultTeacher as Result.Ok).value
        // SEC-102: the demo role is the FIXED sandbox role for every email —
        // previously these were FINANCIAL_OFFICER and TEACHER respectively.
        assertEquals(DEMO_SANDBOX_ROLE, financeSession.role)
        assertEquals(DEMO_SANDBOX_ROLE, teacherSession.role)
        // Sandbox token is deliberately not a JWT.
        assertTrue(financeSession.accessToken.startsWith("local-"))
        // The session is observable (the LAST sign-in wins) and both
        // sign-ins are audited.
        assertEquals(teacherSession, repo.observeSession().first())
        assertEquals(2, dao.rows.size)
        assertTrue(dao.rows.all { it.entityType == "auth" })
    }

    @Test
    fun `unconfigured RELEASE build fails closed — no demo session`() = runBlocking {
        val repo = newRepo(FakeAuditLogDao())
        val env = AuthEnvironment(supabaseConfigured = false, isDebugBuild = false)

        val result = repo.signInInternal("admin@elimtiyaz.dz", "pw", env)

        assertTrue("Expected Err for unconfigured release build, got $result", result is Result.Err)
        assertNull(repo.observeSession().first())
    }

    @Test
    fun `configured build with FAILED sign-in fails closed — no session is ever minted`() = runBlocking {
        val repo = newRepo(FakeAuditLogDao())
        val env = AuthEnvironment(supabaseConfigured = true, isDebugBuild = true)

        // The provider is unconfigured (placeholder .env), so the attempted
        // Supabase call CANNOT succeed under any circumstance — wrong
        // password, unreachable server or no session must ALL end as Err with
        // no session (SEC-101's old behaviour returned a 24h SUPER_ADMIN).
        val result = repo.signInInternal("someone@elimtiyaz.dz", "definitely-wrong-password", env)

        assertTrue("Expected Err for failed sign-in on a configured build, got $result", result is Result.Err)
        assertNull(repo.observeSession().first())
    }

    // ─── Source-level regression guard (SEC-102, T-001 technique) ────────────

    @Test
    fun `SEC-102 source guard — no email-substring role inference anywhere in LocalRepositories`() {
        val candidates = mutableListOf<File>()
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(4) {
            val d = dir ?: return@repeat
            candidates.add(File(d, "src/main/java/com/example/infrastructure/local/LocalRepositories.kt"))
            candidates.add(File(d, "app/src/main/java/com/example/infrastructure/local/LocalRepositories.kt"))
            dir = d.parentFile
        }
        val source = candidates.firstOrNull { it.exists() }?.readText()
            ?: error("LocalRepositories.kt not found from ${System.getProperty("user.dir")}; searched: $candidates")

        val forbiddenFragments = listOf(
            "contains(\"finance\"", "contains(\"teacher\"", "contains(\"manager\"",
            "contains(\"support\"", "contains(\"buyer\"", "contains(\"driver\"",
            "contains(\"warehouse\"", "contains(\"worker\"",
        )
        val offenders = forbiddenFragments.filter { source.contains(it) }
        assertTrue(
            "SEC-102 regression: email-substring role inference re-introduced: $offenders",
            offenders.isEmpty(),
        )
        assertTrue(
            "SEC-102 regression: SUPER_ADMIN must never be a role-lookup fallback ('?: Role.SUPER_ADMIN')",
            !Regex("""\?:\s*Role\.SUPER_ADMIN""").containsMatchIn(source),
        )
    }
}
