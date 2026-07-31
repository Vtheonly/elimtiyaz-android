package com.example.di

import com.example.domain.repository.AuditRepository
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.DepartmentRepository
import com.example.domain.repository.ExpenseRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.PaymentRepository
import com.example.domain.repository.PersonnelRepository
import com.example.domain.repository.StudentRepository
import com.example.domain.repository.StorageRepository
import com.example.infrastructure.supabase.SupabaseAuditRepository
import com.example.infrastructure.supabase.SupabaseAuthRepository
import com.example.infrastructure.supabase.SupabaseExpenseRepository
import com.example.infrastructure.supabase.SupabaseLedgerRepository
import com.example.infrastructure.supabase.SupabaseParentRepository
import com.example.infrastructure.supabase.SupabasePaymentRepository
import com.example.infrastructure.supabase.SupabaseStorageRepository
import com.example.infrastructure.supabase.SupabaseStudentRepository
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

    // Stubs for repositories not yet implemented — will be added in future iterations.
    // @Binds @Singleton abstract fun bindClassRepository(impl: ???): ClassRepository
    // @Binds @Singleton abstract fun bindDepartmentRepository(impl: ???): DepartmentRepository
    // @Binds @Singleton abstract fun bindPersonnelRepository(impl: ???): PersonnelRepository
}
