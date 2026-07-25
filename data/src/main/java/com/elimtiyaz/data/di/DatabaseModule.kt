package com.elimtiyaz.data.di

import android.content.Context
import androidx.room.Room
import com.elimtiyaz.data.local.ElImtiyazDatabase
import com.elimtiyaz.data.local.dao.AcademicClassDao
import com.elimtiyaz.data.local.dao.AssessmentDao
import com.elimtiyaz.data.local.dao.AttendanceRecordDao
import com.elimtiyaz.data.local.dao.AuditDao
import com.elimtiyaz.data.local.dao.ClassSubjectDao
import com.elimtiyaz.data.local.dao.ExpenseDao
import com.elimtiyaz.data.local.dao.HomeworkDao
import com.elimtiyaz.data.local.dao.InstallmentDao
import com.elimtiyaz.data.local.dao.NotificationDao
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the Room database and every DAO. The database file name is
 * `elimtiyaz.db` per the architecture doc. Per master plan §13.05 there is
 * no backup/export method on any DAO.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Provide the [ElImtiyazDatabase] singleton. */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ElImtiyazDatabase =
        Room.databaseBuilder(context, ElImtiyazDatabase::class.java, ElImtiyazDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    /** Provide the parent DAO. */
    @Provides fun provideParentDao(db: ElImtiyazDatabase): ParentDao = db.parentDao()

    /** Provide the student DAO. */
    @Provides fun provideStudentDao(db: ElImtiyazDatabase): StudentDao = db.studentDao()

    /** Provide the class DAO. */
    @Provides fun provideAcademicClassDao(db: ElImtiyazDatabase): AcademicClassDao = db.academicClassDao()

    /** Provide the subject DAO. */
    @Provides fun provideSubjectDao(db: ElImtiyazDatabase): SubjectDao = db.subjectDao()

    /** Provide the class-subject DAO. */
    @Provides fun provideClassSubjectDao(db: ElImtiyazDatabase): ClassSubjectDao = db.classSubjectDao()

    /** Provide the assessment DAO. */
    @Provides fun provideAssessmentDao(db: ElImtiyazDatabase): AssessmentDao = db.assessmentDao()

    /** Provide the attendance DAO. */
    @Provides fun provideAttendanceDao(db: ElImtiyazDatabase): AttendanceRecordDao = db.attendanceDao()

    /** Provide the homework DAO. */
    @Provides fun provideHomeworkDao(db: ElImtiyazDatabase): HomeworkDao = db.homeworkDao()

    /** Provide the payment DAO. */
    @Provides fun providePaymentDao(db: ElImtiyazDatabase): PaymentDao = db.paymentDao()

    /** Provide the installment DAO. */
    @Provides fun provideInstallmentDao(db: ElImtiyazDatabase): InstallmentDao = db.installmentDao()

    /** Provide the expense DAO. */
    @Provides fun provideExpenseDao(db: ElImtiyazDatabase): ExpenseDao = db.expenseDao()

    /** Provide the personnel DAO. */
    @Provides fun providePersonnelDao(db: ElImtiyazDatabase): PersonnelDao = db.personnelDao()

    /** Provide the releve DAO. */
    @Provides fun provideReleveDao(db: ElImtiyazDatabase): ReleveDao = db.releveDao()

    /** Provide the audit DAO. */
    @Provides fun provideAuditDao(db: ElImtiyazDatabase): AuditDao = db.auditDao()

    /** Provide the notification DAO. */
    @Provides fun provideNotificationDao(db: ElImtiyazDatabase): NotificationDao = db.notificationDao()

    /** Provide the routing stop DAO. */
    @Provides fun provideRoutingStopDao(db: ElImtiyazDatabase): RoutingStopDao = db.routingStopDao()

    /** Provide the vehicle DAO. */
    @Provides fun provideVehicleDao(db: ElImtiyazDatabase): VehicleDao = db.vehicleDao()

    /** Provide the trip log DAO. */
    @Provides fun provideTripLogDao(db: ElImtiyazDatabase): TripLogDao = db.tripLogDao()

    /** Provide the sync queue DAO. */
    @Provides fun provideSyncQueueDao(db: ElImtiyazDatabase): SyncQueueDao = db.syncQueueDao()
}
