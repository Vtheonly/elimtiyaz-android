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
import com.example.domain.repository.ReleveRepository
import com.example.domain.repository.RoutingRepository
import com.example.domain.repository.StudentRepository
import com.example.domain.repository.StorageRepository
import com.example.domain.repository.SubjectRepository
import com.example.domain.repository.WorkflowRepository
import com.example.infrastructure.local.LocalAuditRepository
import com.example.infrastructure.local.LocalAuthRepository
import com.example.infrastructure.local.LocalAttendanceRepository
import com.example.infrastructure.local.LocalClassRepository
import com.example.infrastructure.local.LocalDashboardRepository
import com.example.infrastructure.local.LocalDebtRepository
import com.example.infrastructure.local.LocalDepartmentRepository
import com.example.infrastructure.local.LocalExpenseRepository
import com.example.infrastructure.local.LocalGradeRepository
import com.example.infrastructure.local.LocalHomeworkRepository
import com.example.infrastructure.local.LocalInstallmentRepository
import com.example.infrastructure.local.LocalLedgerRepository
import com.example.infrastructure.local.LocalNotificationRepository
import com.example.infrastructure.local.LocalParentRepository
import com.example.infrastructure.local.LocalPaymentRepository
import com.example.infrastructure.local.LocalPersonnelRepository
import com.example.infrastructure.local.LocalPricingRepository
import com.example.infrastructure.local.LocalReleveRepository
import com.example.infrastructure.local.LocalRoutingRepository
import com.example.infrastructure.local.LocalStorageRepository
import com.example.infrastructure.local.LocalStudentRepository
import com.example.infrastructure.local.LocalSubjectRepository
import com.example.infrastructure.local.LocalWorkflowRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository bindings — maps domain interfaces to their LOCAL Room-backed
 * implementations.
 *
 * The mobile app is offline-first: Room is the source of truth. Every UI
 * screen reads from and writes to local SQLite tables via these repositories.
 * The business logic (pricing, ledger, waterfall, GPA) is computed in
 * `com.example.core` and matches the desktop's math exactly.
 *
 * To swap in Supabase or another remote backend later, replace the right-hand
 * side of the corresponding @Binds method.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton abstract fun bindAuthRepository(impl: LocalAuthRepository): AuthRepository
    @Binds @Singleton abstract fun bindAuditRepository(impl: LocalAuditRepository): AuditRepository
    @Binds @Singleton abstract fun bindParentRepository(impl: LocalParentRepository): ParentRepository
    @Binds @Singleton abstract fun bindStudentRepository(impl: LocalStudentRepository): StudentRepository
    @Binds @Singleton abstract fun bindPaymentRepository(impl: LocalPaymentRepository): PaymentRepository
    @Binds @Singleton abstract fun bindLedgerRepository(impl: LocalLedgerRepository): LedgerRepository
    @Binds @Singleton abstract fun bindExpenseRepository(impl: LocalExpenseRepository): ExpenseRepository
    @Binds @Singleton abstract fun bindStorageRepository(impl: LocalStorageRepository): StorageRepository

    @Binds @Singleton abstract fun bindClassRepository(impl: LocalClassRepository): ClassRepository
    @Binds @Singleton abstract fun bindSubjectRepository(impl: LocalSubjectRepository): SubjectRepository
    @Binds @Singleton abstract fun bindGradeRepository(impl: LocalGradeRepository): GradeRepository
    @Binds @Singleton abstract fun bindAttendanceRepository(impl: LocalAttendanceRepository): AttendanceRepository
    @Binds @Singleton abstract fun bindHomeworkRepository(impl: LocalHomeworkRepository): HomeworkRepository
    @Binds @Singleton abstract fun bindPersonnelRepository(impl: LocalPersonnelRepository): PersonnelRepository
    @Binds @Singleton abstract fun bindDepartmentRepository(impl: LocalDepartmentRepository): DepartmentRepository
    @Binds @Singleton abstract fun bindDashboardRepository(impl: LocalDashboardRepository): DashboardRepository
    @Binds @Singleton abstract fun bindPricingRepository(impl: LocalPricingRepository): PricingRepository
    @Binds @Singleton abstract fun bindInstallmentRepository(impl: LocalInstallmentRepository): InstallmentRepository
    @Binds @Singleton abstract fun bindDebtRepository(impl: LocalDebtRepository): DebtRepository
    @Binds @Singleton abstract fun bindNotificationRepository(impl: LocalNotificationRepository): NotificationRepository

    @Binds @Singleton abstract fun bindRoutingRepository(impl: LocalRoutingRepository): RoutingRepository
    @Binds @Singleton abstract fun bindReleveRepository(impl: LocalReleveRepository): ReleveRepository
    @Binds @Singleton abstract fun bindWorkflowRepository(impl: LocalWorkflowRepository): WorkflowRepository
}
