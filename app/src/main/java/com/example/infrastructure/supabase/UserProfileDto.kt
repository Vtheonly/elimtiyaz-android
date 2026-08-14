package com.example.infrastructure.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for the `user_profiles` table — used by [com.example.infrastructure.local.LocalAuthRepository]
 * to fetch the signed-in user's profile during Supabase Auth.
 *
 * SHARED-UNIFICATION (migration 0027):
 *   Every field carries an explicit `@SerialName` matching the snake_case
 *   column name in PostgreSQL. Without these annotations, the Kotlin
 *   serialization framework would look for camelCase JSON keys and silently
 *   fall back to defaults — causing the auth flow to drop the tenant_id,
 *   display_name, role_id, etc. and forcing the user into demo mode.
 *
 * Kept as a standalone file so the local Room-based auth repository can
 * reference it without pulling in the full (now-deleted) SupabaseAuthRepository.
 */
@Serializable
data class UserProfileDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("auth_user_id") val authUserId: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("status") val status: String = "active",
    @SerialName("locale") val locale: String? = null,
    @SerialName("role_id") val roleId: String? = null,
)
