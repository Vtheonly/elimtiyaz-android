package com.example.infrastructure.sync

import com.example.infrastructure.room.SyncQueueEntity
import com.example.infrastructure.supabase.SupabaseSyncDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatcher that pushes a single [SyncQueueEntity] to the appropriate
 * [SupabaseSyncDao] push method, selected by `entry.entity`.
 *
 * Extracted from [SyncService] so the entity-type → push-handler routing
 * can evolve independently of the drain loop / snapshot / scheduling
 * concerns. The dispatcher is a thin lookup — it does NOT touch the
 * queue's status, retry counters, or audit log; those concerns remain in
 * [SyncService] (the drain orchestrator).
 *
 * @param supabaseSyncDao The shared Supabase table-write DAO.
 */
@Singleton
class SyncQueueDispatcher @Inject constructor(
    private val supabaseSyncDao: SupabaseSyncDao,
) {

    /** Dispatch a single queue entry to the appropriate [SupabaseSyncDao] push method. */
    suspend fun pushEntry(entry: SyncQueueEntity) {
        when (entry.entity) {
            "parent" -> supabaseSyncDao.pushParent(entry)
            "student" -> supabaseSyncDao.pushStudent(entry)
            "payment" -> supabaseSyncDao.pushPayment(entry)
            "installment" -> supabaseSyncDao.pushInstallment(entry)
            "expense" -> supabaseSyncDao.pushExpense(entry)
            "attendance" -> supabaseSyncDao.pushAttendance(entry)
            "grade" -> supabaseSyncDao.pushGrade(entry)
            "homework" -> supabaseSyncDao.pushHomework(entry)
            "personnel" -> supabaseSyncDao.pushPersonnel(entry)
            "ledger_entry" -> supabaseSyncDao.pushLedgerEntry(entry)
            else -> throw IllegalArgumentException("Unknown sync entity: ${entry.entity}")
        }
    }
}
