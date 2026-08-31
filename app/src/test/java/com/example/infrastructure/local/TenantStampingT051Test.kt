package com.example.infrastructure.local

import com.example.core.Permission
import com.example.core.Role
import com.example.core.Session
import com.example.session.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/**
 * T-051 / WEAK-011 + TENANT-104 + WEAK-012 — tenant stamping + audit identity.
 *
 * The defects:
 *  - WEAK-011: the file-private `audit()`/`auditLog()` helpers hardcoded the
 *    DEMO tenant UUID into EVERY audit row and never captured `actorRole`
 *    (role-based audit queries returned nothing).
 *  - TENANT-104: 30+ entity constructors in the local repositories stamped
 *    the DEMO tenant UUID regardless of the signed-in user's tenant.
 *  - WEAK-012: the pull fallback selected the DEMO tenant's rows when no
 *    session tenant existed (signed-out user pulls demo data into the store).
 *
 * Fix under test: ONE session-aware [AuditContext] supplies the real tenant
 * + actor role to every repository (falling back to the demo tenant ONLY in
 * the signed-out/seed state), and PullSyncRepository now pulls NOTHING when
 * no session tenant exists.
 *
 * Behavioural unit: AuditContext with a real SessionManager (a fake
 * AuthRepository — the test lives in the same package as the T-002-era
 * fakes). Wiring (constructor injection into all 17 repository classes, no
 * demo literal left in either LocalRepositories file) is pinned by
 * source-scans.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TenantStampingT051Test {

    // ── Minimal fakes ─────────────────────────────────────────────────────

    /** SessionManager needs an AuthRepository only for restore/sign-in — unused here. */
    private class NoopAuthRepo : com.example.domain.repository.AuthRepository {
        override fun observeSession() = kotlinx.coroutines.flow.MutableStateFlow<Session?>(null)
        override suspend fun signIn(email: String, password: String) = com.example.core.Result.Err(com.example.core.Errors.unknown("noop"))
        override suspend fun signOut() = com.example.core.Result.Ok(Unit)
        override suspend fun refreshSession() = com.example.core.Result.Ok(null)
        override suspend fun changePassword(currentPassword: String, newPassword: String) = com.example.core.Result.Err(com.example.core.Errors.unknown("noop"))
    }

    private fun auditContextWith(session: Session?): AuditContext {
        val sm = SessionManager(NoopAuthRepo())
        sm.setSession(session)
        return AuditContext(dagger.Lazy { sm })
    }

    private fun session(tenantId: String, role: Role) = Session(
        userId = "user-1", tenantId = tenantId, email = "staff@elimtiyaz.dz",
        displayName = "Staff", avatarUrl = null, role = role,
        permissions = setOf(Permission.VIEW_ACADEMICS), accessToken = "jwt",
        refreshToken = null, expiresAt = Long.MAX_VALUE, locale = "fr",
    )

    // ── AuditContext semantics ────────────────────────────────────────────

    @Test
    fun `audit rows carry the session's REAL tenant - not the demo UUID`() {
        val ctx = auditContextWith(session("tenant-real-1", Role.FINANCIAL_OFFICER))
        val row = ctx.audit("payment.collect", "payment", "pay-1", "user-1", "Staff")
        assertEquals("tenant-real-1", row.tenantId)
    }

    @Test
    fun `audit rows capture the actor ROLE (was always null)`() {
        val ctx = auditContextWith(session("tenant-real-1", Role.SUPER_ADMIN))
        val row = ctx.audit("parent.update", "parent", "par-1", "user-1", "Staff")
        assertEquals(Role.SUPER_ADMIN.code, row.actorRole)
    }

    @Test
    fun `signed-out fallback keeps the seeding tenant (DatabaseSeeder parity) and null role`() {
        val ctx = auditContextWith(null)
        assertEquals(AuditContext.DEMO_TENANT_ID, ctx.tenantId())
        assertNull(ctx.actorRole())
    }

    @Test
    fun `auditLog mirrors audit (LocalRepositories2 call-shape compatibility)`() {
        val ctx = auditContextWith(session("tenant-real-2", Role.TEACHER))
        val row = ctx.auditLog("grade.enter", "assessment", "asm-1", "user-1", "Staff")
        assertEquals("tenant-real-2", row.tenantId)
        assertEquals(Role.TEACHER.code, row.actorRole)
    }

    // ── Wiring: no hardcoded demo tenant left in the repositories ─────────

    @Test
    fun `no demo-tenant literal remains in either LocalRepositories file (TENANT-104 closed)`() {
        for (f in listOf("src/main/java/com/example/infrastructure/local/LocalRepositories.kt", "src/main/java/com/example/infrastructure/local/LocalRepositories2.kt")) {
            val src = File(f).readText()
            assertFalse(
                "$f still hardcodes the demo tenant UUID",
                src.contains("00000000-0000-0000-0000-000000000001"),
            )
            assertTrue(
                "$f must route tenant stamping through auditContext",
                src.contains("auditContext.tenantId()"),
            )
        }
    }

    @Test
    fun `all repository classes inject AuditContext and the old helpers are gone`() {
        val src1 = File("src/main/java/com/example/infrastructure/local/LocalRepositories.kt").readText()
        val src2 = File("src/main/java/com/example/infrastructure/local/LocalRepositories2.kt").readText()
        for (cls in listOf("LocalAuthRepository", "LocalParentRepository", "LocalStudentRepository", "LocalPaymentRepository", "LocalInstallmentRepository", "LocalLedgerRepository")) {
            val block = Regex("class %s @Inject constructor\\([\\s\\S]*?\\) : ".format(cls)).find(src1)?.value
                ?: error("$cls not found")
            assertTrue("$cls must inject AuditContext", block.contains("private val auditContext: AuditContext,"))
        }
        for (cls in listOf("LocalClassRepository", "LocalAttendanceRepository", "LocalGradeRepository", "LocalExpenseRepository", "LocalPersonnelRepository", "LocalDepartmentRepository")) {
            val block = Regex("class %s @Inject constructor\\([\\s\\S]*?\\) : ".format(cls)).find(src2)?.value
                ?: error("$cls not found")
            assertTrue("$cls must inject AuditContext", block.contains("private val auditContext: AuditContext,"))
        }
        // The old file-private helpers are gone (their bodies carried the defect).
        assertFalse(src1.contains("private fun audit(action: String"))
        assertFalse(src2.contains("private fun auditLog(action: String"))
    }

    @Test
    fun `pull fallback pulls NOTHING when no session tenant exists (WEAK-012)`() {
        val src = File("src/main/java/com/example/infrastructure/sync/PullSyncRepository.kt").readText()
        assertFalse(
            "PullSyncRepository must not fall back to the demo tenant",
            src.contains("?: \"00000000-0000-0000-0000-000000000001\""),
        )
        assertTrue(
            "the signed-out pull path must return Ok(0) (pull nothing)",
            src.contains("?: return@withContext Result.Ok(0)"),
        )
    }
}
