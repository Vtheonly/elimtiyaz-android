package com.example.infrastructure.stub

import com.example.core.Errors
import com.example.core.Result
import com.example.core.Session
import com.example.domain.model.AppNotification
import com.example.domain.model.DebtSummary
import com.example.domain.model.Installment
import com.example.domain.repository.DebtRepository
import com.example.domain.repository.InstallmentRepository
import com.example.domain.repository.NotificationRepository
import com.example.domain.repository.ParentFinancialProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Singleton
class StubNotificationRepository @Inject constructor() : NotificationRepository {
    override fun observe(): Flow<List<AppNotification>> = flowOf(emptyList())
    override fun observeForSession(session: Session): Flow<List<AppNotification>> = flowOf(emptyList())
    override suspend fun markRead(id: String): Result<Unit> = Result.Ok(Unit)
    override suspend fun markAllRead(): Result<Unit> = Result.Ok(Unit)
    override suspend fun dismiss(id: String): Result<Unit> = Result.Ok(Unit)
}

@Singleton
class StubDebtRepository @Inject constructor() : DebtRepository {
    override fun observeSummary(): Flow<List<DebtSummary>> = flowOf(emptyList())
    override fun observeParentProfile(parentId: String): Flow<ParentFinancialProfile?> = flowOf(null)
    override suspend fun sendReminder(parentId: String, actorId: String, actorName: String): Result<Unit> = Result.Ok(Unit)
}

@Singleton
class StubInstallmentRepository @Inject constructor() : InstallmentRepository {
    override fun observeByParent(parentId: String): Flow<List<Installment>> = flowOf(emptyList())
    override fun observeByStudent(studentId: String): Flow<List<Installment>> = flowOf(emptyList())
    override fun observeById(id: String): Flow<Installment?> = flowOf(null)
    override suspend fun markPaid(id: String, actorId: String, actorName: String): Result<Installment> = Result.Err(Errors.notFound("Installment not found"))
    override suspend fun updateDueDate(id: String, dueDate: String, note: String?, actorId: String, actorName: String): Result<Installment> = Result.Err(Errors.notFound("Installment not found"))
    override suspend fun regenerateForCycle(parentId: String, cycle: String, actorId: String, actorName: String): Result<List<Installment>> = Result.Ok(emptyList())
    override suspend fun findOverdue(): Result<List<Installment>> = Result.Ok(emptyList())
}
