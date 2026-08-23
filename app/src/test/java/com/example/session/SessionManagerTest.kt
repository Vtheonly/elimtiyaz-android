package com.example.session

import com.example.core.Permission
import com.example.core.Result
import com.example.core.Role
import com.example.core.Session
import com.example.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the [SessionManager.restoreSession] bugfix (iter 2).
 *
 * Previously [SessionManager.restoreSession] returned the auth result
 * WITHOUT calling [SessionManager.setSession], so the [SessionManager.state]
 * StateFlow stayed `null` after a cold-start even when a valid session
 * existed. The auth gate then routed to Login instead of Main.
 *
 * These tests verify the fix: when [AuthRepository.refreshSession] returns
 * a non-null Session, [SessionManager.state] is updated to that session.
 */
class SessionManagerTest {

    private lateinit var authRepository: FakeAuthRepository
    private lateinit var sessionManager: SessionManager

    private val demoSession = Session(
        userId = "usr-001",
        tenantId = "tenant-xyz",
        email = "demo@elimtiyaz.dz",
        displayName = "Demo Admin",
        avatarUrl = null,
        role = Role.SUPER_ADMIN,
        permissions = setOf(Permission.VIEW_FINANCIALS, Permission.COLLECT_PAYMENT),
        accessToken = "demo-token",
        refreshToken = null,
        expiresAt = System.currentTimeMillis() + 3_600_000L,
        locale = "fr",
    )

    @Before
    fun setUp() {
        authRepository = FakeAuthRepository()
        sessionManager = SessionManager(authRepository)
    }

    @Test
    fun `restoreSession propagates non-null session to state`() = runTest {
        authRepository.refreshResult = Result.Ok(demoSession)

        // Before restore: state is null
        assertNull(sessionManager.current())

        // Restore
        val result = sessionManager.restoreSession()

        // After restore: state is the demo session
        assertTrue(result is Result.Ok)
        assertEquals(demoSession, sessionManager.current())
        assertEquals(demoSession.userId, sessionManager.currentUserId())
        assertEquals(demoSession.tenantId, sessionManager.currentTenantId())
        assertEquals(demoSession.displayName, sessionManager.currentDisplayName())
    }

    @Test
    fun `restoreSession does not overwrite state when result is null`() = runTest {
        authRepository.refreshResult = Result.Ok(null)

        val result = sessionManager.restoreSession()

        assertTrue(result is Result.Ok)
        assertNull(sessionManager.current())
    }

    @Test
    fun `restoreSession does not overwrite state when result is Err`() = runTest {
        authRepository.refreshResult = Result.Err(
            com.example.core.Errors.network("connection refused"),
        )

        val result = sessionManager.restoreSession()

        assertTrue(result is Result.Err)
        assertNull(sessionManager.current())
    }

    @Test
    fun `setSession null clears the state`() {
        // Start with a session
        sessionManager.setSession(demoSession)
        assertEquals(demoSession, sessionManager.current())

        // Sign out
        sessionManager.setSession(null)
        assertNull(sessionManager.current())
    }

    @Test
    fun `state flow emits the restored session`() = runTest {
        authRepository.refreshResult = Result.Ok(demoSession)

        // Subscribe to the state flow
        val states = mutableListOf<Session?>()
        val job = kotlinx.coroutines.GlobalScope.launch {
            sessionManager.state.collect { states.add(it) }
        }
        // Initial value is null
        // Give the collector a tick to subscribe
        kotlinx.coroutines.delay(10)
        assertTrue(states.isNotEmpty())
        assertNull(states.first())

        // Trigger restore
        sessionManager.restoreSession()
        // restoreSession dispatches onto Dispatchers.IO — a fixed 10ms delay
        // races it in plain-JVM test runs. Poll deterministically instead.
        val deadline = System.currentTimeMillis() + 5_000
        while (!states.contains(demoSession) && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(20)
        }

        // The state should now include the demo session
        assertTrue(states.contains(demoSession))

        job.cancel()
    }

    @Test
    fun `can returns true only when session has the permission`() {
        // No session
        assertFalse(sessionManager.can(Permission.VIEW_FINANCIALS))

        // Session with the permission
        sessionManager.setSession(demoSession)
        assertTrue(sessionManager.can(Permission.VIEW_FINANCIALS))
        assertTrue(sessionManager.can(Permission.COLLECT_PAYMENT))
        assertFalse(sessionManager.can(Permission.MANAGE_TENANTS)) // not in demoSession
    }

    @Test
    fun `hasRole checks the session role`() {
        assertFalse(sessionManager.hasRole(Role.SUPER_ADMIN))
        sessionManager.setSession(demoSession)
        assertTrue(sessionManager.hasRole(Role.SUPER_ADMIN))
        assertFalse(sessionManager.hasRole(Role.TEACHER))
    }

    /**
     * Minimal fake of [AuthRepository] — only implements [refreshSession]
     * (the only method [SessionManager] uses).
     */
    private class FakeAuthRepository : AuthRepository {
        var refreshResult: Result<Session?> = Result.Ok(null)
        private val sessionState = MutableStateFlow<Session?>(null)

        override suspend fun signIn(email: String, password: String): Result<Session> {
            error("not used in this test")
        }

        override suspend fun signOut(): Result<Unit> = Result.Ok(Unit)

        override suspend fun refreshSession(): Result<Session?> = refreshResult

        override suspend fun changePassword(
            currentPassword: String,
            newPassword: String,
        ): Result<Unit> = Result.Ok(Unit)

        override fun observeSession(): Flow<Session?> = sessionState
    }
}
