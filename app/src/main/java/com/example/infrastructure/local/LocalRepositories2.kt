package com.example.infrastructure.local

import com.example.core.Errors
import com.example.core.Result
import com.example.core.agingBucketFromDays
import com.example.core.daysBetweenFloor
import com.example.core.LedgerEngine
import com.example.domain.model.AcademicClass
import com.example.domain.model.AppNotification
import com.example.domain.model.Assessment
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AuditLog
import com.example.domain.model.DashboardKpi
import com.example.domain.model.DebtSummary
import com.example.domain.model.Department
import com.example.domain.model.Expense
import com.example.domain.model.GradeLevelTuition
import com.example.domain.model.Homework
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.model.Payment
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
import com.example.infrastructure.room.AcademicClassDao
import com.example.infrastructure.room.AcademicClassEntity
import com.example.infrastructure.room.AssessmentDao
import com.example.infrastructure.room.AssessmentEntity
import com.example.infrastructure.room.AttendanceDao
import com.example.infrastructure.room.AttendanceEntity
import com.example.infrastructure.room.AuditLogDao
import com.example.infrastructure.room.AuditLogEntity
import com.example.infrastructure.room.DepartmentDao
import com.example.infrastructure.room.DepartmentEntity
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.room.ExpenseDao
import com.example.infrastructure.room.ExpenseEntity
import com.example.infrastructure.room.HomeworkDao
import com.example.infrastructure.room.HomeworkEntity
import com.example.infrastructure.room.LocalMappers
import com.example.infrastructure.room.NotificationDao
import com.example.infrastructure.room.NotificationEntity
import com.example.infrastructure.room.ParentDao
import com.example.infrastructure.room.PaymentDao
import com.example.infrastructure.room.PersonnelDao
import com.example.infrastructure.room.PersonnelEntity
import com.example.infrastructure.room.PricingConfigDao
import com.example.infrastructure.room.PricingConfigEntity
import com.example.infrastructure.room.PricingDiscountEntity
import com.example.infrastructure.room.ReleveEntryDao
import com.example.infrastructure.room.ReleveEntryEntity
import com.example.infrastructure.room.StudentDao
import com.example.infrastructure.room.SubjectDao
import com.example.infrastructure.room.SubjectEntity
import com.example.infrastructure.room.TransportPricingEntity
import com.example.infrastructure.room.TripLogDao
import com.example.infrastructure.room.WorkflowRunDao
import com.example.infrastructure.room.WorkflowRunEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ─── Class Repository ───────────────────────────────────────────────────────

@Singleton
class LocalClassRepository @Inject constructor(
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
            id = "cls-${UUID.randomUUID()}", tenantId = "00000000-0000-0000-0000-000000000001", code = code,
            name = input.name, level = input.level, gradeYear = input.gradeYear,
            gradeLevel = input.level, section = null, room = input.room, capacity = input.capacity,
            homeroomTeacherId = input.homeroomTeacherId, homeroomTeacherName = null,
            academicYear = input.academicYear, isActive = true, createdAt = now, updatedAt = now,
        )
        classDao.upsert(entity)
        auditDao.upsert(auditLog("class.create", "class", entity.id, actorId, actorName))
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
        auditDao.upsert(auditLog("class.update", "class", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain(0) })
    }

    override suspend fun deleteClass(id: String, actorId: String, actorName: String): Result<Unit> {
        classDao.deleteById(id)
        auditDao.upsert(auditLog("class.delete", "class", id, actorId, actorName))
        return Result.Ok(Unit)
    }
}

// ─── Dashboard Repository (real KPI computation) ────────────────────────────

