package com.elimtiyaz.data.di

import com.elimtiyaz.data.mock.MockAttendanceRepository
import com.elimtiyaz.data.mock.MockAuditRepository
import com.elimtiyaz.data.mock.MockClassRepository
import com.elimtiyaz.data.mock.MockDashboardRepository
import com.elimtiyaz.data.mock.MockDebtRepository
import com.elimtiyaz.data.mock.MockExpenseRepository
import com.elimtiyaz.data.mock.MockGradeRepository
import com.elimtiyaz.data.mock.MockHomeworkRepository
import com.elimtiyaz.data.mock.MockInstallmentRepository
import com.elimtiyaz.data.mock.MockNotificationRepository
import com.elimtiyaz.data.mock.MockParentRepository
import com.elimtiyaz.data.mock.MockPaymentRepository
import com.elimtiyaz.data.mock.MockPersonnelRepository
import com.elimtiyaz.data.mock.MockReleveRepository
import com.elimtiyaz.data.mock.MockRoutingRepository
import com.elimtiyaz.data.mock.MockStudentRepository
import com.elimtiyaz.data.mock.MockSubjectRepository
import com.elimtiyaz.data.repository.SupabaseAttendanceRepository
import com.elimtiyaz.data.repository.SupabaseAuditRepository
import com.elimtiyaz.data.repository.SupabaseAuthRepository
import com.elimtiyaz.data.repository.SupabaseClassRepository
import com.elimtiyaz.data.repository.SupabaseDashboardRepository
import com.elimtiyaz.data.repository.SupabaseDebtRepository
import com.elimtiyaz.data.repository.SupabaseExpenseRepository
import com.elimtiyaz.data.repository.SupabaseGradeRepository
import com.elimtiyaz.data.repository.SupabaseHomeworkRepository
import com.elimtiyaz.data.repository.SupabaseInstallmentRepository
import com.elimtiyaz.data.repository.SupabaseNotificationRepository
import com.elimtiyaz.data.repository.SupabaseParentRepository
import com.elimtiyaz.data.repository.SupabasePaymentRepository
import com.elimtiyaz.data.repository.SupabasePersonnelRepository
import com.elimtiyaz.data.repository.SupabaseReleveRepository
import com.elimtiyaz.data.repository.SupabaseRoutingRepository
import com.elimtiyaz.data.repository.SupabaseStudentRepository
import com.elimtiyaz.data.repository.SupabaseSubjectRepository
import com.elimtiyaz.data.mock.MockNotificationRepository
import com.elimtiyaz.data.mock.MockAuthRepository
import com.elimtiyaz.domain.repository.NotificationRepository
import com.elimtiyaz.domain.repository.AttendanceRepository
import com.elimtiyaz.domain.repository.AuditRepository
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.ClassRepository
import com.elimtiyaz.domain.repository.DashboardRepository
import com.elimtiyaz.domain.repository.DebtRepository
import com.elimtiyaz.domain.repository.ExpenseRepository
import com.elimtiyaz.domain.repository.GradeRepository
import com.elimtiyaz.domain.repository.HomeworkRepository
import com.elimtiyaz.domain.repository.InstallmentRepository
import com.elimtiyaz.domain.repository.NotificationRepository
import com.elimtiyaz.domain.repository.ParentRepository
import com.elimtiyaz.domain.repository.PaymentRepository
import com.elimtiyaz.domain.repository.PersonnelRepository
import com.elimtiyaz.domain.repository.ReleveRepository
import com.elimtiyaz.domain.repository.RoutingRepository
import com.elimtiyaz.domain.repository.StudentRepository
import com.elimtiyaz.domain.repository.SubjectRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Central Hilt module that provides every repository interface. Each provider
 * reads the `"isMockMode"` boolean (populated by [NetworkModule] from
 * `BuildConfig` / `local.properties`) and routes to either the Supabase
 * implementation or the in-memory mock.
 *
 * When Supabase keys are absent, every repository returns its mock impl and
 * the app is fully demoable offline. When keys are present, the real Supabase
 * impl is used and the mock impls are simply not bound to any consumer.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /** Provide the [AuthRepository] — mock or Supabase based on the flag. */
    @Provides
    @Singleton
    fun provideAuthRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockAuthRepository,
        real: SupabaseAuthRepository,
    ): AuthRepository = if (mockMode) mock else real

    /** Provide the [ParentRepository]. */
    @Provides
    @Singleton
    fun provideParentRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockParentRepository,
        real: SupabaseParentRepository,
    ): ParentRepository = if (mockMode) mock else real

    /** Provide the [StudentRepository]. */
    @Provides
    @Singleton
    fun provideStudentRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockStudentRepository,
        real: SupabaseStudentRepository,
    ): StudentRepository = if (mockMode) mock else real

    /** Provide the [ClassRepository]. */
    @Provides
    @Singleton
    fun provideClassRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockClassRepository,
        real: SupabaseClassRepository,
    ): ClassRepository = if (mockMode) mock else real

    /** Provide the [SubjectRepository]. */
    @Provides
    @Singleton
    fun provideSubjectRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockSubjectRepository,
        real: SupabaseSubjectRepository,
    ): SubjectRepository = if (mockMode) mock else real

    /** Provide the [GradeRepository]. */
    @Provides
    @Singleton
    fun provideGradeRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockGradeRepository,
        real: SupabaseGradeRepository,
    ): GradeRepository = if (mockMode) mock else real

    /** Provide the [AttendanceRepository]. */
    @Provides
    @Singleton
    fun provideAttendanceRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockAttendanceRepository,
        real: SupabaseAttendanceRepository,
    ): AttendanceRepository = if (mockMode) mock else real

    /** Provide the [HomeworkRepository]. */
    @Provides
    @Singleton
    fun provideHomeworkRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockHomeworkRepository,
        real: SupabaseHomeworkRepository,
    ): HomeworkRepository = if (mockMode) mock else real

    /** Provide the [PaymentRepository]. */
    @Provides
    @Singleton
    fun providePaymentRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockPaymentRepository,
        real: SupabasePaymentRepository,
    ): PaymentRepository = if (mockMode) mock else real

    /** Provide the [InstallmentRepository]. */
    @Provides
    @Singleton
    fun provideInstallmentRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockInstallmentRepository,
        real: SupabaseInstallmentRepository,
    ): InstallmentRepository = if (mockMode) mock else real

    /** Provide the [DebtRepository]. */
    @Provides
    @Singleton
    fun provideDebtRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockDebtRepository,
        real: SupabaseDebtRepository,
    ): DebtRepository = if (mockMode) mock else real

    /** Provide the [ExpenseRepository]. */
    @Provides
    @Singleton
    fun provideExpenseRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockExpenseRepository,
        real: SupabaseExpenseRepository,
    ): ExpenseRepository = if (mockMode) mock else real

    /** Provide the [PersonnelRepository]. */
    @Provides
    @Singleton
    fun providePersonnelRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockPersonnelRepository,
        real: SupabasePersonnelRepository,
    ): PersonnelRepository = if (mockMode) mock else real

    /** Provide the [ReleveRepository]. */
    @Provides
    @Singleton
    fun provideReleveRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockReleveRepository,
        real: SupabaseReleveRepository,
    ): ReleveRepository = if (mockMode) mock else real

    /** Provide the [AuditRepository]. */
    @Provides
    @Singleton
    fun provideAuditRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockAuditRepository,
        real: SupabaseAuditRepository,
    ): AuditRepository = if (mockMode) mock else real

    /** Provide the [DashboardRepository]. */
    @Provides
    @Singleton
    fun provideDashboardRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockDashboardRepository,
        real: SupabaseDashboardRepository,
    ): DashboardRepository = if (mockMode) mock else real

    /** Provide the [RoutingRepository]. */
    @Provides
    @Singleton
    fun provideRoutingRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockRoutingRepository,
        real: SupabaseRoutingRepository,
    ): RoutingRepository = if (mockMode) mock else real

    /** Provide the [NotificationRepository]. */
    @Provides
    @Singleton
    fun provideNotificationRepository(
        @Named("isMockMode") mockMode: Boolean,
        mock: MockNotificationRepository,
        real: SupabaseNotificationRepository,
    ): NotificationRepository = if (mockMode) mock else real
}
