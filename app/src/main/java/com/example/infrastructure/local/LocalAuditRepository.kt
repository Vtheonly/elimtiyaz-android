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

// ─── Audit Repository ───────────────────────────────────────────────────────

@Singleton
class LocalAuditRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val auditDao: AuditLogDao,
) : AuditRepository {

    override fun observe(limit: Int): Flow<List<AuditLog>> =
        auditDao.observeRecent().map { rows -> rows.map { LocalMappers.run { it.toDomain() } }.take(limit) }

    override fun observeByEntity(entityType: String, entityId: String): Flow<List<AuditLog>> =
        auditDao.observeRecent().map { rows ->
            rows.filter { it.entityType == entityType && it.entityId == entityId }.map { LocalMappers.run { it.toDomain() } }
        }

    override suspend fun query(filter: AuditFilter): Result<List<AuditLog>> {
        // TIER 3 R19 FIX: previously `query()` always returned `emptyList()`,
        // making the audit log unsearchable. Now it actually filters by the
        // criteria the caller specified. The DAO's `observeRecent()` returns
        // the most recent 200 rows; we filter in-memory because the audit_logs
        // table is small (≤200 rows per the LIMIT in observeRecent) and a
        // dynamic SQL query would require either @RawQuery or a separate
        // @Query per filter combination.
        val rows = auditDao.observeRecent().first()
        val filtered = rows.asSequence()
            .filter { filter.action == null || it.action == filter.action }
            .filter { filter.entityType == null || it.entityType == filter.entityType }
            .filter { filter.entityId == null || it.entityId == filter.entityId }
            .filter { filter.actorId == null || it.actorId == filter.actorId }
            .filter { filter.from == null || it.createdAt >= filter.from }
            .filter { filter.to == null || it.createdAt <= filter.to }
            .drop(filter.offset)
            .take(filter.limit)
            .map { LocalMappers.run { it.toDomain() } }
            .toList()
        return Result.Ok(filtered)
    }

    override suspend fun log(input: AuditLogInput): Result<AuditLog> {
        // TIER 3 R19 FIX: previously `actorId` was hardcoded to "system".
        // Now we honor the caller-provided actor fields, falling back to
        // "system" only when the caller omits them. This makes the audit
        // trail useful for accountability — every action is attributed to
        // the real logged-in user, not the system.
        val entity = AuditLogEntity(
            id = "aud-${UUID.randomUUID()}", tenantId = auditContext.tenantId(),
            action = input.action, entityType = input.entityType, entityId = input.entityId,
            actorId = input.actorId ?: "system",
            actorName = input.actorName ?: "Système",
            actorRole = input.actorRole,
            beforeJson = input.beforeJson, afterJson = input.afterJson,
            note = input.note, createdAt = Instant.now().toString(),
        )
        auditDao.upsert(entity)
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }
}