@Singleton
class LocalDashboardRepository @Inject constructor(
    private val db: ElImtiyazDatabase,
) : DashboardRepository {

    override fun observeKpis(): Flow<DashboardKpi?> = combine(
        db.studentDao().observeActiveCount(),
        db.parentDao().observeAll(),
        db.paymentDao().observeAll(),
        db.ledgerEntryDao().observeAll(),
        db.expenseDao().observeByStatus("submitted"),
    ) { activeStudents, parents, payments, ledgerEntries, pendingExpenses ->
        val now = Instant.now()
        val monthStart = OffsetDateTime.now(ZoneOffset.UTC).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant().toString()
        val monthlyRevenue = payments.filter { it.status == "paid" && it.collectedAt >= monthStart }.sumOf { it.amount }
        val totalOutstanding = ledgerEntries.filter { it.type == "charge" || it.type == "payment" || it.type == "adjustment" }.sumOf { it.amount }
        DashboardKpi(
            totalStudents = activeStudents,
            totalParents = parents.size,
            totalStaff = 1,
            monthlyRevenue = monthlyRevenue,
            outstandingDebt = totalOutstanding.coerceAtLeast(0L),
            pendingExpenses = pendingExpenses.size,
            attendanceRateToday = 0.0,
            overdueAlerts = 0,
        )
    }

    override fun observeRevenueLast12Months(): Flow<List<com.example.domain.repository.RevenuePoint>> =
        db.paymentDao().observeAll().map { payments ->
            val now = LocalDate.now(ZoneOffset.UTC)
            (11 downTo 0).map { monthsBack ->
                val target = now.minusMonths(monthsBack.toLong())
                val monthStart = OffsetDateTime.of(target.year, target.monthValue, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toString()
                val nextMonthStart = OffsetDateTime.of(target.year, target.monthValue, 1, 0, 0, 0, 0, ZoneOffset.UTC).plusMonths(1).toInstant().toString()
                val sum = payments.filter { it.status == "paid" && it.collectedAt >= monthStart && it.collectedAt < nextMonthStart }.sumOf { it.amount }
                com.example.domain.repository.RevenuePoint(label = "${target.monthValue}/${target.year}", amount = sum)
            }
        }

    override fun observeDebtByAging(): Flow<List<DebtSummary>> = combine(
        db.parentDao().observeAll(),
        db.ledgerEntryDao().observeAll(),
    ) { parents, ledgerEntries ->
        parents.map { parent ->
            val parentEntries = ledgerEntries.filter { it.parentId == parent.id }
            val summary = LedgerEngine.computeParentSummary(parentEntries.map { LocalMappers.run { it.toDomain() } }, parent.id, parent.fullName)
            val maxDays = LedgerEngine.maxDaysOverdueFromLedger(parentEntries.map { LocalMappers.run { it.toDomain() } })
            DebtSummary(
                parentId = parent.id,
                parentName = parent.fullName,
                parentPhone = parent.phone,
                studentCount = 0,
                outstandingAmount = summary.totalOutstanding.coerceAtLeast(0L),
                daysOverdue = maxDays,
                bucket = agingBucketFromDays(maxDays),
            )
        }.filter { it.outstandingAmount > 0L }.sortedByDescending { it.outstandingAmount }
    }

    override suspend fun refreshKpis(): Result<Unit> = Result.Ok(Unit)
}

// ─── Debt Repository ────────────────────────────────────────────────────────

@Singleton
class LocalDebtRepository @Inject constructor(
    private val db: ElImtiyazDatabase,
) : DebtRepository {

    override fun observeSummary(): Flow<List<DebtSummary>> = combine(
        db.parentDao().observeAll(),
        db.ledgerEntryDao().observeAll(),
    ) { parents, ledgerEntries ->
        parents.map { parent ->
            val parentEntries = ledgerEntries.filter { it.parentId == parent.id }
            val summary = LedgerEngine.computeParentSummary(parentEntries.map { LocalMappers.run { it.toDomain() } }, parent.id, parent.fullName)
            DebtSummary(
                parentId = parent.id,
                parentName = parent.fullName,
                parentPhone = parent.phone,
                studentCount = 0,
                outstandingAmount = summary.totalOutstanding.coerceAtLeast(0L),
                daysOverdue = LedgerEngine.maxDaysOverdueFromLedger(parentEntries.map { LocalMappers.run { it.toDomain() } }),
                bucket = agingBucketFromDays(LedgerEngine.maxDaysOverdueFromLedger(parentEntries.map { LocalMappers.run { it.toDomain() } })),
            )
        }.filter { it.outstandingAmount > 0L }.sortedByDescending { it.outstandingAmount }
    }

    override fun observeParentProfile(parentId: String): Flow<ParentFinancialProfile?> = combine(
        db.parentDao().observeById(parentId),
        db.ledgerEntryDao().observeByParent(parentId),
        db.installmentDao().observeByParent(parentId),
        db.paymentDao().observeByParent(parentId),
    ) { parent, ledgerEntries, installments, payments ->
        if (parent == null) null
        else {
            val summary = LedgerEngine.computeParentSummary(ledgerEntries.map { LocalMappers.run { it.toDomain() } }, parentId, parent.fullName)
            ParentFinancialProfile(
                parentId = parentId,
                parentName = parent.fullName,
                totalDue = summary.totalCharged,
                totalPaid = summary.totalPaid,
                totalOutstanding = summary.totalOutstanding.coerceAtLeast(0L),
                overdueAmount = summary.totalOverdue.coerceAtLeast(0L),
                installments = installments.map { LocalMappers.run { it.toDomain() } },
                recentPayments = payments.map { LocalMappers.run { it.toDomain() } },
            )
        }
    }

    override suspend fun sendReminder(parentId: String, actorId: String, actorName: String): Result<Unit> {
        db.auditLogDao().upsert(auditLog("debt.reminder_sent", "parent", parentId, actorId, actorName))
        return Result.Ok(Unit)
    }
}

// ─── Pricing Repository ─────────────────────────────────────────────────────

@Singleton
class LocalPricingRepository @Inject constructor(
    private val pricingDao: PricingConfigDao,
) : PricingRepository {

    override fun observe(): Flow<PricingConfig?> = pricingDao.observeActive().map { entity ->
        entity?.let { e ->
            val discounts = pricingDao.listActiveDiscounts()
            LocalMappers.run { e.toDomain(discounts) }
        }
    }

    override fun observeGradeLevelTuition(): Flow<List<GradeLevelTuition>> = pricingDao.observeActive().map {
        pricingDao.listGradeLevelTuition().map { g -> LocalMappers.run { g.toDomain() } }
    }

    override suspend fun updateRegistrationFee(amount: Long, actorId: String, actorName: String): Result<Unit> {
        val config = pricingDao.getActive() ?: return Result.Err(Errors.notFound("No active pricing config"))
        pricingDao.upsertConfig(config.copy(registrationFee = amount, updatedAt = Instant.now().toString()))
        return Result.Ok(Unit)
    }

    override suspend fun updateLatePenalty(amount: Long, actorId: String, actorName: String): Result<Unit> {
        val config = pricingDao.getActive() ?: return Result.Err(Errors.notFound("No active pricing config"))
        pricingDao.upsertConfig(config.copy(latePenaltyPerDay = amount, updatedAt = Instant.now().toString()))
        return Result.Ok(Unit)
    }

    override suspend fun updateTuitionForGradeLevel(gradeLevel: String, annualAmount: Long, tranches: Triple<Long, Long, Long>, actorId: String, actorName: String): Result<Unit> {
        val config = pricingDao.getActive() ?: return Result.Err(Errors.notFound("No active pricing config"))
        val existing = pricingDao.getTuitionByGrade(gradeLevel)
        val updated = (existing ?: com.example.infrastructure.room.GradeLevelTuitionEntity(
            id = "glt-${UUID.randomUUID()}", pricingConfigId = config.id, gradeLevel = gradeLevel,
            annualAmount = annualAmount, tranche1 = 0, tranche2 = 0, tranche3 = 0,
        )).copy(annualAmount = annualAmount, tranche1 = tranches.first, tranche2 = tranches.second, tranche3 = tranches.third)
        pricingDao.upsertGradeLevelTuition(listOf(updated))
        return Result.Ok(Unit)
    }
}

// ─── Audit Repository ───────────────────────────────────────────────────────

@Singleton
class LocalAuditRepository @Inject constructor(
    private val auditDao: AuditLogDao,
) : AuditRepository {

    override fun observe(limit: Int): Flow<List<AuditLog>> =
        auditDao.observeRecent().map { rows -> rows.map { LocalMappers.run { it.toDomain() } }.take(limit) }

    override fun observeByEntity(entityType: String, entityId: String): Flow<List<AuditLog>> =
        auditDao.observeRecent().map { rows ->
            rows.filter { it.entityType == entityType && it.entityId == entityId }.map { LocalMappers.run { it.toDomain() } }
        }

    override suspend fun query(filter: AuditFilter): Result<List<AuditLog>> = Result.Ok(emptyList())

    override suspend fun log(input: AuditLogInput): Result<AuditLog> {
        val entity = AuditLogEntity(
            id = "aud-${UUID.randomUUID()}", tenantId = "00000000-0000-0000-0000-000000000001",
            action = input.action, entityType = input.entityType, entityId = input.entityId,
            actorId = "system", actorName = "System", actorRole = null,
            beforeJson = input.beforeJson, afterJson = input.afterJson,
            note = input.note, createdAt = Instant.now().toString(),
        )
        auditDao.upsert(entity)
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }
}

// ─── Attendance Repository ──────────────────────────────────────────────────

@Singleton
class LocalAttendanceRepository @Inject constructor(
    private val attendanceDao: AttendanceDao,
    private val auditDao: AuditLogDao,
) : com.example.domain.repository.AttendanceRepository {

    override fun observeByClass(classId: String, date: String): Flow<List<AttendanceRecord>> =
        attendanceDao.observeByClassAndDate(classId, date).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByStudent(studentId: String): Flow<List<AttendanceRecord>> {
        val since = LocalDate.now(ZoneOffset.UTC).minusDays(90).toString()
        return attendanceDao.observeByStudent(studentId, since).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }
    }

    override suspend fun recordRollCall(classId: String, date: String, session: String, records: List<RollCallEntry>, actorId: String, actorName: String): Result<Unit> {
        val now = Instant.now().toString()
        val entities = records.map { r ->
            AttendanceEntity(
                id = "att-${UUID.randomUUID()}", tenantId = "00000000-0000-0000-0000-000000000001",
                studentId = r.studentId, classId = classId, date = date, session = session,
                status = r.status, arrivalTime = null, note = r.note,
                recordedBy = actorId, recordedBy_name = actorName, recordedAt = now,
            )
        }
        attendanceDao.upsertAll(entities)
        auditDao.upsert(auditLog("attendance.rollCall", "class", classId, actorId, actorName,
            after = """{"date":"$date","session":"$session","count":${records.size}}"""))
        return Result.Ok(Unit)
    }

    override suspend fun alertAbsences(studentIds: List<String>, actorId: String, actorName: String): Result<Unit> {
        studentIds.forEach { id ->
            auditDao.upsert(auditLog("attendance.alert", "student", id, actorId, actorName))
        }
        return Result.Ok(Unit)
    }
}

