package com.example.di

import com.example.domain.repository.AuditRepository
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.AttendanceRepository
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.DashboardRepository
import com.example.domain.repository.DebtRepository
import com.example.domain.repository.DepartmentRepository
import com.example.domain.repository.ExpenseRepository
import com.example.domain.repository.GradeRepository
import com.example.domain.repository.HomeworkRepository
import com.example.domain.repository.InstallmentRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.NotificationRepository
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.PaymentRepository
import com.example.domain.repository.PersonnelRepository
import com.example.domain.repository.PricingRepository
import com.example.domain.repository.StudentRepository
import com.example.domain.repository.StorageRepository
import com.example.domain.repository.SubjectRepository
import com.example.infrastructure.supabase.SupabaseAttendanceRepository
import com.example.infrastructure.supabase.SupabaseAuditRepository
import com.example.infrastructure.supabase.SupabaseAuthRepository
import com.example.infrastructure.supabase.SupabaseClassRepository
import com.example.infrastructure.supabase.SupabaseDashboardRepository
import com.example.infrastructure.supabase.SupabaseDebtRepository
import com.example.infrastructure.supabase.SupabaseDepartmentRepository
import com.example.infrastructure.supabase.SupabaseExpenseRepository
import com.example.infrastructure.supabase.SupabaseGradeRepository
import com.example.infrastructure.supabase.SupabaseHomeworkRepository
import com.example.infrastructure.supabase.SupabaseInstallmentRepository
import com.example.infrastructure.supabase.SupabaseLedgerRepository
import com.example.infrastructure.supabase.SupabaseNotificationRepository
import com.example.infrastructure.supabase.SupabaseParentRepository
import com.example.infrastructure.supabase.SupabasePaymentRepository
import com.example.infrastructure.supabase.SupabasePersonnelRepository
import com.example.infrastructure.supabase.SupabasePricingRepository
import com.example.infrastructure.supabase.SupabaseStorageRepository
import com.example.infrastructure.supabase.SupabaseStudentRepository
import com.example.infrastructure.supabase.SupabaseSubjectRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository bindings — maps domain interfaces to their Supabase implementations.
 *
 * To swap in a mock or Room-only implementation (e.g. for testing), replace
 * the right-hand side of the corresponding @Binds method.
 *
 * Wave 1B (DOMAIN-REPOS) added 12 new Supabase-backed repositories; the
 * previous stub implementations (Notification/Debt/Installment) have been
 * removed — see `infrastructure/stub/StubRepositories.kt`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository
    @Binds @Singleton abstract fun bindAuditRepository(impl: SupabaseAuditRepository): AuditRepository
    @Binds @Singleton abstract fun bindParentRepository(impl: SupabaseParentRepository): ParentRepository
    @Binds @Singleton abstract fun bindStudentRepository(impl: SupabaseStudentRepository): StudentRepository
    @Binds @Singleton abstract fun bindPaymentRepository(impl: SupabasePaymentRepository): PaymentRepository
    @Binds @Singleton abstract fun bindLedgerRepository(impl: SupabaseLedgerRepository): LedgerRepository
    @Binds @Singleton abstract fun bindExpenseRepository(impl: SupabaseExpenseRepository): ExpenseRepository
    @Binds @Singleton abstract fun bindStorageRepository(impl: SupabaseStorageRepository): StorageRepository

    // ---- Wave 1B: 12 new Supabase-backed repositories ----
    @Binds @Singleton abstract fun bindClassRepository(impl: SupabaseClassRepository): ClassRepository
    @Binds @Singleton abstract fun bindSubjectRepository(impl: SupabaseSubjectRepository): SubjectRepository
    @Binds @Singleton abstract fun bindGradeRepository(impl: SupabaseGradeRepository): GradeRepository
    @Binds @Singleton abstract fun bindAttendanceRepository(impl: SupabaseAttendanceRepository): AttendanceRepository
    @Binds @Singleton abstract fun bindHomeworkRepository(impl: SupabaseHomeworkRepository): HomeworkRepository
    @Binds @Singleton abstract fun bindPersonnelRepository(impl: SupabasePersonnelRepository): PersonnelRepository
    @Binds @Singleton abstract fun bindDepartmentRepository(impl: SupabaseDepartmentRepository): DepartmentRepository
    @Binds @Singleton abstract fun bindDashboardRepository(impl: SupabaseDashboardRepository): DashboardRepository
    @Binds @Singleton abstract fun bindPricingRepository(impl: SupabasePricingRepository): PricingRepository
    @Binds @Singleton abstract fun bindInstallmentRepository(impl: SupabaseInstallmentRepository): InstallmentRepository
    @Binds @Singleton abstract fun bindDebtRepository(impl: SupabaseDebtRepository): DebtRepository
    @Binds @Singleton abstract fun bindNotificationRepository(impl: SupabaseNotificationRepository): NotificationRepository
}
