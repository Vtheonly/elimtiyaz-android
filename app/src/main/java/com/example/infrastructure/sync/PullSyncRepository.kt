package com.example.infrastructure.sync

import android.util.Log
import com.example.core.Result
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.supabase.NetworkTimeouts
import com.example.infrastructure.supabase.ParentDto
import com.example.infrastructure.supabase.StudentDto
import com.example.infrastructure.supabase.SupabaseClientProvider
import com.example.infrastructure.supabase.toEntity
import com.example.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pull-side sync — fetches the LATEST parents + students (and, in future
 * revisions, payments + ledger entries + device tokens) from Supabase and
 * upserts them into the local Room cache.
 *
 * This is the FIX for the previous "push-only" sync architecture: the
 * Android app could write to Supabase via the `upsert_*_from_import` RPCs,
 * but never READ back what the Desktop app imported. The shared schema
 * (migration 0027 + 0028) defines `pull_parents_for_sync` /
 * `pull_students_for_sync` / `pull_payments_for_sync` /
 * `pull_ledger_entries_for_sync` / `pull_device_tokens_for_sync` RPCs that
 * return all rows for the current tenant (optionally filtered by
 * `p_since` for incremental sync).
 *
 * Flow:
 *   Desktop imports Excel → upsert_parent_from_import / upsert_student_from_import
 *   ↓
 *   Supabase stores the canonical rows
 *   ↓
 *   Android calls pull_parents_for_sync / pull_students_for_sync
 *   ↓
 *   Rows decoded as ParentDto / StudentDto
 *   ↓
 *   Room cache upserts via ParentEntity / StudentEntity
 *   ↓
 *   UI recomposes with the freshly-pulled data
 *
 * Idempotency: each pulled row is upserted by primary key (`id`) into Room,
 * so re-pulling the same data doesn't create duplicates. The RPCs themselves
 * are SECURITY DEFINER + read-only, so they're safe to call from the anon-key
 * client.
 *
 * Error handling: every step is wrapped in runCatching + logged. A network
 * failure or a Supabase error never crashes the caller — it just returns
 * `Result.Err` and the SyncWorker will retry on the next cycle.
 */
@Singleton
class PullSyncRepository @Inject constructor(
    private val db: ElImtiyazDatabase,
    private val provider: SupabaseClientProvider,
    private val sessionManager: SessionManager,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * Pull ALL parents for the current tenant from Supabase and upsert them
     * into Room. Returns the number of rows pulled.
     *
     * Pass `sinceIso` (an ISO-8601 timestamp) for incremental sync — only
     * rows whose `updated_at` is after `sinceIso` are returned. Pass `null`
     * for a full refresh.
     */
    suspend fun pullParents(sinceIso: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        if (!NetworkTimeouts.isSupabaseConfigured) {
            return@withContext Result.Err(com.example.core.Errors.server("Supabase not configured"))
        }
        val tenantId = sessionManager.currentTenantId()
            ?: return@withContext Result.Err(com.example.core.Errors.unauthorized("No session"))
        try {
            // NetworkTimeouts.guard returns null on timeout/error, so we
            // can't use it here — we want to surface the actual exception
            // so the caller knows whether to retry. We do the network call
            // directly and catch Throwable ourselves.
            val params = buildJsonObject {
                put("p_tenant_id", tenantId)
                if (sinceIso != null) put("p_since", sinceIso)
                put("p_limit", 1000)
            }
            val raw = provider.postgrest.rpc("pull_parents_for_sync", params)
            // The Supabase Kotlin SDK returns the RPC result as a JsonElement.
            // Decode it into a List<ParentDto>.
            val dtoList = json.decodeFromString(ListSerializer(ParentDto.serializer()), raw.toString())
            // Upsert every row into Room.
            for (dto in dtoList) {
                db.parentDao().upsert(dto.toEntity())
            }
            Log.i("PullSync", "Pulled ${dtoList.size} parents from Supabase (since=$sinceIso)")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Log.w("PullSync", "pullParents failed: ${e.message}", e)
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * Pull ALL students for the current tenant from Supabase and upsert them
     * into Room. Returns the number of rows pulled.
     */
    suspend fun pullStudents(sinceIso: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        if (!NetworkTimeouts.isSupabaseConfigured) {
            return@withContext Result.Err(com.example.core.Errors.server("Supabase not configured"))
        }
        val tenantId = sessionManager.currentTenantId()
            ?: return@withContext Result.Err(com.example.core.Errors.unauthorized("No session"))
        try {
            val params = buildJsonObject {
                put("p_tenant_id", tenantId)
                if (sinceIso != null) put("p_since", sinceIso)
                put("p_limit", 1000)
            }
            val raw = provider.postgrest.rpc("pull_students_for_sync", params)
            val dtoList = json.decodeFromString(ListSerializer(StudentDto.serializer()), raw.toString())
            for (dto in dtoList) {
                db.studentDao().upsert(dto.toEntity())
            }
            Log.i("PullSync", "Pulled ${dtoList.size} students from Supabase (since=$sinceIso)")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Log.w("PullSync", "pullStudents failed: ${e.message}", e)
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * Convenience: pull parents + students in one call. Returns the total
     * number of rows pulled. Used by [SyncWorker] on every periodic sync.
     */
    suspend fun pullAll(sinceIso: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val parents = pullParents(sinceIso)
        val students = pullStudents(sinceIso)
        val p = (parents as? Result.Ok)?.value ?: 0
        val s = (students as? Result.Ok)?.value ?: 0
        if (parents is Result.Err && students is Result.Err) {
            Result.Err(parents.error)
        } else {
            Result.Ok(p + s)
        }
    }
}