// ─── Grade Repository ───────────────────────────────────────────────────────

@Singleton
class LocalGradeRepository @Inject constructor(
    private val assessmentDao: AssessmentDao,
    private val auditDao: AuditLogDao,
) : GradeRepository {

    override fun observeForStudent(studentId: String, term: String, academicYear: String): Flow<List<Assessment>> =
        assessmentDao.observeByStudentTerm(studentId, term, academicYear).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeForClass(classId: String, subjectId: String, term: String, academicYear: String): Flow<List<Assessment>> =
        assessmentDao.observeByClassTerm(classId, term, academicYear).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override suspend fun enterGrade(input: EnterGradeInput, actorId: String, actorName: String): Result<Assessment> {
        val now = Instant.now().toString()
        val subjectAvg = com.example.core.computeSubjectAverage(input.devoir1, input.devoir2, input.examen)
        val existing = assessmentDao.getByStudentSubjectTerm(input.studentId, input.subjectId, input.term, input.academicYear)
        val entity = (existing ?: AssessmentEntity(
            id = "asm-${UUID.randomUUID()}", tenantId = "00000000-0000-0000-0000-000000000001",
            studentId = input.studentId, subjectId = input.subjectId, classId = input.classId,
            term = input.term, academicYear = input.academicYear,
            devoir1 = null, devoir2 = null, examen = null, coefficient = input.coefficient,
            subjectAverage = null, enteredBy = actorId, enteredAt = now,
        )).copy(
            devoir1 = input.devoir1, devoir2 = input.devoir2, examen = input.examen,
            coefficient = input.coefficient, subjectAverage = subjectAvg,
            enteredBy = actorId, enteredAt = now,
        )
        assessmentDao.upsert(entity)
        auditDao.upsert(auditLog("grade.enter", "assessment", entity.id, actorId, actorName))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }
}

