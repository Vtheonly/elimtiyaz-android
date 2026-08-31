package com.example.infrastructure.sync

import android.util.Log
import com.example.core.Result
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.supabase.ClassDto
import com.example.infrastructure.supabase.DepartmentDto
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
) {
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
                for (dto in dtoList) {
                    db.parentDao().upsert(dto.toEntity())
                    count++
                }
                fetched = count > 0
                Log.i("PullSync", "RPC pull_parents_for_sync success: $count parents")
            } catch (rpcEx: Throwable) {
                Log.w("PullSync", "RPC pull_parents_for_sync failed: ${rpcEx.message}")
            }

            if (!fetched) {
                try {
                    val dtoList = provider.postgrest.from("parents").select { limit(2000) }.decodeList<ParentDto>()
                    for (dto in dtoList) {
                        db.parentDao().upsert(dto.toEntity())
                        count++
                    }
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
                for (dto in dtoList) {
                    db.studentDao().upsert(dto.toEntity())
                    count++
                }
                fetched = count > 0
                Log.i("PullSync", "RPC pull_students_for_sync success: $count students")
            } catch (rpcEx: Throwable) {
                Log.w("PullSync", "RPC pull_students_for_sync failed: ${rpcEx.message}")
            }

            if (!fetched) {
                try {
                    val dtoList = provider.postgrest.from("students").select { limit(2000) }.decodeList<StudentDto>()
                    for (dto in dtoList) {
                        db.studentDao().upsert(dto.toEntity())
                        count++
                    }
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

    suspend fun pullPayments(sinceIso: String? = null): Result<Int> = withContext(Dispatchers.IO) {
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
                for (dto in dtoList) {
                    db.paymentDao().upsert(dto.toEntity())
                    count++
                }
            } catch (_: Throwable) {
                try {
                    val dtoList = provider.postgrest.from("payments").select { limit(2000) }.decodeList<PaymentDto>()
                    for (dto in dtoList) {
                        db.paymentDao().upsert(dto.toEntity())
                        count++
                    }
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
                for (dto in dtoList) {
                    db.ledgerEntryDao().upsert(dto.toEntity())
                    count++
                }
            } catch (_: Throwable) {
                try {
                    val dtoList = provider.postgrest.from("ledger_entries").select { limit(2000) }.decodeList<LedgerEntryDto>()
                    for (dto in dtoList) {
                        db.ledgerEntryDao().upsert(dto.toEntity())
                        count++
                    }
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
            for (dto in dtoList) db.academicClassDao().upsert(dto.toEntity())
            Log.i("PullSync", "Pulled ${dtoList.size} classes")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    suspend fun pullSubjects(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dtoList = provider.postgrest.from("subjects").select { limit(2000) }.decodeList<SubjectDto>()
            for (dto in dtoList) db.subjectDao().upsert(dto.toEntity())
            Log.i("PullSync", "Pulled ${dtoList.size} subjects")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    suspend fun pullInstallments(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dtoList = provider.postgrest.from("installments").select { limit(2000) }.decodeList<InstallmentDto>()
            for (dto in dtoList) db.installmentDao().upsert(dto.toEntity())
            Log.i("PullSync", "Pulled ${dtoList.size} installments")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    suspend fun pullPersonnel(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dtoList = provider.postgrest.from("personnel").select { limit(2000) }.decodeList<PersonnelDto>()
            for (dto in dtoList) db.personnelDao().upsert(dto.toEntity())
            Log.i("PullSync", "Pulled ${dtoList.size} personnel")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    suspend fun pullDepartments(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dtoList = provider.postgrest.from("departments").select { limit(2000) }.decodeList<DepartmentDto>()
            for (dto in dtoList) db.departmentDao().upsertAll(listOf(dto.toEntity()))
            Log.i("PullSync", "Pulled ${dtoList.size} departments")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    suspend fun pullNotifications(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dtoList = provider.postgrest.from("notifications").select { limit(200) }.decodeList<NotificationDto>()
            for (dto in dtoList) db.notificationDao().upsertAll(listOf(dto.toEntity()))
            Log.i("PullSync", "Pulled ${dtoList.size} notifications")
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
            for (dto in dtoList) db.workflowRunDao().upsert(dto.toEntity())
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
        val total = p + s + pay + led + cls + sub + ins + per + dep + notif + wfr

        Log.i("PullSync", "=== PULL COMPLETE: Total $total records synchronized ===")
        return Result.Ok(total)
    }

    companion object {
        /** Dedup window — one real pullAll per 10 s however many call sites fire. */
        const val PULL_DEDUP_WINDOW_MS: Long = 10_000L
    }
}