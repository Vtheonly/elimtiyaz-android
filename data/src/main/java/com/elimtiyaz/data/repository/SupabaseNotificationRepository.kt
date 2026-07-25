package com.elimtiyaz.data.repository

import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.DispatcherProvider
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.onFailure
import com.elimtiyaz.data.local.dao.NotificationDao
import com.elimtiyaz.data.local.dao.SyncQueueDao
import com.elimtiyaz.data.local.entity.toDomain
import com.elimtiyaz.data.local.entity.toEntity
import com.elimtiyaz.data.remote.dto.AppNotificationDto
import com.elimtiyaz.domain.model.AppNotification
import com.elimtiyaz.domain.repository.NotificationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val NOTIFICATIONS_TABLE = "notifications"

/** Supabase-backed [NotificationRepository] — in-app notification center. */
@Singleton
class SupabaseNotificationRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val notificationDao: NotificationDao,
    private val syncQueueDao: SyncQueueDao,
    private val dispatchers: DispatcherProvider,
) : NotificationRepository {

    private val log = Logger.withTag("Data.Notification")
    private val sync = SyncQueueHelper(syncQueueDao)

    /** Stream all notifications (most recent first). */
    override fun notifications(): Flow<Result<List<AppNotification>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { notificationDao.observeAll().first().map { it.toDomain() } },
        fetch = { supabase.from(NOTIFICATIONS_TABLE).select().decodeList<AppNotificationDto>().map { it.toDomain() } },
        persist = { ns -> notificationDao.upsertAll(ns.map { it.toEntity() }) },
    )

    /** Mark a notification as read. */
    override suspend fun markRead(id: String): Result<Unit> = Result.runCatching {
        supabase.from(NOTIFICATIONS_TABLE).update(mapOf("read_at" to nowIso())) { filter { eq("id", id) } }
        // Locally patch the cached row.
        val cached = notificationDao.observeAll().first().firstOrNull { it.id == id }
        cached?.let { notificationDao.upsert(it.copy(readAt = nowIso())) }
        log.i { "Marked notification $id read" }
    }.onFailure {
        sync.enqueueRaw(NOTIFICATIONS_TABLE, "update", sync.encode(mapOf("id" to id, "read_at" to nowIso())))
    }

    /** Mark all notifications as read. */
    override suspend fun markAllRead(): Result<Unit> = Result.runCatching {
        supabase.from(NOTIFICATIONS_TABLE).update(mapOf("read_at" to nowIso())) {
            filter { exact("read_at", null) }
        }
        val all = notificationDao.observeAll().first()
        notificationDao.upsertAll(all.map { it.copy(readAt = nowIso()) })
        log.i { "Marked all notifications read" }
    }

    /** Clear all notifications. */
    override suspend fun clear(): Result<Unit> = Result.runCatching {
        supabase.from(NOTIFICATIONS_TABLE).delete { /* all rows */ }
        val all = notificationDao.observeAll().first()
        all.forEach { notificationDao.delete(it) }
        log.i { "Cleared ${all.size} notifications" }
    }
}
