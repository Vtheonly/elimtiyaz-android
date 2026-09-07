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

// ─── Subject Repository ─────────────────────────────────────────────────────

@Singleton
class LocalSubjectRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val subjectDao: SubjectDao,
    private val classSubjectDao: ClassSubjectDao,
    private val assessmentDao: AssessmentDao,
    private val auditDao: AuditLogDao,
) : SubjectRepository {

    override fun observe(): Flow<List<Subject>> =
        subjectDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByLevel(level: String): Flow<List<Subject>> =
        // FIX: actually filter by level — previously returned ALL subjects.
        // Subjects scoped "all" apply to every level.
        subjectDao.observeAll().map { rows ->
            rows.filter { it.level == "all" || it.level == level }
                .map { LocalMappers.run { it.toDomain() } }
        }

    override fun observeByClass(classId: String): Flow<List<Subject>> =
        // FIX: the Room schema now HAS a per-class assignment table
        // (`class_subjects`, migration v9). When a class has explicit
        // assignments, return exactly those subjects; otherwise fall back to
        // all active subjects so the pickers (grade entry, homework push)
        // never regress to an empty list.
        combine(
            subjectDao.observeAll(),
            classSubjectDao.observeByClass(classId),
        ) { subjects, assignments ->
            if (assignments.isEmpty()) {
                subjects.filter { it.isActive }.map { LocalMappers.run { it.toDomain() } }
            } else {
                assignments.mapNotNull { assignment ->
                    subjects.firstOrNull { it.id == assignment.subjectId }?.let { subject ->
                        LocalMappers.run { subject.toDomain() }
                    }
                }
            }
        }

    override suspend fun createSubject(input: CreateSubjectInput, actorId: String, actorName: String): Result<Subject> {
        // FIX: persist the level + passing grade from the input — previously
        // the level was silently dropped.
        val entity = SubjectEntity(
            id = "sub-${UUID.randomUUID()}", tenantId = auditContext.tenantId(),
            code = input.code, name = input.name,
            category = if (input.isExtracurricular) "extracurricular" else "academic",
            coefficient = input.coefficient, weeklyHours = 0.0,
            isExtracurricular = input.isExtracurricular, isActive = true,
            level = input.level.ifBlank { "all" },
            passingGrade = input.passingGrade,
            // Vault §06.02 (iteration 2) — persist the per-COMPONENT
            // coefficients. Defaults (1, 1, 2) preserve the historical
            // recipe when the create dialog leaves them unset.
            coefficientDevoir1 = input.coefficientDevoir1,
            coefficientDevoir2 = input.coefficientDevoir2,
            coefficientExamen = input.coefficientExamen,
        )
        subjectDao.upsertAll(listOf(entity))
        // Vault §05.05/§05.06 — subject configuration is admin-controlled and
        // audited (create included).
        auditDao.upsert(
            auditContext.auditLog(
                action = com.example.core.AuditActions.SUBJECT_CREATE,
                entityType = "subject",
                entityId = entity.id,
                actorId = actorId,
                actorName = actorName,
                after = """{"code":"${entity.code}","coefficient":${entity.coefficient},"isExtracurricular":${entity.isExtracurricular},"coefD1":${entity.coefficientDevoir1},"coefD2":${entity.coefficientDevoir2},"coefEx":${entity.coefficientExamen}}""",
            )
        )
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    // FIX ("Not implemented"): updateSubject previously always failed.
    // Vault §05.06 — coefficient changes are AUDITED and trigger an automatic
    // GPA recompute for affected students. Android computes GPAs on read
    // (`computeOverallGpa` over assessment rows), so refreshing the
    // coefficient snapshot on the CURRENT academic year's rows IS the
    // recompute; archived years are append-only and never touched.
    //
    // ITERATION-2 (vault §06.02): the recompute now ALSO re-snapshots the
    // per-COMPONENT coefficients (D1/D2/Examen) onto each assessment row and
    // re-derives `subjectAverage` with the new weights — the SUBJECT-level
    // coefficient alone is no longer enough to express the full grading
    // recipe. Past-year rows stay immutable (append-only rule).
    override suspend fun updateSubject(id: String, input: UpdateSubjectInput, actorId: String, actorName: String): Result<Subject> {
        val existing = subjectDao.getById(id) ?: return Result.Err(Errors.notFound("Subject $id not found"))
        val subjectCoefChanged = input.coefficient != null && input.coefficient != existing.coefficient
        val coefD1Changed = input.coefficientDevoir1 != null && input.coefficientDevoir1 != existing.coefficientDevoir1
        val coefD2Changed = input.coefficientDevoir2 != null && input.coefficientDevoir2 != existing.coefficientDevoir2
        val coefExChanged = input.coefficientExamen != null && input.coefficientExamen != existing.coefficientExamen
        val anyCoefChanged = subjectCoefChanged || coefD1Changed || coefD2Changed || coefExChanged

        val updated = existing.copy(
            name = input.name ?: existing.name,
            coefficient = input.coefficient ?: existing.coefficient,
            passingGrade = input.passingGrade ?: existing.passingGrade,
            coefficientDevoir1 = input.coefficientDevoir1 ?: existing.coefficientDevoir1,
            coefficientDevoir2 = input.coefficientDevoir2 ?: existing.coefficientDevoir2,
            coefficientExamen = input.coefficientExamen ?: existing.coefficientExamen,
        )
        subjectDao.upsert(updated)

        // Vault §05.06 — audit the coefficient change (see 12. Security and
        // Audit: every mutation is traceable).
        auditDao.upsert(
            auditContext.auditLog(
                action = com.example.core.AuditActions.SUBJECT_UPDATE,
                entityType = "subject",
                entityId = id,
                actorId = actorId,
                actorName = actorName,
                after = """{"coefficient":{"from":${existing.coefficient},"to":${updated.coefficient}},"coefD1":{"from":${existing.coefficientDevoir1},"to":${updated.coefficientDevoir1}},"coefD2":{"from":${existing.coefficientDevoir2},"to":${updated.coefficientDevoir2}},"coefEx":{"from":${existing.coefficientExamen},"to":${updated.coefficientExamen}},"name":"${updated.name}"}""",
            )
        )

        // Vault §05.06 + §06.02 — automatic GPA recompute for affected
        // students: refresh the coefficient snapshot on the CURRENT year's
        // assessment rows only (past years are append-only). GPAs are derived
        // on read, so the next read reflects the new coefficients
        // immediately. The recompute re-derives `subjectAverage` inline
        // using the new per-component weights.
        if (anyCoefChanged) {
            val now = java.time.LocalDate.now()
            val currentYear =
                if (now.monthValue >= 9) "${now.year}-${now.year + 1}" else "${now.year - 1}-${now.year}"
            // SUBJECT-level coef refresh (single UPDATE, no per-row recompute).
            assessmentDao.updateCoefficientForSubjectYear(id, updated.coefficient, currentYear)
            // Per-COMPONENT coef refresh + subjectAverage recompute. Read +
            // rewrite in Kotlin because the average formula can't be expressed
            // as a single SQL UPDATE.
            val rows = assessmentDao.listBySubjectAndYear(id, currentYear)
            if (rows.isNotEmpty()) {
                val recomputed = rows.map { row ->
                    val newAvg = com.example.core.computeSubjectAverage(
                        row.devoir1, row.devoir2, row.examen,
                        updated.coefficientDevoir1, updated.coefficientDevoir2, updated.coefficientExamen,
                    )
                    row.copy(
                        coefficient = updated.coefficient,
                        coefficientDevoir1 = updated.coefficientDevoir1,
                        coefficientDevoir2 = updated.coefficientDevoir2,
                        coefficientExamen = updated.coefficientExamen,
                        subjectAverage = newAvg,
                    )
                }
                assessmentDao.upsertAll(recomputed)
            }
        }
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    // FIX (silent no-op): archiveSubject previously returned Ok without doing
    // anything — the UI showed success but the subject never disappeared.
    override suspend fun archiveSubject(id: String, actorId: String, actorName: String): Result<Unit> {
        val existing = subjectDao.getById(id) ?: return Result.Err(Errors.notFound("Subject $id not found"))
        subjectDao.upsert(existing.copy(isActive = false))
        return Result.Ok(Unit)
    }
    // FIX (silent no-op): assignSubjectToClass previously returned Ok without
    // persisting anything — the caller saw success but no assignment existed.
    // Now a real `class_subjects` row is written (idempotent per
    // class+subject pair) and the action is audit-logged.
    override suspend fun assignSubjectToClass(
        classId: String,
        subjectId: String,
        teacherId: String?,
        weeklyHours: Int,
        coefficient: Double,
        actorId: String,
        actorName: String,
    ): Result<Unit> {
        if (subjectDao.getById(subjectId) == null) {
            return Result.Err(Errors.notFound("Matière $subjectId introuvable"))
        }
        val existing = classSubjectDao.listByClass(classId)
            .firstOrNull { it.subjectId == subjectId }
        val entity = (existing ?: ClassSubjectEntity(
            id = "cls-sub-${UUID.randomUUID()}",
            tenantId = auditContext.tenantId(),
            classId = classId,
            subjectId = subjectId,
            teacherId = teacherId,
            weeklyHours = weeklyHours,
            coefficient = coefficient,
            createdAt = Instant.now().toString(),
        )).copy(
            teacherId = teacherId ?: existing?.teacherId,
            weeklyHours = weeklyHours,
            coefficient = coefficient,
        )
        classSubjectDao.upsert(entity)
        auditDao.upsert(
            auditContext.auditLog("subject.assignToClass", "class_subject", entity.id, actorId, actorName,
                after = """{"classId":"$classId","subjectId":"$subjectId","weeklyHours":$weeklyHours,"coefficient":$coefficient}"""),
        )
        return Result.Ok(Unit)
    }
}
