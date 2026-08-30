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

// ─── Auth security model (T-002: SEC-101 / SEC-102 / WEAK-101) ──────────────

/**
 * Resolves the session role EXCLUSIVELY from server-side role assignments.
 *
 * Canonical path (mirrors the desktop reference client, which calls
 * `client.rpc("current_user_roles")` and falls back to `Role.SupportStaff`):
 * the SQL function (migration 0003) reads `role_assignments` for the
 * signed-in user and returns the unrevoked role codes. The FIRST recognisable
 * code wins (`Role.fromCode` also maps legacy aliases such as
 * "direction" → super_admin). When the list is empty or unrecognisable — i.e.
 * a signed-in user with NO role assignments — the fallback is the
 * LEAST-PRIVILEGE staff role (support_staff), never SUPER_ADMIN.
 *
 * This function MUST stay pure (no network, no email inspection): the deleted
 * email-substring role inference (SEC-102) is regression-guarded by
 * `LocalAuthRepositoryTest`.
 */
internal fun resolveRoleFromAssignments(roleCodes: List<String>): Role =
    roleCodes.firstNotNullOfOrNull { Role.fromCode(it) } ?: Role.SUPPORT_STAFF

/**
 * Assembles the server-authenticated [Session] (pure — unit-tested).
 *
 * Security invariants enforced here:
 *  * the role comes ONLY from [roleCodes] via [resolveRoleFromAssignments]
 *    (least-privilege support_staff fallback — SEC-102 fix);
 *  * [accessToken] is the REAL Supabase JWT from the SDK session, which the
 *    server can validate — never a user UUID or synthetic string
 *    (WEAK-101 fix);
 *  * an unknown role must never expand to "all permissions" — the permission
 *    fallback is the empty set, not `Permission.entries`.
 *
 * Identity fields (userId/tenantId/email/displayName/…) are already resolved
 * by the caller from `user_profiles` with auth-record fallbacks.
 */
internal fun buildServerSession(
    userId: String,
    tenantId: String,
    email: String,
    displayName: String,
    avatarUrl: String?,
    locale: String,
    roleCodes: List<String>,
    accessToken: String,
    refreshToken: String?,
    expiresAtEpochMs: Long,
): Session {
    val role = resolveRoleFromAssignments(roleCodes)
    return Session(
        userId = userId,
        tenantId = tenantId,
        email = email,
        displayName = displayName,
        avatarUrl = avatarUrl,
        role = role,
        permissions = Permission.DEFAULT_ROLE_PERMISSIONS[role] ?: emptySet(),
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = expiresAtEpochMs,
        locale = locale,
    )
}

/**
 * The fixed role of the LOCAL DEMO SANDBOX session, reachable ONLY in debug
 * builds with no Supabase configuration (see [AuthEnvironment]). It grants
 * nothing server-side: the sandbox token ("local-…") is not a JWT and no
 * backend is configured. NEVER use as a fallback for server-resolved
 * sessions — those fall back to support_staff (see
 * [resolveRoleFromAssignments]).
 */
internal val DEMO_SANDBOX_ROLE: Role = Role.SUPER_ADMIN

/**
 * The runtime environment [LocalAuthRepository] makes its fail-closed
 * decisions against. Injectable so unit tests can drive both branches;
 * production always uses [AuthEnvironment.fromBuildConfig].
 */
internal data class AuthEnvironment(
    val supabaseConfigured: Boolean,
    val isDebugBuild: Boolean,
) {
    /**
     * SEC-101 fix: the demo fallback is allowed ONLY when Supabase is
     * genuinely unconfigured AND this is a debug build. A failed login on a
     * configured build is a FAILED LOGIN — never a demo session; a release
     * build without configuration fails closed.
     */
    fun isDemoFallbackAllowed(): Boolean = !supabaseConfigured && isDebugBuild

    companion object {
        fun fromBuildConfig(): AuthEnvironment = AuthEnvironment(
            supabaseConfigured = com.example.infrastructure.supabase.NetworkTimeouts.isSupabaseConfigured,
            isDebugBuild = BuildConfig.DEBUG,
        )
    }
}

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
                    tenantId = profile?.tenantId ?: "00000000-0000-0000-0000-000000000001",
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
            tenantId = "00000000-0000-0000-0000-000000000001",
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
            tenantId = profile.tenantId ?: "00000000-0000-0000-0000-000000000001",
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

// ─── Parent Repository ──────────────────────────────────────────────────────

/** Canonical enrollment-status value set (SQL 0005 + migration 0037 superset). */
val CANONICAL_STUDENT_STATUSES: Set<String> = setOf(
    "inquiry", "quoted", "enrolled", "active", "suspended", "transferred", "withdrawn", "graduated",
)

