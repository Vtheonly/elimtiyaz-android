package com.elimtiyaz.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.elimtiyaz.data.local.dao.AcademicClassDao
import com.elimtiyaz.data.local.dao.AppNotificationDao
import com.elimtiyaz.data.local.dao.AssessmentDao
import com.elimtiyaz.data.local.dao.AttendanceRecordDao
import com.elimtiyaz.data.local.dao.AuditDao
import com.elimtiyaz.data.local.dao.ClassSubjectDao
import com.elimtiyaz.data.local.dao.ExpenseDao
import com.elimtiyaz.data.local.dao.HomeworkDao
import com.elimtiyaz.data.local.dao.InstallmentDao
import com.elimtiyaz.data.local.dao.ParentDao
import com.elimtiyaz.data.local.dao.PaymentDao
import com.elimtiyaz.data.local.dao.PersonnelDao
import com.elimtiyaz.data.local.dao.ReleveDao
import com.elimtiyaz.data.local.dao.RoutingStopDao
import com.elimtiyaz.data.local.dao.StudentDao
import com.elimtiyaz.data.local.dao.SubjectDao
import com.elimtiyaz.data.local.dao.SyncQueueDao
import com.elimtiyaz.data.local.dao.TripLogDao
import com.elimtiyaz.data.local.dao.VehicleDao
import com.elimtiyaz.data.local.entity.AcademicClassEntity
import com.elimtiyaz.data.local.entity.AppNotificationEntity
import com.elimtiyaz.data.local.entity.AssessmentEntity
import com.elimtiyaz.data.local.entity.AttendanceRecordEntity
import com.elimtiyaz.data.local.entity.AuditEntryEntity
import com.elimtiyaz.data.local.entity.ClassSubjectEntity
import com.elimtiyaz.data.local.entity.ExpenseEntity
import com.elimtiyaz.data.local.entity.HomeworkEntity
import com.elimtiyaz.data.local.entity.InstallmentEntity
import com.elimtiyaz.data.local.entity.ParentEntity
import com.elimtiyaz.data.local.entity.PaymentEntity
import com.elimtiyaz.data.local.entity.PersonnelEntity
import com.elimtiyaz.data.local.entity.ReleveEntryEntity
import com.elimtiyaz.data.local.entity.RoutingStopEntity
import com.elimtiyaz.data.local.entity.StudentEntity
import com.elimtiyaz.data.local.entity.SubjectEntity
import com.elimtiyaz.data.local.entity.SyncQueueEntity
import com.elimtiyaz.data.local.entity.TripLogEntity
import com.elimtiyaz.data.local.entity.VehicleEntity

/**
 * The single Room database for the El-Imtiyaz Staff app. Per master plan
 * §13.05 there is **no backup or export** functionality exposed — the DB is
 * strictly a cache for Supabase rows plus the offline sync queue.
 *
 * Versioning: start at 1. Future migrations must be added via
 * [androidx.room.migration.Migration] and never via `fallbackToDestructiveMigration`
 * in production.
 */
@Database(
    entities = [
        ParentEntity::class,
        StudentEntity::class,
        AcademicClassEntity::class,
        SubjectEntity::class,
        ClassSubjectEntity::class,
        AssessmentEntity::class,
        AttendanceRecordEntity::class,
        HomeworkEntity::class,
        PaymentEntity::class,
        InstallmentEntity::class,
        ExpenseEntity::class,
        PersonnelEntity::class,
        ReleveEntryEntity::class,
        AuditEntryEntity::class,
        AppNotificationEntity::class,
        RoutingStopEntity::class,
        VehicleEntity::class,
        TripLogEntity::class,
        SyncQueueEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class ElImtiyazDatabase : RoomDatabase() {
    /** Parent DAO. */
    abstract fun parentDao(): ParentDao
    /** Student DAO. */
    abstract fun studentDao(): StudentDao
    /** Class DAO. */
    abstract fun academicClassDao(): AcademicClassDao
    /** Subject DAO. */
    abstract fun subjectDao(): SubjectDao
    /** Class-subject DAO. */
    abstract fun classSubjectDao(): ClassSubjectDao
    /** Assessment DAO. */
    abstract fun assessmentDao(): AssessmentDao
    /** Attendance DAO. */
    abstract fun attendanceDao(): AttendanceRecordDao
    /** Homework DAO. */
    abstract fun homeworkDao(): HomeworkDao
    /** Payment DAO. */
    abstract fun paymentDao(): PaymentDao
    /** Installment DAO. */
    abstract fun installmentDao(): InstallmentDao
    /** Expense DAO. */
    abstract fun expenseDao(): ExpenseDao
    /** Personnel DAO. */
    abstract fun personnelDao(): PersonnelDao
    /** Releve DAO. */
    abstract fun releveDao(): ReleveDao
    /** Audit DAO. */
    abstract fun auditDao(): AuditDao
    /** Notification DAO. */
    abstract fun notificationDao(): AppNotificationDao
    /** Routing stop DAO. */
    abstract fun routingStopDao(): RoutingStopDao
    /** Vehicle DAO. */
    abstract fun vehicleDao(): VehicleDao
    /** Trip log DAO. */
    abstract fun tripLogDao(): TripLogDao
    /** Sync queue DAO. */
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        /** The database file name (used by Hilt's DatabaseModule). */
        const val NAME = "elimtiyaz.db"
    }
}
