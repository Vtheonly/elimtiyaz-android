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
