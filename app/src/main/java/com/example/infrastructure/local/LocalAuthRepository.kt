package com.example.infrastructure.local

import com.example.BuildConfig
import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.core.Permission
import com.example.core.Result
import com.example.core.Role
import com.example.core.Session
import com.example.core.allocatePaymentToInstallments
import com.example.core.createChargeEntry
import com.example.core.createPaymentEntry
import com.example.core.createReversalEntry
import com.example.core.deriveAccountId
import com.example.core.generateEntryId
import com.example.core.LedgerEngine
import com.example.core.WaterfallInstallment
import com.example.domain.model.Parent
import com.example.domain.model.Student
import com.example.domain.model.Payment
import com.example.domain.model.Installment
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.BatchRegisterResult
import com.example.domain.repository.CreateParentInput
import com.example.domain.repository.CreateStudentInput
import com.example.domain.repository.CollectPaymentInput
import com.example.domain.repository.InstallmentRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.PaymentRepository
import com.example.domain.repository.StudentRepository
import com.example.domain.repository.UpdateParentInput
import com.example.domain.repository.UpdateStudentInput
import com.example.infrastructure.room.AuditLogDao
import com.example.infrastructure.room.AuditLogEntity
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.room.InstallmentDao
import com.example.infrastructure.room.InstallmentEntity
import com.example.infrastructure.room.LedgerEntryDao
import com.example.infrastructure.room.LedgerEntryEntity
import com.example.infrastructure.room.LocalMappers
import com.example.infrastructure.room.ParentDao
import com.example.infrastructure.room.ParentEntity
import com.example.infrastructure.room.PaymentDao
import com.example.infrastructure.room.PaymentEntity
import com.example.infrastructure.room.StudentDao
import com.example.infrastructure.room.StudentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid AuthRepository — Supabase-first, FAIL-CLOSED (T-002).
 *
 * Security model (SEC-101 / SEC-102 / WEAK-101 fixes, 2026-08-29):
 *  * **Supabase configured:** sign-in succeeds ONLY with real credentials;
 *    any failure (wrong password, timeout, server error, empty session)
 *    returns [Result.Err]. No offline/demo session is ever minted.
 *  * **Roles:** resolved EXCLUSIVELY from `role_assignments` via the
 *    canonical `current_user_roles()` RPC with the least-privilege
 *    support_staff fallback — never from email substrings, never
 *    SUPER_ADMIN by default.
 *  * **Tokens:** [Session.accessToken] stores the real Supabase JWT from the
 *    SDK session (server-validatable), not the user UUID.
 *  * **Unconfigured + DEBUG build:** a local demo sandbox session with the
 *    fixed [DEMO_SANDBOX_ROLE] — no server, no real token.
 *  * **Unconfigured + RELEASE build:** fails closed.
 */
