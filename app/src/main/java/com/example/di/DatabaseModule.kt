package com.example.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.infrastructure.room.AcademicClassDao
import com.example.infrastructure.room.AssessmentDao
import com.example.infrastructure.room.AttendanceDao
import com.example.infrastructure.room.AuditLogDao
import com.example.infrastructure.room.DepartmentDao
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.room.ExpenseDao
import com.example.infrastructure.room.HomeworkDao
import com.example.infrastructure.room.InstallmentDao
import com.example.infrastructure.room.LedgerEntryDao
import com.example.infrastructure.room.NotificationDao
import com.example.infrastructure.room.ParentDao
import com.example.infrastructure.room.PaymentDao
import com.example.infrastructure.room.PersonnelDao
import com.example.infrastructure.room.PricingConfigDao
import com.example.infrastructure.room.ReleveEntryDao
import com.example.infrastructure.room.StudentDao
import com.example.infrastructure.room.SubjectDao
import com.example.infrastructure.room.TripLogDao
import com.example.infrastructure.room.WorkflowRunDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Database + WorkManager + DataStore DI module.
 *
 * Room is the **local source of truth** for this build. The database mirrors
 * the desktop's Supabase schema field-by-field so business logic and financial
 * calculations produce identical numbers on both platforms.
 *
 * `fallbackToDestructiveMigration()` is used because this is a development
 * build — schema changes between versions simply rebuild the database. The
 * [DatabaseSeeder] re-seeds real demo data (from `Prices.md`) on first launch.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ElImtiyazDatabase =
        Room.databaseBuilder(context, ElImtiyazDatabase::class.java, "el_imtiyaz.db")
            // TIER 2 R12 — register MIGRATION_4_5 so users don't lose their
            // data when upgrading. The migration adds the `paymentPlan` column
            // to the `students` table with default `'tranches'` (matching the
            // desktop's default for imported students without an explicit
            // `payment_plan` value).
            //
            // TIER 3 R18 — register MIGRATION_5_6 so the `finalSpentAmount`
            // column is added to the `expenses` table. The column stores the
            // actual spent amount confirmed by the proof scan at settlement
            // time — previously `settleProof()` accepted the parameter but
            // silently dropped it.
            .addMigrations(
                ElImtiyazDatabase.MIGRATION_3_4,
                ElImtiyazDatabase.MIGRATION_4_5,
                ElImtiyazDatabase.MIGRATION_5_6,
            )
            // Fallback for any future schema changes that don't yet have an
            // explicit migration — destructive, but only fires if a migration
            // is missing. Production deployments should add explicit migrations
            // for every schema bump.
            .fallbackToDestructiveMigration(true)
            .build()

    // ── Original cache DAOs (kept for sync layer) ──
    @Provides @Singleton
    fun provideParentCacheDao(db: ElImtiyazDatabase) = db.parentCacheDao()

    @Provides @Singleton
    fun provideStudentCacheDao(db: ElImtiyazDatabase) = db.studentCacheDao()

    @Provides @Singleton
    fun providePaymentCacheDao(db: ElImtiyazDatabase) = db.paymentCacheDao()

    @Provides @Singleton
    fun provideLedgerCacheDao(db: ElImtiyazDatabase) = db.ledgerCacheDao()

    @Provides @Singleton
    fun provideSyncQueueDao(db: ElImtiyazDatabase) = db.syncQueueDao()

    // ── Local source-of-truth DAOs ──
    @Provides @Singleton
    fun provideParentDao(db: ElImtiyazDatabase): ParentDao = db.parentDao()

    @Provides @Singleton
    fun provideStudentDao(db: ElImtiyazDatabase): StudentDao = db.studentDao()

    @Provides @Singleton
    fun provideAcademicClassDao(db: ElImtiyazDatabase): AcademicClassDao = db.academicClassDao()

    @Provides @Singleton
    fun provideSubjectDao(db: ElImtiyazDatabase): SubjectDao = db.subjectDao()

    @Provides @Singleton
    fun provideAttendanceDao(db: ElImtiyazDatabase): AttendanceDao = db.attendanceDao()

    @Provides @Singleton
    fun provideAssessmentDao(db: ElImtiyazDatabase): AssessmentDao = db.assessmentDao()

    @Provides @Singleton
    fun provideHomeworkDao(db: ElImtiyazDatabase): HomeworkDao = db.homeworkDao()

    @Provides @Singleton
    fun providePaymentDao(db: ElImtiyazDatabase): PaymentDao = db.paymentDao()

    @Provides @Singleton
    fun provideInstallmentDao(db: ElImtiyazDatabase): InstallmentDao = db.installmentDao()

    @Provides @Singleton
    fun provideLedgerEntryDao(db: ElImtiyazDatabase): LedgerEntryDao = db.ledgerEntryDao()

    @Provides @Singleton
    fun provideExpenseDao(db: ElImtiyazDatabase): ExpenseDao = db.expenseDao()

    @Provides @Singleton
    fun providePersonnelDao(db: ElImtiyazDatabase): PersonnelDao = db.personnelDao()

    @Provides @Singleton
    fun provideDepartmentDao(db: ElImtiyazDatabase): DepartmentDao = db.departmentDao()

    @Provides @Singleton
    fun providePricingConfigDao(db: ElImtiyazDatabase): PricingConfigDao = db.pricingConfigDao()

    @Provides @Singleton
    fun provideNotificationDao(db: ElImtiyazDatabase): NotificationDao = db.notificationDao()

    @Provides @Singleton
    fun provideAuditLogDao(db: ElImtiyazDatabase): AuditLogDao = db.auditLogDao()

    @Provides @Singleton
    fun provideTripLogDao(db: ElImtiyazDatabase): TripLogDao = db.tripLogDao()

    @Provides @Singleton
    fun provideReleveEntryDao(db: ElImtiyazDatabase): ReleveEntryDao = db.releveEntryDao()

    @Provides @Singleton
    fun provideWorkflowRunDao(db: ElImtiyazDatabase): WorkflowRunDao = db.workflowRunDao()

    @Provides @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    /**
     * Provide the singleton [DataStore<Preferences>] for app settings.
     */
    @Provides @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { java.io.File(context.filesDir, "datastore/el_imtiyaz_settings.preferences_pb") },
    )
}
