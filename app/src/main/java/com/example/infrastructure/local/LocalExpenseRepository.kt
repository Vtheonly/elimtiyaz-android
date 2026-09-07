package com.example.infrastructure.local

import com.example.core.Errors
import com.example.core.Result
import com.example.core.absenceAlertThreshold
import com.example.core.currentTermWindow
import com.example.core.agingBucketFromDays
import com.example.core.daysBetweenFloor
import com.example.core.formatDzd
import com.example.core.LedgerEngine
import com.example.domain.model.AcademicClass
import com.example.domain.model.AppNotification
import com.example.domain.model.Assessment
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AuditLog
import com.example.domain.model.ClassRollCallStatus
import com.example.domain.model.DashboardKpi
import com.example.domain.model.DashboardOperationalAlert
import com.example.domain.model.DebtSummary
import com.example.domain.model.Department
import com.example.domain.model.Expense
import com.example.domain.model.GradeLevelTuition
import com.example.domain.model.Homework
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethodSummary
import com.example.domain.model.Personnel
import com.example.domain.model.PricingConfig
import com.example.domain.model.ReleveEntry
import com.example.domain.model.Student
import com.example.domain.model.Subject
import com.example.domain.repository.AuditFilter
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.CreateClassInput
import com.example.domain.repository.CreateDepartmentInput
import com.example.domain.repository.CreatePersonnelInput
import com.example.domain.repository.CreateSubjectInput
import com.example.domain.repository.DashboardRepository
import com.example.domain.repository.DebtRepository
import com.example.domain.repository.DepartmentRepository
import com.example.domain.repository.EnterGradeInput
import com.example.domain.repository.ExpenseRepository
import com.example.domain.repository.GradeRepository
import com.example.domain.repository.HomeworkRepository
import com.example.domain.repository.NotificationRepository
import com.example.domain.repository.ParentFinancialProfile
import com.example.domain.repository.PricingRepository
import com.example.domain.repository.PushHomeworkInput
import com.example.domain.repository.ReleveRepository
import com.example.domain.repository.RollCallEntry
import com.example.domain.repository.RoutingRepository
import com.example.domain.repository.StorageRepository
import com.example.domain.repository.SubmitExpenseInput
import com.example.domain.repository.SubjectRepository
import com.example.domain.repository.UpdateClassInput
import com.example.domain.repository.UpdatePersonnelInput
import com.example.domain.repository.UpdateSubjectInput
import com.example.domain.repository.WorkflowRepository
import com.example.domain.model.GeoPoint
import com.example.infrastructure.routing.OsrmClient
import com.example.infrastructure.routing.TspSolver
import com.example.infrastructure.room.AcademicClassDao
import com.example.infrastructure.room.AcademicClassEntity
import com.example.infrastructure.room.AssessmentDao
import com.example.infrastructure.room.AssessmentEntity
import com.example.infrastructure.room.AttendanceDao
import com.example.infrastructure.room.AttendanceEntity
import com.example.infrastructure.room.AuditLogDao
import com.example.infrastructure.room.AuditLogEntity
import com.example.infrastructure.room.ClassSubjectDao
import com.example.infrastructure.room.ClassSubjectEntity
import com.example.infrastructure.room.DepartmentDao
import com.example.infrastructure.room.DepartmentEntity
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.room.ExpenseDao
import com.example.infrastructure.room.ExpenseEntity
import com.example.infrastructure.room.HomeworkDao
import com.example.infrastructure.room.HomeworkEntity
import com.example.infrastructure.room.InstallmentEntity
import com.example.infrastructure.room.LedgerEntryEntity
import com.example.infrastructure.room.LocalMappers
import com.example.infrastructure.room.NotificationDao
import com.example.infrastructure.room.NotificationEntity
import com.example.infrastructure.room.ParentDao
import com.example.infrastructure.room.ParentEntity
import com.example.infrastructure.room.PaymentDao
import com.example.infrastructure.room.PaymentEntity
import com.example.infrastructure.room.PersonnelDao
import com.example.infrastructure.room.PersonnelEntity
import com.example.infrastructure.room.PricingConfigDao
import com.example.infrastructure.room.PricingConfigEntity
import com.example.infrastructure.room.PricingDiscountEntity
import com.example.infrastructure.room.ReleveEntryDao
import com.example.infrastructure.room.ReleveEntryEntity
import com.example.infrastructure.room.StudentDao
import com.example.infrastructure.room.StudentEntity
import com.example.infrastructure.room.SubjectDao
import com.example.infrastructure.room.SubjectEntity
import com.example.infrastructure.room.TransportPricingEntity
import com.example.infrastructure.room.TripLogDao
import com.example.infrastructure.room.TripLogEntity
import com.example.infrastructure.room.VehicleDao
import com.example.infrastructure.room.VehicleEntity
import com.example.infrastructure.room.RoutingStopDao
import com.example.infrastructure.room.RoutingStopEntity
import com.example.infrastructure.room.WorkflowRunDao
import com.example.infrastructure.room.WorkflowRunEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ─── Expense Repository ─────────────────────────────────────────────────────

