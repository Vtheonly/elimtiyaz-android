package com.elimtiyaz.domain.repository

import com.elimtiyaz.core.common.Result
import com.elimtiyaz.domain.model.AppNotification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun notifications(): Flow<Result<List<AppNotification>>>
    suspend fun markRead(id: String): Result<Unit>
    suspend fun markAllRead(): Result<Unit>
    suspend fun clear(): Result<Unit>
}

interface SettingsRepository {
    val locale: Flow<String>
    val themeMode: Flow<String>      // system / light / dark
    val isMockMode: Flow<Boolean>
    suspend fun setLocale(locale: String): Result<Unit>
    suspend fun setThemeMode(mode: String): Result<Unit>
}
