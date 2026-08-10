package com.example.infrastructure.supabase

import kotlinx.serialization.Serializable

/**
 * DTO for the `user_profiles` table — used by [LocalAuthRepository] to
 * fetch the signed-in user's profile during Supabase Auth.
 *
 * Kept as a standalone file so the local Room-based auth repository can
 * reference it without pulling in the full (now-deleted) SupabaseAuthRepository.
 */
@Serializable
data class UserProfileDto(
    val id: String,
    val tenantId: String,
    val authUserId: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val status: String = "active",
    val locale: String? = null,
    val roleId: String? = null,
)