@Singleton
class LocalExpenseRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val expenseDao: ExpenseDao,
    private val auditDao: AuditLogDao,
) : ExpenseRepository {

    override fun observe(): Flow<List<Expense>> =
        expenseDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByStatus(status: String): Flow<List<Expense>> =
        expenseDao.observeByStatus(status).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Expense?> =
        expenseDao.observeAll().map { rows -> rows.firstOrNull { it.id == id }?.let { e -> LocalMappers.run { e.toDomain() } } }

    override suspend fun submit(input: SubmitExpenseInput, actorId: String, actorName: String): Result<Expense> {
        val now = Instant.now().toString()
        val year = LocalDate.now().year
        val seq = (expenseDao.countPending() + 1).toString().padStart(3, '0')
        val entity = ExpenseEntity(
            id = "exp-${UUID.randomUUID()}", tenantId = auditContext.tenantId(),
            requestCode = "EXP-$year-$seq", title = input.title, description = input.description,
            amount = input.amount, category = input.category, payee = input.payee,
            status = "submitted", submittedBy = actorId, submittedByName = actorName,
            submittedAt = now, approvedBy = null, approvedAt = null,
            disbursedAt = null, settledAt = null, proofUrl = null,
            urgency = input.urgency, anomalyScore = 0.0, notes = null,
            createdAt = now, updatedAt = now,
        )
        expenseDao.upsert(entity)
        auditDao.upsert(auditContext.auditLog("expense.submit", "expense", entity.id, actorId, actorName))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    override suspend fun approve(id: String, note: String, actorId: String, actorName: String): Result<Expense> {
        val existing = expenseDao.getById(id) ?: return Result.Err(Errors.notFound("Expense $id not found"))
        // TIER 4 FIX — enforce the canonical no-self-approval rule (plan §08;
        // desktop expense-ops.ts + SQL 0008 both enforce it).
        if (existing.submittedBy == actorId) {
            auditDao.upsert(auditContext.auditLog("expense.approve.blocked", "expense", id, actorId, actorName))
            return Result.Err(Errors.forbidden(
                "Un demandeur ne peut pas approuver sa propre dépense (règle d'auto-approbation)",
            ))
        }
        val updated = existing.copy(status = "approved", approvedBy = actorId, approvedAt = Instant.now().toString(), notes = note)
        expenseDao.update(updated)
        auditDao.upsert(auditContext.auditLog("expense.approve", "expense", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun reject(id: String, reason: String, actorId: String, actorName: String): Result<Expense> {
        val existing = expenseDao.getById(id) ?: return Result.Err(Errors.notFound("Expense $id not found"))
        // TIER 4 FIX — the no-self-approval rule applies to reject too.
        if (existing.submittedBy == actorId) {
            return Result.Err(Errors.forbidden(
                "Un demandeur ne peut pas rejeter sa propre dépense (règle d'auto-approbation)",
            ))
        }
        val updated = existing.copy(status = "rejected", approvedBy = actorId, approvedAt = Instant.now().toString(), notes = reason)
        expenseDao.update(updated)
        auditDao.upsert(auditContext.auditLog("expense.reject", "expense", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun disburse(id: String, actorId: String, actorName: String): Result<Expense> {
        val existing = expenseDao.getById(id) ?: return Result.Err(Errors.notFound("Expense $id not found"))
        val updated = existing.copy(status = "disbursed", disbursedAt = Instant.now().toString())
        expenseDao.update(updated)
        auditDao.upsert(auditContext.auditLog("expense.disburse", "expense", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun settleProof(id: String, proofPath: String, finalAmount: Long, actorId: String, actorName: String): Result<Expense> {
        val existing = expenseDao.getById(id) ?: return Result.Err(Errors.notFound("Expense $id not found"))
        // TIER 3 R18 FIX: previously `finalAmount` was silently dropped —
        // the `copy()` call didn't include it because the column didn't exist
        // on `ExpenseEntity`. Now that the column exists (migration v5→v6),
        // the final amount confirmed by the proof scan is persisted and
        // surfaces in the domain object so the desktop's expense report
        // can show "Requested: 5,000 DZD — Actual: 4,820 DZD".
        val updated = existing.copy(
            status = "settled",
            proofUrl = proofPath,
            settledAt = Instant.now().toString(),
            finalSpentAmount = finalAmount,
        )
        expenseDao.update(updated)
        auditDao.upsert(auditContext.auditLog("expense.settle", "expense", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }
}