@Singleton
class LocalAuthRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val auditDao: AuditLogDao,
    private val supabaseProvider: com.example.infrastructure.supabase.SupabaseClientProvider,
) : com.example.domain.repository.AuthRepository {

    private val _sessionState = kotlinx.coroutines.flow.MutableStateFlow<Session?>(null)
    private val sessionState: kotlinx.coroutines.flow.StateFlow<Session?> = _sessionState

    override fun observeSession(): Flow<Session?> = sessionState

    override suspend fun signIn(email: String, password: String): Result<Session> =
        signInInternal(email, password, AuthEnvironment.fromBuildConfig())

    /**
     * Internal seam so unit tests can drive both environment branches;
     * production always goes through [signIn] (which uses
     * [AuthEnvironment.fromBuildConfig]).
     */
    internal suspend fun signInInternal(email: String, password: String, env: AuthEnvironment): Result<Session> {
        // ── Stage 1: real Supabase Auth (8s hard timeout) — FAIL CLOSED ─────
        // T-002 / SEC-101 fix: on a configured build a failed or empty
        // sign-in is a hard error — never a demo session. The SDK's real
        // [UserSession] (JWT + user) is captured in one guarded call.
        if (env.supabaseConfigured) {
            val authSession = com.example.infrastructure.supabase.NetworkTimeouts.guard<io.github.jan.supabase.auth.user.UserSession?>(
                "auth.signIn", timeoutMs = 8_000L, onlyIfConfigured = false,
            ) {
                supabaseProvider.auth.signInWith(io.github.jan.supabase.auth.providers.builtin.Email) {
                    this.email = email
                    this.password = password
                }
                // T-002 / WEAK-101 fix — take the SDK session (real JWT),
                // not just the user record.
                supabaseProvider.auth.currentSessionOrNull()
            }

            val userInfo = authSession?.user
            if (userInfo != null && authSession != null) {
                // Fetch the user's profile from the `user_profiles` table.
                val profile = com.example.infrastructure.supabase.NetworkTimeouts.guard<com.example.infrastructure.supabase.UserProfileDto?>(
                    "auth.fetchProfile", timeoutMs = 5_000L,
                ) {
                    supabaseProvider.postgrest.from("user_profiles")
                        .select {
                            filter { eq("auth_user_id", userInfo.id) }
                            limit(1)
                        }
                        .decodeList<com.example.infrastructure.supabase.UserProfileDto>()
                        .firstOrNull()
                }

                // T-002 / SEC-102 fix — role resolution is SERVER-SIDE ONLY:
                // `role_assignments` via the canonical `current_user_roles()`
                // RPC (migration 0003), the same path as the desktop reference
                // client. The email-substring inference that defaulted to
                // SUPER_ADMIN was deleted.
                val roleCodes = com.example.infrastructure.supabase.NetworkTimeouts.guard<List<String>>(
                    "auth.fetchRoles", timeoutMs = 5_000L,
                ) {
                    supabaseProvider.postgrest.rpc("current_user_roles").decodeList<String>()
                } ?: emptyList()

                val displayName = profile?.displayName
                    ?: userInfo.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                    ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
                val session = buildServerSession(
                    userId = profile?.id ?: userInfo.id,
                    tenantId = profile?.tenantId ?: auditContext.tenantId(),
                    email = profile?.email ?: userInfo.email ?: email,
                    displayName = displayName,
                    avatarUrl = profile?.avatarUrl,
                    locale = profile?.locale ?: "fr",
                    roleCodes = roleCodes,
                    // T-002 / WEAK-101 fix — the REAL Supabase JWT (not the
                    // user UUID) + refresh token + expiry from the SDK session.
                    accessToken = authSession.accessToken,
                    refreshToken = authSession.refreshToken,
                    expiresAtEpochMs = authSession.expiresAt.toEpochMilliseconds(),
                )
                _sessionState.value = session
                auditDao.upsert(
                    AuditLogEntity(
                        id = "aud-${UUID.randomUUID()}",
                        tenantId = session.tenantId,
                        action = AuditActions.AUTH_LOGIN,
                        entityType = "auth", entityId = session.userId,
                        actorId = session.userId, actorName = session.displayName,
                        actorRole = session.role.code,
                        beforeJson = null, afterJson = """{"email":"${session.email}","source":"supabase"}""",
                        note = "Supabase sign-in", createdAt = Instant.now().toString(),
                    )
                )
                return Result.Ok(session)
            }

            // T-002 / SEC-101 fix — FAIL CLOSED. Supabase IS configured but
            // the sign-in failed (wrong credentials, timeout, server error)
            // or the SDK returned no session. The previous code fell through
            // to the demo fallback and minted a 24-hour SUPER_ADMIN session —
            // deleted. The LoginScreen renders the error message.
            return Result.Err(
                com.example.core.Errors.unauthorized(
                    "Supabase sign-in failed for $email (bad credentials, timeout, server error or no session)",
                    userMessage = "Échec de la connexion — vérifiez vos identifiants ou la configuration du serveur.",
                ),
            )
        }

        // ── Stage 2: demo sandbox — debug builds WITHOUT Supabase config ONLY ──
        // T-002 / SEC-101 fix: this branch used to fire on ANY failed Supabase
        // login (including wrong passwords on a configured build) and minted a
        // 24-hour session whose role was guessed from the email substring,
        // defaulting to SUPER_ADMIN (SEC-102). Now it runs only when
        // `env.isDemoFallbackAllowed()` (unconfigured AND debug), and the role
        // is the FIXED [DEMO_SANDBOX_ROLE] — no email-derived privileges.
        if (!env.isDemoFallbackAllowed()) {
            return Result.Err(
                com.example.core.Errors.unauthorized(
                    "Supabase is not configured — refusing to start a demo session (release build)",
                    userMessage = "Aucun serveur configuré — renseignez SUPABASE_URL et SUPABASE_ANON_KEY dans Paramètres > Supabase.",
                ),
            )
        }
        return demoSandboxSignIn(email)
    }

    /**
     * Local demo sandbox session — debug builds without Supabase config only
     * (see [AuthEnvironment.isDemoFallbackAllowed]). The token is
     * deliberately NOT a JWT; it authenticates nowhere server-side.
     */
    private suspend fun demoSandboxSignIn(email: String): Result<Session> {
        val demoPermissions = Permission.DEFAULT_ROLE_PERMISSIONS[DEMO_SANDBOX_ROLE] ?: emptySet()
        val localSession = Session(
            userId = "usr-local-demo",
            tenantId = auditContext.tenantId(),
            email = email.ifBlank { "admin@elimtiyaz.dz" },
            displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() }.ifBlank { "Administrateur" },
            avatarUrl = null,
            role = DEMO_SANDBOX_ROLE,
            permissions = demoPermissions,
            accessToken = "local-${System.currentTimeMillis()}",
            refreshToken = null,
            expiresAt = System.currentTimeMillis() + 86_400_000L,
            locale = "fr",
        )
        _sessionState.value = localSession
        auditDao.upsert(
            AuditLogEntity(
                id = "aud-${UUID.randomUUID()}",
                tenantId = localSession.tenantId,
                action = AuditActions.AUTH_LOGIN,
                entityType = "auth", entityId = localSession.userId,
                actorId = localSession.userId, actorName = localSession.displayName,
                actorRole = localSession.role.code,
                beforeJson = null, afterJson = """{"email":"${localSession.email}","source":"local-demo"}""",
                note = "Local sign-in (demo sandbox — debug build only)", createdAt = Instant.now().toString(),
            )
        )
        return Result.Ok(localSession)
    }

    override suspend fun signOut(): Result<Unit> {
        // SYNC-104 fix (2026-08-30): deactivate this user's Android FCM tokens
        // BEFORE the auth session is revoked — deactivate_fcm_tokens (hub
        // migration 0050) verifies the caller via auth.uid(), so it must run
        // while the JWT is still valid. Called directly on the provider
        // (NOT via FcmTokenRegistrar — that would create a Hilt cycle:
        // LocalAuthRepository → FcmTokenRegistrar → SessionManager →
        // AuthRepository). Non-fatal by design: a stale token is re-activated
        // on the next sign-in; sign-out must proceed even when the backend is
        // unreachable.
        val sessionUserId = _sessionState.value?.userId
        if (sessionUserId != null && com.example.infrastructure.supabase.NetworkTimeouts.isSupabaseConfigured) {
            runCatching {
                com.example.infrastructure.supabase.NetworkTimeouts.guard<Unit>("fcm.deactivateTokens", timeoutMs = 2_000L) {
                    val params = kotlinx.serialization.json.buildJsonObject {
                        put("p_user_id", sessionUserId)
                        put("p_platform", "android")
                    }
                    supabaseProvider.postgrest.rpc("deactivate_fcm_tokens", params)
                }
            }.onFailure {
                android.util.Log.w("LocalAuthRepository", "FCM token deactivation failed (non-fatal): ${it.message}")
            }
        }
        if (com.example.infrastructure.supabase.NetworkTimeouts.isSupabaseConfigured) {
            com.example.infrastructure.supabase.NetworkTimeouts.guard<Unit>("auth.signOut", timeoutMs = 2_000L) {
                supabaseProvider.auth.signOut()
            }
        }
        _sessionState.value?.let { s ->
            auditDao.upsert(
                AuditLogEntity(
                    id = "aud-${UUID.randomUUID()}",
                    tenantId = s.tenantId,
                    action = AuditActions.AUTH_LOGOUT,
                    entityType = "auth", entityId = s.userId,
                    actorId = s.userId, actorName = s.displayName,
                    actorRole = s.role.code,
                    beforeJson = null, afterJson = null,
                    note = "Mobile sign-out", createdAt = Instant.now().toString(),
                )
            )
        }
        _sessionState.value = null
        return Result.Ok(Unit)
    }

    override suspend fun refreshSession(): Result<Session?> {
        _sessionState.value?.let { return Result.Ok(it) }

        if (!com.example.infrastructure.supabase.NetworkTimeouts.isSupabaseConfigured) return Result.Ok(null)

        // T-002 / WEAK-101 fix — restore from the SDK's REAL session (JWT),
        // not just the user record.
        val authSession = com.example.infrastructure.supabase.NetworkTimeouts.guard<io.github.jan.supabase.auth.user.UserSession?>(
            "auth.refreshSession", timeoutMs = 3_000L, onlyIfConfigured = false,
        ) {
            supabaseProvider.auth.currentSessionOrNull()
        } ?: return Result.Ok(null)

        // No user in the stored SDK session → nothing restorable (fail closed).
        val current = authSession.user ?: return Result.Ok(null)

        val profile = com.example.infrastructure.supabase.NetworkTimeouts.guard<com.example.infrastructure.supabase.UserProfileDto?>(
            "auth.refreshProfile",
        ) {
            supabaseProvider.postgrest.from("user_profiles")
                .select {
                    filter { eq("auth_user_id", current.id) }
                    limit(1)
                }
                .decodeList<com.example.infrastructure.supabase.UserProfileDto>()
                .firstOrNull()
        } ?: return Result.Ok(null)

        // T-002 / SEC-102 fix — role via role_assignments RPC with the
        // least-privilege fallback (was a direct SUPER_ADMIN fallback).
        val roleCodes = com.example.infrastructure.supabase.NetworkTimeouts.guard<List<String>>(
            "auth.refreshRoles",
        ) {
            supabaseProvider.postgrest.rpc("current_user_roles").decodeList<String>()
        } ?: emptyList()

        val displayName = profile.displayName
            ?: current.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "Administrateur"
        val session = buildServerSession(
            userId = profile.id,
            tenantId = profile.tenantId ?: auditContext.tenantId(),
            email = profile.email ?: current.email ?: "",
            displayName = displayName,
            avatarUrl = profile.avatarUrl,
            locale = profile.locale ?: "fr",
            roleCodes = roleCodes,
            accessToken = authSession.accessToken,
            refreshToken = authSession.refreshToken,
            expiresAtEpochMs = authSession.expiresAt.toEpochMilliseconds(),
        )
        _sessionState.value = session
        return Result.Ok(session)
    }

    // FIX (fake success): changePassword previously ignored `currentPassword`
    // entirely and returned Ok(Unit) even when nothing was changed (offline /
    // unconfigured builds showed a success banner for a no-op). Now:
    //   1. The CURRENT password is verified by re-authenticating with the
    //      auth server (wrong current password → explicit error).
    //   2. The new password is really pushed via `updateUser`.
    //   3. Offline / unconfigured → honest error instead of silent success.
    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        if (!com.example.infrastructure.supabase.NetworkTimeouts.isSupabaseConfigured) {
            return Result.Err(
                com.example.core.Errors.unknown(
                    "changePassword requires the auth server",
                    userMessage = "Le changement de mot de passe nécessite une connexion au serveur d'authentification.",
                ),
            )
        }
        val email = _sessionState.value?.email
            ?: return Result.Err(
                com.example.core.Errors.unknown(
                    "changePassword requires an active session",
                    userMessage = "Aucune session active — reconnectez-vous avant de changer le mot de passe.",
                ),
            )

        // Verify the current password by re-authenticating (Supabase has no
        // "verify password" RPC — re-sign-in is the canonical check).
        val verified = com.example.infrastructure.supabase.NetworkTimeouts.guard("auth.verifyCurrentPassword", timeoutMs = 8_000L) {
            try {
                supabaseProvider.auth.signInWith(io.github.jan.supabase.auth.providers.builtin.Email) {
                    this.email = email
                    this.password = currentPassword
                }
                true
            } catch (_: Throwable) {
                false
            }
        } ?: return Result.Err(
            com.example.core.Errors.unknown(
                "password verification unreachable",
                userMessage = "Impossible de vérifier le mot de passe actuel (serveur injoignable).",
            ),
        )
        if (!verified) {
            return Result.Err(
                com.example.core.Errors.unknown(
                    "current password mismatch",
                    userMessage = "Mot de passe actuel incorrect.",
                ),
            )
        }

        val updated = com.example.infrastructure.supabase.NetworkTimeouts.guard<Unit>("auth.changePassword", timeoutMs = 4_000L) {
            supabaseProvider.auth.updateUser {
                password = newPassword
            }
        } ?: return Result.Err(
            com.example.core.Errors.unknown(
                "password update unreachable",
                userMessage = "Échec de la mise à jour — serveur injoignable.",
            ),
        )

        _sessionState.value?.let { s ->
            auditDao.upsert(
                AuditLogEntity(
                    id = "aud-${UUID.randomUUID()}",
                    tenantId = s.tenantId,
                    action = AuditActions.AUTH_PASSWORD_CHANGE,
                    entityType = "auth", entityId = s.userId,
                    actorId = s.userId, actorName = s.displayName,
                    actorRole = s.role.code,
                    beforeJson = null, afterJson = null,
                    note = "Password changed by user", createdAt = Instant.now().toString(),
                ),
            )
        }
        return Result.Ok(updated)
    }
}
