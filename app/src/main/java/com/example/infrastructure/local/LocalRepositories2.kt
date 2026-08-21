package com.example.infrastructure.local

import com.example.core.Errors
import com.example.core.Result
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
import com.example.infrastructure.room.WorkflowRunDao
import com.example.infrastructure.room.WorkflowRunEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

// ─── Dashboard Repository (Rich Real-Time KPI & Operations Computation) ───────

private data class DashboardGroup1(
    val students: List<StudentEntity>,
    val parents: List<ParentEntity>,
    val staff: List<PersonnelEntity>,
    val payments: List<PaymentEntity>,
)

private data class DashboardGroup2(
    val installments: List<InstallmentEntity>,
    val ledger: List<LedgerEntryEntity>,
    val expenses: List<ExpenseEntity>,
    val attendance: List<AttendanceEntity>,
    val classes: List<AcademicClassEntity>,
)

@Singleton
class LocalDashboardRepository @Inject constructor(
    private val db: ElImtiyazDatabase,
) : DashboardRepository {

    override fun observeKpis(): Flow<DashboardKpi?> = combine(
        combine(
            db.studentDao().observeAll(),
            db.parentDao().observeAll(),
            db.personnelDao().observeAll(),
            db.paymentDao().observeAll(),
        ) { students, parents, staff, payments ->
            DashboardGroup1(students, parents, staff, payments)
        },
        combine(
            db.installmentDao().observeAll(),
            db.ledgerEntryDao().observeAll(),
            db.expenseDao().observeAll(),
            db.attendanceDao().observeAll(),
            db.academicClassDao().observeAll(),
        ) { installments, ledger, expenses, attendance, classes ->
            DashboardGroup2(installments, ledger, expenses, attendance, classes)
        },
    ) { g1, g2 ->
        val todayIso = LocalDate.now(ZoneOffset.UTC).toString()
        val monthStart = OffsetDateTime.now(ZoneOffset.UTC)
            .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
            .toInstant().toString()
        // TIER 2 R16 — add upper bound for monthlyRevenue filter so future-dated
        // payments are NOT counted as current-month revenue. The audit (D53)
        // flagged that the 12-month chart applied the bound but the KPI filter
        // did not — internal inconsistency.
        val nextMonthStart = OffsetDateTime.now(ZoneOffset.UTC)
            .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
            .plusMonths(1).toInstant().toString()

        val activeStudents = g1.students.filter { it.status == "active" }
        val activeStaff = g1.staff.filter { it.status == "active" }
        val activeClasses = g2.classes.filter { it.isActive }

        val todayPayments = g1.payments.filter { it.status == "paid" && it.collectedAt.startsWith(todayIso) }
        val todayRevenue = todayPayments.sumOf { it.amount }
        val todayPaymentsCount = todayPayments.size

        // TIER 2 R16 — added the upper bound (`< nextMonthStart`).
        val monthlyPayments = g1.payments.filter {
            it.status == "paid" && it.collectedAt >= monthStart && it.collectedAt < nextMonthStart
        }
        val monthlyRevenue = monthlyPayments.sumOf { it.amount }

        val pendingChecks = g1.payments.filter { it.method == "check" && it.status == "pending" }
        val pendingChecksCount = pendingChecks.size
        val pendingChecksAmount = pendingChecks.sumOf { it.amount }

        val submittedExpenses = g2.expenses.filter { it.status == "submitted" }
        val pendingExpensesCount = submittedExpenses.size
        val pendingExpensesAmount = submittedExpenses.sumOf { it.amount }

        // TIER 2 R16 — replaced the naive Σ amounts with the canonical
        // `LedgerEngine.computeParentSummary` per parent. The previous code:
        //   g2.ledger.filter { it.type == "charge" || it.type == "payment" || it.type == "adjustment" }.sumOf { it.amount }
        // had three bugs (audit D51):
        //   1. Excluded refunds (type=refund) — refunds reduce what the parent owes.
        //   2. Included reversed originals (reversal entries negate originals, but
        //      the originals were still summed).
        //   3. Didn't aggregate per-account before summing — `computeParentSummary`
        //      applies the canonical per-account balance replay.
        val domainLedger = g2.ledger.map { LocalMappers.run { it.toDomain() } }
        val parentIds = domainLedger.map { it.parentId }.distinct()
        val totalOutstanding = parentIds.sumOf { pid ->
            val parentEntries = domainLedger.filter { it.parentId == pid }
            LedgerEngine.computeParentSummary(parentEntries, pid, "").totalOutstanding.coerceAtLeast(0L)
        }

        // TIER 2 R16 — overdue: canonical rule (INV-4) classifies an account as
        // overdue when balance > 0 AND the latest charge's due date is past.
        // `computeParentSummary` already applies this rule via `totalOverdue`,
        // so we use it directly instead of the previous naive installment-filter.
        val overdueDebt = parentIds.sumOf { pid ->
            val parentEntries = domainLedger.filter { it.parentId == pid }
            LedgerEngine.computeParentSummary(parentEntries, pid, "").totalOverdue.coerceAtLeast(0L)
        }
        val overdueFamiliesCount = parentIds.count { pid ->
            val parentEntries = domainLedger.filter { it.parentId == pid }
            LedgerEngine.computeParentSummary(parentEntries, pid, "").totalOverdue > 0L
        }

        val todayAttendance = g2.attendance.filter { it.date == todayIso }
        val todayPresent = todayAttendance.count { it.status == "present" }
        val todayAbsent = todayAttendance.count { it.status == "absent_unexcused" || it.status == "absent_excused" }
        // TIER 2 R16 — removed the fabricated `96.5` fallback (audit D54).
        // When no attendance records exist for today, return 0.0 (truthful)
        // rather than inventing a 96.5% rate. The UI can choose to display
        // "—" when the rate is 0 AND todayAttendance is empty.
        val attendanceRateToday = if (todayAttendance.isNotEmpty()) {
            (todayPresent.toDouble() / todayAttendance.size.toDouble() * 100.0)
        } else {
            0.0
        }
        val classesWithRollCall = todayAttendance.map { it.classId }.distinct().size

        DashboardKpi(
            // TIER 2 R16 — removed the fabricated fallback values (audit D54):
            //   `if (activeStudents.isNotEmpty()) activeStudents.size else 390`
            // returned 390 (fake) when Room was empty. Now returns the real
            // count (0 when empty). Same fix for `totalParents`, `totalStaff`,
            // `totalClassesCount`.
            totalStudents = activeStudents.size,
            totalParents = g1.parents.size,
            totalStaff = activeStaff.size,
            monthlyRevenue = monthlyRevenue,
            todayRevenue = todayRevenue,
            todayPaymentsCount = todayPaymentsCount,
            outstandingDebt = totalOutstanding.coerceAtLeast(0L),
            overdueDebt = overdueDebt,
            overdueFamiliesCount = overdueFamiliesCount,
            pendingExpenses = pendingExpensesCount,
            pendingExpensesAmount = pendingExpensesAmount,
            attendanceRateToday = attendanceRateToday,
            todayPresentCount = todayPresent,
            todayAbsentCount = todayAbsent,
            classesCompletedRollCall = classesWithRollCall,
            totalClassesCount = activeClasses.size,
            pendingChecksCount = pendingChecksCount,
            pendingChecksAmount = pendingChecksAmount,
            overdueAlerts = overdueFamiliesCount,
        )
    }

    override fun observeRevenueLast12Months(): Flow<List<com.example.domain.repository.RevenuePoint>> =
        db.paymentDao().observeAll().map { payments ->
            val now = LocalDate.now(ZoneOffset.UTC)
            val months = (11 downTo 0).map { monthsBack ->
                val target = now.minusMonths(monthsBack.toLong())
                val monthStart = OffsetDateTime.of(target.year, target.monthValue, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toString()
                val nextMonthStart = OffsetDateTime.of(target.year, target.monthValue, 1, 0, 0, 0, 0, ZoneOffset.UTC).plusMonths(1).toInstant().toString()
                val sum = payments.filter { it.status == "paid" && it.collectedAt >= monthStart && it.collectedAt < nextMonthStart }.sumOf { it.amount }
                val label = when (target.monthValue) {
                    1 -> "Jan"
                    2 -> "Fév"
                    3 -> "Mar"
                    4 -> "Avr"
                    5 -> "Mai"
                    6 -> "Juin"
                    7 -> "Juil"
                    8 -> "Août"
                    9 -> "Sept"
                    10 -> "Oct"
                    11 -> "Nov"
                    12 -> "Déc"
                    else -> "${target.monthValue}"
                }
                com.example.domain.repository.RevenuePoint(label = label, amount = sum)
            }
            // TIER 2 R16 — removed the fabricated 6-month revenue fallback
            // (audit D55). When Room is empty (or has no paid payments), the
            // chart should show zeros — NOT the fake values "Sept=13.4M DZD"
            // etc. — because those numbers mislead the user into thinking real
            // data exists. Real revenue numbers come from real payments.
            months
        }

    override fun observePaymentMethodsSummary(): Flow<List<PaymentMethodSummary>> =
        db.paymentDao().observeAll().map { payments ->
            val paidPayments = payments.filter { it.status == "paid" }
            val totalSum = paidPayments.sumOf { it.amount }.toDouble()
            val methods = listOf(
                "cash" to "Espèces",
                "check" to "Chèques",
                "transfer" to "Virements",
            )
            methods.map { (code, label) ->
                val matching = paidPayments.filter { it.method.lowercase() == code }
                val amount = matching.sumOf { it.amount }
                val count = matching.size
                val percentage = if (totalSum > 0.0) (amount.toDouble() / totalSum * 100.0) else 0.0
                PaymentMethodSummary(
                    method = code,
                    label = label,
                    count = count,
                    totalAmount = amount,
                    percentage = percentage,
                )
            }
        }

    override fun observeClassRollCallStatus(): Flow<List<ClassRollCallStatus>> = combine(
        db.academicClassDao().observeAll(),
        db.studentDao().observeAll(),
        db.attendanceDao().observeAll(),
    ) { classes, students, attendance ->
        val todayIso = LocalDate.now(ZoneOffset.UTC).toString()
        val todayAttendance = attendance.filter { it.date == todayIso }

        classes.filter { it.isActive }.map { cls ->
            val classStudents = students.filter { it.classId == cls.id && it.status == "active" }
            val classAttendance = todayAttendance.filter { it.classId == cls.id }
            val isCompleted = classAttendance.isNotEmpty()
            val presentCount = classAttendance.count { it.status == "present" }
            val absentCount = classAttendance.count { it.status == "absent_unexcused" || it.status == "absent_excused" }
            val lateCount = classAttendance.count { it.status == "late" }

            ClassRollCallStatus(
                classId = cls.id,
                className = cls.name,
                level = cls.level,
                totalStudents = if (classStudents.isNotEmpty()) classStudents.size else cls.capacity,
                isCompletedToday = isCompleted,
                presentCount = if (isCompleted) presentCount else 0,
                absentCount = if (isCompleted) absentCount else 0,
                lateCount = if (isCompleted) lateCount else 0,
            )
        }.sortedWith(compareBy({ it.level }, { it.className }))
    }

    override fun observeOperationalAlerts(): Flow<List<DashboardOperationalAlert>> = combine(
        combine(
            db.parentDao().observeAll(),
            db.installmentDao().observeAll(),
            db.expenseDao().observeAll(),
        ) { parents, installments, expenses ->
            Triple(parents, installments, expenses)
        },
        combine(
            db.paymentDao().observeAll(),
            db.attendanceDao().observeAll(),
            db.academicClassDao().observeAll(),
        ) { payments, attendance, classes ->
            Triple(payments, attendance, classes)
        },
    ) { (parents, installments, expenses), (payments, attendance, classes) ->
        val nowIso = Instant.now().toString()
        val todayIso = LocalDate.now(ZoneOffset.UTC).toString()
        val alerts = mutableListOf<DashboardOperationalAlert>()

        // 1. Overdue Debt Alerts (top overdue parents with phone numbers for 1-tap call)
        val overdueInstallments = installments.filter { it.status != "paid" && it.dueDate < nowIso }
        val overdueByParent = overdueInstallments.groupBy { it.parentId }
        overdueByParent.entries
            .mapNotNull { (parentId, insts) ->
                val parent = parents.firstOrNull { it.id == parentId } ?: return@mapNotNull null
                val totalOverdue = insts.sumOf { (it.amountDue - it.amountPaid).coerceAtLeast(0L) }
                val oldestDue = insts.minOfOrNull { it.dueDate } ?: nowIso
                val daysOverdue = daysBetweenFloor(oldestDue)
                Triple(parent, totalOverdue, daysOverdue)
            }
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
            .take(4)
            .forEach { (parent, totalOverdue, daysOverdue) ->
                alerts.add(
                    DashboardOperationalAlert(
                        id = "alert-debt-${parent.id}",
                        type = "overdue_debt",
                        title = "Échéance impayée : ${parent.fullName}",
                        description = "Retard de $daysOverdue jours sur les tranches (${(totalOverdue / 100).formatDzd()} DZD restant).",
                        amount = totalOverdue,
                        phone = parent.phone,
                        severity = if (daysOverdue > 30) "urgent" else "high",
                        entityType = "parent",
                        entityId = parent.id,
                        actionLabel = "Relancer",
                    )
                )
            }

        // 2. Pending Expenses Approval
        expenses.filter { it.status == "submitted" }.take(3).forEach { exp ->
            alerts.add(
                DashboardOperationalAlert(
                    id = "alert-exp-${exp.id}",
                    type = "pending_expense",
                    title = "Dépense à valider : ${exp.title}",
                    description = "Demande de ${(exp.amount / 100).formatDzd()} DZD pour ${exp.payee} soumise par ${exp.submittedByName}.",
                    amount = exp.amount,
                    severity = if (exp.urgency == "high" || exp.urgency == "critical") "urgent" else "medium",
                    entityType = "expense",
                    entityId = exp.id,
                    actionLabel = "Examiner",
                )
            )
        }

        // 3. Pending Bank Checks to Deposit
        val pendingChecks = payments.filter { it.method == "check" && it.status == "pending" }
        if (pendingChecks.isNotEmpty()) {
            val totalPendingChecks = pendingChecks.sumOf { it.amount }
            alerts.add(
                DashboardOperationalAlert(
                    id = "alert-pending-checks",
                    type = "pending_check",
                    title = "${pendingChecks.size} chèque(s) en attente de dépôt",
                    description = "Total de ${(totalPendingChecks / 100).formatDzd()} DZD en chèques à déposer pour compensation bancaire.",
                    amount = totalPendingChecks,
                    count = pendingChecks.size,
                    severity = "medium",
                    entityType = "financials",
                    entityId = "checks",
                    actionLabel = "Voir chèques",
                )
            )
        }

        // 4. Missing Roll Calls Today
        val todayAttendanceClasses = attendance.filter { it.date == todayIso }.map { it.classId }.toSet()
        val missingClasses = classes.filter { it.isActive && it.id !in todayAttendanceClasses }
        if (missingClasses.isNotEmpty()) {
            val classNames = missingClasses.take(3).joinToString(", ") { it.name }
            alerts.add(
                DashboardOperationalAlert(
                    id = "alert-missing-rollcall",
                    type = "missing_roll_call",
                    title = "Appel du jour non validé (${missingClasses.size} classe(s))",
                    description = "Classes en attente : $classNames${if (missingClasses.size > 3) "..." else ""}",
                    count = missingClasses.size,
                    severity = "medium",
                    entityType = "class",
                    entityId = missingClasses.first().id,
                    actionLabel = "Faire l'appel",
                )
            )
        }

        alerts
    }

    override fun observeDebtByAging(): Flow<List<DebtSummary>> = combine(
        db.parentDao().observeAll(),
        db.ledgerEntryDao().observeAll(),
        db.studentDao().observeAll(),
    ) { parents, ledgerEntries, students ->
        parents.map { parent ->
            val parentEntries = ledgerEntries.filter { it.parentId == parent.id }
            val studentCount = students.count { it.parentId == parent.id }
            val summary = LedgerEngine.computeParentSummary(parentEntries.map { LocalMappers.run { it.toDomain() } }, parent.id, parent.fullName)
            val maxDays = LedgerEngine.maxDaysOverdueFromLedger(parentEntries.map { LocalMappers.run { it.toDomain() } })
            DebtSummary(
                parentId = parent.id,
                parentName = parent.fullName,
                parentPhone = parent.phone,
                studentCount = studentCount,
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
        db.studentDao().observeAll(),
    ) { parents, ledgerEntries, students ->
        parents.map { parent ->
            val parentEntries = ledgerEntries.filter { it.parentId == parent.id }
            val studentCount = students.count { it.parentId == parent.id }
            val summary = LedgerEngine.computeParentSummary(parentEntries.map { LocalMappers.run { it.toDomain() } }, parent.id, parent.fullName)
            val maxDays = LedgerEngine.maxDaysOverdueFromLedger(parentEntries.map { LocalMappers.run { it.toDomain() } })
            DebtSummary(
                parentId = parent.id,
                parentName = parent.fullName,
                parentPhone = parent.phone,
                studentCount = studentCount,
                outstandingAmount = summary.totalOutstanding.coerceAtLeast(0L),
                daysOverdue = maxDays,
                bucket = agingBucketFromDays(maxDays),
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
            val domainEntries = ledgerEntries.map { LocalMappers.run { it.toDomain() } }
            val summary = LedgerEngine.computeParentSummary(domainEntries, parentId, parent.fullName)
            // TIER 2 R17 — populate `adjustments` from the ledger's adjustment
            // entries. Mirrors the desktop's `ParentFinancialProfile.adjustments`.
            // Filters out reversal entries (they negate originals — the
            // canonical `computeParentSummary` already excludes them from
            // totals, so we exclude them here too for UI consistency).
            val adjustments = domainEntries
                .filter { it.type == com.example.core.LedgerEntryType.ADJUSTMENT && it.reversesId == null }
                .map { e ->
                    com.example.domain.repository.AccountAdjustment(
                        id = e.id,
                        parentId = e.parentId,
                        amount = e.amount,
                        reason = e.description,
                        approvedBy = e.actorId,
                        approvedAt = e.at,
                        receiptRef = e.receiptNumber,
                    )
                }
                .sortedByDescending { it.approvedAt }
            ParentFinancialProfile(
                parentId = parentId,
                parentName = parent.fullName,
                totalDue = summary.totalCharged,
                totalPaid = summary.totalPaid,
                totalOutstanding = summary.totalOutstanding.coerceAtLeast(0L),
                overdueAmount = summary.totalOverdue.coerceAtLeast(0L),
                installments = installments.map { LocalMappers.run { it.toDomain() } },
                recentPayments = payments.map { LocalMappers.run { it.toDomain() } },
                adjustments = adjustments,
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
            id = "aud-${UUID.randomUUID()}", tenantId = "00000000-0000-0000-0000-000000000001",
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
        expenseDao.observeAll().map { rows -> rows.firstOrNull { it.id == id }?.let { e -> LocalMappers.run { e.toDomain() } } }

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

// ─── Routing Repository ──────────────────────────────────────────────────────

@Singleton
class LocalRoutingRepository @Inject constructor() : RoutingRepository {
    override fun observeVehicles(): Flow<Result<List<com.example.domain.model.Vehicle>>> = kotlinx.coroutines.flow.flowOf(Result.Ok(emptyList()))
    override fun observeStops(shift: com.example.domain.model.RoutingShift?): Flow<Result<List<com.example.domain.model.RoutingStop>>> = kotlinx.coroutines.flow.flowOf(Result.Ok(emptyList()))
    override fun observeTripHistory(): Flow<Result<List<com.example.domain.model.TripLog>>> = kotlinx.coroutines.flow.flowOf(Result.Ok(emptyList()))
    override suspend fun optimizeRoute(vehicleId: String, shift: com.example.domain.model.RoutingShift, actorId: String, actorName: String): Result<com.example.domain.model.OptimizedRoute> = Result.Err(Errors.notFound("Routing not configured"))
    override suspend fun startTrip(vehicleId: String, driverId: String, driverName: String): Result<com.example.domain.model.TripLog> = Result.Err(Errors.notFound("Not implemented"))
    override suspend fun endTrip(tripId: String, stopsCompleted: Int, totalDistanceKm: Double, actorId: String, actorName: String): Result<com.example.domain.model.TripLog> = Result.Err(Errors.notFound("Not implemented"))
}

// ─── Workflow Repository ─────────────────────────────────────────────────────

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

// ─── Storage Repository ─────────────────────────────────────────────────────

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