package com.example.infrastructure.sync

import com.example.infrastructure.room.SyncQueueEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatcher that processes a single [SyncQueueEntity].
 *
 * In this local-first build, Room is the source of truth and all writes are
 * committed directly to the local database. The sync queue is retained for
 * forward compatibility with a future Supabase backend — entries are simply
 * marked as "synced" (local commit) without a remote push.
 *
 * When a real Supabase backend is configured, replace the body of
 * [pushEntry] with the appropriate remote RPC call.
 */
@Singleton
class SyncQueueDispatcher @Inject constructor() {

    /** Process a single queue entry. Local build: no-op (data already persisted). */
    suspend fun pushEntry(entry: SyncQueueEntity) {
        // Local-first build: data is already written to Room by the repository.
        // The sync queue entry will be marked as "synced" by the SyncService.
    }
}
