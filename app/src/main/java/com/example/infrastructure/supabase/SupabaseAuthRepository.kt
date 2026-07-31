package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.Permission
import com.example.core.Result
import com.example.core.Role
import com.example.core.Session
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of AuthRepository.
 *
 * Sign-in flow (mirrors desktop `SupabaseAuthRepository.signIn`):
 *   1. signInWithPassword(email, password) → access+refresh tokens
 *   2. Fetch user_profiles row by auth_user_id
 *   3. Reject if status == "pending" or "suspended"
 *   4. Fetch roles via current_user_roles() RPC
 *   5. Fetch permissions via current_user_permissions() RPC
 *   6. Build immutable Session value
 *
 * JWT persistence: handled automatically by the Supabase Auth plugin
 * (encrypted storage configured in [com.example.di.SupabaseModule]).
 */
@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : com.example.domain.repository.AuthRepository {

    private val auth: Auth get() = provider.auth

    private val _sessionState = MutableStateFlow<Session?>(null)
    override fun observeSession(): StateFlow<Session?> = _sessionState.asStateFlow()

    override suspend fun signIn(email: String, password: String): Result<Session> = try {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }

        val userInfo = auth.currentUserOrNull()
            ?: return Result.Err(Errors.unauthorized("No authenticated user after sign-in"))

        val profile = fetchUserProfile(userInfo.id)
            ?: return Result.Err(Errors.notFound("User profile not found for auth uid ${userInfo.id}"))

        when (profile.status) {
            "pending"   -> return Result.Err(Errors.forbidden("Account pending approval"))
            "suspended" -> return Result.Err(Errors.forbidden("Account suspended"))
            "deleted"   -> return Result.Err(Errors.notFound("Account deleted"))
        }

        val roles = fetchUserRoles()
        val role = roles.firstOrNull()
            ?.let { Role.fromCode(it) }
            ?: Role.SUPPORT_STAFF

        val permissionCodes = fetchUserPermissions()
        val permissions = permissionCodes.mapNotNull { Permission.fromCode(it) }.toSet()

        val session = Session(
            userId = profile.id,
            tenantId = profile.tenantId,
            email = profile.email ?: email,
            displayName = profile.displayName ?: email,
            avatarUrl = profile.avatarUrl,
            role = role,
            permissions = permissions,
            accessToken = userInfo.id, // The JWT is managed internally by the SDK
            refreshToken = null,
            expiresAt = System.currentTimeMillis() + 3_600_000L,
            locale = profile.locale ?: "fr",
        )

        _sessionState.value = session

        // Audit log the sign-in
        auditRepository.log(AuditLogInput(
            action = AuditActions.AUTH_LOGIN,
            entityType = "user_profile",
            entityId = profile.id,
            note = "Sign-in from Android app",
        ))

        Result.Ok(session)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun signOut(): Result<Unit> = try {
        val session = _sessionState.value
        auth.signOut()
        _sessionState.value = null
        if (session != null) {
            auditRepository.log(AuditLogInput(
                action = AuditActions.AUTH_LOGOUT,
                entityType = "user_profile",
                entityId = session.userId,
                note = "Sign-out from Android app",
            ))
        }
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun refreshSession(): Result<Session?> = try {
        auth.refreshCurrentSession()
        val userInfo = auth.currentUserOrNull()
        if (userInfo != null) {
            // Re-derive session from the refreshed user
            signIn(userInfo.email ?: "", "") // Best-effort; if it fails, return null
        } else {
            _sessionState.value = null
            Result.Ok(null)
        }
    } catch (e: Exception) {
        _sessionState.value = null
        Result.Err(Errors.fromException(e))
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = try {
        // Strength validation (plan §12.04)
        if (!isPasswordStrong(newPassword)) {
            return Result.Err(Errors.validation("Password must be 8+ chars with lowercase, uppercase, and digit"))
        }

        // Re-authenticate with current password
        val userInfo = auth.currentUserOrNull()
            ?: return Result.Err(Errors.unauthorized("No session"))

        auth.signInWith(Email) {
            email = userInfo.email ?: ""
            password = currentPassword
        }

        // Update password — Supabase auto-revokes other sessions
        auth.updateUser {
            password = newPassword
        }

        // Force global sign-out (revokes ALL sessions across ALL devices)
        auth.signOut(scope = io.github.jan.supabase.auth.signOut.Scope.GLOBAL)
        _sessionState.value = null

        auditRepository.log(AuditLogInput(
            action = AuditActions.AUTH_PASSWORD_CHANGE,
            entityType = "user_profile",
            entityId = userInfo.id,
            note = "Password changed; all sessions revoked",
        ))

        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private suspend fun fetchUserProfile(authUserId: String): UserProfileDto? {
        return try {
            provider.postgrest.from("user_profiles")
                .select {
                    filter { eq("auth_user_id", authUserId) }
                    limit(1)
                }
                .decodeList<UserProfileDto>()
                .firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchUserRoles(): List<String> = try {
        provider.postgrest.rpc("current_user_roles", buildJsonObject {})
            .decodeAs<List<String>>()
    } catch (e: Exception) { emptyList() }

    private suspend fun fetchUserPermissions(): List<String> = try {
        provider.postgrest.rpc("current_user_permissions", buildJsonObject {})
            .decodeAs<List<String>>()
    } catch (e: Exception) { emptyList() }

    private fun isPasswordStrong(pw: String): Boolean {
        if (pw.length < 8) return false
        if (pw.none { it.isLowerCase() }) return false
        if (pw.none { it.isUpperCase() }) return false
        if (pw.none { it.isDigit() }) return false
        return true
    }

    @Serializable
    data class UserProfileDto(
        val id: String,
        val tenantId: String,
        val email: String? = null,
        val displayName: String? = null,
        val avatarUrl: String? = null,
        val status: String = "pending",
        val locale: String? = null,
    )
}
