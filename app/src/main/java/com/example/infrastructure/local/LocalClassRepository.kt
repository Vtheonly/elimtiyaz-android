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

// ─── Class Repository ───────────────────────────────────────────────────────

@Singleton
class LocalClassRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val classDao: AcademicClassDao,
    private val studentDao: StudentDao,
    private val auditDao: AuditLogDao,
) : ClassRepository {

    override fun observe(): Flow<List<AcademicClass>> =
        classDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain(studentDao.countActive()) } } }

    override fun observeByLevel(level: String): Flow<List<AcademicClass>> =
        classDao.observeAll().map { rows -> rows.filter { it.level == level }.map { LocalMappers.run { it.toDomain(0) } } }

    override fun observeById(id: String): Flow<AcademicClass?> =
        classDao.observeById(id).map { it?.let { e -> LocalMappers.run { e.toDomain(0) } } }

    override suspend fun createClass(input: CreateClassInput, actorId: String, actorName: String): Result<AcademicClass> {
        val now = Instant.now().toString()
        val code = "CLS-${input.level.uppercase()}-${UUID.randomUUID().toString().takeLast(4).uppercase()}"
        val entity = AcademicClassEntity(
            id = "cls-${UUID.randomUUID()}", tenantId = auditContext.tenantId(), code = code,
            name = input.name, level = input.level, gradeYear = input.gradeYear,
            gradeLevel = input.level, section = null, room = input.room, capacity = input.capacity,
            homeroomTeacherId = input.homeroomTeacherId, homeroomTeacherName = null,
            academicYear = input.academicYear, isActive = true, createdAt = now, updatedAt = now,
        )
        classDao.upsert(entity)
        auditDao.upsert(auditContext.auditLog("class.create", "class", entity.id, actorId, actorName))
        return Result.Ok(LocalMappers.run { entity.toDomain(0) })
    }

    override suspend fun updateClass(id: String, input: UpdateClassInput, actorId: String, actorName: String): Result<AcademicClass> {
        val existing = classDao.getById(id) ?: return Result.Err(Errors.notFound("Class $id not found"))
        val updated = existing.copy(
            name = input.name ?: existing.name,
            room = input.room ?: existing.room,
            capacity = input.capacity ?: existing.capacity,
            homeroomTeacherId = input.homeroomTeacherId ?: existing.homeroomTeacherId,
            updatedAt = Instant.now().toString(),
        )
        classDao.update(updated)
        auditDao.upsert(auditContext.auditLog("class.update", "class", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain(0) })
    }

    override suspend fun deleteClass(id: String, actorId: String, actorName: String): Result<Unit> {
        classDao.deleteById(id)
        auditDao.upsert(auditContext.auditLog("class.delete", "class", id, actorId, actorName))
        return Result.Ok(Unit)
    }
}
