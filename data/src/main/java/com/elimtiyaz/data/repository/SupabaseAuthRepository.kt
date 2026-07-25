package com.elimtiyaz.data.repository

import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.DispatcherProvider
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Role
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.core.common.onFailure
import com.elimtiyaz.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase-backed [AuthRepository]. Stores the current [Session] in a hot
 * [MutableStateFlow] so the UI can observe authentication state. The mock
 * equivalent is [com.elimtiyaz.data.mock.MockAuthRepository].
 */
@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val dispatchers: DispatcherProvider,
) : AuthRepository {

    private val log = Logger.withTag("Data.Auth")
    private val _session = MutableStateFlow<Session?>(null)
    private val _isOffline = MutableStateFlow(false)

    override val session: Flow<Session?> = _session.asStateFlow()
    override val isOffline: Flow<Boolean> = _isOffline.asStateFlow()

    /** Sign in via Supabase Auth email/password and build a [Session]. */
    override suspend fun signIn(email: String, password: String): Result<Session> = withContext(dispatchers.io) {
        Result.runCatching {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val user = supabase.auth.currentUserOrNull()
                ?: error("Aucun utilisateur retourné par Supabase Auth.")
            val role = Role.fromKey(user.userMetadata?.get("role")?.toString())
                ?: Role.SupportStaff
            val session = Session(
                userId = user.id,
                tenantId = user.userMetadata?.get("tenant_id")?.toString().orEmpty(),
                email = email,
                displayName = user.userMetadata?.get("display_name")?.toString()
                    ?.takeIf { it.isNotBlank() } ?: email.substringBefore('@'),
                avatarUrl = user.userMetadata?.get("avatar_url")?.toString(),
                role = role,
                permissions = permissionsFor(role),
                accessToken = supabase.auth.currentAccessTokenOrNull() ?: "",
                refreshToken = supabase.auth.currentSessionOrNull()?.refreshToken,
                expiresAt = Clock.System.now().toEpochMilliseconds() + EIGHT_HOURS_MILLIS,
                locale = user.userMetadata?.get("locale")?.toString() ?: "fr",
            )
            _session.value = session
            _isOffline.value = false
            log.i { "Signed in as ${session.email} (${session.role.key})" }
            session
        }.onFailure { log.w { "signIn failed: ${it.userMessage}" } }
    }

    /** Activate an account via the OTP flow + new password. */
    override suspend fun activateAccount(email: String, otp: String, newPassword: String): Result<Session> =
        withContext(dispatchers.io) {
            Result.runCatching {
                // The recovery OTP verifies the email; then we set a new password.
                supabase.auth.updateUser { this.password = newPassword }
                signIn(email, newPassword).getOrNull() ?: error("Échec de l'activation du compte.")
            }
        }

    /** Trigger a password-reset email. */
    override suspend fun requestPasswordReset(email: String): Result<Unit> = withContext(dispatchers.io) {
        Result.runCatching {
            supabase.auth.resetPasswordForEmail(email)
            log.i { "Password reset email sent to $email" }
        }
    }

    /** Sign out from Supabase and clear the in-memory session. */
    override suspend fun signOut(): Result<Unit> = withContext(dispatchers.io) {
        Result.runCatching {
            supabase.auth.signOut()
            _session.value = null
            log.i { "Signed out" }
        }
    }

    /** Refresh the access token and return the updated session. */
    override suspend fun refreshSession(): Result<Session?> = withContext(dispatchers.io) {
        Result.runCatching {
            supabase.auth.refreshCurrentSession()
            _session.value?.let { current ->
                current.copy(
                    accessToken = supabase.auth.currentAccessTokenOrNull() ?: current.accessToken,
                    expiresAt = Clock.System.now().toEpochMilliseconds() + EIGHT_HOURS_MILLIS,
                ).also { _session.value = it }
            }
        }
    }

    /** Precompute the permission set for a role per the master plan RBAC matrix. */
    private fun permissionsFor(role: Role): Set<Permission> = when (role) {
        Role.SuperAdmin -> Permission.values().toSet()
        Role.FinancialOfficer -> setOf(
            Permission.ViewFinancials, Permission.CollectPayment, Permission.RefundPayment,
            Permission.AdjustAccount, Permission.GenerateReceipt, Permission.ViewDebt,
            Permission.SendReminder, Permission.SubmitExpense, Permission.ApproveExpense,
            Permission.DisburseExpense, Permission.SettleExpenseProof, Permission.ViewRoster,
            Permission.ViewPersonnel, Permission.ViewAuditLog, Permission.ViewReleve,
        )
        Role.Teacher -> setOf(
            Permission.ViewRoster, Permission.ViewAcademics, Permission.EnterGrades,
            Permission.AssignHomework, Permission.RollCall, Permission.ViewPersonnel,
        )
        Role.SupportStaff -> setOf(
            Permission.ViewRoster, Permission.CreateParent, Permission.EditParent,
            Permission.ViewAcademics, Permission.ViewFinancials, Permission.ViewPersonnel,
        )
        Role.Parent, Role.Student -> Session.WebPortalOnly
    }

    private companion object {
        /** Access-token lifetime used by Supabase by default (8 hours). */
        const val EIGHT_HOURS_MILLIS = 8L * 60L * 60L * 1_000L
    }
}

/** Internal helper — current ISO timestamp, used for audit fields. */
internal fun nowIso(): String = Formatters.nowIso()
