package com.elimtiyaz.data.mock

import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.domain.model.AppNotification
import com.elimtiyaz.domain.repository.NotificationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

private suspend fun mockDelay() = delay((200L..500L).random())

/** Mock [NotificationRepository]. */
@Singleton
class MockNotificationRepository @Inject constructor() : NotificationRepository {

    private val log = Logger.withTag("Mock.Notification")
    private val state = MutableStateFlow(MockData.notifications)

    /** Stream all notifications (most recent first). */
    override fun notifications(): Flow<Result<List<AppNotification>>> = state.map {
        Result.success(it.sortedByDescending { n -> n.createdAt })
    }

    /** Mark a notification as read. */
    override suspend fun markRead(id: String): Result<Unit> {
        mockDelay()
        state.value = state.value.map { n ->
            if (n.id != id) n else n.copy(readAt = Clock.System.now().toString())
        }
        log.i { "Marked notification $id read" }
        return Result.success(Unit)
    }

    /** Mark all notifications as read. */
    override suspend fun markAllRead(): Result<Unit> {
        mockDelay()
        val nowIso = Clock.System.now().toString()
        state.value = state.value.map { it.copy(readAt = it.readAt ?: nowIso) }
        log.i { "Marked all notifications read" }
        return Result.success(Unit)
    }

    /** Clear all notifications. */
    override suspend fun clear(): Result<Unit> {
        mockDelay()
        state.value = emptyList()
        log.i { "Cleared notifications" }
        return Result.success(Unit)
    }
}