@Singleton
class LocalParentRepository @Inject constructor(
    private val parentDao: ParentDao,
    private val auditDao: AuditLogDao,
    // FIX (orphaned records): needed by deleteParent to refuse deleting a
    // parent that still has linked students (desktop parity).
    private val studentDao: StudentDao,
) : ParentRepository {

    override fun observe(): Flow<List<Parent>> =
        parentDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Parent?> =
        parentDao.observeById(id).map { it?.let { e -> LocalMappers.run { e.toDomain() } } }

    override fun search(query: String): Flow<List<Parent>> =
        parentDao.search(query).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override suspend fun createParent(input: CreateParentInput, actorId: String, actorName: String): Result<Parent> {
        val now = Instant.now().toString()
        val year = java.time.LocalDate.now().year
        // TIER 2 R15 — deterministic parent_code via FNV-1a hash.
        // Re-creating the same parent (or re-importing the same Excel row)
        // produces the SAME code → the `upsert_parent_from_import` RPC's
        // primary identity match (tenant_id, parent_code) succeeds →
        // idempotent upsert, no duplicate parents.
        val code = com.example.core.deterministicParentCode(
            year = year,
            input = com.example.core.ParentCodeInput(
                phone = input.phone,
                displayName = input.displayName,
                firstName = input.firstName,
                lastName = input.lastName,
            ),
        )
        // TIER 2 R15 — deterministic activation_code derived from
        // (parentCode, tenantId) so re-creating the same parent produces
        // the same code → the `bind-activation-code` edge function's
        // idempotency contract holds.
        val activationCode = com.example.core.deterministicActivationCode(
            parentCode = code,
            tenantId = "00000000-0000-0000-0000-000000000001",
        )
        val entity = ParentEntity(
            id = "par-${UUID.randomUUID()}",
            tenantId = "00000000-0000-0000-0000-000000000001", code = code,
            firstName = input.firstName, lastName = input.lastName,
            displayName = input.displayName ?: "${input.firstName} ${input.lastName}".trim().ifEmpty { null },
            phone = input.phone,
            // Vault §04.03 — secondary phone / national ID / relationship from
            // the batch-registration master info block.
            whatsapp = input.secondaryPhone ?: input.phone,
            email = input.email, occupation = input.occupation,
            address = input.address, transportDestination = input.transportDestination,
            nationalId = input.nationalId,
            relationship = input.relationship,
            preferredLanguage = input.preferredLanguage, avatarUrl = null,
            isActive = true, isFinanciallyRestricted = false,
            activationCode = activationCode, createdAt = now, updatedAt = now,
        )
        parentDao.upsert(entity)
        val parent = LocalMappers.run { entity.toDomain() }
        auditDao.upsert(audit("parent.create", "parent", parent.id, actorId, actorName,
            after = """{"code":"$code","name":"${parent.fullName}"}"""))
        return Result.Ok(parent)
    }

    override suspend fun updateParent(id: String, input: UpdateParentInput, actorId: String, actorName: String): Result<Parent> {
        val existing = parentDao.getById(id) ?: return Result.Err(Errors.notFound("Parent $id not found"))
        val updated = existing.copy(
            firstName = input.firstName ?: existing.firstName,
            lastName = input.lastName ?: existing.lastName,
            // FIX (dropped field): `displayName` was accepted but never applied.
            // Only recompute when first/last actually change so unrelated
            // updates don't clobber an imported display name.
            displayName = when {
                input.displayName != null -> input.displayName
                input.firstName != null || input.lastName != null ->
                    listOfNotNull(
                        input.firstName ?: existing.firstName,
                        input.lastName ?: existing.lastName,
                    ).joinToString(" ").trim().ifEmpty { existing.displayName }
                else -> existing.displayName
            },
            phone = input.phone ?: existing.phone,
            email = input.email ?: existing.email,
            occupation = input.occupation ?: existing.occupation,
            address = input.address ?: existing.address,
            transportDestination = input.transportDestination ?: existing.transportDestination,
            // Vault §04.03 — master-info edits (secondary phone / national ID /
            // relationship). Nullable elvis keeps unset fields untouched.
            whatsapp = input.secondaryPhone ?: existing.whatsapp,
            nationalId = input.nationalId ?: existing.nationalId,
            relationship = input.relationship ?: existing.relationship,
            preferredLanguage = input.preferredLanguage ?: existing.preferredLanguage,
            updatedAt = Instant.now().toString(),
        )
        parentDao.update(updated)
        auditDao.upsert(audit("parent.update", "parent", id, actorId, actorName, after = "{}"))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun deleteParent(id: String, actorId: String, actorName: String): Result<Unit> {
        // FIX (orphaned records): deleting a parent left its students (and
        // their payments/ledger entries) orphaned — dangling parent_id FKs.
        // Mirrors the desktop semantics: refuse to delete a parent that still
        // has linked students.
        val children = studentDao.observeByParent(id).first()
        if (children.isNotEmpty()) {
            return Result.Err(Errors.conflict(
                "Impossible de supprimer ce parent : ${children.size} élève(s) y sont encore rattachés. " +
                    "Transférez ou retirez d'abord les élèves.",
            ))
        }
        parentDao.deleteById(id)
        auditDao.upsert(audit("parent.delete", "parent", id, actorId, actorName))
        return Result.Ok(Unit)
    }
}

// ─── Student Repository ─────────────────────────────────────────────────────

@Singleton
class LocalStudentRepository @Inject constructor(
    private val db: ElImtiyazDatabase,
    private val studentDao: StudentDao,
    private val parentDao: ParentDao,
    private val auditDao: AuditLogDao,
    // CANONICAL-FINANCIAL-LOGIC.md §8.1 — wire SyncSupport so Android
    // batch-registration writes (parent + students + ledger entries +
    // installments) propagate to Supabase. Without this, the desktop
    // never sees families registered on Android.
    private val syncSupport: com.example.infrastructure.sync.SyncSupport? = null,
) : StudentRepository {

    /** JSON builder helper for sync payloads (mirrors LocalPaymentRepository). */
    private fun syncJson(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        kotlinx.serialization.json.buildJsonObject(builder).toString()

    override fun observe(): Flow<List<Student>> =
        studentDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByParent(parentId: String): Flow<List<Student>> =
        studentDao.observeByParent(parentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByClass(classId: String): Flow<List<Student>> =
        studentDao.observeByClass(classId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Student?> =
        studentDao.observeById(id).map { it?.let { e -> LocalMappers.run { e.toDomain() } } }

    override fun search(query: String): Flow<List<Student>> =
        studentDao.search(query).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override suspend fun createStudent(input: CreateStudentInput, actorId: String, actorName: String): Result<Student> {
        val parentId = input.parentId ?: return Result.Err(Errors.validation("Parent ID is required"))
        val now = Instant.now().toString()
        val year = java.time.LocalDate.now().year
        val seq = (studentDao.countActive() + 1).toString().padStart(6, '0')
        val code = "ELV-$year-$seq"
        val entity = StudentEntity(
            id = "stu-${UUID.randomUUID()}",
            tenantId = "00000000-0000-0000-0000-000000000001", code = code, parentId = parentId,
            firstName = input.firstName, lastName = input.lastName,
            displayName = input.displayName ?: "${input.firstName} ${input.lastName}".trim().ifEmpty { null },
            gender = input.gender,
            birthDate = input.birthDate, enrollmentDate = now,
            level = input.level, gradeLevel = input.gradeLevel,
            classId = input.classId, photoUrl = null, medicalNotes = input.medicalNotes,
            status = "active", createdAt = now, updatedAt = now,
        )
        studentDao.upsert(entity)
        auditDao.upsert(audit("student.create", "student", entity.id, actorId, actorName,
            after = """{"code":"$code","name":"${entity.fullName}"}"""))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    override suspend fun updateStudent(id: String, input: UpdateStudentInput, actorId: String, actorName: String): Result<Student> {
        val existing = studentDao.getById(id) ?: return Result.Err(Errors.notFound("Student $id not found"))
        // TIER 4 FIX — validate enrollment status against the canonical value
        // set (mirrors the SQL CHECK after migration 0037). Previously any
        // arbitrary string was accepted locally and later crashed the server
        // CHECK on push.
        input.status?.let { status ->
            if (status !in CANONICAL_STUDENT_STATUSES) {
                return Result.Err(Errors.validation(
                    "Statut d'inscription invalide: '$status'. Valeurs autorisées: ${CANONICAL_STUDENT_STATUSES.joinToString(", ")}",
                ))
            }
        }
        val updated = existing.copy(
            firstName = input.firstName ?: existing.firstName,
            lastName = input.lastName ?: existing.lastName,
            // FIX (dropped field): `displayName` was accepted but never applied —
            // since imported students store their complete name ONLY in
            // displayName, those names could never be corrected.
            // Only recompute the derived name when first/last actually change,
            // so unrelated updates don't clobber an imported display name.
            displayName = when {
                input.displayName != null -> input.displayName
                input.firstName != null || input.lastName != null ->
                    listOfNotNull(
                        input.firstName ?: existing.firstName,
                        input.lastName ?: existing.lastName,
                    ).joinToString(" ").trim().ifEmpty { existing.displayName }
                else -> existing.displayName
            },
            birthDate = input.birthDate ?: existing.birthDate,
            level = input.level ?: existing.level,
            gradeLevel = input.gradeLevel ?: existing.gradeLevel,
            classId = input.classId ?: existing.classId,
            status = input.status ?: existing.status,
            medicalNotes = input.medicalNotes ?: existing.medicalNotes,
            updatedAt = Instant.now().toString(),
        )
        studentDao.update(updated)
        auditDao.upsert(audit("student.update", "student", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun batchRegister(parent: CreateParentInput, students: List<CreateStudentInput>, actorId: String, actorName: String): Result<BatchRegisterResult> {
        if (students.isEmpty()) return Result.Err(Errors.validation("At least one student is required"))
        val now = Instant.now().toString()
        val year = java.time.LocalDate.now().year

        val parentCode = com.example.core.deterministicParentCode(
            year = year,
            input = com.example.core.ParentCodeInput(
                phone = parent.phone,
                displayName = parent.displayName,
                firstName = parent.firstName,
                lastName = parent.lastName,
            ),
        )
        // TIER 2 R15 — deterministic activation_code derived from (parentCode, tenantId).
        val activationCode = com.example.core.deterministicActivationCode(
            parentCode = parentCode,
            tenantId = "00000000-0000-0000-0000-000000000001",
        )
        val parentEntity = ParentEntity(
            id = "par-${UUID.randomUUID()}",
            tenantId = "00000000-0000-0000-0000-000000000001", code = parentCode,
            firstName = parent.firstName, lastName = parent.lastName,
            displayName = parent.displayName ?: "${parent.firstName} ${parent.lastName}".trim().ifEmpty { null },
            phone = parent.phone,
            // Vault §04.03 — secondary phone / national ID / relationship from
            // the registration master-info block (Step 1).
            whatsapp = parent.secondaryPhone ?: parent.phone,
            email = parent.email, occupation = parent.occupation,
            address = parent.address, transportDestination = parent.transportDestination,
            nationalId = parent.nationalId,
            relationship = parent.relationship,
            preferredLanguage = parent.preferredLanguage, avatarUrl = null,
            isActive = true, isFinanciallyRestricted = false,
            activationCode = activationCode, createdAt = now, updatedAt = now,
        )
        parentDao.upsert(parentEntity)

        val (due1, due2, due3) = com.example.core.officialTuitionDueDates(year)
        val studentEntities = mutableListOf<StudentEntity>()
        val ledgerEntries = mutableListOf<LedgerEntryEntity>()
        val installments = mutableListOf<InstallmentEntity>()
        val pricingDao = db.pricingConfigDao()

        students.forEachIndexed { index, s ->
            val seq = (studentDao.countActive() + index + 1).toString().padStart(6, '0')
            val code = "ELV-$year-$seq"
            val studentEntity = StudentEntity(
                id = "stu-${UUID.randomUUID()}",
                tenantId = "00000000-0000-0000-0000-000000000001", code = code, parentId = parentEntity.id,
                firstName = s.firstName, lastName = s.lastName,
                displayName = s.displayName ?: "${s.firstName} ${s.lastName}".trim().ifEmpty { null },
                gender = s.gender,
                birthDate = s.birthDate, enrollmentDate = now,
                level = s.level, gradeLevel = s.gradeLevel,
                classId = s.classId, photoUrl = null, medicalNotes = s.medicalNotes,
                status = "active", createdAt = now, updatedAt = now,
            )
            studentEntities.add(studentEntity)

            val tuition = pricingDao.getTuitionByGrade(s.gradeLevel)
            if (tuition != null) {
                // CANONICAL-FINANCIAL-LOGIC.md §5 — apply ALL 5 discount rules
                // in a single pass on the GROSS annual tuition, then split the
                // net into 3 tranches (or 1 for full_annual). This mirrors the
                // desktop's `computeBilling` + `buildTuitionChargeEntries` so
                // both apps produce identical charge entries for the same
                // student.
                //
                // The previous implementation applied only the sibling
                // discount inline (missing passage_palier, full_annual,
                // highest_average, seniority_5y) — a structural divergence
                // that produced different charge entries for the same
                // student on the same day.
                val paymentPlan = com.example.core.PaymentPlan.fromCode(s.paymentPlan)
                val discountParams = com.example.core.EvaluateAllDiscountsParams(
                    grossTuition = tuition.annualAmount,
                    previousGradeLevel = s.previousGradeLevel,
                    currentGradeLevel = s.gradeLevel,
                    childIndex = index + 1,
                    paymentPlan = paymentPlan,
                    paymentDate = now,
                    academicYearStartYear = year,
                    academicYearStart = "${year}-09-15T00:00:00Z",
                    enrollmentDate = s.enrollmentDate ?: now,
                    previousRank = s.previousRank,
                )
                val evaluations = com.example.core.evaluateAllSystemDiscounts(discountParams)
                val totalDiscount = com.example.core.sumDiscounts(evaluations)
                // `totalDiscount` is NEGATIVE (reduces the gross). The charge
                // entry's `amount` is the NET tuition (gross + totalDiscount),
                // matching the desktop's `netTuition = max(0, gross + tuitionDiscount)`.
                val netTuition = (tuition.annualAmount + totalDiscount).coerceAtLeast(0L)
                val accountId = deriveAccountId(parentEntity.id, PaymentCategory.TUITION, studentEntity.id)

                // CANONICAL-FINANCIAL-LOGIC.md §6.1 — branches on `paymentPlan`:
                //   - `full_annual`: emit 1 charge entry with `metadata: { tranche: null, paymentPlan: "full_annual", ... }`
                //   - `tranches`: emit 3 charge entries with `metadata: { tranche: 1|2|3, paymentPlan: "tranches", ... }`
                val discountsMetadata = evaluations.map { ev ->
                    mapOf("code" to ev.code, "amount" to ev.amount, "reason" to ev.reason)
                }
                if (paymentPlan == com.example.core.PaymentPlan.FULL_ANNUAL) {
                    ledgerEntries.add(
                        LedgerEntryEntity(
                            id = generateEntryId(), tenantId = parentEntity.tenantId,
                            accountId = accountId, parentId = parentEntity.id, studentId = studentEntity.id,
                            category = PaymentCategory.TUITION.code, amount = netTuition,
                            type = "charge", sourceType = "installment", sourceId = "reg-${studentEntity.id}",
                            method = null, receiptNumber = null, paymentStatus = null, reversesId = null,
                            description = "Scolarité annuelle ${s.gradeLevel.uppercase()} $year (paiement intégral)",
                            actorId = actorId, actorName = actorName, at = now,
                            metadataJson = com.example.infrastructure.room.LocalMappers.serializeMetadataJson(
                                mapOf(
                                    "tranche" to null,
                                    "paymentPlan" to "full_annual",
                                    "academicCycle" to year.toString(),
                                    "gradeLevel" to s.gradeLevel,
                                    "discounts" to discountsMetadata,
                                    "netTuition" to netTuition,
                                    "grossTuition" to tuition.annualAmount,
                                    "totalDiscount" to totalDiscount,
                                ),
                            ),
                        )
                    )
                    installments.add(inst("ins-${studentEntity.id}-annual", parentEntity.id, studentEntity.id, "tuition", "Année complète", netTuition, due1, now))
                } else {
                    // Tranches — split the NET (post-discount) by 40/30/30.
                    val (t1, t2, t3) = com.example.core.splitNetTuitionByOfficialSchedule(netTuition)
                    data class TrancheSpec(val amount: Long, val number: Int, val label: String, val dueDate: String)
                    val trancheSpecs = listOf(
                        TrancheSpec(t1, 1, "Tranche 1 (Sept–Déc)", due1),
                        TrancheSpec(t2, 2, "Tranche 2 (Jan–Mar)", due2),
                        TrancheSpec(t3, 3, "Tranche 3 (Avr–Juin)", due3),
                    )
                    for (spec in trancheSpecs) {
                        val amt = spec.amount
                        val trancheNum = spec.number
                        val label = spec.label
                        val dueDate = spec.dueDate
                        ledgerEntries.add(
                            LedgerEntryEntity(
                                id = generateEntryId(), tenantId = parentEntity.tenantId,
                                accountId = accountId, parentId = parentEntity.id, studentId = studentEntity.id,
                                category = PaymentCategory.TUITION.code, amount = amt,
                                type = "charge", sourceType = "installment", sourceId = "reg-${studentEntity.id}-t$trancheNum",
                                method = null, receiptNumber = null, paymentStatus = null, reversesId = null,
                                description = "$label — Scolarité ${s.gradeLevel.uppercase()} $year",
                                actorId = actorId, actorName = actorName, at = now,
                                metadataJson = com.example.infrastructure.room.LocalMappers.serializeMetadataJson(
                                    mapOf(
                                        "tranche" to trancheNum,
                                        "paymentPlan" to "tranches",
                                        "academicCycle" to year.toString(),
                                        "gradeLevel" to s.gradeLevel,
                                        "discounts" to discountsMetadata,
                                        "netTuition" to netTuition,
                                        "grossTuition" to tuition.annualAmount,
                                        "totalDiscount" to totalDiscount,
                                    ),
                                ),
                            )
                        )
                        installments.add(inst("ins-${studentEntity.id}-t$trancheNum", parentEntity.id, studentEntity.id, "tuition", label, amt, dueDate, now))
                    }
                }
            }

            val transport = parent.transportDestination?.let { pricingDao.getTransportByDestination(it) }
            if (transport != null) {
                val accountId = deriveAccountId(parentEntity.id, PaymentCategory.TRANSPORT, studentEntity.id)
                ledgerEntries.add(
                    LedgerEntryEntity(
                        id = generateEntryId(), tenantId = parentEntity.tenantId,
                        accountId = accountId, parentId = parentEntity.id, studentId = studentEntity.id,
                        category = PaymentCategory.TRANSPORT.code, amount = transport.annualAmount,
                        type = "charge", sourceType = "installment", sourceId = "reg-${studentEntity.id}-transport",
                        method = null, receiptNumber = null, paymentStatus = null, reversesId = null,
                        description = "Transport ${parent.transportDestination}",
                        actorId = actorId, actorName = actorName, at = now,
                        metadataJson = "{}",
                    )
                )
                installments.add(inst("ins-${studentEntity.id}-tr1", parentEntity.id, studentEntity.id, "transport", "Transport T1", transport.tranche1, due1, now))
                installments.add(inst("ins-${studentEntity.id}-tr2", parentEntity.id, studentEntity.id, "transport", "Transport T2", transport.tranche2, due2, now))
                installments.add(inst("ins-${studentEntity.id}-tr3", parentEntity.id, studentEntity.id, "transport", "Transport T3", transport.tranche3, due3, now))
            }
        }

        studentDao.upsertAll(studentEntities)
        db.ledgerEntryDao().upsertAll(ledgerEntries)
        db.installmentDao().upsertAll(installments)

        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the parent + each
        // student + each ledger entry + each installment for sync push so
        // the desktop sees newly-registered families from Android. Without
        // this wiring, batch-registered families live in Android Room only.
        syncSupport?.run {
            // Parent
            enqueueOnly(
                entity = "parent",
                operation = "create",
                payload = syncJson {
                    put("id", parentEntity.id); put("tenantId", parentEntity.tenantId)
                    put("code", parentEntity.code); put("firstName", parentEntity.firstName)
                    put("lastName", parentEntity.lastName); put("displayName", parentEntity.displayName ?: "")
                    put("phone", parentEntity.phone); put("whatsapp", parentEntity.whatsapp ?: "")
                    put("email", parentEntity.email ?: ""); put("occupation", parentEntity.occupation ?: "")
                    put("address", parentEntity.address ?: "")
                    put("preferredLanguage", parentEntity.preferredLanguage)
                    put("transportDestination", parentEntity.transportDestination ?: "")
                    // Vault §04.03 — master-info fields ride the sync payload;
                    // the dispatcher's RPC signature is unchanged (it maps the
                    // params it knows, extra keys are ignored server-side).
                    parentEntity.nationalId?.let { put("nationalId", it) }
                    parentEntity.relationship?.let { put("relationship", it) }
                    put("isActive", parentEntity.isActive)
                    put("activationCode", parentEntity.activationCode)
                    put("createdAt", parentEntity.createdAt)
                },
                isMock = false, sourceScreen = "BatchRegistrationScreen",
            )
            // Students
            for (s in studentEntities) {
                enqueueOnly(
                    entity = "student",
                    operation = "create",
                    payload = syncJson {
                        put("id", s.id); put("tenantId", s.tenantId); put("code", s.code)
                        put("parentId", s.parentId); put("parentCode", parentEntity.code)
                            put("firstName", s.firstName)
                        put("lastName", s.lastName); put("displayName", s.displayName ?: "")
                        put("gender", s.gender); put("birthDate", s.birthDate ?: "")
                        put("enrollmentDate", s.enrollmentDate); put("level", s.level)
                        put("gradeLevel", s.gradeLevel); put("classId", s.classId ?: "")
                        put("medicalNotes", s.medicalNotes ?: "")
                        put("status", s.status); put("createdAt", s.createdAt)
                    },
                    isMock = false, sourceScreen = "BatchRegistrationScreen",
                )
            }
            // Ledger entries (tuition charges, transport charges, adjustments)
            for (e in ledgerEntries) {
                enqueueOnly(
                    entity = "ledger_entry",
                    operation = "create",
                    payload = syncJson {
                        put("id", e.id); put("tenantId", e.tenantId); put("accountId", e.accountId)
                        put("parentId", e.parentId); put("parentCode", parentEntity.code)
                        put("studentId", e.studentId ?: "")
                            put("category", e.category); put("amount", e.amount)
                        put("type", e.type); put("sourceType", e.sourceType)
                        put("sourceId", e.sourceId); put("method", e.method ?: "")
                        put("receiptNumber", e.receiptNumber ?: "")
                        put("paymentStatus", e.paymentStatus ?: "")
                        put("reversesId", e.reversesId ?: "")
                        put("description", e.description)
                        put("actorId", e.actorId); put("actorName", e.actorName)
                        put("at", e.at); put("metadataJson", e.metadataJson)
                    },
                    isMock = false, sourceScreen = "BatchRegistrationScreen",
                )
            }
            // Installments (installment is not in the SyncQueueDispatcher's
            // switch statement yet — it falls through to the no-op case,
            // which is fine; the queue entry is marked "synced" and the
            // desktop's pull-side `pull_installments_for_sync` will pick
            // them up on next pull cycle.)
            for (ins in installments) {
                enqueueOnly(
                    entity = "installment",
                    operation = "create",
                    payload = syncJson {
                        put("id", ins.id); put("tenantId", ins.tenantId)
                        put("parentId", ins.parentId); put("parentCode", parentEntity.code)
                            put("studentId", ins.studentId)
                        put("category", ins.category); put("label", ins.label)
                        put("amountDue", ins.amountDue); put("amountPaid", ins.amountPaid)
                        put("amountPending", ins.amountPending); put("dueDate", ins.dueDate)
                        put("status", ins.status)
                    },
                    isMock = false, sourceScreen = "BatchRegistrationScreen",
                )
            }
        }

        auditDao.upsert(audit("crm.batch_register", "parent", parentEntity.id, actorId, actorName,
            after = """{"student_count":${students.size},"activation_code":"$activationCode"}"""))

        return Result.Ok(BatchRegisterResult(
            parent = LocalMappers.run { parentEntity.toDomain() },
            students = studentEntities.map { LocalMappers.run { it.toDomain() } },
            activationCode = activationCode,
        ))
    }

    override suspend fun promoteStudents(academicYear: String, decisions: List<com.example.domain.repository.PromotionDecision>, actorId: String, actorName: String): Result<Unit> {
        // TIER 4 FIX — the previous implementation was a stub: it bumped
        // `updatedAt` and wrote an audit row WITHOUT changing gradeLevel or
        // status, so mobile promotions silently diverged from the desktop's
        // canonical state transitions. It now applies the canonical Algerian
        // progression ladder (core/AcademicProgression.kt, ported 1:1 from the
        // desktop's getNextGradeProgression).
        val now = Instant.now().toString()
        decisions.forEach { d ->
            val existing = studentDao.getById(d.studentId) ?: return@forEach
            val progression = com.example.core.getNextGradeProgression(existing.gradeLevel)
            val updated = when (d.decision) {
                com.example.core.PromotionDecisions.PROMOTED -> {
                    val next = progression.nextGradeCode
                        ?: return@forEach // unknown ladder position — keep state
                    existing.copy(
                        gradeLevel = next,
                        level = progression.nextLevel ?: existing.level,
                        status = "active",
                        updatedAt = now,
                    )
                }
                com.example.core.PromotionDecisions.GRADUATED ->
                    existing.copy(status = "graduated", updatedAt = now)
                else -> existing.copy(status = "active", updatedAt = now) // repeated
            }
            studentDao.update(updated)
            auditDao.upsert(audit("student.promote", "student", d.studentId, actorId, actorName,
                after = """{"decision":"${d.decision}","year":"$academicYear","from":"${existing.gradeLevel}","to":"${updated.gradeLevel}","status":"${updated.status}"}"""))
            // Propagate the promotion to Supabase so the desktop sees it.
            syncSupport?.enqueueOnly(
                entity = "student",
                operation = "promote",
                payload = syncJson {
                    put("id", updated.id); put("tenantId", updated.tenantId)
                    put("code", updated.code); put("parentId", updated.parentId)
                    put("firstName", updated.firstName); put("lastName", updated.lastName)
                    put("displayName", updated.displayName ?: "")
                    put("gradeLevel", updated.gradeLevel); put("level", updated.level)
                    put("status", updated.status)
                },
                isMock = false, sourceScreen = "PromotionScreen",
            )
        }
        return Result.Ok(Unit)
    }
}

// ─── Payment Repository ─────────────────────────────────────────────────────

@Singleton
class LocalPaymentRepository @Inject constructor(
    private val db: ElImtiyazDatabase,
    private val paymentDao: PaymentDao,
    private val installmentDao: InstallmentDao,
    private val ledgerDao: LedgerEntryDao,
    private val auditDao: AuditLogDao,
    // TIER 4 FIX — parent lookup so sync payloads carry the canonical
    // parent_code (the server resolves parent refs by UUID or code, never
    // by mobile-local ids).
    private val parentDao: ParentDao,
    // CANONICAL-FINANCIAL-LOGIC.md §8.1 — wire the SyncSupport helper so
    // Android payment writes propagate to Supabase. Previously Android was
    // read-only relative to Supabase for the payments table.
    private val syncSupport: com.example.infrastructure.sync.SyncSupport? = null,
) : PaymentRepository {

    /** Serialize an entity for the sync queue payload. */
    private fun syncJson(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        kotlinx.serialization.json.buildJsonObject(builder).toString()

    override fun observe(): Flow<List<Payment>> =
        paymentDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByParent(parentId: String): Flow<List<Payment>> =
        paymentDao.observeByParent(parentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByStudent(studentId: String): Flow<List<Payment>> =
        paymentDao.observeByStudent(studentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Payment?> =
        paymentDao.observeById(id).map { it?.let { e -> LocalMappers.run { e.toDomain() } } }

    /** Resolve the canonical parent code for sync payloads (server-side ref). */
    private suspend fun parentCodeFor(parentId: String): String? =
        parentDao.getById(parentId)?.code

    override suspend fun collect(input: CollectPaymentInput, actorId: String, actorName: String): Result<Payment> {
        if (input.amount <= 0L) return Result.Err(Errors.validation("Amount must be > 0"))
        if (input.method.requiresProof && input.proofPath.isNullOrBlank())
            return Result.Err(Errors.validation("Proof is required for ${input.method.code}"))

        val now = Instant.now().toString()
        val year = java.time.LocalDate.now().year
        val seq = (paymentDao.listAll().size + 1).toString().padStart(6, '0')
        val receipt = "REC-$year-$seq"
        val paymentId = "pay-${UUID.randomUUID()}"
        val status = if (input.method == PaymentMethod.CASH) PaymentStatus.PAID else PaymentStatus.PENDING

        val entity = PaymentEntity(
            id = paymentId, tenantId = "00000000-0000-0000-0000-000000000001", receiptNumber = receipt,
            parentId = input.parentId, studentId = input.studentId, amount = input.amount,
            method = input.method.code, status = status.code, category = input.category.code,
            installmentId = input.installmentId, proofUrl = input.proofPath,
            checkNumber = input.checkNumber, checkBankName = input.checkBankName,
            checkIssueDate = input.checkIssueDate, checkClearanceDate = input.checkClearanceDate,
            transferReference = input.transferReference, transferSourceBank = input.transferSourceBank,
            notes = input.notes, collectedBy = actorId, collectedBy_name = actorName,
            collectedAt = now, createdAt = now, updatedAt = now,
        )
        paymentDao.upsert(entity)
        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the payment row for sync push.
        val parentCode = parentCodeFor(entity.parentId) ?: ""
        syncSupport?.enqueueOnly(
            entity = "payment",
            operation = "create",
            payload = syncJson {
                put("id", entity.id); put("tenantId", entity.tenantId)
                put("receiptNumber", entity.receiptNumber)
                put("parentId", entity.parentId); put("parentCode", parentCode); put("studentId", entity.studentId ?: "")
                put("amount", entity.amount); put("method", entity.method)
                put("status", entity.status); put("category", entity.category)
                put("installmentId", entity.installmentId ?: "")
                put("proofUrl", entity.proofUrl ?: "")
                put("checkNumber", entity.checkNumber ?: "")
                put("checkBankName", entity.checkBankName ?: "")
                put("checkIssueDate", entity.checkIssueDate ?: "")
                put("checkClearanceDate", entity.checkClearanceDate ?: "")
                put("transferReference", entity.transferReference ?: "")
                put("transferSourceBank", entity.transferSourceBank ?: "")
                put("notes", entity.notes ?: "")
                put("collectedBy", entity.collectedBy)
                put("collectedAt", entity.collectedAt)
            },
            isMock = false, sourceScreen = "CounterPaymentScreen",
        )

        val ledgerEntry = createPaymentEntry(
            tenantId = entity.tenantId, parentId = input.parentId, studentId = input.studentId,
            category = input.category, amount = input.amount,
            method = input.method, receiptNumber = receipt, paymentStatus = status,
            sourceId = paymentId, actorId = actorId, actorName = actorName,
            description = "Encaissement $receipt",
        )
        ledgerDao.upsert(ledgerEntry.toEntity())
        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the ledger entry for sync push.
        // FIX (duplicate declaration): `parentCode` was declared twice in the
        // same scope — a compile error that broke the whole build.
        syncSupport?.enqueueOnly(
            entity = "ledger_entry",
            operation = "create",
            payload = syncJson {
                put("id", ledgerEntry.id); put("tenantId", ledgerEntry.tenantId)
                put("accountId", ledgerEntry.accountId)
                put("parentId", ledgerEntry.parentId); put("parentCode", parentCode); put("studentId", ledgerEntry.studentId ?: "")
                put("category", ledgerEntry.category.code); put("amount", ledgerEntry.amount)
                put("type", ledgerEntry.type.code); put("sourceType", ledgerEntry.sourceType.code)
                put("sourceId", ledgerEntry.sourceId); put("method", ledgerEntry.method?.code ?: "")
                put("receiptNumber", ledgerEntry.receiptNumber ?: "")
                put("paymentStatus", ledgerEntry.paymentStatus?.code ?: "")
                put("reversesId", ledgerEntry.reversesId ?: "")
                put("description", ledgerEntry.description)
                put("actorId", ledgerEntry.actorId); put("actorName", ledgerEntry.actorName)
                put("at", ledgerEntry.at)
                put("metadataJson", com.example.infrastructure.room.LocalMappers.serializeMetadataJson(ledgerEntry.metadata))
            },
            isMock = false, sourceScreen = "CounterPaymentScreen",
        )

        val familyInstallments = installmentDao.listByParent(input.parentId)
            .map { WaterfallInstallment(it.id, PaymentCategory.fromCode(it.category), it.amountDue, it.amountPaid, it.amountPending, it.dueDate, it.status) }

        val allocation = allocatePaymentToInstallments(
            installments = familyInstallments,
            paymentAmount = input.amount,
            categoryFilter = input.category,
            paymentStatus = status,
        )

        allocation.allocations.forEach { a ->
            installmentDao.getById(a.installmentId)?.let { ins ->
                installmentDao.update(ins.copy(
                    amountPaid = a.newAmountPaid,
                    amountPending = a.newAmountPending,
                    status = a.newStatus,
                    paidDate = if (a.newStatus == "paid") now else ins.paidDate,
                    updatedAt = now,
                ))
            }
        }

        if (allocation.unallocatedAmount > 0L) {
            // CANONICAL-FINANCIAL-LOGIC.md §4 INV-7 — overpayment credit
            // MUST land on a parent-scoped `parent_credit` account, NOT on
            // the input category's student-scoped account. Otherwise the
            // desktop reconciler raises `UNBACKED_PARENT_CREDIT` and the
            // auto-absorb-on-future-charges logic cannot find the credit.
            val creditEntry = com.example.core.createAdjustmentEntry(
                tenantId = entity.tenantId,
                parentId = input.parentId,
                studentId = null, // parent-scoped — NOT input.studentId
                category = PaymentCategory.PARENT_CREDIT,
                amount = -allocation.unallocatedAmount,
                sourceId = paymentId, actorId = actorId, actorName = actorName,
                reason = "Crédit parent (trop-perçu) $receipt",
            )
            ledgerDao.upsert(creditEntry.toEntity())
            // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the parent_credit
            // adjustment for sync push so the desktop sees it.
        val parentCode = parentCodeFor(creditEntry.parentId) ?: ""
            syncSupport?.enqueueOnly(
                entity = "ledger_entry",
                operation = "create",
                payload = syncJson {
                    put("id", creditEntry.id); put("tenantId", creditEntry.tenantId)
                    put("accountId", creditEntry.accountId)
                    put("parentId", creditEntry.parentId); put("parentCode", parentCode); put("studentId", creditEntry.studentId ?: "")
                    put("category", creditEntry.category.code); put("amount", creditEntry.amount)
                    put("type", creditEntry.type.code); put("sourceType", creditEntry.sourceType.code)
                    put("sourceId", creditEntry.sourceId); put("method", creditEntry.method?.code ?: "")
                    put("receiptNumber", creditEntry.receiptNumber ?: "")
                    put("paymentStatus", creditEntry.paymentStatus?.code ?: "")
                    put("reversesId", creditEntry.reversesId ?: "")
                    put("description", creditEntry.description)
                    put("actorId", creditEntry.actorId); put("actorName", creditEntry.actorName)
                    put("at", creditEntry.at)
                    put("metadataJson", com.example.infrastructure.room.LocalMappers.serializeMetadataJson(creditEntry.metadata))
                },
                isMock = false, sourceScreen = "CounterPaymentScreen",
            )
        }

        auditDao.upsert(audit("payment.collect", "payment", paymentId, actorId, actorName,
            after = """{"receipt":"$receipt","amount":${input.amount},"method":"${input.method.code}"}"""))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    override suspend fun refund(paymentId: String, reason: String, actorId: String, actorName: String): Result<Payment> {
        val existing = paymentDao.getById(paymentId) ?: return Result.Err(Errors.notFound("Payment $paymentId not found"))
        val now = Instant.now().toString()
        val updated = existing.copy(status = PaymentStatus.REFUNDED.code, updatedAt = now)
        paymentDao.update(updated)
        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the payment status update
        // (refunded) for sync push so the desktop sees the refund.
        syncSupport?.enqueueOnly(
            entity = "payment",
            operation = "refund",
            payload = syncJson {
                put("id", existing.id); put("status", PaymentStatus.REFUNDED.code)
                put("receiptNumber", existing.receiptNumber); put("updatedAt", now)
            },
            isMock = false, sourceScreen = "PaymentDetailScreen",
        )

        val originalLedger = ledgerDao.listByParent(existing.parentId)
            .firstOrNull { it.sourceId == paymentId && it.type == "payment" }
        if (originalLedger != null) {
            val reversal = createReversalEntry(LocalMappers.run { originalLedger.toDomain() }, reason, actorId, actorName)
            ledgerDao.upsert(reversal.toEntity())
            // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the reversal ledger
            // entry for sync push.
        val parentCode = parentCodeFor(reversal.parentId) ?: ""
            syncSupport?.enqueueOnly(
                entity = "ledger_entry",
                operation = "reverse",
                payload = syncJson {
                    put("id", reversal.id); put("tenantId", reversal.tenantId)
                    put("accountId", reversal.accountId)
                    put("parentId", reversal.parentId); put("parentCode", parentCode); put("studentId", reversal.studentId ?: "")
                    put("category", reversal.category.code); put("amount", reversal.amount)
                    put("type", reversal.type.code); put("sourceType", reversal.sourceType.code)
                    put("sourceId", reversal.sourceId); put("method", reversal.method?.code ?: "")
                    put("receiptNumber", reversal.receiptNumber ?: "")
                    put("paymentStatus", reversal.paymentStatus?.code ?: "")
                    put("reversesId", reversal.reversesId ?: "")
                    put("description", reversal.description)
                    put("actorId", reversal.actorId); put("actorName", reversal.actorName)
                    put("at", reversal.at)
                    put("metadataJson", com.example.infrastructure.room.LocalMappers.serializeMetadataJson(reversal.metadata))
                },
                isMock = false, sourceScreen = "PaymentDetailScreen",
            )

            // CANONICAL-FINANCIAL-LOGIC.md §4 INV-8 — refund LIFO must
            // branch on `originalWasPending`. Without this, refunding an
            // uncleared (pending) check/transfer tries to subtract from
            // `amountPaid` (which is 0 for a pending payment), the revert
            // is a silent no-op, and `amountPending` stays inflated.
            val originalWasPending = originalLedger.paymentStatus == PaymentStatus.PENDING.code

            val familyInstallments = installmentDao.listByParent(existing.parentId)
                .map { WaterfallInstallment(it.id, PaymentCategory.fromCode(it.category), it.amountDue, it.amountPaid, it.amountPending, it.dueDate, it.status) }
            val revert = com.example.core.revertPaymentAllocation(
                installments = familyInstallments,
                reversalAmount = existing.amount,
                categoryFilter = PaymentCategory.fromCode(existing.category),
                originalWasPending = originalWasPending,
            )
            revert.reverts.forEach { r ->
                installmentDao.getById(r.installmentId)?.let { ins ->
                    installmentDao.update(ins.copy(
                        amountPaid = r.newAmountPaid,
                        amountPending = r.newAmountPending,
                        status = r.newStatus,
                        updatedAt = now,
                    ))
                }
            }
        }

        auditDao.upsert(audit("payment.refund", "payment", paymentId, actorId, actorName,
            after = """{"reason":"$reason"}"""))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun adjust(input: com.example.domain.repository.AdjustAccountInput, actorId: String, actorName: String): Result<Unit> {
        // CANONICAL-FINANCIAL-LOGIC.md §4 INV-7 — overpayment credits (amount < 0)
        // MUST land on the parent-scoped parent_credit account, NOT the input
        // category's student-scoped account. Positive adjustments (penalty / late
        // fee) keep their input category + studentId.
        val isCredit = input.amount < 0L
        val resolvedCategory = if (isCredit) PaymentCategory.PARENT_CREDIT else input.category
        val resolvedStudentId = if (isCredit) null else input.studentId
        val entry = com.example.core.createAdjustmentEntry(
            tenantId = "00000000-0000-0000-0000-000000000001",
            parentId = input.parentId, studentId = resolvedStudentId,
            category = resolvedCategory, amount = input.amount,
            sourceId = "adj-${UUID.randomUUID()}", actorId = actorId, actorName = actorName,
            reason = input.reason, receiptRef = input.receiptRef,
        )
        ledgerDao.upsert(entry.toEntity())
        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the adjustment entry for
        // sync push so the desktop sees it.
        val parentCode = parentCodeFor(entry.parentId) ?: ""
        syncSupport?.enqueueOnly(
            entity = "ledger_entry",
            operation = "adjust",
            payload = syncJson {
                put("id", entry.id); put("tenantId", entry.tenantId)
                put("accountId", entry.accountId)
                put("parentId", entry.parentId); put("parentCode", parentCode); put("studentId", entry.studentId ?: "")
                put("category", entry.category.code); put("amount", entry.amount)
                put("type", entry.type.code); put("sourceType", entry.sourceType.code)
                put("sourceId", entry.sourceId); put("method", entry.method?.code ?: "")
                put("receiptNumber", entry.receiptNumber ?: "")
                put("paymentStatus", entry.paymentStatus?.code ?: "")
                put("reversesId", entry.reversesId ?: "")
                put("description", entry.description)
                put("actorId", entry.actorId); put("actorName", entry.actorName)
                put("at", entry.at)
                put("metadataJson", com.example.infrastructure.room.LocalMappers.serializeMetadataJson(entry.metadata))
            },
            isMock = false, sourceScreen = "AdjustAccount",
        )
        auditDao.upsert(audit("payment.adjust", "ledger", entry.id, actorId, actorName,
            after = """{"reason":"${input.reason}","amount":${input.amount}}"""))
        return Result.Ok(Unit)
    }
}

// ─── Installment Repository ─────────────────────────────────────────────────

@Singleton
class LocalInstallmentRepository @Inject constructor(
    private val installmentDao: InstallmentDao,
    private val auditDao: AuditLogDao,
    // CANONICAL-FINANCIAL-LOGIC.md §8.1 — wire SyncSupport so installment
    // updates (markPaid, updateDueDate) propagate to Supabase.
    private val syncSupport: com.example.infrastructure.sync.SyncSupport? = null,
    // FIX (ledger divergence): needed so an interactive "mark paid" also
    // writes the backing payment + ledger entry — previously ONLY the
    // installment row flipped, so the canonical ledger replay (Progression
    // card, parent balances) never changed.
    private val db: ElImtiyazDatabase,
) : InstallmentRepository {

    private fun syncJson(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        kotlinx.serialization.json.buildJsonObject(builder).toString()

    override fun observeByParent(parentId: String): Flow<List<Installment>> =
        installmentDao.observeByParent(parentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByStudent(studentId: String): Flow<List<Installment>> =
        installmentDao.observeByStudent(studentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Installment?> =
        installmentDao.observeById(id).map { it?.let { e -> LocalMappers.run { e.toDomain() } } }

    override suspend fun markPaid(id: String, actorId: String, actorName: String): Result<Installment> {
        val existing = installmentDao.getById(id) ?: return Result.Err(Errors.notFound("Installment $id not found"))
        val now = Instant.now().toString()
        // CANONICAL-FINANCIAL-LOGIC.md §7.3 — INV "amountPaid >= amountDue"
        // when status='paid'. Set amountPaid = amountDue (matches the desktop
        // SupabaseInstallmentRepository.markPaid fix).
        val updated = existing.copy(amountPaid = existing.amountDue, amountPending = 0L, status = "paid", paidDate = now, updatedAt = now)
        installmentDao.update(updated)

        // FIX (ledger divergence): record the backing cash payment + ledger
        // entry so the canonical ledger replay reflects the settlement.
        // Without this, the tranche showed "paid" while the parent's balance
        // and the Progression card ignored it entirely.
        if (existing.status != "paid" && existing.amountDue > 0L) {
            try {
                val paymentDao = db.paymentDao()
                val ledgerDao = db.ledgerEntryDao()
                val year = java.time.LocalDate.now().year
                val seq = (paymentDao.listAll().size + 1).toString().padStart(6, '0')
                val receipt = "REC-$year-$seq"
                val paymentId = "pay-${UUID.randomUUID()}"
                val paymentEntity = com.example.infrastructure.room.PaymentEntity(
                    id = paymentId,
                    tenantId = "00000000-0000-0000-0000-000000000001",
                    receiptNumber = receipt,
                    parentId = existing.parentId,
                    studentId = existing.studentId,
                    amount = existing.amountDue,
                    method = PaymentMethod.CASH.code,
                    status = PaymentStatus.PAID.code,
                    category = existing.category,
                    installmentId = existing.id,
                    proofUrl = null, checkNumber = null, checkBankName = null,
                    checkIssueDate = null, checkClearanceDate = null,
                    transferReference = null, transferSourceBank = null,
                    notes = "Marqué payé (tranche ${existing.label})",
                    collectedBy = actorId, collectedBy_name = actorName,
                    collectedAt = now, createdAt = now, updatedAt = now,
                )
                paymentDao.upsert(paymentEntity)
                val category = PaymentCategory.fromCode(existing.category)
                    ?: PaymentCategory.OTHER
                ledgerDao.upsert(
                    createPaymentEntry(
                        tenantId = paymentEntity.tenantId,
                        parentId = existing.parentId,
                        studentId = existing.studentId,
                        category = category,
                        amount = existing.amountDue,
                        method = PaymentMethod.CASH,
                        receiptNumber = receipt,
                        paymentStatus = PaymentStatus.PAID,
                        sourceId = paymentId,
                        actorId = actorId,
                        actorName = actorName,
                        description = "Encaissement $receipt — tranche ${existing.label}",
                    ).toEntity()
                )
                syncSupport?.enqueueOnly(
                    entity = "payment",
                    operation = "create",
                    payload = syncJson {
                        put("id", paymentId)
                        put("tenantId", paymentEntity.tenantId)
                        put("receiptNumber", receipt)
                        put("parentId", existing.parentId)
                        put("studentId", existing.studentId ?: "")
                        put("amount", existing.amountDue)
                        put("method", paymentEntity.method)
                        put("status", paymentEntity.status)
                        put("category", existing.category)
                        put("installmentId", existing.id)
                        put("collectedBy", actorId)
                        put("collectedAt", now)
                    },
                    isMock = false, sourceScreen = "InstallmentSchedule",
                )
            } catch (t: Throwable) {
                // Never fail the markPaid because of the companion entries —
                // but record what happened for diagnosis.
                auditDao.upsert(audit("installment.markPaid.companion_error", "installment", id, actorId, actorName,
                    after = "{\"error\":\"" + (t.message ?: "unknown") + "\"}"))
            }
        }

        // Enqueue for sync push.
        syncSupport?.enqueueOnly(
            entity = "installment",
            operation = "markPaid",
            payload = syncJson {
                put("id", id); put("status", "paid"); put("amountPaid", updated.amountPaid)
                put("amountPending", updated.amountPending); put("paidDate", now)
            },
            isMock = false, sourceScreen = "InstallmentSchedule",
        )
        auditDao.upsert(audit("installment.markPaid", "installment", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun updateDueDate(id: String, dueDate: String, note: String?, actorId: String, actorName: String): Result<Installment> {
        val existing = installmentDao.getById(id) ?: return Result.Err(Errors.notFound("Installment $id not found"))
        val updated = existing.copy(dueDate = dueDate, customSchedule = true, customScheduleNote = note, updatedAt = Instant.now().toString())
        installmentDao.update(updated)
        // Enqueue for sync push.
        syncSupport?.enqueueOnly(
            entity = "installment",
            operation = "updateDueDate",
            payload = syncJson {
                put("id", id); put("dueDate", dueDate); put("note", note ?: "")
                put("customSchedule", true)
            },
            isMock = false, sourceScreen = "InstallmentSchedule",
        )
        auditDao.upsert(audit("installment.updateDueDate", "installment", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun regenerateForCycle(parentId: String, cycle: String, actorId: String, actorName: String): Result<List<Installment>> {
        auditDao.upsert(audit("installment.regenerate", "installment", parentId, actorId, actorName, after = """{"cycle":"$cycle"}"""))
        return Result.Ok(installmentDao.listByParent(parentId).map { LocalMappers.run { it.toDomain() } })
    }

    override suspend fun findOverdue(): Result<List<Installment>> {
        val now = Instant.now().toString()
        return Result.Ok(installmentDao.listOverdue(now).map { LocalMappers.run { it.toDomain() } })
    }
}

// ─── Ledger Repository ──────────────────────────────────────────────────────

@Singleton
class LocalLedgerRepository @Inject constructor(
    private val ledgerDao: LedgerEntryDao,
    // TIER 4 FIX — reconcile()'s cross-checks (R10) need the payment,
    // installment and parent tables as inputs. The constructor previously
    // injected only ledgerDao, leaving paymentDao / installmentDao / parentDao
    // as unresolved references — a compile error.
    private val paymentDao: PaymentDao,
    private val installmentDao: InstallmentDao,
    private val parentDao: ParentDao,
    // CANONICAL-FINANCIAL-LOGIC.md §8.1 — wire SyncSupport so ledger writes
    // (append, appendMany, reverse) propagate to Supabase.
    private val syncSupport: com.example.infrastructure.sync.SyncSupport? = null,
) : LedgerRepository {

    private fun syncJson(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        kotlinx.serialization.json.buildJsonObject(builder).toString()

    /** Serialize a LedgerEntry for the sync queue payload. */
    private fun com.example.core.LedgerEntry.toSyncPayload(): String = syncJson {
        put("id", id); put("tenantId", tenantId); put("accountId", accountId)
        put("parentId", parentId); put("studentId", studentId ?: "")
        put("category", category.code); put("amount", amount)
        put("type", type.code); put("sourceType", sourceType.code)
        put("sourceId", sourceId); put("method", method?.code ?: "")
        put("receiptNumber", receiptNumber ?: "")
        put("paymentStatus", paymentStatus?.code ?: "")
        put("reversesId", reversesId ?: "")
        put("description", description)
        put("actorId", actorId); put("actorName", actorName); put("at", at)
        put("metadataJson", com.example.infrastructure.room.LocalMappers.serializeMetadataJson(metadata))
    }

    override fun observe(): Flow<List<com.example.core.LedgerEntry>> =
        ledgerDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByParent(parentId: String): Flow<List<com.example.core.LedgerEntry>> =
        ledgerDao.observeByParent(parentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByAccount(accountId: String): Flow<List<com.example.core.LedgerEntry>> =
        ledgerDao.observeByAccount(accountId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override suspend fun append(entry: com.example.core.LedgerEntry): Result<com.example.core.LedgerEntry> {
        ledgerDao.upsert(entry.toEntity())
        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the ledger entry for sync push.
        syncSupport?.enqueueOnly(
            entity = "ledger_entry",
            operation = "create",
            payload = entry.toSyncPayload(),
            isMock = false, sourceScreen = "LedgerAppend",
        )
        return Result.Ok(entry)
    }

    override suspend fun appendMany(entries: List<com.example.core.LedgerEntry>): Result<List<com.example.core.LedgerEntry>> {
        ledgerDao.upsertAll(entries.map { it.toEntity() })
        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue each entry for sync push.
        for (entry in entries) {
            syncSupport?.enqueueOnly(
                entity = "ledger_entry",
                operation = "create",
                payload = entry.toSyncPayload(),
                isMock = false, sourceScreen = "LedgerAppendMany",
            )
        }
        return Result.Ok(entries)
    }

    override suspend fun reverse(originalId: String, reason: String, actorId: String, actorName: String): Result<com.example.core.LedgerEntry> {
        val original = ledgerDao.getById(originalId) ?: return Result.Err(Errors.notFound("Ledger entry $originalId not found"))
        val reversal = createReversalEntry(LocalMappers.run { original.toDomain() }, reason, actorId, actorName)
        ledgerDao.upsert(reversal.toEntity())
        // CANONICAL-FINANCIAL-LOGIC.md §8.1 — enqueue the reversal entry for sync push.
        syncSupport?.enqueueOnly(
            entity = "ledger_entry",
            operation = "reverse",
            payload = reversal.toSyncPayload(),
            isMock = false, sourceScreen = "LedgerReverse",
        )
        return Result.Ok(reversal)
    }

    override suspend fun summary(parentId: String): Result<com.example.core.ParentLedgerSummary> {
        val entries = ledgerDao.listByParent(parentId).map { LocalMappers.run { it.toDomain() } }
        // TIER 4 FIX — pass the overdue due-date map (canonical rule: an account
        // is overdue when balance > 0 and its latest charge date is in the past).
        // Without the map, computeParentSummary silently reports totalOverdue = 0.
        val dueDates = com.example.core.LedgerEngine.buildOverdueDueDateMap(entries)
        val summary = LedgerEngine.computeParentSummary(entries, parentId, "", dueDates)
        return Result.Ok(summary)
    }

    override suspend fun reconcile(): Result<com.example.core.Reconcile.Report> {
        val entries = ledgerDao.listAll().map { LocalMappers.run { it.toDomain() } }
        // TIER 2 R10 — pass real cross-check inputs so the 3 new unified-architecture
        // cross-checks (UNBACKED_TRANCHE_SATISFACTION, PAYMENT_LEDGER_MISMATCH,
        // UNBACKED_PARENT_CREDIT) have data to verify against. Previously the
        // call passed an empty CrossCheckInputs() — those 3 checks were no-ops.
        val payments = paymentDao.listAll().map { LocalMappers.run { it.toDomain() } }
        val installments = installmentDao.listAll().map { LocalMappers.run { it.toDomain() } }
        val parents = parentDao.listAll()

        val paymentInputs = payments.map { p ->
            com.example.core.Reconcile.PaymentCrossCheck(
                id = p.id, amount = p.amount, status = p.status,
            )
        }
        val installmentInputs = installments.map { i ->
            com.example.core.Reconcile.InstallmentCrossCheck(
                id = i.id,
                parentId = i.parentId,
                studentId = i.studentId,
                category = i.category.code,
                amountDue = i.amountDue,
                amountPaid = i.amountPaid,
                label = i.label,
                status = i.status.code,
            )
        }
        // Build parent summaries — one entry per parent with outstanding + accounts.
        // We group by parentId and compute the canonical summary via LedgerEngine.
        val parentSummaries = parents.map { p ->
            val parentEntries = entries.filter { it.parentId == p.id }
            val summary = com.example.core.LedgerEngine.computeParentSummary(
                parentEntries, p.id, p.fullName,
            )
            com.example.core.Reconcile.ParentSummaryCrossCheck(
                parentId = p.id,
                parentName = p.fullName,
                totalOutstanding = summary.totalOutstanding,
                accounts = summary.accounts.map { acc ->
                    com.example.core.Reconcile.ParentAccountCrossCheck(
                        accountId = acc.accountId,
                        category = acc.category.code,
                        studentId = acc.studentId,
                        balance = acc.balance,
                        unallocatedCredit = acc.unallocatedCredit,
                    )
                },
            )
        }
        // paymentToInstallmentId lookup — payments carry `installmentId` field.
        val payToInst = payments.filter { it.installmentId != null }
            .associate { it.id to it.installmentId!! }

        val inputs = com.example.core.Reconcile.CrossCheckInputs(
            payments = paymentInputs,
            installments = installmentInputs,
            parentSummaries = parentSummaries,
            paymentToInstallmentId = payToInst,
        )
        return Result.Ok(com.example.core.Reconcile.reconcileLedger(entries, inputs))
    }
}

// ─── Helper extensions ──────────────────────────────────────────────────────

private fun com.example.core.LedgerEntry.toEntity() = LedgerEntryEntity(
    id = id, tenantId = tenantId, accountId = accountId, parentId = parentId,
    studentId = studentId, category = category.code, amount = amount, type = type.code,
    sourceType = sourceType.code, sourceId = sourceId, method = method?.code,
    receiptNumber = receiptNumber, paymentStatus = paymentStatus?.code,
    reversesId = reversesId, description = description, actorId = actorId,
    actorName = actorName, at = at,
    // CANONICAL-FINANCIAL-LOGIC.md §7.5 + §8.4 — persist metadata so pull-side
    // replay has access to tranche/level/gradeLevel/paymentPlan/academicCycle
    // /clubCategory/therapyKind/period/sessionCount/serviceQualifier/pricingSource
    // /reversedEntryId/reason.
    metadataJson = com.example.infrastructure.room.LocalMappers.serializeMetadataJson(metadata),
)

private fun audit(action: String, entityType: String, entityId: String, actorId: String, actorName: String, after: String? = null) = AuditLogEntity(
    id = "aud-${UUID.randomUUID()}", tenantId = "00000000-0000-0000-0000-000000000001",
    action = action, entityType = entityType, entityId = entityId,
    actorId = actorId, actorName = actorName, actorRole = null,
    beforeJson = null, afterJson = after, note = null,
    createdAt = Instant.now().toString(),
)

private fun inst(id: String, parentId: String, studentId: String, category: String, label: String, amountDue: Long, dueDate: String, now: String) = InstallmentEntity(
    id = id, tenantId = "00000000-0000-0000-0000-000000000001", parentId = parentId, studentId = studentId,
    category = category, label = label, amountDue = amountDue, amountPaid = 0L, amountPending = 0L,
    dueDate = dueDate, paidDate = null,
    status = if (dueDate < now) "overdue" else "pending",
    academicCycle = null, customSchedule = false, customScheduleNote = null,
    createdAt = now, updatedAt = now,
)