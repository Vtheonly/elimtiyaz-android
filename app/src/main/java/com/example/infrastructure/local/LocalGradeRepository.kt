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

class LocalGradeRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val assessmentDao: AssessmentDao,
    private val auditDao: AuditLogDao,
    // TIER 4 FIX — subject lookup so entered assessments carry the canonical
    // isExtracurricular flag (drives the GPA exclusion rule).
    private val subjectDao: com.example.infrastructure.room.SubjectDao,
    // VAULT §06.02 — grade entries must reach the shared backend (and thus
    // the Student Web Portal's Academic Hub). The dispatcher upserts into the
    // canonical `assessments` table written by the desktop grade-entry flow.
    private val syncSupport: com.example.infrastructure.sync.SyncSupport? = null,
) : GradeRepository {

    override fun observeForStudent(studentId: String, term: String, academicYear: String): Flow<List<Assessment>> =
        assessmentDao.observeByStudentTerm(studentId, term, academicYear).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    // FIX (ignored parameter): subjectId was dropped — the flow returned every
    // subject's assessments for the class. Callers (ClassDetail "Notes" tab,
    // the gradebook) believed they were scoped to one subject.
    override fun observeForClass(classId: String, subjectId: String, term: String, academicYear: String): Flow<List<Assessment>> =
        assessmentDao.observeByClassTerm(classId, term, academicYear).map { rows ->
            rows.filter { it.subjectId == subjectId }.map { LocalMappers.run { it.toDomain() } }
        }

    override fun observeForClass(classId: String, term: String, academicYear: String): Flow<List<Assessment>> =
        assessmentDao.observeByClassTerm(classId, term, academicYear).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    // Vault §04.07 / §06.05 — Student Academic History (all years, all terms).
    override fun observeAllForStudent(studentId: String): Flow<List<Assessment>> =
        assessmentDao.observeByStudent(studentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override suspend fun enterGrade(input: EnterGradeInput, actorId: String, actorName: String): Result<Assessment> {
        val now = Instant.now().toString()
        // Vault §06.02 (iteration 2) — read the SUBJECT's per-COMPONENT
        // coefficients (D1/D2/Examen) to compute the canonical subject
        // average with the weights the admin configured on the subject.
        // The previous build hard-coded the (D1 + D2 + 2×Ex) / 4 recipe;
        // the new recipe is (D1×c1 + D2×c2 + Ex×c3) / (c1 + c2 + c3) and
        // the three coefs are snapshotted onto the assessment row so past
        // years stay immutable when the subject's coefs are later edited.
        val subject = subjectDao.getById(input.subjectId)
        val subjectIsExtracurricular = subject?.isExtracurricular ?: false
        val coefD1 = subject?.coefficientDevoir1 ?: 1.0
        val coefD2 = subject?.coefficientDevoir2 ?: 1.0
        val coefEx = subject?.coefficientExamen ?: 2.0
        // CANONICAL — the subject average is only computable when all three
        // marks are present (matches the SQL trigger).
        val subjectAvg = com.example.core.computeSubjectAverage(
            input.devoir1, input.devoir2, input.examen, coefD1, coefD2, coefEx,
        )
        val existing = assessmentDao.getByStudentSubjectTerm(input.studentId, input.subjectId, input.term, input.academicYear)
        val entity = (existing ?: AssessmentEntity(
            id = "asm-${UUID.randomUUID()}", tenantId = auditContext.tenantId(),
            studentId = input.studentId, subjectId = input.subjectId, classId = input.classId,
            term = input.term, academicYear = input.academicYear,
            devoir1 = null, devoir2 = null, examen = null, coefficient = input.coefficient,
            isExtracurricular = subjectIsExtracurricular,
            subjectAverage = null, enteredBy = actorId, enteredAt = now,
            // Vault §06.02 — snapshot the SUBJECT's per-component coefs onto
            // the new assessment row (defaults preserved for legacy paths).
            coefficientDevoir1 = coefD1, coefficientDevoir2 = coefD2, coefficientExamen = coefEx,
        )).copy(
            devoir1 = input.devoir1, devoir2 = input.devoir2, examen = input.examen,
            coefficient = input.coefficient, subjectAverage = subjectAvg,
            isExtracurricular = subjectIsExtracurricular,
            enteredBy = actorId, enteredAt = now,
            // Vault §06.02 — refresh the per-component coef snapshot on
            // every grade edit so it always reflects the live subject config.
            coefficientDevoir1 = coefD1, coefficientDevoir2 = coefD2, coefficientExamen = coefEx,
        )
        assessmentDao.upsert(entity)
        // VAULT §06.02 — enqueue the canonical row for the Supabase push
        // (dispatcher writes the SAME assessments table the desktop app and
        // the web portal read from).
        syncSupport?.enqueueOnly(
            entity = "grade",
            operation = "upsert",
            payload = buildGradeSyncPayload(entity),
            isMock = false,
            sourceScreen = "GradeEntryScreen",
        )
        auditDao.upsert(auditContext.auditLog("grade.enter", "assessment", entity.id, actorId, actorName))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }
}

/** Canonical assessments-row payload for the sync dispatcher (§06.02). */
private fun buildGradeSyncPayload(e: com.example.infrastructure.room.AssessmentEntity): String =
    kotlinx.serialization.json.buildJsonObject {
        put("id", e.id)
        put("tenantId", e.tenantId)
        put("studentId", e.studentId)
        put("subjectId", e.subjectId)
        put("classId", e.classId)
        put("term", e.term)
        put("academicYear", e.academicYear)
        e.devoir1?.let { put("devoir1", it) }
        e.devoir2?.let { put("devoir2", it) }
        e.examen?.let { put("examen", it) }
        put("coefficient", e.coefficient)
        put("isExtracurricular", e.isExtracurricular)
        e.subjectAverage?.let { put("subjectAverage", it) }
        put("enteredBy", e.enteredBy)
        put("enteredAt", e.enteredAt)
        put("coefficientDevoir1", e.coefficientDevoir1)
        put("coefficientDevoir2", e.coefficientDevoir2)
        put("coefficientExamen", e.coefficientExamen)
    }.toString()
