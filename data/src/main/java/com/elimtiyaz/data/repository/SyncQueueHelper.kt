package com.elimtiyaz.data.repository

import co.touchlab.kermit.Logger
import com.elimtiyaz.data.local.dao.SyncQueueDao
import com.elimtiyaz.data.local.entity.SyncQueueEntity
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Tiny facade over [SyncQueueDao] used by every Supabase repository to enqueue
 * a failed write. Each enqueue operation is fire-and-forget — failures here
 * are logged but never propagated, since the original call already returned an
 * error to the caller.
 */
internal class SyncQueueHelper(
    private val dao: SyncQueueDao,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val log = Logger.withTag("Data.Sync")

    /**
     * Persist a payload (already serialised to JSON) to the sync queue to be
     * replayed by the worker later. Use the inline helper [encode] to obtain
     * the JSON string.
     */
    suspend fun enqueueRaw(tableName: String, operation: String, payloadJson: String) {
        runCatching {
            dao.upsert(
                SyncQueueEntity(
                    id = "${tableName}_${operation}_${Clock.System.now().toEpochMilliseconds()}",
                    tableName = tableName,
                    operation = operation,
                    payloadJson = payloadJson,
                    createdAt = Clock.System.now().toString(),
                ),
            )
            log.i { "Queued $operation on $tableName for later sync" }
        }.onFailure { log.w { "Failed to enqueue $operation on $tableName: ${it.message}" } }
    }

    /** Re-serialise the payload to a [JsonElement] for the worker. */
    fun decodeElement(payloadJson: String): JsonElement = json.parseToJsonElement(payloadJson)

    /** Encode any `@Serializable` value to a JSON string for [enqueueRaw]. */
    inline fun <reified T> encode(value: T): String = json.encodeToString(value)
}