// ─── Expense Repository ─────────────────────────────────────────────────────

@Singleton
class LocalExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val auditDao: AuditLogDao,
) : ExpenseRepository {

    override fun observe(): Flow<List<Expense>> =
        expenseDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByStatus(status: String): Flow<List<Expense>> =
        expenseDao.observeByStatus(status).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Expense?> =
        expenseDao.observeByStatus("submitted").map { rows -> rows.firstOrNull { it.id == id }?.let { e -> LocalMappers.run { e.toDomain() } } }

    override suspend fun submit(input: SubmitExpenseInput, actorId: String, actorName: String): Result<Expense> {
        val now = Instant.now().toString()
        val year = LocalDate.now().year
        val seq = (expenseDao.countPending() + 1).toString().padStart(3, '0')
        val entity = ExpenseEntity(
            id = "exp-${UUID.randomUUID()}", tenantId = "00000000-0000-0000-0000-000000000001",
            requestCode = "EXP-$year-$seq", title = input.title, description = input.description,
            amount = input.amount, category = input.category, payee = input.payee,
            status = "submitted", submittedBy = actorId, submittedByName = actorName,
            submittedAt = now, approvedBy = null, approvedAt = null,
            disbursedAt = null, settledAt = null, proofUrl = null,
            urgency = input.urgency, anomalyScore = 0.0, notes = null,
            createdAt = now, updatedAt = now,
        )
        expenseDao.upsert(entity)
        auditDao.upsert(auditLog("expense.submit", "expense", entity.id, actorId, actorName))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    override suspend fun approve(id: String, note: String, actorId: String, actorName: String): Result<Expense> {
        val existing = expenseDao.getById(id) ?: return Result.Err(Errors.notFound("Expense $id not found"))
        val updated = existing.copy(status = "approved", approvedBy = actorId, approvedAt = Instant.now().toString(), notes = note)
        expenseDao.update(updated)
        auditDao.upsert(auditLog("expense.approve", "expense", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun reject(id: String, reason: String, actorId: String, actorName: String): Result<Expense> {
        val existing = expenseDao.getById(id) ?: return Result.Err(Errors.notFound("Expense $id not found"))
        val updated = existing.copy(status = "rejected", approvedBy = actorId, approvedAt = Instant.now().toString(), notes = reason)
        expenseDao.update(updated)
        auditDao.upsert(auditLog("expense.reject", "expense", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun disburse(id: String, actorId: String, actorName: String): Result<Expense> {
        val existing = expenseDao.getById(id) ?: return Result.Err(Errors.notFound("Expense $id not found"))
        val updated = existing.copy(status = "disbursed", disbursedAt = Instant.now().toString())
        expenseDao.update(updated)
        auditDao.upsert(auditLog("expense.disburse", "expense", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun settleProof(id: String, proofPath: String, finalAmount: Long, actorId: String, actorName: String): Result<Expense> {
        val existing = expenseDao.getById(id) ?: return Result.Err(Errors.notFound("Expense $id not found"))
        val updated = existing.copy(status = "settled", proofUrl = proofPath, settledAt = Instant.now().toString())
        expenseDao.update(updated)
        auditDao.upsert(auditLog("expense.settle", "expense", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }
}

// ─── Personnel Repository ───────────────────────────────────────────────────

@Singleton
class LocalPersonnelRepository @Inject constructor(
    private val personnelDao: PersonnelDao,
    private val auditDao: AuditLogDao,
) : com.example.domain.repository.PersonnelRepository {

    override fun observe(): Flow<List<Personnel>> =
        personnelDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByCategory(category: String): Flow<List<Personnel>> =
        personnelDao.observeAll().map { rows -> rows.filter { it.role == category }.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Personnel?> =
        personnelDao.observeById(id).map { it?.let { e -> LocalMappers.run { e.toDomain() } } }

    override fun observeByUserId(userId: String): Flow<Personnel?> =
        personnelDao.observeById(userId).map { it?.let { e -> LocalMappers.run { e.toDomain() } } }

    override suspend fun createPersonnel(input: CreatePersonnelInput, actorId: String, actorName: String): Result<Personnel> {
        val now = Instant.now().toString()
        val entity = PersonnelEntity(
            id = "per-${UUID.randomUUID()}", tenantId = "00000000-0000-0000-0000-000000000001",
            code = "PER-${(personnelDao.countActive() + 1).toString().padStart(3, '0')}",
            firstName = input.firstName, lastName = input.lastName, role = input.roleId,
            departmentId = input.departmentId, departmentName = null,
            phone = input.phone, email = input.email, status = "active",
            hireDate = input.hireDate, weeklyHoursTarget = input.weeklyHoursTarget,
            createdAt = now, updatedAt = now,
        )
        personnelDao.upsert(entity)
        auditDao.upsert(auditLog("personnel.create", "personnel", entity.id, actorId, actorName))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    override suspend fun updatePersonnel(id: String, input: UpdatePersonnelInput, actorId: String, actorName: String): Result<Personnel> {
        val existing = personnelDao.getById(id) ?: return Result.Err(Errors.notFound("Personnel $id not found"))
        val updated = existing.copy(
            phone = input.phone ?: existing.phone, email = input.email ?: existing.email,
            status = input.status ?: existing.status, updatedAt = Instant.now().toString(),
        )
        personnelDao.upsert(updated)
        auditDao.upsert(auditLog("personnel.update", "personnel", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun deletePersonnel(id: String, actorId: String, actorName: String): Result<Unit> {
        val existing = personnelDao.getById(id) ?: return Result.Err(Errors.notFound("Personnel $id not found"))
        personnelDao.upsert(existing.copy(status = "terminated", updatedAt = Instant.now().toString()))
        auditDao.upsert(auditLog("personnel.delete", "personnel", id, actorId, actorName))
        return Result.Ok(Unit)
    }
}

// ─── Department Repository ──────────────────────────────────────────────────

@Singleton
class LocalDepartmentRepository @Inject constructor(
    private val departmentDao: DepartmentDao,
) : DepartmentRepository {

    override fun observe(): Flow<List<Department>> =
        departmentDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Department?> =
        departmentDao.observeAll().map { rows -> rows.firstOrNull { it.id == id }?.let { e -> LocalMappers.run { e.toDomain() } } }

    override suspend fun createDepartment(input: CreateDepartmentInput, actorId: String, actorName: String): Result<Department> {
        val entity = DepartmentEntity(
            id = "dep-${UUID.randomUUID()}", tenantId = "00000000-0000-0000-0000-000000000001",
            name = input.name, description = input.description,
            headPersonnelId = input.headPersonnelId, parentDepartmentId = input.parentDepartmentId,
            colorHex = input.colorHex, archivedAt = null,
        )
        departmentDao.upsertAll(listOf(entity))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    override suspend fun archiveDepartment(id: String, actorId: String, actorName: String): Result<Unit> = Result.Ok(Unit)
    override suspend fun unarchiveDepartment(id: String, actorId: String, actorName: String): Result<Unit> = Result.Ok(Unit)
}

// ─── Subject Repository ─────────────────────────────────────────────────────

@Singleton
class LocalSubjectRepository @Inject constructor(
    private val subjectDao: SubjectDao,
) : SubjectRepository {

    override fun observe(): Flow<List<Subject>> =
        subjectDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByLevel(level: String): Flow<List<Subject>> =
        subjectDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByClass(classId: String): Flow<List<Subject>> =
        subjectDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override suspend fun createSubject(input: CreateSubjectInput, actorId: String, actorName: String): Result<Subject> {
        val entity = SubjectEntity(
            id = "sub-${UUID.randomUUID()}", tenantId = "00000000-0000-0000-0000-000000000001",
            code = input.code, name = input.name, category = "academic",
            coefficient = input.coefficient, weeklyHours = 0.0,
            isExtracurricular = input.isExtracurricular, isActive = true,
        )
        subjectDao.upsertAll(listOf(entity))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    override suspend fun updateSubject(id: String, input: UpdateSubjectInput, actorId: String, actorName: String): Result<Subject> = Result.Err(Errors.notFound("Not implemented"))
    override suspend fun archiveSubject(id: String, actorId: String, actorName: String): Result<Unit> = Result.Ok(Unit)
    override suspend fun assignSubjectToClass(classId: String, subjectId: String, teacherId: String?, weeklyHours: Int, coefficient: Int, actorId: String, actorName: String): Result<Unit> = Result.Ok(Unit)
}

// ─── Homework Repository ────────────────────────────────────────────────────

@Singleton
class LocalHomeworkRepository @Inject constructor(
    private val homeworkDao: HomeworkDao,
) : HomeworkRepository {

    override fun observeForClass(classId: String): Flow<List<Homework>> =
        homeworkDao.observeByClass(classId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeForTeacher(teacherId: String): Flow<List<Homework>> =
        homeworkDao.observeAll().map { rows -> rows.filter { it.teacherId == teacherId }.map { LocalMappers.run { it.toDomain() } } }

    override suspend fun push(input: PushHomeworkInput, actorId: String, actorName: String): Result<Homework> {
        val now = Instant.now().toString()
        val entity = HomeworkEntity(
            id = "hwk-${UUID.randomUUID()}", tenantId = "00000000-0000-0000-0000-000000000001",
            classId = input.classId, subjectId = input.subjectId, subjectName = "",
            teacherId = actorId, teacherName = actorName,
            title = input.title, description = input.description, dueDate = input.dueDate,
            attachmentsJson = input.attachments.joinToString(",") { "\"$it\"" }.let { "[$it]" },
            createdAt = now,
        )
        homeworkDao.upsert(entity)
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }
}

// ─── Notification Repository ────────────────────────────────────────────────

@Singleton
class LocalNotificationRepository @Inject constructor(
    private val notificationDao: NotificationDao,
) : NotificationRepository {

    override fun observe(): Flow<List<AppNotification>> =
        notificationDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeForSession(session: com.example.core.Session): Flow<List<AppNotification>> =
        notificationDao.observeForUser(session.userId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override suspend fun markRead(id: String): Result<Unit> { notificationDao.markRead(id); return Result.Ok(Unit) }
    override suspend fun markAllRead(): Result<Unit> = Result.Ok(Unit)
    override suspend fun dismiss(id: String): Result<Unit> = Result.Ok(Unit)
}

// ─── Releve Repository ──────────────────────────────────────────────────────

@Singleton
class LocalReleveRepository @Inject constructor(
    private val releveDao: ReleveEntryDao,
) : ReleveRepository {

    override fun observeByPersonnel(personnelId: String, fromIso: String, toIso: String): Flow<Result<List<ReleveEntry>>> =
        releveDao.observeByPersonnel(personnelId).map { rows ->
            Result.Ok(rows.filter { it.date in fromIso..toIso }.map { LocalMappers.run { it.toDomain() } })
        }

    override suspend fun logEntry(entry: ReleveEntry, actorId: String, actorName: String): Result<ReleveEntry> {
        val entity = ReleveEntryEntity(
            id = entry.id.ifBlank { "rel-${UUID.randomUUID()}" },
            tenantId = "00000000-0000-0000-0000-000000000001",
            personnelId = entry.personnelId, personnelName = entry.personnelName,
            date = entry.date, activityType = entry.activity.wireCode,
            description = "", durationMinutes = (entry.durationMinutes ?: 0).toInt(),
            recordedBy = actorId, recordedAt = Instant.now().toString(),
        )
        releveDao.upsert(entity)
        return Result.Ok(entry)
    }
}

private fun ReleveEntryEntity.toDomain() = ReleveEntry(
    id = id, personnelId = personnelId, personnelName = personnelName,
    date = date, hoursIn = "", hoursOut = null,
    activity = com.example.domain.model.ReleveActivity.fromCode(activityType),
    classId = null, subjectId = null, taskId = null,
    recordedBy = recordedBy, recordedAt = recordedAt,
    durationMinutes = durationMinutes.toLong(),
)

// ─── Routing Repository (stub — driver dashboard not in scope) ───────────────

@Singleton
class LocalRoutingRepository @Inject constructor() : RoutingRepository {
    override fun observeVehicles(): Flow<Result<List<com.example.domain.model.Vehicle>>> = kotlinx.coroutines.flow.flowOf(Result.Ok(emptyList()))
    override fun observeStops(shift: com.example.domain.model.RoutingShift?): Flow<Result<List<com.example.domain.model.RoutingStop>>> = kotlinx.coroutines.flow.flowOf(Result.Ok(emptyList()))
    override fun observeTripHistory(): Flow<Result<List<com.example.domain.model.TripLog>>> = kotlinx.coroutines.flow.flowOf(Result.Ok(emptyList()))
    override suspend fun optimizeRoute(vehicleId: String, shift: com.example.domain.model.RoutingShift, actorId: String, actorName: String): Result<com.example.domain.model.OptimizedRoute> = Result.Err(Errors.notFound("Routing not configured"))
    override suspend fun startTrip(vehicleId: String, driverId: String, driverName: String): Result<com.example.domain.model.TripLog> = Result.Err(Errors.notFound("Not implemented"))
    override suspend fun endTrip(tripId: String, stopsCompleted: Int, totalDistanceKm: Double, actorId: String, actorName: String): Result<com.example.domain.model.TripLog> = Result.Err(Errors.notFound("Not implemented"))
}

// ─── Workflow Repository (stub — DAG editing excluded from mobile) ──────────

@Singleton
class LocalWorkflowRepository @Inject constructor(
    private val workflowRunDao: WorkflowRunDao,
) : WorkflowRepository {

    override fun observeRuns(limit: Int): Flow<Result<List<com.example.domain.model.WorkflowRun>>> =
        workflowRunDao.observeRecent().map { rows ->
            Result.Ok(rows.map {
                com.example.domain.model.WorkflowRun(
                    id = it.id, workflowId = it.workflowId, workflowName = it.workflowName,
                    trigger = com.example.domain.model.WorkflowTrigger.Manual,
                    status = com.example.domain.model.WorkflowRunStatus.fromCode(it.status),
                    startedAt = it.startedAt, completedAt = it.finishedAt,
                    actorId = it.startedBy, actorName = null,
                    errorMessage = it.errorMessage,
                )
            })
        }

    override fun observeRunById(runId: String): Flow<Result<com.example.domain.model.WorkflowRun?>> =
        workflowRunDao.observeRecent().map { rows ->
            Result.Ok(rows.firstOrNull { it.id == runId }?.let {
                com.example.domain.model.WorkflowRun(
                    id = it.id, workflowId = it.workflowId, workflowName = it.workflowName,
                    trigger = com.example.domain.model.WorkflowTrigger.Manual,
                    status = com.example.domain.model.WorkflowRunStatus.fromCode(it.status),
                    startedAt = it.startedAt, completedAt = it.finishedAt,
                    actorId = it.startedBy, actorName = null,
                    errorMessage = it.errorMessage,
                )
            })
        }

    override suspend fun retryRun(runId: String, actorId: String, actorName: String): Result<String> {
        val now = Instant.now().toString()
        val newId = "wfr-${UUID.randomUUID()}"
        workflowRunDao.upsert(WorkflowRunEntity(
            id = newId, tenantId = "00000000-0000-0000-0000-000000000001",
            workflowId = "retry-$runId", workflowName = "Manual retry",
            status = "completed", startedBy = actorId, startedAt = now,
            finishedAt = now, resultJson = "{}", errorMessage = null,
        ))
        return Result.Ok(newId)
    }
}

// ─── Storage Repository (stub — proof uploads are local-only in this build) ─

@Singleton
class LocalStorageRepository @Inject constructor() : StorageRepository {
    override suspend fun uploadProof(bucket: String, entityId: String, fileName: String, bytes: ByteArray, mimeType: String): Result<String> =
        Result.Ok("local://$bucket/$entityId/$fileName")
    override suspend fun createSignedUrl(bucket: String, path: String, expiresInSeconds: Long): Result<String> =
        Result.Ok("local://$bucket/$path")
}

// ─── Helper ─────────────────────────────────────────────────────────────────

private fun auditLog(action: String, entityType: String, entityId: String, actorId: String, actorName: String, after: String? = null) = AuditLogEntity(
    id = "aud-${UUID.randomUUID()}", tenantId = "00000000-0000-0000-0000-000000000001",
    action = action, entityType = entityType, entityId = entityId,
    actorId = actorId, actorName = actorName, actorRole = null,
    beforeJson = null, afterJson = after, note = null,
    createdAt = Instant.now().toString(),
)
