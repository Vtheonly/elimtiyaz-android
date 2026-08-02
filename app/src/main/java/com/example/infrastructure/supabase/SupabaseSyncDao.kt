package com.example.infrastructure.supabase

import androidx.annotation.WorkerThread
import com.example.infrastructure.room.SyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin table-write DAO used by [com.example.infrastructure.sync.SyncWorker]
 * to drain the offline sync queue.
 *
 * The high-level domain repositories (`ParentRepository`, etc.) wrap business
 * logic (validation, audit logging, derived fields) that is NOT appropriate
 * for sync replay — the original operation already ran locally and produced
 * a payload that should be persisted verbatim. This DAO performs direct
 * `upsert` / `insert` calls against the appropriate Supabase table, leaving
 * RLS, triggers, and SECURITY DEFINER functions to enforce invariants
 * server-side (exactly as the desktop sync layer does).
 *
 * Each method:
 *   - Parses [SyncQueueEntity.payload] (a JSON string) into a [JsonObject].
 *   - Routes the payload to the correct table via [SupabaseClientProvider.postgrest].
 *   - Throws on any failure — the caller is responsible for retry / status update.
 *
 * Entity-type → table mapping (mirrors the desktop `pushHandlers` map):
 *   - parent     → `parents`             (upsert)
 *   - student    → `students`            (upsert)
 *   - payment    → `payments`            (insert — payments are immutable)
 *   - installment → `installments`       (update keyed by id)
 *   - expense    → `expense_tickets`     (upsert)
 *   - attendance → `attendance_records`  (upsert)
 *   - grade      → `grades`              (upsert)
 *   - homework   → `homework`            (upsert)
 *   - personnel  → `personnel`           (upsert)
 *   - ledger_entry → `ledger_entries`    (insert — immutable, RLS blocks UPDATE)
 *
 * @param provider The shared Supabase client provider.
 */
@Singleton
class SupabaseSyncDao @Inject constructor(
    private val provider: SupabaseClientProvider,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Upsert a parent row to the `parents` table. */
    @WorkerThread
    suspend fun pushParent(entry: SyncQueueEntity) = upsert("parents", entry)

    /** Upsert a student row to the `students` table. */
    @WorkerThread
    suspend fun pushStudent(entry: SyncQueueEntity) = upsert("students", entry)

    /** Insert a payment row to the `payments` table (immutable server-side). */
    @WorkerThread
    suspend fun pushPayment(entry: SyncQueueEntity) = insert("payments", entry)

    /** Update an installment row keyed by `id` in the payload. */
    @WorkerThread
    suspend fun pushInstallment(entry: SyncQueueEntity) = updateById("installments", entry)

    /** Upsert an expense ticket to the `expense_tickets` table. */
    @WorkerThread
    suspend fun pushExpense(entry: SyncQueueEntity) = upsert("expense_tickets", entry)

    /** Upsert an attendance record to the `attendance_records` table. */
    @WorkerThread
    suspend fun pushAttendance(entry: SyncQueueEntity) = upsert("attendance_records", entry)

    /** Upsert a grade to the `grades` table (server-side trigger auto-computes subject_average). */
    @WorkerThread
    suspend fun pushGrade(entry: SyncQueueEntity) = upsert("grades", entry)

    /** Upsert a homework assignment to the `homework` table. */
    @WorkerThread
    suspend fun pushHomework(entry: SyncQueueEntity) = upsert("homework", entry)

    /** Upsert a personnel row to the `personnel` table. */
    @WorkerThread
    suspend fun pushPersonnel(entry: SyncQueueEntity) = upsert("personnel", entry)

    /** Insert a ledger entry to the `ledger_entries` table (immutable — RLS blocks UPDATE). */
    @WorkerThread
    suspend fun pushLedgerEntry(entry: SyncQueueEntity) = insert("ledger_entries", entry)

    // ── Internal helpers ────────────────────────────────────────────────

    /** Upsert the payload as a JsonObject to the given table. */
    private suspend fun upsert(table: String, entry: SyncQueueEntity) = withContext(Dispatchers.IO) {
        val payload = parsePayload(entry)
        provider.postgrest.from(table).upsert(payload)
        Unit
    }

    /** Insert the payload as a JsonObject to the given table (no upsert — for immutable tables). */
    private suspend fun insert(table: String, entry: SyncQueueEntity) = withContext(Dispatchers.IO) {
        val payload = parsePayload(entry)
        provider.postgrest.from(table).insert(payload)
        Unit
    }

    /** Update a single row in the given table keyed by `id` (extracted from the payload). */
    private suspend fun updateById(table: String, entry: SyncQueueEntity) = withContext(Dispatchers.IO) {
        val payload = parsePayload(entry)
        val id = payload["id"]?.toString()?.trim('"')
            ?: error("Cannot update $table: payload missing 'id' for sync entry ${entry.id}")
        provider.postgrest.from(table).update(payload) {
            filter { eq("id", id) }
        }
        Unit
    }

    /** Parse the payload JSON string into a [JsonObject], throwing on malformed input. */
    private fun parsePayload(entry: SyncQueueEntity): JsonObject {
        val raw = entry.payload.ifBlank { "{}" }
        return json.parseToJsonElement(raw).jsonObject
    }
}
