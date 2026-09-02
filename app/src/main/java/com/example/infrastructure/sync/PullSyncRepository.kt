package com.example.infrastructure.sync

import android.util.Log
import com.example.core.Result
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.supabase.AssessmentDto
import com.example.infrastructure.supabase.AttendanceRecordDto
import com.example.infrastructure.supabase.ClassDto
import com.example.infrastructure.supabase.DepartmentDto
import com.example.infrastructure.supabase.HomeworkDto
import com.example.infrastructure.supabase.InstallmentDto
import com.example.infrastructure.supabase.LedgerEntryDto
import com.example.infrastructure.supabase.NotificationDto
import com.example.infrastructure.supabase.ParentDto
import com.example.infrastructure.supabase.PaymentDto
import com.example.infrastructure.supabase.PersonnelDto
import com.example.infrastructure.supabase.StudentDto
import com.example.infrastructure.supabase.SubjectDto
import com.example.infrastructure.supabase.SupabaseClientProvider
import com.example.infrastructure.supabase.WorkflowRunDto
import com.example.infrastructure.supabase.toEntity
import com.example.session.SessionManager
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
                val params = buildJsonObject {
                    put("p_tenant_id", tenantId)
                    if (sinceIso != null) put("p_since", sinceIso)
                    put("p_limit", 2000)
                }
                val dtoList = provider.postgrest.rpc("pull_parents_for_sync", params).decodeList<ParentDto>()
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
                    val dtoList = provider.postgrest.from("parents").select { limit(2000) }.decodeList<ParentDto>()
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
                val params = buildJsonObject {
                    put("p_tenant_id", tenantId)
                    if (sinceIso != null) put("p_since", sinceIso)
                    put("p_limit", 2000)
                }
                val dtoList = provider.postgrest.rpc("pull_students_for_sync", params).decodeList<StudentDto>()
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
                    val dtoList = provider.postgrest.from("students").select { limit(2000) }.decodeList<StudentDto>()
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
                val params = buildJsonObject {
                    put("p_tenant_id", tenantId)
                    if (sinceIso != null) put("p_since", sinceIso)
                    put("p_limit", 2000)
                }
                val dtoList = provider.postgrest.rpc("pull_payments_for_sync", params).decodeList<PaymentDto>()
                // T-039: batch upsert.
                db.paymentDao().upsertAll(dtoList.map { it.toEntity() })
                count = dtoList.size
            } catch (_: Throwable) {
                try {
                    val dtoList = provider.postgrest.from("payments").select { limit(2000) }.decodeList<PaymentDto>()
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
                val params = buildJsonObject {
                    put("p_tenant_id", tenantId)
                    if (sinceIso != null) put("p_since", sinceIso)
                    put("p_limit", 2000)
                }
                val dtoList = provider.postgrest.rpc("pull_ledger_entries_for_sync", params).decodeList<LedgerEntryDto>()
                // T-039: batch upsert.
                db.ledgerEntryDao().upsertAll(dtoList.map { it.toEntity() })
                count = dtoList.size
            } catch (_: Throwable) {
                try {
                    val dtoList = provider.postgrest.from("ledger_entries").select { limit(2000) }.decodeList<LedgerEntryDto>()
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
            val dtoList = provider.postgrest.from("classes").select { limit(2000) }.decodeList<ClassDto>()
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
            val dtoList = provider.postgrest.from("subjects").select { limit(2000) }.decodeList<SubjectDto>()
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
            val dtoList = provider.postgrest.from("installments").select { limit(2000) }.decodeList<InstallmentDto>()
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
            val dtoList = provider.postgrest.from("personnel").select { limit(2000) }.decodeList<PersonnelDto>()
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
            val dtoList = provider.postgrest.from("departments").select { limit(2000) }.decodeList<DepartmentDto>()
            // T-039: batch upsert (was a per-row listOf() wrapper loop).
            db.departmentDao().upsertAll(dtoList.map { it.toEntity() })
            Log.i("PullSync", "Pulled ${dtoList.size} departments")
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
            val dtoList = provider.postgrest.from("homework").select { limit(2000) }
                .decodeList<HomeworkDto>()
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
            val dtoList = provider.postgrest.from("attendance_records").select { limit(2000) }
                .decodeList<AttendanceRecordDto>()
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
            val dtoList = provider.postgrest.from("assessments").select { limit(2000) }
                .decodeList<AssessmentDto>()
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
            val dtoList = provider.postgrest.from("workflow_runs")
                .select { limit(50) }
                .decodeList<WorkflowRunDto>()
            // T-039: batch upsert.
            db.workflowRunDao().upsertAll(dtoList.map { it.toEntity() })
            Log.i("PullSync", "Pulled ${dtoList.size} workflow runs")
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
        val total = p + s + pay + led + cls + sub + ins + per + dep + notif + wfr + hwk + att + asm

        Log.i("PullSync", "=== PULL COMPLETE: Total $total records synchronized ===")
        return Result.Ok(total)
    }

    companion object {
        /** Dedup window — one real pullAll per 10 s however many call sites fire. */
        const val PULL_DEDUP_WINDOW_MS: Long = 10_000L

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