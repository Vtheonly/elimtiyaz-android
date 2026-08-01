package com.example.domain.repository

import com.example.core.Result
import com.example.core.Session
import kotlinx.coroutines.flow.Flow

/**
 * Auth repository contract — sign-in, sign-out, refresh, password change.
 * Implementations: [com.example.infrastructure.supabase.SupabaseAuthRepository],
 * [com.example.infrastructure.stub.StubAuthRepository].
 */
interface AuthRepository {
    suspend fun signIn(email: String, password: String): Result<Session>
    suspend fun signOut(): Result<Unit>
    suspend fun refreshSession(): Result<Session?>
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>
    fun observeSession(): Flow<Session?>
}
