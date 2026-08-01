package com.example.domain.repository

import com.example.core.Result
import com.example.core.Session
import com.example.domain.model.AppNotification
import kotlinx.coroutines.flow.Flow

/** In-app notification repository contract. */
interface NotificationRepository {
    fun observe(): Flow<List<AppNotification>>
    fun observeForSession(session: Session): Flow<List<AppNotification>>
    suspend fun markRead(id: String): Result<Unit>
    suspend fun markAllRead(): Result<Unit>
    suspend fun dismiss(id: String): Result<Unit>
}
