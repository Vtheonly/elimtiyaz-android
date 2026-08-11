package com.example.infrastructure.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParentCacheDao {
    @Query("SELECT * FROM parent_cache ORDER BY lastName ASC")
    fun observeAll(): Flow<List<ParentCacheEntity>>

    @Query("SELECT * FROM parent_cache ORDER BY lastName ASC")
    suspend fun listAll(): List<ParentCacheEntity>

    @Query("SELECT * FROM parent_cache WHERE id = :id")
    fun observeById(id: String): Flow<ParentCacheEntity?>

    @Query("SELECT * FROM parent_cache WHERE id = :id")
    suspend fun getById(id: String): ParentCacheEntity?

    @Query("SELECT * FROM parent_cache WHERE firstName LIKE '%' || :q || '%' OR lastName LIKE '%' || :q || '%' OR displayName LIKE '%' || :q || '%' OR phone LIKE '%' || :q || '%' OR code LIKE '%' || :q || '%'")
    fun search(q: String): Flow<List<ParentCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ParentCacheEntity>)

    @Query("DELETE FROM parent_cache WHERE syncedAt < :before")
    suspend fun deleteStale(before: Long): Int

    @Query("DELETE FROM parent_cache")
    suspend fun clear()
}

@Dao
interface StudentCacheDao {
    @Query("SELECT * FROM student_cache ORDER BY lastName ASC LIMIT 500")
    fun observeAll(): Flow<List<StudentCacheEntity>>

    @Query("SELECT * FROM student_cache ORDER BY lastName ASC LIMIT 500")
    suspend fun listAll(): List<StudentCacheEntity>

    @Query("SELECT * FROM student_cache WHERE parentId = :parentId ORDER BY lastName ASC")
    fun observeByParent(parentId: String): Flow<List<StudentCacheEntity>>

    @Query("SELECT * FROM student_cache WHERE parentId = :parentId ORDER BY lastName ASC")
    suspend fun listByParent(parentId: String): List<StudentCacheEntity>

    @Query("SELECT * FROM student_cache WHERE classId = :classId ORDER BY lastName ASC")
    fun observeByClass(classId: String): Flow<List<StudentCacheEntity>>

    @Query("SELECT * FROM student_cache WHERE id = :id")
    fun observeById(id: String): Flow<StudentCacheEntity?>

    @Query("SELECT * FROM student_cache WHERE id = :id")
    suspend fun getById(id: String): StudentCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<StudentCacheEntity>)

    @Query("DELETE FROM student_cache")
    suspend fun clear()
}

@Dao
interface PaymentCacheDao {
    @Query("SELECT * FROM payment_cache ORDER BY collectedAt DESC LIMIT 200")
    fun observeAll(): Flow<List<PaymentCacheEntity>>

    @Query("SELECT * FROM payment_cache ORDER BY collectedAt DESC LIMIT 200")
    suspend fun listAll(): List<PaymentCacheEntity>

    @Query("SELECT * FROM payment_cache WHERE parentId = :parentId ORDER BY collectedAt DESC")
    fun observeByParent(parentId: String): Flow<List<PaymentCacheEntity>>

    @Query("SELECT * FROM payment_cache WHERE parentId = :parentId ORDER BY collectedAt DESC")
    suspend fun listByParent(parentId: String): List<PaymentCacheEntity>

    @Query("SELECT * FROM payment_cache WHERE id = :id")
    fun observeById(id: String): Flow<PaymentCacheEntity?>

    @Query("SELECT * FROM payment_cache WHERE id = :id")
    suspend fun getById(id: String): PaymentCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<PaymentCacheEntity>)

    @Query("DELETE FROM payment_cache")
    suspend fun clear()
}

@Dao
interface LedgerCacheDao {
    @Query("SELECT * FROM ledger_cache ORDER BY entryDate ASC LIMIT 1000")
    fun observeAll(): Flow<List<LedgerCacheEntity>>

    @Query("SELECT * FROM ledger_cache ORDER BY entryDate ASC LIMIT 1000")
    suspend fun listAll(): List<LedgerCacheEntity>

    @Query("SELECT * FROM ledger_cache WHERE parentId = :parentId ORDER BY entryDate ASC")
    fun observeByParent(parentId: String): Flow<List<LedgerCacheEntity>>

    @Query("SELECT * FROM ledger_cache WHERE parentId = :parentId ORDER BY entryDate ASC")
    suspend fun listByParent(parentId: String): List<LedgerCacheEntity>

    @Query("SELECT * FROM ledger_cache WHERE accountId = :accountId ORDER BY entryDate ASC")
    fun observeByAccount(accountId: String): Flow<List<LedgerCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<LedgerCacheEntity>)

    @Query("DELETE FROM ledger_cache")
    suspend fun clear()
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = :status ORDER BY queuedAt ASC")
    fun observeByStatus(status: String): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status = 'pending' ORDER BY queuedAt ASC")
    suspend fun listPending(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue ORDER BY queuedAt DESC LIMIT :limit")
    suspend fun listRecent(limit: Int = 50): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SyncQueueEntity)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'pending'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'synced'")
    fun observeSyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'failed'")
    fun observeFailedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'skipped_mock'")
    fun observeSkippedMockCount(): Flow<Int>

    @Query("DELETE FROM sync_queue WHERE status IN ('synced', 'skipped_mock') AND queuedAt < :before")
    suspend fun pruneOld(before: String): Int

    @Query("DELETE FROM sync_queue")
    suspend fun clear()
}
