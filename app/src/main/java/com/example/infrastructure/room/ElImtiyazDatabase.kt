package com.example.infrastructure.room

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * El Imtiyaz local database — the PRIMARY source of truth for this build.
 *
 * The mobile app is offline-first: every UI screen reads from and writes to
 * these tables. The schema mirrors the desktop's Supabase schema
 * (`supabase/migrations/`) field-by-field so the business logic, calculations,
 * and financial mathematics produce identical numbers on both platforms.
 *
 * Versioned at 2. The original cache entities (parent_cache, student_cache,
 * payment_cache, ledger_cache, sync_queue) are retained for backward
 * compatibility with the sync layer; the new source-of-truth tables are
 * the non-suffixed ones (parents, students, payments, ledger_entries, etc.).
 */
@Database(
    entities = [
        // ── Original cache layer (kept for sync compatibility) ──
        ParentCacheEntity::class,
        StudentCacheEntity::class,
        PaymentCacheEntity::class,
        LedgerCacheEntity::class,
        SyncQueueEntity::class,
        // ── Local source-of-truth tables ──
        ParentEntity::class,
        StudentEntity::class,
        AcademicClassEntity::class,
        SubjectEntity::class,
        AttendanceEntity::class,
        AssessmentEntity::class,
        HomeworkEntity::class,
        PaymentEntity::class,
        InstallmentEntity::class,
        LedgerEntryEntity::class,
        ExpenseEntity::class,
        PersonnelEntity::class,
        DepartmentEntity::class,
        PricingConfigEntity::class,
        PricingDiscountEntity::class,
        GradeLevelTuitionEntity::class,
        TransportPricingEntity::class,
        NotificationEntity::class,
        AuditLogEntity::class,
        TripLogEntity::class,
        ReleveEntryEntity::class,
        WorkflowRunEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class ElImtiyazDatabase : RoomDatabase() {
    // ── Original cache DAOs ──
    abstract fun parentCacheDao(): ParentCacheDao
    abstract fun studentCacheDao(): StudentCacheDao
    abstract fun paymentCacheDao(): PaymentCacheDao
    abstract fun ledgerCacheDao(): LedgerCacheDao
    abstract fun syncQueueDao(): SyncQueueDao

    // ── Local source-of-truth DAOs ──
    abstract fun parentDao(): ParentDao
    abstract fun studentDao(): StudentDao
    abstract fun academicClassDao(): AcademicClassDao
    abstract fun subjectDao(): SubjectDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun assessmentDao(): AssessmentDao
    abstract fun homeworkDao(): HomeworkDao
    abstract fun paymentDao(): PaymentDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun ledgerEntryDao(): LedgerEntryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun personnelDao(): PersonnelDao
    abstract fun departmentDao(): DepartmentDao
    abstract fun pricingConfigDao(): PricingConfigDao
    abstract fun notificationDao(): NotificationDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun tripLogDao(): TripLogDao
    abstract fun releveEntryDao(): ReleveEntryDao
    abstract fun workflowRunDao(): WorkflowRunDao

    companion object {
        /**
         * Room migration v3 → v4 (CANONICAL-FINANCIAL-LOGIC.md §7.5 + §8.4).
         *
         * Adds the `metadataJson` TEXT column to `ledger_entries` so that
         * pull-side metadata (tranche, level, gradeLevel, paymentPlan,
         * academicCycle, clubCategory, therapyKind, period, sessionCount,
         * serviceQualifier, pricingSource, reversedEntryId, reason) is
         * preserved across the full sync cycle instead of being dropped.
         *
         * Default value `'{}'` so existing rows continue to map to an empty
         * metadata map (matching the previous `metadata = emptyMap()` behavior).
         */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE ledger_entries ADD COLUMN metadataJson TEXT NOT NULL DEFAULT '{}'"
                )
            }
        }
    }
}
