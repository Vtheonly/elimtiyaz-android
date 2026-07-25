package com.elimtiyaz.data.mock

import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Role
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock [AuthRepository] — accepts a handful of pre-configured logins so the
 * demo can exercise every role without a backend.
 *
 * Default logins (email / password → role):
 * - `admin@elimtiyaz.dz` / `admin123` → SuperAdmin
 * - `financial@elimtiyaz.dz` / `fin123` → FinancialOfficer
 * - `teacher@elimtiyaz.dz` / `teach123` → Teacher
 * - `driver@elimtiyaz.dz` / `drive123` → SupportStaff (driver role)
 */
@Singleton
class MockAuthRepository @Inject constructor() : AuthRepository {

    private val log = Logger.withTag("Mock.Auth")
    private val _session = MutableStateFlow<Session?>(null)
    private val _isOffline = MutableStateFlow(false)

    override val session: Flow<Session?> = _session.asStateFlow()
    override val isOffline: Flow<Boolean> = _isOffline.asStateFlow()

    /** Sign in using one of the pre-configured mock accounts. */
    override suspend fun signIn(email: String, password: String): Result<Session> {
        delay(NETWORK_DELAY)
        val account = ACCOUNTS.firstOrNull { it.email == email && it.password == password }
            ?: return Result.failure("Identifiants invalides. Essayez admin@elimtiyaz.dz / admin123.")
        val session = Session(
            userId = account.userId,
            tenantId = MockData.TENANT_ID,
            email = account.email,
            displayName = account.displayName,
            avatarUrl = null,
            role = account.role,
            permissions = account.permissions,
            accessToken = "mock-token-${Clock.System.now().toEpochMilliseconds()}",
            refreshToken = "mock-refresh",
            expiresAt = Clock.System.now().toEpochMilliseconds() + 8 * 60 * 60 * 1_000L,
            locale = "fr",
        )
        _session.value = session
        log.i { "Mock login as ${account.email} (${account.role.key})" }
        return Result.success(session)
    }

    /** Mock activation — accepts any non-blank OTP and sets the new password. */
    override suspend fun activateAccount(email: String, otp: String, newPassword: String): Result<Session> {
        delay(NETWORK_DELAY)
        if (email.isBlank() || otp.length < 4 || newPassword.length < 4) {
            return Result.failure("OTP ou mot de passe invalide.")
        }
        return signIn(email, newPassword)
    }

    /** Mock reset — always succeeds. */
    override suspend fun requestPasswordReset(email: String): Result<Unit> {
        delay(NETWORK_DELAY)
        log.i { "Mock password-reset email sent to $email" }
        return Result.success(Unit)
    }

    /** Sign out — clears the in-memory session. */
    override suspend fun signOut(): Result<Unit> {
        _session.value = null
        log.i { "Mock sign-out" }
        return Result.success(Unit)
    }

    /** Mock refresh — extends the expiry by 8 hours. */
    override suspend fun refreshSession(): Result<Session?> {
        val current = _session.value ?: return Result.success(null)
        val refreshed = current.copy(
            expiresAt = Clock.System.now().toEpochMilliseconds() + 8 * 60 * 60 * 1_000L,
        )
        _session.value = refreshed
        return Result.success(refreshed)
    }

    private companion object {
        const val NETWORK_DELAY = 350L
    }
}

/** Mock account definition. */
private data class MockAccount(
    val userId: String,
    val email: String,
    val password: String,
    val displayName: String,
    val role: Role,
    val permissions: Set<Permission>,
)

/** All pre-configured mock accounts. */
private val ACCOUNTS: List<MockAccount> = listOf(
    MockAccount(
        userId = "u-admin", email = "admin@elimtiyaz.dz", password = "admin123",
        displayName = "M. Boudjelal", role = Role.SuperAdmin,
        permissions = Permission.values().toSet(),
    ),
    MockAccount(
        userId = "u-fin", email = "financial@elimtiyaz.dz", password = "fin123",
        displayName = "Mme Larbi", role = Role.FinancialOfficer,
        permissions = setOf(
            Permission.ViewFinancials, Permission.CollectPayment, Permission.RefundPayment,
            Permission.AdjustAccount, Permission.GenerateReceipt, Permission.ViewDebt,
            Permission.SendReminder, Permission.SubmitExpense, Permission.ApproveExpense,
            Permission.DisburseExpense, Permission.SettleExpenseProof, Permission.ViewRoster,
            Permission.ViewPersonnel, Permission.ViewAuditLog, Permission.ViewReleve,
        ),
    ),
    MockAccount(
        userId = "u-teach", email = "teacher@elimtiyaz.dz", password = "teach123",
        displayName = "Mme Haddad", role = Role.Teacher,
        permissions = setOf(
            Permission.ViewRoster, Permission.ViewAcademics, Permission.EnterGrades,
            Permission.AssignHomework, Permission.RollCall, Permission.ViewPersonnel,
        ),
    ),
    MockAccount(
        userId = "u-driver", email = "driver@elimtiyaz.dz", password = "drive123",
        displayName = "M. Belhadj", role = Role.SupportStaff,
        permissions = setOf(Permission.AccessDriverMode, Permission.ViewRoster),
    ),
)
