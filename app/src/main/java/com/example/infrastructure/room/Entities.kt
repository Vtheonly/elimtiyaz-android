package com.example.infrastructure.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room cache entities — mirror the Supabase schema for offline reads.
 * Room is NOT the primary store; it's a read cache + sync queue.
 * Supabase is the source of truth.
 *
 * Every cache entity carries `tenantId` (for tenant-scoped queries),
 * `updatedAt` (for cache invalidation), and `syncedAt` (when the row was
 * last fetched from Supabase).
 */

@Entity(tableName = "parent_cache", indices = [Index("tenantId"), Index("code"), Index("updatedAt")])
data class ParentCacheEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val code: String,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val whatsapp: String?,
    val email: String?,
    val occupation: String?,
    val address: String?,
    val transportDestination: String?,
    val preferredLanguage: String,
    val avatarUrl: String?,
    val createdAt: String,
    val updatedAt: String,
    val syncedAt: Long,
)

@Entity(tableName = "student_cache", indices = [Index("tenantId"), Index("parentId"), Index("classId"), Index("code")])
data class StudentCacheEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val code: String,
    val parentId: String,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val birthDate: String,
    val enrollmentDate: String,
    val level: String,
    val gradeLevel: String,
    val classId: String?,
    val photoUrl: String?,
    val medicalNotes: String?,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val syncedAt: Long,
)

@Entity(tableName = "payment_cache", indices = [Index("tenantId"), Index("parentId"), Index("studentId"), Index("receiptNumber")])
data class PaymentCacheEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val receiptNumber: String,
    val parentId: String,
    val studentId: String?,
    val amount: Long,
    val method: String,
    val status: String,
    val category: String,
    val installmentId: String?,
    val proofUrl: String?,
    val notes: String?,
    val collectedBy: String,
    val collectedAt: String,
    val createdAt: String,
    val updatedAt: String,
    val syncedAt: Long,
)

@Entity(tableName = "ledger_cache", indices = [Index("tenantId"), Index("parentId"), Index("accountId"), Index("entryDate")])
data class LedgerCacheEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val accountId: String,
    val parentId: String,
    val studentId: String?,
    val category: String,
    val amount: Long,
    val type: String,
    val sourceType: String,
    val sourceId: String,
    val method: String?,
    val receiptNumber: String?,
    val paymentStatus: String?,
    val reversesId: String?,
    val description: String,
    val actorId: String,
    val actorName: String,
    val entryDate: String,
    val syncedAt: Long,
)

/**
 * Sync queue entry — mirrors the desktop's SyncQueueEntry shape.
 * Every offline write is enqueued here and drained by [com.example.infrastructure.sync.SyncWorker].
 *
 * `isMock` is CRITICAL: mock data is NEVER pushed to Supabase. Enforced
 * at enqueue time AND at drain time (defense in depth).
 */
@Entity(
    tableName = "sync_queue",
    indices = [Index("status"), Index("tenantId"), Index("queuedAt"), Index("isMock")],
)
data class SyncQueueEntity(
    @PrimaryKey val id: String,                  // sync_{timestamp_base36}_{random}
    val queuedAt: String,                        // ISO timestamp
    val lastAttemptAt: String?,
    val entity: String,                          // parent | student | payment | installment | ...
    val operation: String,                       // insert | update | delete
    val tenantId: String,                        // NEVER sync across tenants
    val actorId: String,                         // user ID or "system"
    val payload: String,                         // JSON
    val isMock: Boolean,                         // CRITICAL: mock records NEVER pushed
    val sourceScreen: String?,
    val status: String,                          // pending | synced | failed | skipped_mock
    val attempts: Int,
    val lastError: String?,
)
