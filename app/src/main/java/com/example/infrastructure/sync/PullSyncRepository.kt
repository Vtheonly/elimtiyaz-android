package com.example.infrastructure.sync

import android.util.Log
import com.example.core.Result
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.supabase.AssessmentDto
import com.example.infrastructure.supabase.AttendanceRecordDto
import com.example.infrastructure.supabase.AuditLogDto
import com.example.infrastructure.supabase.ClassDto
import com.example.infrastructure.supabase.DepartmentDto
import com.example.infrastructure.supabase.HomeworkDto
import com.example.infrastructure.supabase.ExpenseTicketDto
import com.example.infrastructure.supabase.InstallmentDto
import com.example.infrastructure.supabase.LedgerEntryDto
import com.example.infrastructure.supabase.NotificationDto
import com.example.infrastructure.supabase.ParentDto
import com.example.infrastructure.supabase.PaymentDto
import com.example.infrastructure.supabase.PersonnelDto
import com.example.infrastructure.supabase.ReleveEntryDto
import com.example.infrastructure.supabase.StudentDto
import com.example.infrastructure.supabase.SubjectDto
import com.example.infrastructure.supabase.SupabaseClientProvider
import com.example.infrastructure.supabase.WorkflowRunDto
import com.example.infrastructure.supabase.toEntity
import com.example.session.SessionManager
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PullSyncRepository @Inject constructor(
    private val db: ElImtiyazDatabase,
    private val provider: SupabaseClientProvider,
    private val sessionManager: SessionManager,
) : RealtimePullTarget {
    // T-069 / REALTIME-104: this class implements the RealtimePullTarget
    // seam so RealtimeSyncManager can trigger the granular pulls WITHOUT
    // constructing this heavyweight repository (Room + Supabase client) in
    // unit tests. The four overrides below are the methods the manager's
    // routing map consumes — the interface is a SEAM, not a second
    // implementation.
    /**
     * WEAK-010 dedup: pullAll historically fired from 6 call sites (startup,
     * navigation, session change, roster refresh, SyncWorker — TWICE per tick
     * via drainPending + its own call). One real pull per window; concurrent
     * calls collapse; the rest return Ok(0) without touching the network.
     */
    private val pullInFlight = AtomicBoolean(false)
    private val lastPullStartedAtMs = AtomicLong(0L)

    // ── SYNC-301 (T-493): keyset pagination ─────────────────────────────
    //
    // Every unbounded pull used to cap at 2 000 rows (`limit(2000)`) while
    // the live census (T-487, 2026-10-04) is installments=5 963 /
    // ledger=3 342 / payments=2 198 — the device computed every financial
    // statistic (tranche waves, collection %, debts, aging) on an arbitrary
    // truncated subset. The loops below drain each stream to completion:
    //
    //  - PLAIN TABLES paginate on the PRIMARY KEY (`id > lastId ORDER BY id
    //    LIMIT page`) — the same keyset pattern the desktop's T-021/IMPORT-116
    //    preflight uses. `id` is unique, so no page can repeat and no row can
    //    be skipped between pages (a plain `offset` pagination would both
    //    repeat and skip under concurrent writes).
    //
    //  - The `pull_*_for_sync` RPCs paginate on the `p_since` INCLUSIVE
    //    cursor (server-side `updated_at >= p_since ORDER BY updated_at ASC
    //    LIMIT p_limit`). Inclusive means boundary rows re-fetch on the next
    //    page — harmless, the Room upsert is idempotent — BUT a table whose
    //    rows share one bulk-import timestamp larger than a page can NEVER
    //    advance the cursor (the same first N rows return forever). The
    //    tie-guard stops pagination and logs honestly when the cursor fails
    //    to advance; the RPC page size (5 000) covers every current live
    //    table (parents 741 / students 1 137 / payments 2 198 / ledger
    //    3 342) in ONE page, so the guard is a future-proofing edge, not the
    //    common path.
    /**
     * The generic keyset drain: fetches pages until a short page (or the
     * tie-guard / page cap) ends the loop. [fetchPage] receives the previous
     * page's cursor (null for the first page) and returns the decoded rows;
     * [cursorOf] extracts the next cursor from a row (the PRIMARY KEY for
     * plain tables, `updated_at` for the RPC path).
     *
     * Pure suspend loop — no Supabase types — so the pagination CONTRACT is
     * unit-testable without a network (PullPaginationT493Test).
     */
    internal suspend fun <T : Any> drainByCursor(
        fetchPage: suspend (cursor: String?) -> List<T>,
        cursorOf: (T) -> String?,
        pageSize: Int = RPC_PULL_PAGE_SIZE,
        maxPages: Int = MAX_PULL_PAGES,
    ): List<T> {
        val all = mutableListOf<T>()
        var cursor: String? = null
        var pages = 0
        while (pages < maxPages) {
            val page = fetchPage(cursor)
            if (page.isEmpty()) return all
            all += page
            if (page.size < pageSize) return all
            val firstCursor = cursorOf(page.first())
            val next = cursorOf(page.last())
            // Tie-guard (RPC path): a FULL MULTI-ROW page whose rows all share
            // one cursor value (first == last) is a bulk-timestamp tie at
            // least as large as the page — an INCLUSIVE `>=` cursor would
            // re-fetch the same rows forever. (The size>1 condition keeps
            // single-row pages — where first==last trivially — advancing.)
            // Stop honestly instead (the next full pull retries; the Room
            // upsert is idempotent either way).
            val uniformTie = page.size > 1 && firstCursor != null && firstCursor == next
            if (next == null || next == cursor || uniformTie) {
                if (next == cursor || uniformTie) {
                    Log.w(
                        "PullSync",
                        "drainByCursor: cursor stuck at $next after ${all.size} rows — " +
                            "bulk-timestamp tie at least as large as the page ($pageSize); stopping (partial pull)",
                    )
                }
                return all
            }
            cursor = next
            pages++
        }
        Log.w("PullSync", "drainByCursor: page cap ($maxPages) reached at ${all.size} rows — stopping (partial pull)")
        return all
    }

    suspend fun pullParents(sinceIso: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val targetUrl = provider.getActiveUrl()
        Log.i("PullSync", "pullParents -> Connecting to $targetUrl")
        // T-051/WEAK-012: no session tenant (signed out / global admin without
        // a tenant choice) -> pull NOTHING. The old fallback pulled the DEMO
        // tenant's rows into the local store.
        val tenantId = sessionManager.currentTenantId() ?: return@withContext Result.Ok(0)
        try {
            var count = 0
            var fetched = false
            try {
                // SYNC-301 (T-493): the RPC drains page-by-page on the
                // p_since cursor (was a single p_limit=2000 call —
                // truncated at the live census's scale).
                // T-495 (SYNC-303): pull_parents_for_sync RETURNS TABLE(...)
                // — a row-typed RPC the gateway SLICES at its max-rows
                // setting (1 000, verified live: Content-Range rows 0-999).
                // p_limit 5 000 came back as a 1 000-row page the drain
                // mistook for a completed short page. The page size must
                // be ≤ the slice so a full page stays FULL.
                val dtoList = drainByCursor(
                    fetchPage = { cursor ->
                        val params = buildJsonObject {
                            put("p_tenant_id", tenantId)
                            put("p_since", cursor ?: sinceIso ?: "1970-01-01T00:00:00Z")
                            put("p_limit", ROW_TYPED_RPC_PAGE_SIZE)
                        }
                        provider.postgrest.rpc("pull_parents_for_sync", params).decodeList<ParentDto>()
                    },
                    cursorOf = { it.updatedAt },
                    pageSize = ROW_TYPED_RPC_PAGE_SIZE,
                )
                // T-039: batch upsert (single Room round-trip, was O(N)).
                db.parentDao().upsertAll(dtoList.map { it.toEntity() })
                count = dtoList.size
                fetched = count > 0
                Log.i("PullSync", "RPC pull_parents_for_sync success: $count parents")
            } catch (rpcEx: Throwable) {
                Log.w("PullSync", "RPC pull_parents_for_sync failed: ${rpcEx.message}")
            }

            if (!fetched) {
                try {
                    // SYNC-301: the fallback paginates on the id keyset (was
                    // an arbitrary first-2000 select).
                    val dtoList = drainByCursor(
                        fetchPage = { cursor ->
                            provider.postgrest.from("parents").select {
                                limit(TABLE_PULL_PAGE_SIZE.toLong())
                                order("id", Order.ASCENDING)
                                if (cursor != null) filter { gt("id", cursor) }
                            }.decodeList<ParentDto>()
                        },
                        cursorOf = { it.id },
                        pageSize = TABLE_PULL_PAGE_SIZE,
                    )
                    // T-039: batch upsert.
                    db.parentDao().upsertAll(dtoList.map { it.toEntity() })
                    count += dtoList.size
                    Log.i("PullSync", "Table parents select success: $count parents")
                } catch (tEx: Throwable) {
                    Log.w("PullSync", "Table parents select failed: ${tEx.message}")
                }
            }
            Result.Ok(count)
        } catch (e: Exception) {
            Log.e("PullSync", "pullParents error: ${e.message}", e)
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    suspend fun pullStudents(sinceIso: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        // T-051/WEAK-012: no session tenant (signed out / global admin without
        // a tenant choice) -> pull NOTHING. The old fallback pulled the DEMO
        // tenant's rows into the local store.
        val tenantId = sessionManager.currentTenantId() ?: return@withContext Result.Ok(0)
        try {
            var count = 0
            var fetched = false
            try {
                // SYNC-301 (T-493): p_since cursor drain (was p_limit=2000).
                // T-495 (SYNC-303): pull_students_for_sync RETURNS TABLE(...)
                // — row-typed, so the gateway slices the response at its
                // max-rows setting (1 000; verified live: p_limit 5 000 →
                // exactly 1 000 rows, Content-Range rows 0-999, while the
                // jsonb-returning payments/ledger RPCs pass through whole
                // at 2 198 / 3 342). The live census holds 1 137 students
                // — the old page size silently delivered only the first
                // 1 000. Page size ≤ the slice; the drain then completes
                // (proven live: 1 137 rows in 2 pages, all distinct ids).
                val dtoList = drainByCursor(
                    fetchPage = { cursor ->
                        val params = buildJsonObject {
                            put("p_tenant_id", tenantId)
                            put("p_since", cursor ?: sinceIso ?: "1970-01-01T00:00:00Z")
                            put("p_limit", ROW_TYPED_RPC_PAGE_SIZE)
                        }
                        provider.postgrest.rpc("pull_students_for_sync", params).decodeList<StudentDto>()
                    },
                    cursorOf = { it.updatedAt },
                    pageSize = ROW_TYPED_RPC_PAGE_SIZE,
                )
                // T-039: batch upsert (single Room round-trip).
                db.studentDao().upsertAll(dtoList.map { it.toEntity() })
                count = dtoList.size
                fetched = count > 0
                Log.i("PullSync", "RPC pull_students_for_sync success: $count students")
            } catch (rpcEx: Throwable) {
                Log.w("PullSync", "RPC pull_students_for_sync failed: ${rpcEx.message}")
            }

            if (!fetched) {
                try {
                    // SYNC-301: id-keyset drain (was limit(2000)).
                    val dtoList = drainByCursor(
                        fetchPage = { cursor ->
                            provider.postgrest.from("students").select {
                                limit(TABLE_PULL_PAGE_SIZE.toLong())
                                order("id", Order.ASCENDING)
                                if (cursor != null) filter { gt("id", cursor) }
                            }.decodeList<StudentDto>()
                        },
                        cursorOf = { it.id },
                        pageSize = TABLE_PULL_PAGE_SIZE,
                    )
                    // T-039: batch upsert.
                    db.studentDao().upsertAll(dtoList.map { it.toEntity() })
                    count += dtoList.size
                    Log.i("PullSync", "Table students select success: $count students")
                } catch (tEx: Throwable) {
                    Log.w("PullSync", "Table students select failed: ${tEx.message}")
                }
            }
            Result.Ok(count)
        } catch (e: Exception) {
            Log.e("PullSync", "pullStudents error: ${e.message}", e)
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    // Override of the RealtimePullTarget seam — the `sinceIso` default value
    // lives on the interface (Kotlin forbids defaults on overrides); callers
    // without arguments keep compiling via the inherited default.
    override suspend fun pullPayments(sinceIso: String?): Result<Int> = withContext(Dispatchers.IO) {
        // T-051/WEAK-012: no session tenant (signed out / global admin without
        // a tenant choice) -> pull NOTHING. The old fallback pulled the DEMO
        // tenant's rows into the local store.
        val tenantId = sessionManager.currentTenantId() ?: return@withContext Result.Ok(0)
        try {
            var count = 0
            try {
                // SYNC-301 (T-493): p_since cursor drain (was p_limit=2000 —
                // the live census holds 2 198 payments; the cap silently
                // dropped 198 of them from every on-device statistic).
                val dtoList = drainByCursor(
                    fetchPage = { cursor ->
                        val params = buildJsonObject {
                            put("p_tenant_id", tenantId)
                            put("p_since", cursor ?: sinceIso ?: "1970-01-01T00:00:00Z")
                            put("p_limit", RPC_PULL_PAGE_SIZE)
                        }
                        provider.postgrest.rpc("pull_payments_for_sync", params).decodeList<PaymentDto>()
                    },
                    cursorOf = { it.updatedAt },
                )
                // T-039: batch upsert.
                db.paymentDao().upsertAll(dtoList.map { it.toEntity() })
                count = dtoList.size
            } catch (_: Throwable) {
                try {
                    // SYNC-301: id-keyset drain (was limit(2000)).
                    val dtoList = drainByCursor(
                        fetchPage = { cursor ->
                            provider.postgrest.from("payments").select {
                                limit(TABLE_PULL_PAGE_SIZE.toLong())
                                order("id", Order.ASCENDING)
                                if (cursor != null) filter { gt("id", cursor) }
                            }.decodeList<PaymentDto>()
                        },
                        cursorOf = { it.id },
                        pageSize = TABLE_PULL_PAGE_SIZE,
                    )
                    // T-039: batch upsert.
                    db.paymentDao().upsertAll(dtoList.map { it.toEntity() })
                    count = dtoList.size
                } catch (_: Throwable) {}
            }
            Log.i("PullSync", "Pulled $count payments")
            Result.Ok(count)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    suspend fun pullLedgerEntries(sinceIso: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        // T-051/WEAK-012: no session tenant (signed out / global admin without
        // a tenant choice) -> pull NOTHING. The old fallback pulled the DEMO
        // tenant's rows into the local store.
        val tenantId = sessionManager.currentTenantId() ?: return@withContext Result.Ok(0)
        try {
            var count = 0
            try {
                // SYNC-301 (T-493): p_since cursor drain (was p_limit=2000 —
                // the live census holds 3 342 ledger rows).
                val dtoList = drainByCursor(
                    fetchPage = { cursor ->
                        val params = buildJsonObject {
                            put("p_tenant_id", tenantId)
                            put("p_since", cursor ?: sinceIso ?: "1970-01-01T00:00:00Z")
                            put("p_limit", RPC_PULL_PAGE_SIZE)
                        }
                        provider.postgrest.rpc("pull_ledger_entries_for_sync", params).decodeList<LedgerEntryDto>()
                    },
                    cursorOf = { it.updatedAt },
                )
                // T-039: batch upsert.
                db.ledgerEntryDao().upsertAll(dtoList.map { it.toEntity() })
                count = dtoList.size
            } catch (_: Throwable) {
                try {
                    // SYNC-301: id-keyset drain (was limit(2000)).
                    val dtoList = drainByCursor(
                        fetchPage = { cursor ->
                            provider.postgrest.from("ledger_entries").select {
                                limit(TABLE_PULL_PAGE_SIZE.toLong())
                                order("id", Order.ASCENDING)
                                if (cursor != null) filter { gt("id", cursor) }
                            }.decodeList<LedgerEntryDto>()
                        },
                        cursorOf = { it.id },
                        pageSize = TABLE_PULL_PAGE_SIZE,
                    )
                    // T-039: batch upsert.
                    db.ledgerEntryDao().upsertAll(dtoList.map { it.toEntity() })
                    count = dtoList.size
                } catch (_: Throwable) {}
            }
            Log.i("PullSync", "Pulled $count ledger entries")
            Result.Ok(count)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    suspend fun pullClasses(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // SYNC-301 (T-493): id-keyset drain (was limit(2000)).
            val dtoList = drainByCursor(
                fetchPage = { cursor ->
                    provider.postgrest.from("classes").select {
                        limit(TABLE_PULL_PAGE_SIZE.toLong())
                        order("id", Order.ASCENDING)
                        if (cursor != null) filter { gt("id", cursor) }
                    }.decodeList<ClassDto>()
                },
                cursorOf = { it.id },
                pageSize = TABLE_PULL_PAGE_SIZE,
            )
            // T-039: batch upsert.
            db.academicClassDao().upsertAll(dtoList.map { it.toEntity() })
            Log.i("PullSync", "Pulled ${dtoList.size} classes")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    suspend fun pullSubjects(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // SYNC-301 (T-493): id-keyset drain (was limit(2000)).
            val dtoList = drainByCursor(
                fetchPage = { cursor ->
                    provider.postgrest.from("subjects").select {
                        limit(TABLE_PULL_PAGE_SIZE.toLong())
                        order("id", Order.ASCENDING)
                        if (cursor != null) filter { gt("id", cursor) }
                    }.decodeList<SubjectDto>()
                },
                cursorOf = { it.id },
                pageSize = TABLE_PULL_PAGE_SIZE,
            )
            // T-039: batch upsert.
            db.subjectDao().upsertAll(dtoList.map { it.toEntity() })
            Log.i("PullSync", "Pulled ${dtoList.size} subjects")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    override suspend fun pullInstallments(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // SYNC-301 (T-493): id-keyset drain — THE tranche-statistics
            // stream. The live census holds 5 963 installments; the old
            // `limit(2000)` delivered an arbitrary two-fifths of them, so
            // every wave meter / dossiers count / percentage on the device
            // was computed on a subset (the owner: "The tranches are
            // incorrect"). At 1 000 rows/page this drains in 6 pages.
            val dtoList = drainByCursor(
                fetchPage = { cursor ->
                    provider.postgrest.from("installments").select {
                        limit(TABLE_PULL_PAGE_SIZE.toLong())
                        order("id", Order.ASCENDING)
                        if (cursor != null) filter { gt("id", cursor) }
                    }.decodeList<InstallmentDto>()
                },
                cursorOf = { it.id },
                pageSize = TABLE_PULL_PAGE_SIZE,
            )
            // T-039: batch upsert.
            db.installmentDao().upsertAll(dtoList.map { it.toEntity() })
            Log.i("PullSync", "Pulled ${dtoList.size} installments")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    suspend fun pullPersonnel(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // SYNC-301 (T-493): id-keyset drain (was limit(2000)).
            val dtoList = drainByCursor(
                fetchPage = { cursor ->
                    provider.postgrest.from("personnel").select {
                        limit(TABLE_PULL_PAGE_SIZE.toLong())
                        order("id", Order.ASCENDING)
                        if (cursor != null) filter { gt("id", cursor) }
                    }.decodeList<PersonnelDto>()
                },
                cursorOf = { it.id },
                pageSize = TABLE_PULL_PAGE_SIZE,
            )
            // T-039: batch upsert.
            db.personnelDao().upsertAll(dtoList.map { it.toEntity() })
            Log.i("PullSync", "Pulled ${dtoList.size} personnel")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    suspend fun pullDepartments(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // SYNC-301 (T-493): id-keyset drain (was limit(2000)).
            val dtoList = drainByCursor(
                fetchPage = { cursor ->
                    provider.postgrest.from("departments").select {
                        limit(TABLE_PULL_PAGE_SIZE.toLong())
                        order("id", Order.ASCENDING)
                        if (cursor != null) filter { gt("id", cursor) }
                    }.decodeList<DepartmentDto>()
                },
                cursorOf = { it.id },
                pageSize = TABLE_PULL_PAGE_SIZE,
            )
            // T-039: batch upsert (was a per-row listOf() wrapper loop).
            db.departmentDao().upsertAll(dtoList.map { it.toEntity() })
            Log.i("PullSync", "Pulled ${dtoList.size} departments")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * T-492 (SYNC-302): pull the canonical `expense_tickets` rows (migration
     * 0008 + 0056) — desktop-submitted expenses become visible on Android
     * (the Dépenses tab was permanently empty against the live project:
     * the table was never in the pull list). The `expense_categories(code)`
     * embed resolves the category FK; the DTO mapper is the desktop
     * T-093/DRIFT-013 translation layer (status/category/urgency/payee).
     * Sync-301 discipline: the id-keyset drain, never a bare limit.
     */
    suspend fun pullExpenses(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dtoList = drainByCursor(
                fetchPage = { cursor ->
                    provider.postgrest.from("expense_tickets")
                        .select(io.github.jan.supabase.postgrest.query.Columns.raw("*, expense_categories(code)")) {
                            limit(TABLE_PULL_PAGE_SIZE.toLong())
                            order("id", Order.ASCENDING)
                            if (cursor != null) filter { gt("id", cursor) }
                        }
                        .decodeList<ExpenseTicketDto>()
                },
                cursorOf = { it.id },
                pageSize = TABLE_PULL_PAGE_SIZE,
            )
            db.expenseDao().upsertAll(dtoList.map { it.toEntity() })
            Log.i("PullSync", "Pulled ${dtoList.size} expense tickets")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * T-494 (DATA-059): pull the canonical `releve_entries` rows (migration
     * 0009) — the personnel section's Activité tab reads the REAL server
     * timesheets instead of the demo seeder's mock rows (the desktop's
     * SupabaseReleveRepository is the reference, T-481). The denormalized
     * personnelName backfills from the personnel table after the upsert.
     * Sync-301 discipline: the id-keyset drain.
     */
    suspend fun pullReleveEntries(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dtoList = drainByCursor(
                fetchPage = { cursor ->
                    provider.postgrest.from("releve_entries").select {
                        limit(TABLE_PULL_PAGE_SIZE.toLong())
                        order("id", Order.ASCENDING)
                        if (cursor != null) filter { gt("id", cursor) }
                    }.decodeList<ReleveEntryDto>()
                },
                cursorOf = { it.id },
                pageSize = TABLE_PULL_PAGE_SIZE,
            )
            db.releveEntryDao().upsertAll(dtoList.map { it.toEntity() })
            // The pulled rows carry only the personnel id — resolve the
            // display name from the personnel table (one statement).
            db.releveEntryDao().backfillPersonnelNames()
            Log.i("PullSync", "Pulled ${dtoList.size} releve entries")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * T-039 / NOTIF-105: the pull now (a) FILTERS by the signed-in user and
     * their CURRENT role set — resolved fresh via the canonical
     * `current_user_roles()` RPC (migration 0053), the same function the
     * server's `notifications_select` RLS policy (migration 0019) uses — so
     * the client filter mirrors the policy branch-for-branch: direct rows
     * for the profile id, role-broadcasts for ANY held role, and tenant
     * broadcasts (null/null) only for the staff trio the policy names;
     * (b) BATCHES the Room upsert (one call, was O(N) per-row round-trips);
     * and (c) EVICTS stale rows the user can no longer see (direct rows of
     * other users + role-broadcasts for roles they no longer hold) —
     * previously role-broadcast rows stayed in Room forever across role
     * changes.
     *
     * Multi-role note: the server allows a user to hold SEVERAL
     * role_assignments; the Android [com.example.core.Session] models a
     * single primary role, so the filter set is re-resolved here per pull
     * instead of trusting the session's single role — otherwise a
     * teacher+financial_officer user would lose every financial_officer
     * broadcast from the local cache on eviction.
     */
    override suspend fun pullNotifications(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val session = sessionManager.current()
                ?: return@withContext Result.Ok(0) // signed out — pull nothing (defensive; SyncWorker gates on session)
            // Fresh multi-role resolution (canonical RPC, same as RLS).
            // Fallback: the session's single role — a pull this early in a
            // role transition still behaves like the pre-change session.
            val roles: List<String> = runCatching {
                provider.postgrest.rpc("current_user_roles").decodeList<String>()
            }.getOrDefault(emptyList()).ifEmpty { listOf(session.role.code) }
            // 0019 notifications_select: tenant broadcasts (target_user_id
            // NULL + target_role NULL) are visible ONLY to this staff trio.
            val staffBroadcast = roles.any { it in STAFF_BROADCAST_ROLES }
            val dtoList = provider.postgrest.from("notifications").select {
                limit(200)
                filter {
                    // T-172 (NOTIF-200): parity with the desktop's
                    // SupabaseNotificationRepository.refresh() — rows the
                    // server has dismissed (e.g. overdue alerts resolved by
                    // the run-overdue-scan lifecycle once the installment is
                    // paid) must not enter the local cache. The desktop
                    // filters .is("dismissed_at", null) on every read.
                    filter("dismissed_at", FilterOperator.IS, null)
                    or {
                        eq("target_user_id", session.userId)
                        isIn("target_role", roles)
                        if (staffBroadcast) {
                            and {
                                filter("target_user_id", FilterOperator.IS, null)
                                filter("target_role", FilterOperator.IS, null)
                            }
                        }
                    }
                }
            }.decodeList<NotificationDto>()
            db.notificationDao().upsertAll(dtoList.map { it.toEntity() })
            db.notificationDao().evictNotVisibleTo(session.userId, roles, if (staffBroadcast) 1 else 0)
            // T-181 (T-173b / NOTIF-200): evict rows the SERVER has
            // dismissed since the last pull. The T-172 filter above stops
            // NEW dismissed rows from entering the cache, but rows already
            // in Room that got dismissed server-side (e.g. overdue alerts
            // resolved by the run-overdue-scan lifecycle once the
            // installment is paid) lingered forever — evictNotVisibleTo
            // covers VISIBILITY, not dismissal. One targeted round-trip for
            // the stale candidates (local ids absent from the fresh active
            // pull) keeps the cache honest; rows that merely fell outside
            // the 200-row window but are NOT dismissed stay untouched.
            // Desktop parity: its repository filters dismissed_at IS NULL
            // on EVERY read — Room is a persistent cache, so the same
            // semantics need eviction at pull time.
            val pulledIds = dtoList.map { it.id }.toSet()
            val staleCandidates = db.notificationDao().listAll()
                .map { it.id }
                .filter { it !in pulledIds }
            if (staleCandidates.isNotEmpty()) {
                // Chunked (50 ids/query) to keep the PostgREST URL bounded.
                val dismissedIds = staleCandidates.chunked(50).flatMap { chunk ->
                    provider.postgrest.from("notifications").select {
                        filter { isIn("id", chunk) }
                    }.decodeList<NotificationDto>()
                        .filter { it.dismissedAt != null }
                        .map { it.id }
                }
                if (dismissedIds.isNotEmpty()) {
                    db.notificationDao().evictServerDismissed(dismissedIds)
                    Log.i("PullSync", "Evicted ${dismissedIds.size} server-dismissed notifications")
                }
            }
            Log.i("PullSync", "Pulled ${dtoList.size} notifications (roles=${roles.joinToString(",")})")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * T-039 / HOMEWORK-103: pull the canonical `homework` table (migration
     * 0029) so homework created on the DESKTOP (or by another device)
     * appears on Android. Batch upsert into Room. Server RLS scopes the
     * visible rows (tenant staff); the 15-min SyncWorker cycle remains the
     * freshness window.
     */
    override suspend fun pullHomework(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // SYNC-301 (T-493): id-keyset drain (was limit(2000)).
            val dtoList = drainByCursor(
                fetchPage = { cursor ->
                    provider.postgrest.from("homework").select {
                        limit(TABLE_PULL_PAGE_SIZE.toLong())
                        order("id", Order.ASCENDING)
                        if (cursor != null) filter { gt("id", cursor) }
                    }.decodeList<HomeworkDto>()
                },
                cursorOf = { it.id },
                pageSize = TABLE_PULL_PAGE_SIZE,
            )
            db.homeworkDao().upsertAll(dtoList.map { it.toEntity() })
            // Legacy local rows carry the pre-T-024 "hwk-" id prefix that could
            // never reach the server; their post-fix push writes the bare-UUID
            // form. Delete the legacy local copy of each pulled row so the
            // same assignment does not appear twice after the pull.
            dtoList.forEach { db.homeworkDao().deleteLegacyPrefixedCopy(it.id) }
            Log.i("PullSync", "Pulled ${dtoList.size} homework rows")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * T-039 / HOMEWORK-103: pull the canonical `attendance_records` table
     * (migration 0041) — desktop roll calls become visible on Android.
     */
    suspend fun pullAttendance(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // SYNC-301 (T-493): id-keyset drain (was limit(2000) — a full
            // school year of per-student daily records far exceeds 2 000).
            val dtoList = drainByCursor(
                fetchPage = { cursor ->
                    provider.postgrest.from("attendance_records").select {
                        limit(TABLE_PULL_PAGE_SIZE.toLong())
                        order("id", Order.ASCENDING)
                        if (cursor != null) filter { gt("id", cursor) }
                    }.decodeList<AttendanceRecordDto>()
                },
                cursorOf = { it.id },
                pageSize = TABLE_PULL_PAGE_SIZE,
            )
            db.attendanceDao().upsertAll(dtoList.map { it.toEntity() })
            Log.i("PullSync", "Pulled ${dtoList.size} attendance records")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * T-039 / HOMEWORK-103: pull the canonical `assessments` rows (0041
     * per-student shape) — desktop-entered grades become visible on Android.
     */
    suspend fun pullAssessments(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // SYNC-301 (T-493): id-keyset drain (was limit(2000)).
            val dtoList = drainByCursor(
                fetchPage = { cursor ->
                    provider.postgrest.from("assessments").select {
                        limit(TABLE_PULL_PAGE_SIZE.toLong())
                        order("id", Order.ASCENDING)
                        if (cursor != null) filter { gt("id", cursor) }
                    }.decodeList<AssessmentDto>()
                },
                cursorOf = { it.id },
                pageSize = TABLE_PULL_PAGE_SIZE,
            )
            db.assessmentDao().upsertAll(dtoList.map { it.toEntity() })
            Log.i("PullSync", "Pulled ${dtoList.size} assessments")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * Pull recent workflow runs (read-only on mobile per plan §10.02) so the
     * Workflow Monitor displays REAL server executions instead of being
     * permanently empty. Failures are swallowed to `0` — same contract as
     * every other pull.
     */
    suspend fun pullWorkflowRuns(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // T-231: select the REAL columns + the workflows(name) embed
            // (workflow_name is not a column — it resolves via the join,
            // same as the desktop's PostgREST embed).
            val dtoList = provider.postgrest.from("workflow_runs")
                .select(io.github.jan.supabase.postgrest.query.Columns.raw("*, workflows(name)")) { limit(50) }
                .decodeList<WorkflowRunDto>()
            // T-039: batch upsert.
            db.workflowRunDao().upsertAll(dtoList.map { it.toEntity() })
            Log.i("PullSync", "Pulled ${dtoList.size} workflow runs")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * T-299 (OFFLINE-400): pull recent audit_logs rows (RLS-scoped — the
     * staff JWT sees the tenant stream for admins/finance, own rows for
     * other staff) into Room so the attributed audit feed (T-297's
     * red/green diff renderer) stays live. Triggered by the
     * RealtimeSyncManager audit_logs route (migration 0085 added the table
     * to the realtime publication). Failures surface like every other
     * pull — Result.Err, swallowed by the callers.
     */
    override suspend fun pullAudits(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dtoList = provider.postgrest.from("audit_logs").select {
                limit(200)
            }.decodeList<AuditLogDto>()
            db.auditLogDao().upsertAll(dtoList.map { it.toEntity() })
            Log.i("PullSync", "Pulled ${dtoList.size} audit log rows")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    suspend fun pullAll(sinceIso: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        // WEAK-010: deduplicated gate — skip when a pull is running or one
        // started within the dedup window (the "single pull per cycle"
        // contract from T-050).
        if (pullInFlight.get() || !pullInFlight.compareAndSet(false, true)) {
            Log.i("PullSync", "pullAll deduplicated: a pull is already in flight")
            return@withContext Result.Ok(0)
        }
        try {
            val now = System.currentTimeMillis()
            if (now - lastPullStartedAtMs.get() < PULL_DEDUP_WINDOW_MS) {
                Log.i("PullSync", "pullAll deduplicated: last pull started ${now - lastPullStartedAtMs.get()}ms ago (window ${PULL_DEDUP_WINDOW_MS}ms)")
                return@withContext Result.Ok(0)
            }
            lastPullStartedAtMs.set(now)
            doPullAll(sinceIso)
        } finally {
            pullInFlight.set(false)
        }
    }

    private suspend fun doPullAll(sinceIso: String? = null): Result<Int> {
        Log.i("PullSync", "=== STARTING PULL ALL FROM SUPABASE ===")
        val p = (pullParents(sinceIso) as? Result.Ok)?.value ?: 0
        val s = (pullStudents(sinceIso) as? Result.Ok)?.value ?: 0
        val pay = (pullPayments(sinceIso) as? Result.Ok)?.value ?: 0
        val led = (pullLedgerEntries(sinceIso) as? Result.Ok)?.value ?: 0
        val cls = (pullClasses() as? Result.Ok)?.value ?: 0
        val sub = (pullSubjects() as? Result.Ok)?.value ?: 0
        val ins = (pullInstallments() as? Result.Ok)?.value ?: 0
        val per = (pullPersonnel() as? Result.Ok)?.value ?: 0
        val dep = (pullDepartments() as? Result.Ok)?.value ?: 0
        val notif = (pullNotifications() as? Result.Ok)?.value ?: 0
        val wfr = (pullWorkflowRuns() as? Result.Ok)?.value ?: 0
        // T-039 / HOMEWORK-103: the academic cluster — pull homework,
        // attendance and assessments so desktop/other-device writes become
        // visible here (bidirectional sync completes).
        val hwk = (pullHomework() as? Result.Ok)?.value ?: 0
        val att = (pullAttendance() as? Result.Ok)?.value ?: 0
        val asm = (pullAssessments() as? Result.Ok)?.value ?: 0
        // T-492 (SYNC-302): the canonical expense tickets — the Dépenses tab
        // reads the same rows the desktop's approval queue does.
        val exp = (pullExpenses() as? Result.Ok)?.value ?: 0
        // T-494 (DATA-059): the canonical timesheets — the Activité tab reads
        // the real server rows.
        val rel = (pullReleveEntries() as? Result.Ok)?.value ?: 0

        // T-494 (DATA-059): evict the DEMO-seeded rows on CONFIGURED builds —
        // the server's truth is the only content that belongs on a production
        // device. The gate is the CONFIGURED POSTURE, not the individual pull
        // outcomes: the pulls swallow their errors to Ok(0) (the documented
        // contract), so per-cluster gating is not expressible against them —
        // and on a configured build the mock rows are pollution whether the
        // device is currently online (server truth wins) or offline (they
        // must not display as real staff either way). The UNCONFIGURED
        // demo-sandbox build NEVER evicts — its demo rows ARE its content
        // (the seeder just created them). Idempotent by construction.
        if (com.example.infrastructure.supabase.NetworkTimeouts.isSupabaseConfigured) {
            runCatching { evictDemoRowsAfterPull() }
        }

        val total = p + s + pay + led + cls + sub + ins + per + dep + notif + wfr + hwk + att + asm + exp + rel

        Log.i("PullSync", "=== PULL COMPLETE: Total $total records synchronized ===")
        return Result.Ok(total)
    }

    /**
     * T-494 (DATA-059): the demo-row eviction — EXACT seeded ids / parent-
     * scoped deletes only (the [com.example.infrastructure.room.DemoSeedIds]
     * vocabulary; the safety rules live there and on the DAO queries).
     *
     * NOTE: this runs after EVERY successful pullAll cycle — idempotent by
     * construction (deleting absent ids is a no-op), and self-healing: a
     * device that seeded demo rows under the OLD seeder converges on its
     * next online cycle.
     */
    internal suspend fun evictDemoRowsAfterPull() {
        // The personnel cluster: mock workers, their departments, their
        // timesheets.
        db.personnelDao().deleteByIds(com.example.infrastructure.room.DemoSeedIds.PERSONNEL)
        db.departmentDao().deleteByIds(com.example.infrastructure.room.DemoSeedIds.DEPARTMENTS)
        db.releveEntryDao().deleteByPersonnelIds(com.example.infrastructure.room.DemoSeedIds.PERSONNEL)

        // The demo-family cluster: parents, students, their ledger/
        // installments/payments, and the routing demo (the stops reference
        // the demo students).
        val demoParents = com.example.infrastructure.room.DemoSeedIds.PARENTS
        db.parentDao().deleteByIds(demoParents)
        db.studentDao().deleteByIds(com.example.infrastructure.room.DemoSeedIds.STUDENTS)
        db.ledgerEntryDao().deleteDemoRows(demoParents, com.example.infrastructure.room.DemoSeedIds.EXTRA_LEDGER_IDS)
        db.installmentDao().deleteByParentIds(demoParents)
        db.paymentDao().deleteDemoRows(demoParents, com.example.infrastructure.room.DemoSeedIds.EXTRA_PAYMENT_IDS)
        db.vehicleDao().deleteByIds(com.example.infrastructure.room.DemoSeedIds.VEHICLES)
        db.routingStopDao().deleteByIds(com.example.infrastructure.room.DemoSeedIds.ROUTING_STOPS)

        // The academic-history cluster: the seeded grades/attendance (the
        // patterns are UUID-impossible — the proofs live on the DAOs).
        db.assessmentDao().deleteSeeded()
        db.attendanceDao().deleteSeeded()
        Log.i("PullSync", "DATA-059: demo-seeded rows evicted (personnel/departments/releve/families/routing/academics)")
    }

    companion object {
        /** Dedup window — one real pullAll per 10 s however many call sites fire. */
        const val PULL_DEDUP_WINDOW_MS: Long = 10_000L

        /**
         * SYNC-301 (T-493): the plain-table keyset page size. 1 000 rows per
         * request keeps every PostgREST payload small while the id keyset
         * guarantees full drains (installments: 5 963 live rows → 6 pages).
         */
        const val TABLE_PULL_PAGE_SIZE: Int = 1_000

        /**
         * SYNC-301 (T-493): the `pull_*_for_sync` RPC page size. A single
         * 5 000-row page covers every current live RPC table (payments 2 198 /
         * ledger 3 342); the p_since cursor loop + the tie-guard handle
         * anything larger. ONLY for the jsonb-returning RPCs — see
         * [ROW_TYPED_RPC_PAGE_SIZE] for the row-typed pair.
         */
        const val RPC_PULL_PAGE_SIZE: Int = 5_000

        /**
         * T-495 (SYNC-303): the page size for the ROW-TYPED sync RPCs
         * (`pull_parents_for_sync` / `pull_students_for_sync` — both
         * `RETURNS TABLE(...)`). The Supabase API gateway applies its
         * max-rows setting (1 000 on this project) to row-typed RPC
         * responses — slicing them with `Content-Range: rows 0-999` — while
         * jsonb-returning RPCs (`pull_payments_for_sync` /
         * `pull_ledger_entries_for_sync`) pass through whole. A p_limit
         * above the slice came back as a SHORT page the drain mistook for
         * a completed pull (verified live: students p_limit 5 000 → exactly
         * 1 000 of 1 137 rows, silently truncated). The page size must be
         * ≤ the slice so a full page stays FULL and the cursor keeps
         * advancing — the same discipline the plain-table paths already
         * follow against the same gateway setting.
         */
        const val ROW_TYPED_RPC_PAGE_SIZE: Int = 1_000

        /**
         * SYNC-301 (T-493): the drain loop's hard page cap — an infinite-loop
         * guard (60 × 1 000 = 60 000 rows via the table path; 60 × 5 000 via
         * the RPC path). Reaching it logs a warning (honest partial pull).
         */
        const val MAX_PULL_PAGES: Int = 60

        /**
         * T-039 / NOTIF-105 — the roles that may see tenant broadcasts
         * (target_user_id NULL + target_role NULL). Mirrors the
         * `notifications_select` RLS policy (migration 0019):
         * `has_any_role(array['super_admin', 'financial_officer',
         * 'support_staff'])`. If 0019 ever changes, this set must follow.
         */
        val STAFF_BROADCAST_ROLES: Set<String> =
            setOf("super_admin", "financial_officer", "support_staff")
    }
}