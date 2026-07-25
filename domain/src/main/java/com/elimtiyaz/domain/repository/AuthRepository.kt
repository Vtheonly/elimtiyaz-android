package com.elimtiyaz.domain.repository

import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import kotlinx.coroutines.flow.Flow

/**
 * Auth repository — handles Supabase Auth email/password + OTP activation
 * (master plan §02.08). On successful login, returns a [Session] with
 * precomputed permissions.
 */
interface AuthRepository {
    val session: Flow<Session?>
    val isOffline: Flow<Boolean>

    suspend fun signIn(email: String, password: String): Result<Session>
    suspend fun activateAccount(email: String, otp: String, newPassword: String): Result<Session>
    suspend fun requestPasswordReset(email: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    suspend fun refreshSession(): Result<Session?>
}
